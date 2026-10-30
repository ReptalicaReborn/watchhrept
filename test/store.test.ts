import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { loadConfig } from "../src/config.js";
import { ChatStore, normalizeMessage, type ChatMessage, type RawMessage } from "../src/store.js";

function raw(overrides: Partial<RawMessage["data"]> = {}, top: Partial<Omit<RawMessage, "data">> = {}): RawMessage {
    return {
        type: 0,
        threadId: "100",
        isSelf: false,
        ...top,
        data: {
            msgId: "1",
            cliMsgId: "c1",
            msgType: "webchat",
            uidFrom: "100",
            dName: "Alice",
            ts: "1700000000000",
            content: "hello",
            ...overrides,
        },
    };
}

function msg(id: string, ts: number, extra: Partial<ChatMessage> = {}): ChatMessage {
    return { ...normalizeMessage(raw({ msgId: id, ts: String(ts) })), ...extra };
}

test("normalizeMessage keeps plain text", () => {
    const m = normalizeMessage(raw());
    assert.equal(m.text, "hello");
    assert.equal(m.threadKind, "user");
    assert.equal(m.ts, 1700000000000);
    assert.equal(m.attachment, undefined);
});

test("normalizeMessage labels attachments and group threads", () => {
    const m = normalizeMessage(
        raw(
            { msgType: "chat.photo", content: { title: "", href: "https://x/y.jpg", thumb: "https://x/t.jpg" } },
            { type: 1, threadId: "g1" },
        ),
    );
    assert.equal(m.threadKind, "group");
    assert.equal(m.threadId, "g1");
    assert.equal(m.text, "[Photo]");
    assert.deepEqual(m.attachment, { title: undefined, href: "https://x/y.jpg", thumb: "https://x/t.jpg" });
});

test("normalizeMessage falls back for unknown content", () => {
    const m = normalizeMessage(raw({ msgType: "chat.something.new", content: {} }));
    assert.equal(m.text, "[Unsupported message]");
});

test("ChatStore dedupes, sorts and counts unread", () => {
    const store = new ChatStore();
    assert.equal(store.addMessage(msg("2", 2000)), true);
    assert.equal(store.addMessage(msg("1", 1000)), true);
    assert.equal(store.addMessage(msg("1", 1000)), false);

    assert.deepEqual(
        store.getMessages("user", "100").map((m) => m.id),
        ["1", "2"],
    );
    const [thread] = store.listThreads();
    assert.equal(thread.name, "Alice");
    assert.equal(thread.unread, 2);
    assert.equal(thread.lastMessage?.id, "2");

    store.markRead("user", "100", 1500);
    assert.equal(store.listThreads()[0].unread, 1);

    // Replying marks everything up to the reply as read.
    store.addMessage(msg("3", 3000, { isSelf: true }));
    assert.equal(store.listThreads()[0].unread, 0);
});

test("ChatStore paginates with before/limit", () => {
    const store = new ChatStore();
    for (let i = 1; i <= 10; i++) store.addMessage(msg(String(i), i * 1000));
    assert.deepEqual(
        store.getMessages("user", "100", { before: 6000, limit: 2 }).map((m) => m.id),
        ["4", "5"],
    );
});

test("ChatStore keeps friend names over message display names", () => {
    const store = new ChatStore();
    store.upsertThread({ id: "100", kind: "user", name: "Alice Nguyen", avatar: "a.png" });
    store.addMessage(msg("1", 1000));
    assert.equal(store.listThreads()[0].name, "Alice Nguyen");
});

test("ChatStore persists to disk", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "watchhrept-"));
    const file = path.join(dir, "messages.json");
    try {
        const a = new ChatStore(file);
        a.addMessage(msg("1", 1000));
        a.flush();
        assert.equal(fs.statSync(file).mode & 0o777, 0o600);

        const b = new ChatStore(file);
        assert.equal(b.getMessages("user", "100").length, 1);
    } finally {
        fs.rmSync(dir, { recursive: true, force: true });
    }
});

test("loadConfig refuses a public host without a token", () => {
    assert.throws(() => loadConfig({ HOST: "0.0.0.0" }), /ACCESS_TOKEN/);
    assert.equal(loadConfig({ HOST: "0.0.0.0", ACCESS_TOKEN: "s3cret" }).accessToken, "s3cret");
    assert.equal(loadConfig({}).host, "127.0.0.1");
});
