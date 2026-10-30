import fs from "node:fs";
import path from "node:path";

export type ThreadKind = "user" | "group";

export type QuoteInfo = {
    msgId: string;
    fromId: string;
    fromName: string;
    text: string;
};

export type MentionInfo = {
    uid: string;
    pos: number;
    len: number;
};

/**
 * The raw zca-js fields needed to quote, react to, recall, delete or mark a
 * message as seen. Kept server-side only.
 */
export type RawRef = {
    msgType: string;
    content: unknown;
    propertyExt: unknown;
    uidFrom: string;
    idTo: string;
    ts: string;
    ttl: number;
    st: number;
    at: number;
    cmd: number;
};

export type StoredMessage = {
    id: string;
    cliMsgId: string;
    threadId: string;
    threadKind: ThreadKind;
    fromId: string;
    fromName: string;
    isSelf: boolean;
    ts: number;
    msgType: string;
    /** Plain text, or a readable placeholder for non-text messages. */
    text: string;
    attachment?: {
        title?: string;
        href?: string;
        thumb?: string;
    };
    quote?: QuoteInfo;
    mentions?: MentionInfo[];
    recalled?: boolean;
    /** Latest reaction code per user id. */
    reactions?: Record<string, string>;
    deliveredTo?: string[];
    seenBy?: string[];
    /** Missing for messages cached before this field existed. */
    raw?: RawRef;
};

export type ReactionSummary = {
    key: string;
    emoji: string;
    count: number;
    mine: boolean;
};

export type MessageStatus = "sent" | "delivered" | "seen";

/** What clients see. */
export type ChatMessage = Omit<StoredMessage, "raw" | "reactions" | "deliveredTo" | "seenBy"> & {
    reactions: ReactionSummary[];
    mentionsMe: boolean;
    /** Only for messages sent by this account. */
    status?: MessageStatus;
    seenCount?: number;
};

export type ThreadInfo = {
    id: string;
    kind: ThreadKind;
    name: string;
    avatar: string;
};

export type ThreadSummary = ThreadInfo & {
    lastMessage: ChatMessage | null;
    unread: number;
    pinned: boolean;
    /** Epoch ms the mute ends, -1 for "until turned off", absent when not muted. */
    mutedUntil?: number;
};

/** Shape of a raw zca-js message (UserMessage/GroupMessage), kept loose on purpose. */
export type RawMessage = {
    type: number;
    threadId: string;
    isSelf: boolean;
    data: {
        msgId: string;
        cliMsgId: string;
        msgType: string;
        uidFrom: string;
        idTo?: string;
        dName: string;
        ts: string | number;
        content: unknown;
        propertyExt?: unknown;
        ttl?: number;
        st?: number;
        at?: number;
        cmd?: number;
        quote?: {
            ownerId: string | number;
            globalMsgId: string | number;
            fromD?: string;
            msg?: string;
            attach?: string;
        };
        mentions?: { uid: string; pos: number; len: number; type?: number }[];
    };
};

/**
 * Reactions offered by the API, keyed by a stable name, with Zalo's code and an emoji to display.
 * The first six are the quick reactions Zalo shows on every message.
 */
export const REACTIONS: Record<string, { code: string; emoji: string }> = {
    heart: { code: "/-heart", emoji: "❤️" },
    like: { code: "/-strong", emoji: "👍" },
    haha: { code: ":>", emoji: "😆" },
    wow: { code: ":o", emoji: "😮" },
    cry: { code: ":-((", emoji: "😭" },
    angry: { code: ":-h", emoji: "😡" },
    kiss: { code: ":-*", emoji: "😘" },
    tears_of_joy: { code: ":')", emoji: "😂" },
    dislike: { code: "/-weak", emoji: "👎" },
    rose: { code: "/-rose", emoji: "🌹" },
    broken_heart: { code: "/-break", emoji: "💔" },
    ok: { code: "/-ok", emoji: "👌" },
    pray: { code: "_()_", emoji: "🙏" },
    thanks: { code: "/-thanks", emoji: "🙏" },
    handclap: { code: ":handclap", emoji: "👏" },
};

