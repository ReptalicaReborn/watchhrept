import { EventEmitter } from "node:events";
import fs from "node:fs";
import path from "node:path";
import {
    CloseReason,
    LoginQRCallbackEventType,
    MuteAction,
    MuteDuration,
    Reactions,
    ThreadType,
    Zalo,
    type API,
    type Credentials,
    type DeliveredMessage,
    type LoginQRCallbackEvent,
    type Message,
    type Reaction,
    type SeenMessage,
    type SendMessageQuote,
    type Typing,
    type Undo,
} from "zca-js";
import { HttpError } from "./errors.js";
import {
    ChatStore,
    normalizeMessage,
    REACTIONS,
    recalledMessageId,
    threadKey,
    type ChatMessage,
    type MentionInfo,
    type RawMessage,
    type StoredMessage,
    type ThreadKind,
} from "./store.js";

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

export type TypingEvent = { threadKind: ThreadKind; threadId: string; uid: string; name: string };
export type DeletedEvent = { threadKind: ThreadKind; threadId: string; id: string };
export type GroupMember = { id: string; name: string };
export type MuteOption = "1h" | "4h" | "8am" | "forever";

type SessionEvents = {
    status: [SessionStatus];
    message: [ChatMessage];
    message_updated: [ChatMessage];
    message_deleted: [DeletedEvent];
    typing: [TypingEvent];
    threads: [];
};

const GROUP_INFO_BATCH = 50;
const MEMBER_INFO_BATCH = 100;
const MEMBERS_CACHE_MS = 10 * 60_000;
const SEEN_RECEIPT_LIMIT = 50;

