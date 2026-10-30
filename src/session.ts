import { EventEmitter } from "node:events";
import fs from "node:fs";
import path from "node:path";
import {
    CloseReason,
    LoginQRCallbackEventType,
    ThreadType,
    Zalo,
    type API,
    type Credentials,
    type LoginQRCallbackEvent,
    type Message,
} from "zca-js";
import { ChatStore, normalizeMessage, type ChatMessage, type RawMessage, type ThreadKind } from "./store.js";

export type SessionState = "logged_out" | "awaiting_qr_scan" | "logging_in" | "ready" | "disconnected";

export type SessionStatus = {
    state: SessionState;
    /** PNG data URL of the login QR code while state is "awaiting_qr_scan". */
    qrImage: string | null;
    /** Set once the QR code has been scanned on the phone, before it is confirmed. */
    scannedBy: string | null;
    ownId: string | null;
    ownName: string | null;
    error: string | null;
};

type SessionEvents = {
    status: [SessionStatus];
    message: [ChatMessage];
    threads: [];
};

const GROUP_INFO_BATCH = 50;

export class ZaloSession extends EventEmitter<SessionEvents> {
    private api: API | null = null;
    private loginInFlight: Promise<void> | null = null;
    private abortQR: (() => unknown) | null = null;
    private status: SessionStatus = {
        state: "logged_out",
        qrImage: null,
        scannedBy: null,
        ownId: null,
        ownName: null,
        error: null,
    };

    private readonly credentialsFile: string;

    constructor(
        readonly store: ChatStore,
        dataDir: string,
    ) {
        super();
        this.credentialsFile = path.join(dataDir, "credentials.json");
    }

    getStatus(): SessionStatus {
        return { ...this.status };
    }

    /** Resume a saved session if there is one; otherwise stay logged out until a QR login is requested. */
    async resume(): Promise<void> {
        const credentials = this.readCredentials();
        if (!credentials) return;
        await this.runLogin(async () => {
            const api = await this.newZalo().login(credentials);
            await this.onLoggedIn(api);
        });
    }

    /** Start a QR login. The QR image is published through status updates. */
    startQRLogin(): Promise<void> {
        return this.runLogin(async () => {
            const api = await this.newZalo().loginQR({}, (event) => this.onQREvent(event));
            if (!api) throw new Error("QR login was cancelled");
            await this.onLoggedIn(api);
        });
    }

    async logout(): Promise<void> {
        this.abortQR?.();
        this.api?.listener.stop();
        this.api = null;
        fs.rmSync(this.credentialsFile, { force: true });
        this.store.clear();
        this.setStatus({ state: "logged_out", qrImage: null, scannedBy: null, ownId: null, ownName: null, error: null });
    }

    /** Reconnect the real-time listener, e.g. after Zalo Web was opened elsewhere and kicked us. */
    reconnect(): void {
        if (!this.api) throw new Error("Not logged in");
        this.api.listener.start({ retryOnClose: true });
    }

    async sendText(kind: ThreadKind, threadId: string, text: string): Promise<string | null> {
        const api = this.requireApi();
        const res = await api.sendMessage({ msg: text }, threadId, toThreadType(kind));
        // The message itself comes back through the listener (selfListen), which stores it.
        return res.message ? String(res.message.msgId) : null;
    }

    /** Pull recent group history from Zalo. 1:1 chats have no such endpoint. */
    async fetchGroupHistory(groupId: string, count = 50): Promise<void> {
        const api = this.requireApi();
        const res = await api.getGroupChatHistory(groupId, count);
        for (const raw of res.groupMsgs) this.ingest(raw);
        if (res.groupMsgs.length > 0) this.emit("threads");
    }

    private requireApi(): API {
        if (!this.api || (this.status.state !== "ready" && this.status.state !== "disconnected")) {
            throw new Error("Not logged in");
        }
        return this.api;
    }

    private newZalo(): Zalo {
        return new Zalo({ selfListen: true, logging: false, checkUpdate: false });
    }

    private runLogin(fn: () => Promise<void>): Promise<void> {
        if (this.loginInFlight) return this.loginInFlight;
        if (this.status.state === "ready") return Promise.resolve();

        this.setStatus({ state: "logging_in", qrImage: null, scannedBy: null, error: null });
        this.loginInFlight = fn()
            .catch((err: unknown) => {
                this.api = null;
                this.setStatus({
                    state: "logged_out",
                    qrImage: null,
                    scannedBy: null,
                    error: err instanceof Error ? err.message : String(err),
                });
            })
            .finally(() => {
                this.loginInFlight = null;
                this.abortQR = null;
            });
        return this.loginInFlight;
    }