const REACTION_BY_CODE = new Map(Object.entries(REACTIONS).map(([key, r]) => [r.code, { key, emoji: r.emoji }]));

const MAX_MESSAGES_PER_THREAD = 500;

const MSG_TYPE_LABELS: Record<string, string> = {
    "chat.photo": "[Photo]",
    "chat.video.msg": "[Video]",
    "chat.voice": "[Voice message]",
    "chat.sticker": "[Sticker]",
    "chat.gif": "[GIF]",
    "share.file": "[File]",
    "chat.recommended": "[Link]",
    "chat.link": "[Link]",
    "chat.location.new": "[Location]",
    "chat.doodle": "[Drawing]",
    "chat.ecard": "[Card]",
    "chat.todo": "[Task]",
    "chat.poll": "[Poll]",
};

export function threadKey(kind: ThreadKind, id: string): string {
    return `${kind}:${id}`;
}

/** The message id a raw recall ("undo") record points at, if it is one. */
export function recalledMessageId(raw: RawMessage): string | null {
    const content = raw.data.content as { deleteMsg?: unknown; globalMsgId?: unknown } | null;
    if (content && typeof content === "object" && "deleteMsg" in content) return String(content.globalMsgId);
    return null;
}

export function normalizeMessage(raw: RawMessage): StoredMessage {
    const { data } = raw;
    const threadKind: ThreadKind = raw.type === 1 ? "group" : "user";
    const msg: StoredMessage = {
        id: String(data.msgId),
        cliMsgId: String(data.cliMsgId),
        threadId: String(raw.threadId),
        threadKind,
        fromId: String(data.uidFrom),
        fromName: data.dName ?? "",
        isSelf: raw.isSelf,
        ts: Number(data.ts),
        msgType: data.msgType,
        text: "",
        raw: {
            msgType: data.msgType,
            content: data.content,
            propertyExt: data.propertyExt,
            uidFrom: String(data.uidFrom),
            idTo: String(data.idTo ?? ""),
            ts: String(data.ts),
            ttl: data.ttl ?? 0,
            st: data.st ?? 0,
            at: data.at ?? 0,
            cmd: data.cmd ?? 0,
        },
    };

    if (data.quote) {
        msg.quote = {
            msgId: String(data.quote.globalMsgId),
            fromId: String(data.quote.ownerId),
            fromName: data.quote.fromD ?? "",
            text: data.quote.msg || "[Attachment]",
        };
    }
    if (data.mentions?.length) {
        msg.mentions = data.mentions.map(({ uid, pos, len }) => ({ uid: String(uid), pos, len }));
    }

    if (typeof data.content === "string") {
        msg.text = data.content;
        return msg;
    }

    const content = (data.content ?? {}) as Record<string, unknown>;
    const str = (v: unknown) => (typeof v === "string" && v.length > 0 ? v : undefined);
    const attachment = {
        title: str(content.title),
        href: str(content.href),
        thumb: str(content.thumb),
    };
    if (attachment.title || attachment.href || attachment.thumb) {
        msg.attachment = attachment;
    }
    const label = MSG_TYPE_LABELS[data.msgType] ?? "[Unsupported message]";
    msg.text = attachment.title ? `${label} ${attachment.title}` : label;
    return msg;
}

type Snapshot = {
    threads: Record<string, ThreadInfo>;
    messages: Record<string, StoredMessage[]>;
    readUpTo: Record<string, number>;
    pinned?: string[];
    mutedUntil?: Record<string, number>;
    markedUnread?: string[];
};

/**
 * In-memory cache of threads and messages, snapshotted to disk.
 *
 * Zalo Web has no history endpoint for 1:1 chats, so this cache is the
 * client's only long-term history for direct messages.
 */
export class ChatStore {
    private threads = new Map<string, ThreadInfo>();
    private messages = new Map<string, StoredMessage[]>();
    private readUpTo = new Map<string, number>();
    private pinned = new Set<string>();
    private mutedUntil = new Map<string, number>();
    private markedUnread = new Set<string>();
    /** message id -> thread key */
    private index = new Map<string, string>();
    private saveTimer: NodeJS.Timeout | null = null;
    ownId: string | null = null;

