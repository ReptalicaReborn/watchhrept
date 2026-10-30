import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { loadConfig } from "../src/config.js";
import { parseMentions } from "../src/server.js";
import { muteUntil } from "../src/session.js";
import { ChatStore, normalizeMessage, recalledMessageId, type RawMessage, type StoredMessage } from "../src/store.js";

export function raw(overrides: Partial<RawMessage["data"]> = {}, top: Partial<Omit<RawMessage, "data">> = {}): RawMessage {
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
            idTo: "me",
            dName: "Alice",
            ts: "1700000000000",
            content: "hello",
            ...overrides,
        },
    };
}

function msg(id: string, ts: number, extra: Partial<StoredMessage> = {}): StoredMessage {
    return { ...normalizeMessage(raw({ msgId: id, ts: String(ts) })), ...extra };
}

test("normalizeMessage keeps plain text and the raw fields needed for actions", () => {
    const m = normalizeMessage(raw({ ttl: 0, st: 3, at: 5, cmd: 501 }));
    assert.equal(m.text, "hello");
    assert.equal(m.threadKind, "user");
    assert.equal(m.ts, 1700000000000);
    assert.equal(m.attachment, undefined);
    assert.deepEqual(m.raw, {
        msgType: "webchat",
        content: "hello",
        propertyExt: undefined,
        uidFrom: "100",
        idTo: "me",
        ts: "1700000000000",
        ttl: 0,
        st: 3,
        at: 5,
        cmd: 501,
    });
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

test("normalizeMessage reads quotes and mentions", () => {
    const m = normalizeMessage(
        raw(
            {
                content: "@Me look",
                quote: { ownerId: 200, globalMsgId: 42, fromD: "Bob", msg: "", attach: "{}" },
                mentions: [{ uid: "me", pos: 0, len: 3, type: 0 }],
            },
            { type: 1, threadId: "g1" },
        ),
    );
    assert.deepEqual(m.quote, { msgId: "42", fromId: "200", fromName: "Bob", text: "[Attachment]" });
    assert.deepEqual(m.mentions, [{ uid: "me", pos: 0, len: 3 }]);

    const store = new ChatStore();
    store.ownId = "me";
    assert.equal(store.toPublic(m).mentionsMe, true);
    store.ownId = "someone-else";
    assert.equal(store.toPublic(m).mentionsMe, false);
});

test("recalledMessageId recognises recall records", () => {
    assert.equal(recalledMessageId(raw({ content: { deleteMsg: 1, globalMsgId: 77, cliMsgId: 1 } })), "77");
    assert.equal(recalledMessageId(raw()), null);
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
    assert.deepEqual(
        store.unreadMessages("user", "100").map((m) => m.id),
        ["2"],
    );

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

test("reactions are summarised per emoji with our own marked", () => {
    const store = new ChatStore();
    store.ownId = "me";
    store.addMessage(msg("1", 1000));
    store.applyReaction("1", "100", "/-heart");
    store.applyReaction("1", "me", "/-heart");
    store.applyReaction("1", "300", ":>");
    store.applyReaction("1", "300", "/-strong"); // changes their reaction

    let [m] = store.getMessages("user", "100");
    assert.deepEqual(m.reactions, [
        { key: "heart", emoji: "❤️", count: 2, mine: true },
        { key: "like", emoji: "👍", count: 1, mine: false },
    ]);

    store.applyReaction("1", "me", ""); // removal
    [m] = store.getMessages("user", "100");
    assert.deepEqual(m.reactions[0], { key: "heart", emoji: "❤️", count: 1, mine: false });
});

test("recall blanks the message, delete removes it", () => {
    const store = new ChatStore();
    store.addMessage(msg("1", 1000));
    store.addMessage(msg("2", 2000));
    store.applyReaction("1", "100", "/-heart");

    store.recall("1");
    const [m] = store.getMessages("user", "100");
    assert.equal(m.recalled, true);
    assert.equal(m.text, "Message recalled");
    assert.deepEqual(m.reactions, []);

    assert.ok(store.removeMessage("2"));
    assert.equal(store.getMessage("2"), undefined);
    assert.equal(store.addMessage(msg("2", 2000)), true, "a removed id can come back if Zalo replays it");
});

test("receipts: 1:1 covers earlier own messages, groups count per message", () => {
    const store = new ChatStore();
    store.ownId = "me";
    store.addMessage(msg("1", 1000, { isSelf: true }));
    store.addMessage(msg("2", 2000, { isSelf: true }));
    store.addMessage(msg("3", 3000, { isSelf: true }));

    store.applyReceipt("user", "100", "2", { deliveredTo: ["100"] });
    store.applyReceipt("user", "100", "1", { seenBy: ["100"] });
    assert.deepEqual(
        store.getMessages("user", "100").map((m) => m.status),
        ["seen", "delivered", "sent"],
    );
    assert.deepEqual(store.applyReceipt("user", "100", "1", { seenBy: ["100"] }), [], "no-op receipts change nothing");

    const g = normalizeMessage(raw({ msgId: "g1m", uidFrom: "me" }, { type: 1, threadId: "g1", isSelf: true }));
    store.addMessage(g);
    store.applyReceipt("group", "g1", "g1m", { seenBy: ["a", "b"] });
    store.applyReceipt("group", "g1", "g1m", { seenBy: ["b", "c"] });
    const [gm] = store.getMessages("group", "g1");
    assert.equal(gm.status, "seen");
    assert.equal(gm.seenCount, 3);
});

test("pinned threads sort first; mute and manual unread show in summaries", () => {
    const store = new ChatStore();
    store.upsertThread({ id: "a", kind: "user", name: "A", avatar: "" });
    store.upsertThread({ id: "b", kind: "user", name: "B", avatar: "" });
    store.addMessage({ ...msg("1", 5000), threadId: "a" });
    store.addMessage({ ...msg("2", 1000), threadId: "b" });
    store.markRead("user", "a");
    store.markRead("user", "b");

    assert.deepEqual(
        store.listThreads().map((t) => t.id),
        ["a", "b"],
    );
    store.setPinned("user", "b", true);
    assert.deepEqual(
        store.listThreads().map((t) => t.id),
        ["b", "a"],
    );

    store.setMuted("user", "a", -1);
    store.setMuted("user", "b", 500); // already expired
    const now = 1_000;
    const byId = Object.fromEntries(store.listThreads(now).map((t) => [t.id, t]));
    assert.equal(byId.a.mutedUntil, -1);
    assert.equal(byId.b.mutedUntil, undefined);
    assert.equal(store.isMuted("user", "a"), true);

    store.markUnread("user", "a");
    assert.equal(store.listThreads().find((t) => t.id === "a")?.unread, 1);
    assert.equal(store.markRead("user", "a"), true);
    assert.equal(store.listThreads().find((t) => t.id === "a")?.unread, 0);
});

test("ChatStore persists messages and settings to disk", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "watchhrept-"));
    const file = path.join(dir, "messages.json");
    try {
        const a = new ChatStore(file);
        a.addMessage(msg("1", 1000));
        a.applyReaction("1", "100", "/-heart");
        a.setPinned("user", "100", true);
        a.flush();
        assert.equal(fs.statSync(file).mode & 0o777, 0o600);

        const b = new ChatStore(file);
        assert.equal(b.getMessages("user", "100").length, 1);
        assert.equal(b.getMessage("1")?.reactions?.["100"], "/-heart", "message index is rebuilt on load");
        assert.equal(b.listThreads()[0].pinned, true);
    } finally {
        fs.rmSync(dir, { recursive: true, force: true });
    }
});

test("muteUntil computes the end of each mute option", () => {
    const now = new Date(2026, 0, 1, 9, 30);
    assert.equal(muteUntil("1h", now), now.getTime() + 3_600_000);
    assert.equal(muteUntil("forever", now), -1);
    assert.equal(muteUntil("8am", now), new Date(2026, 0, 2, 8, 0).getTime());
    assert.equal(muteUntil("8am", new Date(2026, 0, 1, 7, 0)), new Date(2026, 0, 1, 8, 0).getTime());
});

test("parseMentions validates positions against the text", () => {
    assert.deepEqual(parseMentions([{ uid: "u1", pos: 0, len: 4 }], "@Bob hi"), [{ uid: "u1", pos: 0, len: 4 }]);
    assert.equal(parseMentions(undefined, "x"), undefined);
    assert.throws(() => parseMentions([{ uid: "u1", pos: 5, len: 4 }], "@Bob hi"), /outside/);
    assert.throws(() => parseMentions("nope", "x"), /array/);
});

test("loadConfig refuses a public host without a token", () => {
    assert.throws(() => loadConfig({ HOST: "0.0.0.0" }), /ACCESS_TOKEN/);
    assert.equal(loadConfig({ HOST: "0.0.0.0", ACCESS_TOKEN: "s3cret" }).accessToken, "s3cret");
    assert.equal(loadConfig({}).host, "127.0.0.1");
    assert.equal(loadConfig({}).readReceipts, true);
    assert.equal(loadConfig({ READ_RECEIPTS: "false" }).readReceipts, false);
});
