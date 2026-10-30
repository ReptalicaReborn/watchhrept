import assert from "node:assert/strict";
import os from "node:os";
import { test } from "node:test";
import { ThreadType } from "zca-js";
import { ZaloSession } from "../src/session.js";
import { ChatStore, normalizeMessage, type RawMessage } from "../src/store.js";

type Call = { method: string; args: unknown[] };

/** A ZaloSession wired to a fake zca-js API that records every call. */
function setup(opts: { readReceipts?: boolean } = {}) {
    const store = new ChatStore();
    store.ownId = "me";
    const session = new ZaloSession(store, os.tmpdir(), opts.readReceipts ?? true);
    const calls: Call[] = [];
    const responses: Record<string, unknown> = {
        sendMessage: { message: { msgId: 999 }, attachment: [] },
        forwardMessage: { success: [], fail: [] },
    };
    const api = new Proxy(
        {},
        {
            get: (_t, method: string) =>
                async (...args: unknown[]) => {
                    calls.push({ method, args });
                    return responses[method] ?? { status: 0 };
                },
        },
    );
    const internals = session as unknown as { api: unknown; status: { state: string; ownId: string } };
    internals.api = api;
    internals.status.state = "ready";
    internals.status.ownId = "me";
    const events: { name: string; payload: unknown }[] = [];
    for (const name of ["message", "message_updated", "message_deleted", "typing", "threads"] as const) {
        session.on(name, (payload?: unknown) => events.push({ name, payload }));
    }
    const ingest = (r: RawMessage) => (session as unknown as { ingest(m: unknown): void }).ingest(r);
    return { store, session, calls, events, ingest };
}

function incoming(id: string, ts: number, extra: Partial<RawMessage["data"]> = {}, group = false): RawMessage {
    return {
        type: group ? 1 : 0,
        threadId: group ? "g1" : "100",
        isSelf: extra.uidFrom === "me",
        data: {
            msgId: id,
            cliMsgId: `c${id}`,
            msgType: "webchat",
            uidFrom: "100",
            idTo: group ? "g1" : "me",
            dName: "Alice",
            ts: String(ts),
            content: `text ${id}`,
            ttl: 0,
            st: 3,
            at: 5,
            cmd: 501,
            ...extra,
        },
    };
}

test("replying with a quote sends the quoted message's raw fields", async () => {
    const { session, calls, ingest } = setup();
    ingest(incoming("1", 1000));

    const msgId = await session.sendText("user", "100", "sure", { quoteId: "1" });
    assert.equal(msgId, "999");
    const [content, threadId, type] = calls[0].args as [Record<string, unknown>, string, ThreadType];
    assert.equal(calls[0].method, "sendMessage");
    assert.equal(threadId, "100");
    assert.equal(type, ThreadType.User);
    assert.equal(content.msg, "sure");
    assert.deepEqual(content.quote, {
        content: "text 1",
        msgType: "webchat",
        propertyExt: undefined,
        uidFrom: "100",
        msgId: "1",
        cliMsgId: "c1",
        ts: "1000",
        ttl: 0,
    });
    assert.equal(content.mentions, undefined, "mentions are only sent to groups");
});

test("quoting a message from another thread is rejected", async () => {
    const { session, ingest } = setup();
    ingest(incoming("1", 1000));
    await assert.rejects(session.sendText("user", "other", "x", { quoteId: "1" }), /not found/);
});

test("reacting calls addReaction and updates the message immediately", async () => {
    const { session, calls, events, ingest } = setup();
    ingest(incoming("1", 1000));

    await session.react("user", "100", "1", "heart");
    assert.equal(calls[0].method, "addReaction");
    assert.equal(calls[0].args[0], "/-heart");
    assert.deepEqual(calls[0].args[1], { data: { msgId: "1", cliMsgId: "c1" }, threadId: "100", type: ThreadType.User });
    const updated = events.find((e) => e.name === "message_updated")?.payload as { reactions: unknown[] };
    assert.deepEqual(updated.reactions, [{ key: "heart", emoji: "❤️", count: 1, mine: true }]);

    await session.react("user", "100", "1", null);
    assert.equal(calls[1].args[0], "", "null removes the reaction");
    await assert.rejects(session.react("user", "100", "1", "dance"), /Unknown reaction/);
});

test("recall is limited to own messages", async () => {
    const { session, calls, store, ingest } = setup();
    ingest(incoming("1", 1000));
    ingest(incoming("2", 2000, { uidFrom: "me" }));

    await assert.rejects(session.recall("user", "100", "1"), /own messages/);
    await session.recall("user", "100", "2");
    assert.deepEqual(calls.at(-1), { method: "undo", args: [{ msgId: "2", cliMsgId: "c2" }, "100", ThreadType.User] });
    assert.equal(store.getMessage("2")?.recalled, true);
});

test("incoming recall records and undo events mark messages recalled", () => {
    const { store, ingest, session } = setup();
    ingest(incoming("1", 1000));
    ingest(incoming("2", 2000));
    ingest(incoming("u1", 3000, { content: { deleteMsg: 1, globalMsgId: 1, cliMsgId: 1 } }));
    assert.equal(store.getMessage("1")?.recalled, true);
    assert.equal(store.getMessage("u1"), undefined, "the recall record itself is not shown");

    (session as unknown as { onUndo(u: unknown): void }).onUndo({ data: { content: { globalMsgId: 2 } } });
    assert.equal(store.getMessage("2")?.recalled, true);
});