    constructor(private readonly file: string | null = null) {
        this.load();
    }

    upsertThread(info: ThreadInfo): void {
        const key = threadKey(info.kind, info.id);
        const prev = this.threads.get(key);
        this.threads.set(key, {
            ...info,
            name: info.name || prev?.name || info.id,
            avatar: info.avatar || prev?.avatar || "",
        });
        this.scheduleSave();
    }

    getThread(kind: ThreadKind, id: string): ThreadInfo | undefined {
        return this.threads.get(threadKey(kind, id));
    }

    /** Adds a message; returns false if it was already stored. */
    addMessage(msg: StoredMessage): boolean {
        if (this.index.has(msg.id)) return false;
        const key = threadKey(msg.threadKind, msg.threadId);
        const list = this.messages.get(key) ?? [];

        // Messages mostly arrive in order; insert from the end to keep the list sorted by ts.
        let i = list.length;
        while (i > 0 && list[i - 1].ts > msg.ts) i--;
        list.splice(i, 0, msg);
        this.index.set(msg.id, key);
        if (list.length > MAX_MESSAGES_PER_THREAD) {
            for (const dropped of list.splice(0, list.length - MAX_MESSAGES_PER_THREAD)) this.index.delete(dropped.id);
        }
        this.messages.set(key, list);

        if (!this.threads.has(key)) {
            this.threads.set(key, {
                id: msg.threadId,
                kind: msg.threadKind,
                // For a 1:1 chat with someone not in the friend list, their display name is the best we have.
                name: msg.threadKind === "user" && !msg.isSelf ? msg.fromName || msg.threadId : msg.threadId,
                avatar: "",
            });
        }
        if (msg.isSelf) {
            this.markRead(msg.threadKind, msg.threadId, msg.ts);
        } else if (msg.ts > (this.readUpTo.get(key) ?? 0)) {
            // A new incoming message supersedes a manual "mark as unread".
            this.markedUnread.delete(key);
        }
        this.scheduleSave();
        return true;
    }

    getMessage(msgId: string): StoredMessage | undefined {
        const key = this.index.get(msgId);
        return key ? this.messages.get(key)?.find((m) => m.id === msgId) : undefined;
    }

    getMessages(kind: ThreadKind, id: string, opts: { before?: number; limit?: number } = {}): ChatMessage[] {
        const limit = Math.min(Math.max(opts.limit ?? 50, 1), MAX_MESSAGES_PER_THREAD);
        let list = this.messages.get(threadKey(kind, id)) ?? [];
        if (opts.before !== undefined) list = list.filter((m) => m.ts < opts.before!);
        return list.slice(-limit).map((m) => this.toPublic(m));
    }

    /** Sets (or with an empty code, clears) one user's reaction. Returns the updated message. */
    applyReaction(msgId: string, uid: string, code: string): StoredMessage | undefined {
        return this.update(msgId, (m) => {
            const reactions = { ...m.reactions };
            if (code) reactions[uid] = code;
            else delete reactions[uid];
            m.reactions = reactions;
        });
    }

    recall(msgId: string): StoredMessage | undefined {
        return this.update(msgId, (m) => {
            m.recalled = true;
            m.text = "Message recalled";
            delete m.attachment;
            delete m.quote;
            delete m.mentions;
            delete m.reactions;
        });
    }

    removeMessage(msgId: string): StoredMessage | undefined {
        const key = this.index.get(msgId);
        const list = key ? this.messages.get(key) : undefined;
        const i = list?.findIndex((m) => m.id === msgId) ?? -1;
        if (!list || i < 0) return undefined;
        const [removed] = list.splice(i, 1);
        this.index.delete(msgId);
        this.scheduleSave();
        return removed;
    }