    private onQREvent(event: LoginQRCallbackEvent): void {
        switch (event.type) {
            case LoginQRCallbackEventType.QRCodeGenerated:
                this.abortQR = event.actions.abort;
                this.setStatus({
                    state: "awaiting_qr_scan",
                    qrImage: `data:image/png;base64,${event.data.image}`,
                    scannedBy: null,
                });
                break;
            case LoginQRCallbackEventType.QRCodeScanned:
                this.setStatus({ scannedBy: event.data.display_name });
                break;
            case LoginQRCallbackEventType.QRCodeExpired:
                // Get a fresh code rather than making the user restart.
                event.actions.retry();
                break;
            case LoginQRCallbackEventType.QRCodeDeclined:
                event.actions.abort();
                this.setStatus({ error: "Login was declined on the phone" });
                break;
            case LoginQRCallbackEventType.GotLoginInfo:
                this.writeCredentials({
                    imei: event.data.imei,
                    cookie: event.data.cookie,
                    userAgent: event.data.userAgent,
                });
                this.setStatus({ state: "logging_in", qrImage: null });
                break;
        }
    }

    private async onLoggedIn(api: API): Promise<void> {
        this.api = api;
        const ownId = api.getOwnId();
        let ownName: string | null = null;
        try {
            ownName = (await api.fetchAccountInfo()).profile.displayName;
        } catch {
            // Not essential.
        }

        const { listener } = api;
        // Ignore stragglers from a session that was logged out.
        listener.on("message", (msg) => this.api === api && this.ingest(msg));
        listener.on("old_messages", (msgs) => this.api === api && msgs.forEach((m) => this.ingest(m)));
        listener.on("connected", () => {
            this.setStatus({ state: "ready", error: null });
            // Ask the server to replay messages we missed while offline.
            listener.requestOldMessages(ThreadType.User);
            listener.requestOldMessages(ThreadType.Group);
        });
        listener.on("closed", (code, reason) => {
            if (this.api !== api) return;
            const error =
                code === CloseReason.DuplicateConnection || code === CloseReason.KickConnection
                    ? "Zalo Web was opened somewhere else. Only one web session can listen at a time."
                    : `Connection closed (${code}${reason ? `: ${reason}` : ""})`;
            this.setStatus({ state: "disconnected", error });
        });
        listener.on("error", (err) => console.error("Zalo listener error:", err));

        this.setStatus({ state: "ready", ownId, ownName, qrImage: null, scannedBy: null, error: null });
        listener.start({ retryOnClose: true });

        this.loadContacts(api).catch((err) => console.warn("Could not load contacts:", err));
    }

    private ingest(raw: Message): void {
        const msg = normalizeMessage(raw as unknown as RawMessage);
        if (this.store.addMessage(msg)) this.emit("message", msg);
    }

    private async loadContacts(api: API): Promise<void> {
        const friends = await api.getAllFriends();
        for (const f of friends) {
            this.store.upsertThread({ id: f.userId, kind: "user", name: f.displayName || f.zaloName, avatar: f.avatar });
        }

        const groupIds = Object.keys((await api.getAllGroups()).gridVerMap);
        for (let i = 0; i < groupIds.length; i += GROUP_INFO_BATCH) {
            const info = await api.getGroupInfo(groupIds.slice(i, i + GROUP_INFO_BATCH));
            for (const [id, g] of Object.entries(info.gridInfoMap)) {
                this.store.upsertThread({ id, kind: "group", name: g.name, avatar: g.avt });
            }
        }
        this.emit("threads");
    }

    private setStatus(patch: Partial<SessionStatus>): void {
        this.status = { ...this.status, ...patch };
        this.emit("status", this.getStatus());
    }

    private readCredentials(): Credentials | null {
        try {
            return JSON.parse(fs.readFileSync(this.credentialsFile, "utf8")) as Credentials;
        } catch {
            return null;
        }
    }

    private writeCredentials(credentials: Credentials): void {
        fs.mkdirSync(path.dirname(this.credentialsFile), { recursive: true, mode: 0o700 });
        fs.writeFileSync(this.credentialsFile, JSON.stringify(credentials), { mode: 0o600 });
    }
}

function toThreadType(kind: ThreadKind): ThreadType {
    return kind === "group" ? ThreadType.Group : ThreadType.User;
}