test("delete for me calls deleteMessage with onlyMe and drops the message", async () => {
    const { session, calls, store, events, ingest } = setup();
    ingest(incoming("1", 1000));
    await session.deleteForMe("user", "100", "1");
    assert.deepEqual(calls[0], {
        method: "deleteMessage",
        args: [{ data: { cliMsgId: "c1", msgId: "1", uidFrom: "100" }, threadId: "100", type: ThreadType.User }, true],
    });
    assert.equal(store.getMessage("1"), undefined);
    assert.deepEqual(events.at(-1), { name: "message_deleted", payload: { threadKind: "user", threadId: "100", id: "1" } });
});

test("forward splits targets by thread type and only forwards text", async () => {
    const { session, calls, ingest } = setup();
    ingest(incoming("1", 1000));
    ingest(incoming("2", 2000, { msgType: "chat.photo", content: { href: "https://x" } }));

    await session.forward("user", "100", "1", [
        { kind: "user", id: "a" },
        { kind: "group", id: "g" },
        { kind: "user", id: "b" },
    ]);
    assert.deepEqual(
        calls.map((c) => [c.method, c.args[1], c.args[2]]),
        [
            ["forwardMessage", ["a", "b"], ThreadType.User],
            ["forwardMessage", ["g"], ThreadType.Group],
        ],
    );
    await assert.rejects(session.forward("user", "100", "2", [{ kind: "user", id: "a" }]), /Only text/);
});

test("marking read sends seen receipts for unread incoming messages", async () => {
    const { session, calls, store, ingest } = setup();
    ingest(incoming("1", 1000));
    ingest(incoming("2", 2000));
    await session.markRead("user", "100");

    assert.equal(calls[0].method, "sendSeenEvent");
    const [params, type] = calls[0].args as [Record<string, unknown>[], ThreadType];
    assert.equal(type, ThreadType.User);
    assert.deepEqual(
        params.map((p) => p.msgId),
        ["1", "2"],
    );
    assert.deepEqual(params[0], {
        msgId: "1",
        cliMsgId: "c1",
        uidFrom: "100",
        idTo: "me",
        msgType: "webchat",
        st: 3,
        at: 5,
        cmd: 501,
        ts: "1000",
    });
    assert.equal(store.listThreads()[0].unread, 0);

    await session.markRead("user", "100");
    assert.equal(calls.length, 1, "nothing new to report");
});

test("read receipts can be turned off", async () => {
    const { session, calls, ingest } = setup({ readReceipts: false });
    ingest(incoming("1", 1000));
    await session.markRead("user", "100");
    assert.equal(calls.length, 0);
});

test("group mentions are passed through; reactions and receipts from the listener apply", async () => {
    const { session, calls, store, ingest } = setup();
    ingest(incoming("m1", 1000, { uidFrom: "me" }, true));

    await session.sendText("group", "g1", "@Bob hi", { mentions: [{ uid: "200", pos: 0, len: 4 }] });
    assert.deepEqual((calls[0].args[0] as { mentions: unknown }).mentions, [{ uid: "200", pos: 0, len: 4 }]);

    const internals = session as unknown as { onReaction(r: unknown): void; onSeen(s: unknown): void };
    internals.onReaction({ data: { uidFrom: "200", content: { rMsg: [{ gMsgID: "m1" }], rIcon: ":>" } } });
    internals.onSeen({ type: ThreadType.Group, threadId: "g1", isSelf: false, data: { msgId: "m1", seenUids: ["200", "300"] } });

    const [m] = store.getMessages("group", "g1");
    assert.deepEqual(m.reactions, [{ key: "haha", emoji: "😆", count: 1, mine: false }]);
    assert.equal(m.status, "seen");
    assert.equal(m.seenCount, 2);
});

test("seen on another device clears the group's unread count", () => {
    const { session, store, ingest } = setup();
    ingest(incoming("m1", 1000, {}, true));
    ingest(incoming("m2", 2000, {}, true));
    assert.equal(store.listThreads()[0].unread, 2);

    (session as unknown as { onSeen(s: unknown): void }).onSeen({
        type: ThreadType.Group,
        threadId: "g1",
        isSelf: true,
        data: { msgId: "m2", seenUids: ["me"] },
    });
    assert.equal(store.listThreads()[0].unread, 0);
});

test("pin, mute and mark unread update Zalo and the local summary", async () => {
    const { session, calls, store, ingest } = setup();
    ingest(incoming("1", 1000));
    await session.markRead("user", "100");
    calls.length = 0;

    await session.setPinned("user", "100", true);
    await session.setMuted("user", "100", "forever");
    await session.markUnread("user", "100");
    assert.deepEqual(
        calls.map((c) => c.method),
        ["setPinnedConversations", "setMute", "addUnreadMark"],
    );
    const [t] = store.listThreads();
    assert.equal(t.pinned, true);
    assert.equal(t.mutedUntil, -1);
    assert.equal(t.unread, 1);

    await session.setMuted("user", "100", null);
    assert.equal(store.listThreads()[0].mutedUntil, undefined);
});

test("typing events resolve the typist's name", () => {
    const { session, store, events } = setup();
    store.upsertThread({ id: "100", kind: "user", name: "Alice Nguyen", avatar: "" });
    (session as unknown as { onTyping(t: unknown): void }).onTyping({
        type: ThreadType.User,
        threadId: "100",
        data: { uid: "100", ts: "1", isPC: 0 },
    });
    assert.deepEqual(events.at(-1), {
        name: "typing",
        payload: { threadKind: "user", threadId: "100", uid: "100", name: "Alice Nguyen" },
    });
});

test("normalizeMessage output for a self message from another device", () => {
    const m = normalizeMessage(incoming("1", 1000, { uidFrom: "me" }));
    assert.equal(m.isSelf, true);
});