    /**
     * Records delivery/seen receipts for `msgId`. In 1:1 chats a receipt covers every earlier
     * message we sent; in groups it is per message. Returns the messages that changed.
     */
    applyReceipt(
        kind: ThreadKind,
        threadId: string,
        msgId: string,
        receipt: { deliveredTo?: string[]; seenBy?: string[] },
    ): StoredMessage[] {
        const list = this.messages.get(threadKey(kind, threadId)) ?? [];
        const target = list.find((m) => m.id === msgId);
        if (!target) return [];
        const affected = kind === "user" ? list.filter((m) => m.isSelf && m.ts <= target.ts) : [target];

        const changed: StoredMessage[] = [];
        for (const m of affected) {
            const deliveredTo = union(m.deliveredTo, receipt.deliveredTo);
            const seenBy = union(m.seenBy, receipt.seenBy);
            if (deliveredTo.length === (m.deliveredTo?.length ?? 0) && seenBy.length === (m.seenBy?.length ?? 0)) continue;
            m.deliveredTo = deliveredTo;
            m.seenBy = seenBy;
            changed.push(m);
        }
        if (changed.length) this.scheduleSave();
        return changed;
    }

    /** Incoming messages after the read marker, i.e. the ones a "seen" receipt should cover. */
    unreadMessages(kind: ThreadKind, id: string): StoredMessage[] {
        const key = threadKey(kind, id);
        const readTs = this.readUpTo.get(key) ?? 0;
        return (this.messages.get(key) ?? []).filter((m) => !m.isSelf && m.ts > readTs);
    }

    /** Returns whether the thread had been manually marked as unread. */
    markRead(kind: ThreadKind, id: string, upTo: number = Date.now()): boolean {
        const key = threadKey(kind, id);
        const wasMarkedUnread = this.markedUnread.delete(key);
        if ((this.readUpTo.get(key) ?? 0) < upTo) this.readUpTo.set(key, upTo);
        this.scheduleSave();
        return wasMarkedUnread;
    }

    markUnread(kind: ThreadKind, id: string): void {
        this.markedUnread.add(threadKey(kind, id));
        this.scheduleSave();
    }

    setPinned(kind: ThreadKind, id: string, pinned: boolean): void {
        const key = threadKey(kind, id);
        if (pinned) this.pinned.add(key);
        else this.pinned.delete(key);
        this.scheduleSave();
    }

    replacePinned(keys: string[]): void {
        this.pinned = new Set(keys);
        this.scheduleSave();
    }

    /** `until`: epoch ms, -1 for indefinitely, null to unmute. */
    setMuted(kind: ThreadKind, id: string, until: number | null): void {
        const key = threadKey(kind, id);
        if (until === null) this.mutedUntil.delete(key);
        else this.mutedUntil.set(key, until);
        this.scheduleSave();
    }

    replaceMuted(entries: [string, number][]): void {
        this.mutedUntil = new Map(entries);
        this.scheduleSave();
    }

    isMuted(kind: ThreadKind, id: string, now = Date.now()): boolean {
        const until = this.mutedUntil.get(threadKey(kind, id));
        return until !== undefined && (until === -1 || until > now);
    }

    /** Drops a conversation's messages (after deleting it on Zalo). */
    clearThreadMessages(kind: ThreadKind, id: string): void {
        const key = threadKey(kind, id);
        for (const m of this.messages.get(key) ?? []) this.index.delete(m.id);
        this.messages.delete(key);
        this.markedUnread.delete(key);
        this.scheduleSave();
    }

    lastMessage(kind: ThreadKind, id: string): StoredMessage | undefined {
        return this.messages.get(threadKey(kind, id))?.at(-1);
    }

    listThreads(now = Date.now()): ThreadSummary[] {
        const out: ThreadSummary[] = [];
        for (const [key, info] of this.threads) {
            const list = this.messages.get(key) ?? [];
            const readTs = this.readUpTo.get(key) ?? 0;
            const unread = list.filter((m) => !m.isSelf && m.ts > readTs).length;
            const last = list.at(-1);
            const summary: ThreadSummary = {
                ...info,
                lastMessage: last ? this.toPublic(last) : null,
                unread: Math.max(unread, this.markedUnread.has(key) ? 1 : 0),
                pinned: this.pinned.has(key),
            };
            const muted = this.mutedUntil.get(key);
            if (muted !== undefined && (muted === -1 || muted > now)) summary.mutedUntil = muted;
            out.push(summary);
        }
        return out.sort(
            (a, b) =>
                Number(b.pinned) - Number(a.pinned) ||
                (b.lastMessage?.ts ?? 0) - (a.lastMessage?.ts ?? 0) ||
                a.name.localeCompare(b.name),
        );
    }