export class ZaloSession extends EventEmitter<SessionEvents> {
    private api: API | null = null;
    private loginInFlight: Promise<void> | null = null;
    private abortQR: (() => unknown) | null = null;
    private members = new Map<string, { at: number; list: GroupMember[] }>();
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
        private readonly sendReadReceipts = true,
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
        this.members.clear();
        fs.rmSync(this.credentialsFile, { force: true });
        this.store.clear();
        this.setStatus({ state: "logged_out", qrImage: null, scannedBy: null, ownId: null, ownName: null, error: null });
    }

    /** Reconnect the real-time listener, e.g. after Zalo Web was opened elsewhere and kicked us. */
    reconnect(): void {
        if (!this.api) throw new HttpError(409, "Not logged in");
        this.api.listener.start({ retryOnClose: true });
    }

    // ---- Messages ----

    async sendText(
        kind: ThreadKind,
        threadId: string,
        text: string,
        opts: { quoteId?: string; mentions?: MentionInfo[] } = {},
    ): Promise<string | null> {
        const api = this.requireApi();
        let quote: SendMessageQuote | undefined;
        if (opts.quoteId) {
            const q = this.requireMessage(kind, threadId, opts.quoteId);
            if (q.recalled) throw new HttpError(400, "Can't reply to a recalled message");
            quote = {
                content: q.raw.content,
                msgType: q.raw.msgType,
                propertyExt: q.raw.propertyExt,
                uidFrom: q.raw.uidFrom,
                msgId: q.id,
                cliMsgId: q.cliMsgId,
                ts: q.raw.ts,
                ttl: q.raw.ttl,
            } as SendMessageQuote;
        }
        const mentions = kind === "group" ? opts.mentions : undefined;
        const res = await api.sendMessage({ msg: text, quote, mentions }, threadId, toThreadType(kind));
        // The message itself comes back through the listener (selfListen), which stores it.
        return res.message ? String(res.message.msgId) : null;
    }

    /** `reaction` is a key of REACTIONS, or null to remove ours. */
    async react(kind: ThreadKind, threadId: string, msgId: string, reaction: string | null): Promise<void> {
        const api = this.requireApi();
        const msg = this.requireMessage(kind, threadId, msgId);
        const code = reaction === null ? Reactions.NONE : (REACTIONS[reaction]?.code as Reactions | undefined);
        if (code === undefined) throw new HttpError(400, `Unknown reaction: ${reaction}`);

        await api.addReaction(code, {
            data: { msgId: msg.id, cliMsgId: msg.cliMsgId },
            threadId,
            type: toThreadType(kind),
        });
        // Apply right away; the listener echo is idempotent.
        this.updated(this.store.applyReaction(msg.id, this.status.ownId ?? "", code));
    }

    /** Recall ("thu hồi") one of our own messages for everyone. */
    async recall(kind: ThreadKind, threadId: string, msgId: string): Promise<void> {
        const api = this.requireApi();
        const msg = this.requireMessage(kind, threadId, msgId);
        if (!msg.isSelf) throw new HttpError(403, "You can only recall your own messages");
        await api.undo({ msgId: msg.id, cliMsgId: msg.cliMsgId }, threadId, toThreadType(kind));
        this.updated(this.store.recall(msg.id));
    }

    /** Delete a message from this account's view only. */
    async deleteForMe(kind: ThreadKind, threadId: string, msgId: string): Promise<void> {
        const api = this.requireApi();
        const msg = this.requireMessage(kind, threadId, msgId);
        await api.deleteMessage(
            { data: { cliMsgId: msg.cliMsgId, msgId: msg.id, uidFrom: msg.raw.uidFrom }, threadId, type: toThreadType(kind) },
            true,
        );
        if (this.store.removeMessage(msg.id)) this.emit("message_deleted", { threadKind: kind, threadId, id: msg.id });
    }

    async forward(
        kind: ThreadKind,
        threadId: string,
        msgId: string,
        targets: { kind: ThreadKind; id: string }[],
    ): Promise<void> {
        const api = this.requireApi();
        const msg = this.requireMessage(kind, threadId, msgId);
        if (typeof msg.raw.content !== "string" || msg.recalled) {
            throw new HttpError(400, "Only text messages can be forwarded");
        }
        const payload = {
            message: msg.raw.content,
            reference: { id: msg.id, ts: msg.ts, logSrcType: 1, fwLvl: 1 },
        };
        for (const targetKind of ["user", "group"] as const) {
            const ids = targets.filter((t) => t.kind === targetKind).map((t) => t.id);
            if (!ids.length) continue;
            const res = await api.forwardMessage(payload, ids, toThreadType(targetKind));
            if (res.fail?.length) throw new HttpError(502, `Forwarding failed for ${res.fail.length} chat(s)`);
        }
    }

    async sendTyping(kind: ThreadKind, threadId: string): Promise<void> {
        await this.requireApi().sendTypingEvent(threadId, toThreadType(kind));
    }

    /** Pull recent group history from Zalo. 1:1 chats have no such endpoint. */
    async fetchGroupHistory(groupId: string, count = 50): Promise<void> {
        const api = this.requireApi();
        const res = await api.getGroupChatHistory(groupId, count);
        for (const raw of res.groupMsgs) this.ingest(raw);
        if (res.groupMsgs.length > 0) this.emit("threads");
    }

    // ---- Conversations ----

    async markRead(kind: ThreadKind, threadId: string): Promise<void> {
        const unread = this.store.unreadMessages(kind, threadId);
        const wasMarkedUnread = this.store.markRead(kind, threadId);
        if (!this.api) return;

        // Let the sender see "seen", as the official app does. Best effort.
        const withRaw = unread.filter((m): m is StoredMessage & { raw: NonNullable<StoredMessage["raw"]> } => !!m.raw);
        if (this.sendReadReceipts && withRaw.length) {
            const params = withRaw.slice(-SEEN_RECEIPT_LIMIT).map((m) => ({
                msgId: m.id,
                cliMsgId: m.cliMsgId,
                uidFrom: m.raw.uidFrom,
                idTo: m.raw.idTo,
                msgType: m.raw.msgType,
                st: m.raw.st,
                at: m.raw.at,
                cmd: m.raw.cmd,
                ts: m.raw.ts,
            }));
            await this.api.sendSeenEvent(params, toThreadType(kind)).catch((err) => console.warn("sendSeenEvent:", err));
        }
        if (wasMarkedUnread) {
            await this.api.removeUnreadMark(threadId, toThreadType(kind)).catch(() => {});
        }
    }

    async markUnread(kind: ThreadKind, threadId: string): Promise<void> {
        await this.requireApi().addUnreadMark(threadId, toThreadType(kind));
        this.store.markUnread(kind, threadId);
        this.emit("threads");
    }

    async setPinned(kind: ThreadKind, threadId: string, pinned: boolean): Promise<void> {
        await this.requireApi().setPinnedConversations(pinned, threadId, toThreadType(kind));
        this.store.setPinned(kind, threadId, pinned);
        this.emit("threads");
    }

    async setMuted(kind: ThreadKind, threadId: string, option: MuteOption | null): Promise<void> {
        const api = this.requireApi();
        if (option === null) {
            await api.setMute({ action: MuteAction.UNMUTE }, threadId, toThreadType(kind));
            this.store.setMuted(kind, threadId, null);
        } else {
            const duration = {
                "1h": MuteDuration.ONE_HOUR,
                "4h": MuteDuration.FOUR_HOURS,
                "8am": MuteDuration.UNTIL_8AM,
                forever: MuteDuration.FOREVER,
            }[option];
            await api.setMute({ action: MuteAction.MUTE, duration }, threadId, toThreadType(kind));
            this.store.setMuted(kind, threadId, muteUntil(option));
        }
        this.emit("threads");
    }

    /** Delete the whole conversation history (for this account). */
    async deleteConversation(kind: ThreadKind, threadId: string): Promise<void> {
        const api = this.requireApi();
        const last = this.store.lastMessage(kind, threadId);
        if (last?.raw) {
            await api.deleteChat(
                { ownerId: last.raw.uidFrom, cliMsgId: last.cliMsgId, globalMsgId: last.id },
                threadId,
                toThreadType(kind),
            );
        }
        this.store.clearThreadMessages(kind, threadId);
        this.emit("threads");
    }

    async lastOnline(userId: string): Promise<number | null> {
        const res = await this.requireApi().lastOnline(userId);
        return res.settings?.show_online_status && res.lastOnline ? res.lastOnline : null;
    }

    async groupMembers(groupId: string): Promise<GroupMember[]> {
        const cached = this.members.get(groupId);
        if (cached && Date.now() - cached.at < MEMBERS_CACHE_MS) return cached.list;

        const api = this.requireApi();
        const info = (await api.getGroupInfo(groupId)).gridInfoMap[groupId];
        if (!info) throw new HttpError(404, "Group not found");

        let list: GroupMember[] = (info.currentMems ?? []).map((m) => ({ id: String(m.id), name: m.dName || m.zaloName }));
        if (list.length === 0) {
            // memVerList entries look like "<uid>_<version>".
            const ids = (info.memVerList ?? info.memberIds ?? []).map((v) => String(v).split("_")[0]);
            for (let i = 0; i < ids.length; i += MEMBER_INFO_BATCH) {
                const res = await api.getGroupMembersInfo(ids.slice(i, i + MEMBER_INFO_BATCH));
                for (const [id, p] of Object.entries(res.profiles)) list.push({ id, name: p.displayName || p.zaloName });
            }
        }
        const ownId = this.status.ownId;
        list = list.filter((m) => m.id !== ownId).sort((a, b) => a.name.localeCompare(b.name));
        this.members.set(groupId, { at: Date.now(), list });
        return list;
    }

    // ---- Internals ----

    private requireApi(): API {
        if (!this.api || (this.status.state !== "ready" && this.status.state !== "disconnected")) {
            throw new HttpError(409, "Not logged in");
        }
        return this.api;
    }

    private requireMessage(
        kind: ThreadKind,
        threadId: string,
        msgId: string,
    ): StoredMessage & { raw: NonNullable<StoredMessage["raw"]> } {
        const msg = this.store.getMessage(msgId);
        if (!msg || msg.threadKind !== kind || msg.threadId !== threadId) throw new HttpError(404, "Message not found");
        if (!msg.raw) throw new HttpError(400, "This message is too old to act on");
        return msg as StoredMessage & { raw: NonNullable<StoredMessage["raw"]> };
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
        this.store.ownId = ownId;
        let ownName: string | null = null;
        try {
            ownName = (await api.fetchAccountInfo()).profile.displayName;
        } catch {
            // Not essential.
        }

        const { listener } = api;
        // Ignore stragglers from a session that was logged out.
        const live = () => this.api === api;
        listener.on("message", (msg) => live() && this.ingest(msg));
        listener.on("old_messages", (msgs) => live() && msgs.forEach((m) => this.ingest(m)));
        listener.on("undo", (undo) => live() && this.onUndo(undo));
        listener.on("reaction", (r) => live() && this.onReaction(r));
        listener.on("old_reactions", (rs) => live() && rs.forEach((r) => this.onReaction(r)));
        listener.on("typing", (t) => live() && this.onTyping(t));
        listener.on("seen_messages", (seen) => live() && seen.forEach((s) => this.onSeen(s)));
        listener.on("delivered_messages", (ds) => live() && ds.forEach((d) => this.onDelivered(d)));
        listener.on("group_event", (e) => {
            if (!live() || !e.threadId) return;
            this.members.delete(e.threadId);
            this.loadGroups(api, [e.threadId]).then(() => this.emit("threads"), () => {});
        });
        listener.on("connected", () => {
            this.setStatus({ state: "ready", error: null });
            // Ask the server to replay messages and reactions we missed while offline.
            listener.requestOldMessages(ThreadType.User);
            listener.requestOldMessages(ThreadType.Group);
            listener.requestOldReactions(ThreadType.User);
            listener.requestOldReactions(ThreadType.Group);
        });
        listener.on("closed", (code, reason) => {
            if (!live()) return;
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
        const rawMsg = raw as unknown as RawMessage;
        // Replayed history includes recall records as ordinary messages.
        const recalledId = recalledMessageId(rawMsg);
        if (recalledId) {
            this.updated(this.store.recall(recalledId));
            return;
        }
        const msg = normalizeMessage(rawMsg);
        if (this.store.addMessage(msg)) this.emit("message", this.store.toPublic(msg));
    }

    private onUndo(undo: Undo): void {
        this.updated(this.store.recall(String(undo.data.content.globalMsgId)));
    }

    private onReaction(r: Reaction): void {
        for (const target of r.data.content?.rMsg ?? []) {
            this.updated(this.store.applyReaction(String(target.gMsgID), String(r.data.uidFrom), r.data.content.rIcon));
        }
    }

    private onTyping(t: Typing): void {
        const kind: ThreadKind = t.type === ThreadType.Group ? "group" : "user";
        const uid = String(t.data.uid);
        const name =
            kind === "user"
                ? (this.store.getThread("user", uid)?.name ?? "")
                : (this.members.get(t.threadId)?.list.find((m) => m.id === uid)?.name ??
                  this.store.getThread("user", uid)?.name ??
                  "");
        this.emit("typing", { threadKind: kind, threadId: String(t.threadId), uid, name });
    }

    private onSeen(s: SeenMessage): void {
        const ownId = this.status.ownId;
        if (s.type === ThreadType.Group) {
            const seenBy = s.data.seenUids.map(String).filter((u) => u !== ownId);
            this.receipt("group", s.threadId, s.data.msgId, { seenBy });
            // Read on another device: keep our unread count in sync.
            if (s.isSelf) {
                const msg = this.store.getMessage(String(s.data.msgId));
                if (msg) {
                    this.store.markRead("group", s.threadId, msg.ts);
                    this.emit("threads");
                }
            }
        } else {
            this.receipt("user", s.threadId, s.data.msgId, { seenBy: [s.threadId] });
        }
    }

    private onDelivered(d: DeliveredMessage): void {
        const kind: ThreadKind = d.type === ThreadType.Group ? "group" : "user";
        const ownId = this.status.ownId;
        const others = (ids: string[]) => ids.map(String).filter((u) => u !== ownId);
        this.receipt(kind, d.threadId, d.data.msgId, {
            deliveredTo: others(d.data.deliveredUids ?? []),
            seenBy: others(d.data.seenUids ?? []),
        });
    }

    private receipt(kind: ThreadKind, threadId: string, msgId: string, r: { deliveredTo?: string[]; seenBy?: string[] }) {
        for (const m of this.store.applyReceipt(kind, String(threadId), String(msgId), r)) this.updated(m);
    }

    private updated(msg: StoredMessage | undefined): void {
        if (msg) this.emit("message_updated", this.store.toPublic(msg));
    }

    private async loadContacts(api: API): Promise<void> {
        const friends = await api.getAllFriends();
        for (const f of friends) {
            this.store.upsertThread({ id: f.userId, kind: "user", name: f.displayName || f.zaloName, avatar: f.avatar });
        }
        await this.loadGroups(api, Object.keys((await api.getAllGroups()).gridVerMap));

        // Pinned and muted conversations are account settings shared with the phone app.
        try {
            const pins = await api.getPinConversations();
            this.store.replacePinned(
                pins.conversations.map((c) => threadKey(c.startsWith("g") ? "group" : "user", c.slice(1))),
            );
        } catch (err) {
            console.warn("Could not load pinned chats:", err);
        }
        try {
            const mute = await api.getMute();
            const entries: [string, number][] = [];
            for (const [kind, list] of [
                ["user", mute.chatEntries],
                ["group", mute.groupChatEntries],
            ] as const) {
                for (const e of list ?? []) {
                    const until = e.duration === -1 ? -1 : (e.startTime + e.duration) * 1000;
                    entries.push([threadKey(kind, String(e.id)), until]);
                }
            }
            this.store.replaceMuted(entries);
        } catch (err) {
            console.warn("Could not load muted chats:", err);
        }
        this.emit("threads");
    }

    private async loadGroups(api: API, groupIds: string[]): Promise<void> {
        for (let i = 0; i < groupIds.length; i += GROUP_INFO_BATCH) {
            const info = await api.getGroupInfo(groupIds.slice(i, i + GROUP_INFO_BATCH));
            for (const [id, g] of Object.entries(info.gridInfoMap)) {
                this.store.upsertThread({ id, kind: "group", name: g.name, avatar: g.avt });
            }
        }
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

/** When a mute set now with `option` ends, as epoch ms (-1 = until turned off). */
export function muteUntil(option: MuteOption, now = new Date()): number {
    switch (option) {
        case "1h":
            return now.getTime() + 3_600_000;
        case "4h":
            return now.getTime() + 4 * 3_600_000;
        case "forever":
            return -1;
        case "8am": {
            const next = new Date(now);
            next.setHours(8, 0, 0, 0);
            if (next <= now) next.setDate(next.getDate() + 1);
            return next.getTime();
        }
    }
}
