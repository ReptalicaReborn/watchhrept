import fs from "node:fs";
import path from "node:path";

export type ThreadKind = "user" | "group";

export type ChatMessage = {
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
        dName: string;
        ts: string | number;
        content: unknown;
    };
};

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

export function normalizeMessage(raw: RawMessage): ChatMessage {
    const { data } = raw;
    const threadKind: ThreadKind = raw.type === 1 ? "group" : "user";
    const msg: ChatMessage = {
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
    };

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
    messages: Record<string, ChatMessage[]>;
    readUpTo: Record<string, number>;
};

/**
 * In-memory cache of threads and messages, snapshotted to disk.
 *
 * Zalo Web has no history endpoint for 1:1 chats, so this cache is the
 * client's only long-term history for direct messages.
 */
export class ChatStore {
    private threads = new Map<string, ThreadInfo>();
    private messages = new Map<string, ChatMessage[]>();
    private readUpTo = new Map<string, number>();
    private saveTimer: NodeJS.Timeout | null = null;

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
    addMessage(msg: ChatMessage): boolean {
        const key = threadKey(msg.threadKind, msg.threadId);
        const list = this.messages.get(key) ?? [];
        if (list.some((m) => m.id === msg.id)) return false;

        // Messages mostly arrive in order; insert from the end to keep the list sorted by ts.
        let i = list.length;
        while (i > 0 && list[i - 1].ts > msg.ts) i--;
        list.splice(i, 0, msg);
        if (list.length > MAX_MESSAGES_PER_THREAD) list.splice(0, list.length - MAX_MESSAGES_PER_THREAD);
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
        if (msg.isSelf) this.markRead(msg.threadKind, msg.threadId, msg.ts);
        this.scheduleSave();
        return true;
    }

    getMessages(kind: ThreadKind, id: string, opts: { before?: number; limit?: number } = {}): ChatMessage[] {
        const limit = Math.min(Math.max(opts.limit ?? 50, 1), MAX_MESSAGES_PER_THREAD);
        let list = this.messages.get(threadKey(kind, id)) ?? [];
        if (opts.before !== undefined) list = list.filter((m) => m.ts < opts.before!);
        return list.slice(-limit);
    }

    markRead(kind: ThreadKind, id: string, upTo: number = Date.now()): void {
        const key = threadKey(kind, id);
        if ((this.readUpTo.get(key) ?? 0) >= upTo) return;
        this.readUpTo.set(key, upTo);
        this.scheduleSave();
    }

    listThreads(): ThreadSummary[] {
        const out: ThreadSummary[] = [];
        for (const [key, info] of this.threads) {
            const list = this.messages.get(key) ?? [];
            const readTs = this.readUpTo.get(key) ?? 0;
            out.push({
                ...info,
                lastMessage: list.at(-1) ?? null,
                unread: list.filter((m) => !m.isSelf && m.ts > readTs).length,
            });
        }
        return out.sort(
            (a, b) => (b.lastMessage?.ts ?? 0) - (a.lastMessage?.ts ?? 0) || a.name.localeCompare(b.name),
        );
    }

    clear(): void {
        this.threads.clear();
        this.messages.clear();
        this.readUpTo.clear();
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
        };
        fs.mkdirSync(path.dirname(this.file), { recursive: true, mode: 0o700 });
        const tmp = `${this.file}.tmp`;
        fs.writeFileSync(tmp, JSON.stringify(snapshot), { mode: 0o600 });
        fs.renameSync(tmp, this.file);
    }

    private load(): void {
        if (!this.file || !fs.existsSync(this.file)) return;
        try {
            const snapshot = JSON.parse(fs.readFileSync(this.file, "utf8")) as Snapshot;
            this.threads = new Map(Object.entries(snapshot.threads ?? {}));
            this.messages = new Map(Object.entries(snapshot.messages ?? {}));
            this.readUpTo = new Map(Object.entries(snapshot.readUpTo ?? {}));
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
