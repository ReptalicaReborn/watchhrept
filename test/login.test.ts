import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { LoginQRCallbackEventType, ZaloApiError, ZaloApiLoginQRAborted } from "zca-js";
import { describeError, isTransientError, ZaloSession } from "../src/session.js";
import { ChatStore } from "../src/store.js";

function fetchFailed(code: string): Error {
    return new TypeError("fetch failed", { cause: Object.assign(new Error(`getaddrinfo ${code}`), { code }) });
}

type FakeZalo = { login?: () => Promise<unknown>; loginQR?: (opts: unknown, cb: (e: unknown) => void) => Promise<unknown> };

function setup(zalos: FakeZalo[], withCredentials = true) {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "watchhrept-login-"));
    if (withCredentials) {
        fs.writeFileSync(path.join(dir, "credentials.json"), JSON.stringify({ imei: "i", cookie: [], userAgent: "ua" }));
    }
    const session = new ZaloSession(new ChatStore(), dir);
    let n = 0;
    (session as unknown as { newZalo: () => FakeZalo }).newZalo = () => zalos[Math.min(n++, zalos.length - 1)]!;
    return { session, dir };
}

const tick = () => new Promise((r) => setImmediate(r));

test("isTransientError tells network failures from rejected sessions", () => {
    assert.equal(isTransientError(fetchFailed("ENOTFOUND")), true);
    assert.equal(isTransientError(new ZaloApiError("Failed to fetch login info: Bad Gateway")), true);
    assert.equal(isTransientError(new ZaloApiError("Đăng nhập thất bại")), false);
    assert.equal(isTransientError(new Error("Missing required params")), false);
});

test("describeError explains fetch failures", () => {
    assert.equal(describeError(fetchFailed("ENOTFOUND")), "no internet connection");
    assert.equal(describeError(fetchFailed("ECONNREFUSED")), "connection refused");
    assert.equal(describeError(new Error("boom")), "boom");
});

test("a saved session that can't reach Zalo is retried, not logged out", async () => {
    let attempts = 0;
    const { session, dir } = setup([
        { login: async () => { attempts++; throw fetchFailed("EAI_AGAIN"); } },
        { login: async () => { attempts++; throw new ZaloApiError("Đăng nhập thất bại"); } },
    ]);
    const done = session.resume();
    await tick();
    let s = session.getStatus();
    assert.equal(s.state, "logging_in");
    assert.equal(s.retrying, true);
    assert.match(s.error ?? "", /no internet connection/);
    assert.ok(fs.existsSync(path.join(dir, "credentials.json")), "credentials are kept");

    session.retryNow();
    await done;
    s = session.getStatus();
    assert.equal(attempts, 2);
    assert.equal(s.state, "logged_out");
    assert.equal(s.retrying, false);
    assert.match(s.error ?? "", /session has ended/);
});

test("a QR login replaces a resume that is waiting to retry", async () => {
    const { session } = setup([
        { login: async () => { throw fetchFailed("ENOTFOUND"); } },
        {
            loginQR: (_o, cb) => {
                cb({ type: LoginQRCallbackEventType.QRCodeGenerated, data: { image: "AAAA" }, actions: { abort() {}, retry() {} } });
                return new Promise(() => {});
            },
        },
    ]);
    const resumed = session.resume();
    await tick();
    assert.equal(session.getStatus().retrying, true);

    void session.startQRLogin();
    await resumed;
    await tick();
    const s = session.getStatus();
    assert.equal(s.state, "awaiting_qr_scan");
    assert.equal(s.retrying, false);
    assert.equal(s.qrImage, "data:image/png;base64,AAAA");
});

test("declining on the phone says so instead of 'cancelled'", async () => {
    const { session } = setup(
        [
            {
                loginQR: async (_o, cb) => {
                    let abort = () => {};
                    const aborted = new Promise((_r, reject) => (abort = () => reject(new ZaloApiLoginQRAborted())));
                    cb({ type: LoginQRCallbackEventType.QRCodeDeclined, data: { code: "c" }, actions: { abort, retry() {} } });
                    return aborted;
                },
            },
        ],
        false,
    );
    await session.startQRLogin();
    const s = session.getStatus();
    assert.equal(s.state, "logged_out");
    assert.match(s.error ?? "", /declined/);
});

test("logging out during a QR login leaves no error behind", async () => {
    let abort = () => {};
    const { session } = setup(
        [
            {
                loginQR: (_o, cb) =>
                    new Promise((_r, reject) => {
                        abort = () => reject(new ZaloApiLoginQRAborted());
                        cb({ type: LoginQRCallbackEventType.QRCodeGenerated, data: { image: "AAAA" }, actions: { abort, retry() {} } });
                    }),
            },
        ],
        false,
    );
    const login = session.startQRLogin();
    await tick();
    assert.equal(session.getStatus().state, "awaiting_qr_scan");
    await session.logout();
    await login;
    const s = session.getStatus();
    assert.equal(s.state, "logged_out");
    assert.equal(s.error, null);
});