    toPublic(m: StoredMessage): ChatMessage {
        const { raw: _raw, reactions, deliveredTo, seenBy, ...rest } = m;
        const out: ChatMessage = {
            ...rest,
            reactions: summarizeReactions(reactions, this.ownId),
            mentionsMe: !m.isSelf && (m.mentions ?? []).some((x) => x.uid === this.ownId || x.uid === "-1"),
        };
        if (m.isSelf) {
            out.status = seenBy?.length ? "seen" : deliveredTo?.length ? "delivered" : "sent";
            if (m.threadKind === "group") out.seenCount = seenBy?.length ?? 0;
        }
        return out;
    }

    clear(): void {
        this.threads.clear();
        this.messages.clear();
        this.readUpTo.clear();
        this.pinned.clear();
        this.mutedUntil.clear();
        this.markedUnread.clear();
        this.index.clear();
        this.scheduleSave();
    }

    flush(): void {
        if (this.saveTimer) {
            clearTimeout(this.saveTimer);
            this.saveTimer = null;
        }
        if (!this.file) return;
        const snapshot: Snapshot = {
            threads: Object.fromEntries(this.threads),
            messages: Object.fromEntries(this.messages),
            readUpTo: Object.fromEntries(this.readUpTo),
            pinned: [...this.pinned],
            mutedUntil: Object.fromEntries(this.mutedUntil),
            markedUnread: [...this.markedUnread],
        };
        fs.mkdirSync(path.dirname(this.file), { recursive: true, mode: 0o700 });
        const tmp = `${this.file}.tmp`;
        fs.writeFileSync(tmp, JSON.stringify(snapshot), { mode: 0o600 });
        fs.renameSync(tmp, this.file);
    }

    private update(msgId: string, fn: (m: StoredMessage) => void): StoredMessage | undefined {
        const m = this.getMessage(msgId);
        if (!m) return undefined;
        fn(m);
        this.scheduleSave();
        return m;
    }

    private load(): void {
        if (!this.file || !fs.existsSync(this.file)) return;
        try {
            const snapshot = JSON.parse(fs.readFileSync(this.file, "utf8")) as Snapshot;
            this.threads = new Map(Object.entries(snapshot.threads ?? {}));
            this.messages = new Map(Object.entries(snapshot.messages ?? {}));
            this.readUpTo = new Map(Object.entries(snapshot.readUpTo ?? {}));
            this.pinned = new Set(snapshot.pinned ?? []);
            this.mutedUntil = new Map(Object.entries(snapshot.mutedUntil ?? {}));
            this.markedUnread = new Set(snapshot.markedUnread ?? []);
            for (const [key, list] of this.messages) for (const m of list) this.index.set(m.id, key);
        } catch (err) {
            console.warn(`Ignoring unreadable message cache ${this.file}:`, err);
        }
    }

    private scheduleSave(): void {
        if (!this.file || this.saveTimer) return;
        this.saveTimer = setTimeout(() => this.flush(), 2000);
        this.saveTimer.unref();
    }
}

function union(a: string[] | undefined, b: string[] | undefined): string[] {
    return [...new Set([...(a ?? []), ...(b ?? [])])];
}

function summarizeReactions(reactions: Record<string, string> | undefined, ownId: string | null): ReactionSummary[] {
    const byCode = new Map<string, ReactionSummary>();
    for (const [uid, code] of Object.entries(reactions ?? {})) {
        const known = REACTION_BY_CODE.get(code);
        const summary = byCode.get(code) ?? { key: known?.key ?? code, emoji: known?.emoji ?? code, count: 0, mine: false };
        summary.count++;
        if (uid === ownId) summary.mine = true;
        byCode.set(code, summary);
    }
    return [...byCode.values()].sort((a, b) => b.count - a.count);
}
