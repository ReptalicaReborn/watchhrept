// Token from ?token=... is remembered so it only needs to be typed once per device.
const TOKEN_KEY = "watchhrept-token";
const urlToken = new URLSearchParams(location.search).get("token");
if (urlToken) {
    try { localStorage.setItem(TOKEN_KEY, urlToken); } catch {}
    history.replaceState(null, "", location.pathname);
}
let token = urlToken;
try { token ??= localStorage.getItem(TOKEN_KEY); } catch {}

const $ = (id) => document.getElementById(id);
const state = {
    status: null,
    threads: [],
    current: null, // { kind, id }
    messages: [],
};

async function api(method, path, body) {
    const res = await fetch(`/api${path}`, {
        method,
        headers: {
            ...(body ? { "Content-Type": "application/json" } : {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
    });
    if (res.status === 204) return null;
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
}

// ---- Rendering ----

function renderStatus() {
    const s = state.status;
    if (!s) return;
    const loggedIn = s.state === "ready" || s.state === "disconnected";
    $("login").hidden = loggedIn;
    $("app").hidden = !loggedIn;

    if (!loggedIn) {
        $("qr").hidden = !s.qrImage;
        if (s.qrImage) $("qr").src = s.qrImage;
        $("login-btn").hidden = s.state !== "logged_out";
        $("login-hint").textContent =
            s.state === "awaiting_qr_scan"
                ? s.scannedBy
                    ? `Scanned by ${s.scannedBy}. Confirm on your phone.`
                    : "Open Zalo on your phone and scan this code."
                : s.state === "logging_in"
                  ? "Logging in…"
                  : "";
        $("login-error").textContent = s.error || "";
        return;
    }

    $("me").textContent = s.ownName || s.ownId || "";
    $("banner").hidden = s.state !== "disconnected";
    $("banner-text").textContent = s.error || "Disconnected";
}

function avatarEl(thread) {
    if (thread.avatar) {
        const img = document.createElement("img");
        img.className = "avatar";
        img.src = thread.avatar;
        img.alt = "";
        img.loading = "lazy";
        img.referrerPolicy = "no-referrer";
        return img;
    }
    const div = document.createElement("div");
    div.className = "avatar";
    div.textContent = (thread.name || "?").trim().charAt(0).toUpperCase();
    return div;
}

function renderThreads() {
    const query = $("search").value.trim().toLowerCase();
    const list = $("threads");
    list.replaceChildren();
    for (const t of state.threads) {
        if (query && !t.name.toLowerCase().includes(query)) continue;
        // Without a search, hide contacts we have never talked to.
        if (!query && !t.lastMessage) continue;

        const li = document.createElement("li");
        li.classList.toggle("active", isCurrent(t.kind, t.id));
        li.classList.toggle("has-unread", t.unread > 0);
        li.append(avatarEl(t));

        const name = document.createElement("span");
        name.className = "name";
        name.textContent = (t.kind === "group" ? "👥 " : "") + t.name;
        li.append(name);

        const badge = document.createElement("span");
        if (t.unread > 0) {
            badge.className = "unread";
            badge.textContent = t.unread > 99 ? "99+" : String(t.unread);
        }
        li.append(badge);

        const preview = document.createElement("span");
        preview.className = "preview";
        if (t.lastMessage) {
            const who = t.lastMessage.isSelf ? "You: " : t.kind === "group" ? `${t.lastMessage.fromName}: ` : "";
            preview.textContent = who + t.lastMessage.text;
        }
        li.append(preview);

        li.addEventListener("click", () => openThread(t.kind, t.id));
        list.append(li);
    }
}

function renderMessages() {
    const list = $("messages");
    const nearBottom = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
    list.replaceChildren(...state.messages.map(messageEl));
    if (nearBottom || list.dataset.thread !== currentKey()) list.scrollTop = list.scrollHeight;
    list.dataset.thread = currentKey();
}

function messageEl(m) {
    const li = document.createElement("li");
    li.classList.toggle("mine", m.isSelf);

    if (m.threadKind === "group" && !m.isSelf) {
        const from = document.createElement("span");
        from.className = "from";
        from.textContent = m.fromName;
        li.append(from);
    }

    if (m.quote) {
        const quote = document.createElement("span");
        quote.className = "quote";
        quote.textContent = `↩ ${m.quote.fromName}: ${m.quote.text}`;
        li.append(quote);
    }
    li.classList.toggle("recalled", !!m.recalled);
    li.classList.toggle("mentions-me", !!m.mentionsMe);

    const href = safeUrl(m.attachment?.href);
    if (m.msgType === "chat.photo" && (m.attachment?.thumb || href)) {
        const img = document.createElement("img");
        img.src = safeUrl(m.attachment.thumb) || href;
        img.alt = "Photo";
        img.loading = "lazy";
        img.referrerPolicy = "no-referrer";
        li.append(img);
    } else if (href) {
        const a = document.createElement("a");
        a.href = href;
        a.target = "_blank";
        a.rel = "noopener noreferrer";
        a.textContent = m.text;
        li.append(a);
    } else {
        li.append(document.createTextNode(m.text));
    }

    if (m.reactions?.length) {
        const reactions = document.createElement("span");
        reactions.className = "reactions";
        reactions.textContent = m.reactions.map((r) => (r.count > 1 ? `${r.emoji}${r.count}` : r.emoji)).join(" ");
        li.append(reactions);
    }

    const time = document.createElement("span");
    time.className = "time";
    time.textContent = new Date(m.ts).toLocaleString([], { hour: "2-digit", minute: "2-digit", day: "numeric", month: "short" });
    li.append(time);
    return li;
}

function safeUrl(url) {
    try {
        const u = new URL(url);
        return u.protocol === "https:" || u.protocol === "http:" ? u.href : null;
    } catch {
        return null;
    }
}

// ---- Data ----

const currentKey = () => (state.current ? `${state.current.kind}:${state.current.id}` : "");
const isCurrent = (kind, id) => state.current?.kind === kind && state.current?.id === id;

async function loadThreads() {
    state.threads = await api("GET", "/threads");
    renderThreads();
}

async function openThread(kind, id) {
    state.current = { kind, id };
    state.messages = [];
    $("app").classList.add("chat-open");
    const thread = state.threads.find((t) => t.kind === kind && t.id === id);
    $("chat-title").textContent = thread?.name || id;
    renderThreads();
    renderMessages();

    const data = await api("GET", `/threads/${kind}/${encodeURIComponent(id)}/messages?limit=200`);
    if (!isCurrent(kind, id)) return;
    state.messages = data.messages;
    if (data.thread) $("chat-title").textContent = data.thread.name;
    renderMessages();
    await markRead();
    $("text").focus();
}

async function markRead() {
    if (!state.current) return;
    const { kind, id } = state.current;
    await api("POST", `/threads/${kind}/${encodeURIComponent(id)}/read`);
    const t = state.threads.find((t) => t.kind === kind && t.id === id);
    if (t) t.unread = 0;
    renderThreads();
}

function onIncoming(m) {
    let t = state.threads.find((t) => t.kind === m.threadKind && t.id === m.threadId);
    if (!t) {
        // A thread we have not seen yet: refresh to get its name.
        void loadThreads();
    } else if (!t.lastMessage || m.ts >= t.lastMessage.ts) {
        t.lastMessage = m;
        if (!m.isSelf && !(isCurrent(m.threadKind, m.threadId) && document.hasFocus())) t.unread++;
        state.threads.sort((a, b) => (b.lastMessage?.ts ?? 0) - (a.lastMessage?.ts ?? 0));
        renderThreads();
    }

    if (isCurrent(m.threadKind, m.threadId) && !state.messages.some((x) => x.id === m.id)) {
        state.messages.push(m);
        state.messages.sort((a, b) => a.ts - b.ts);
        renderMessages();
        if (document.hasFocus()) void markRead();
    } else if (!m.isSelf && document.hidden && "Notification" in window && Notification.permission === "granted") {
        new Notification(m.fromName || "Zalo", { body: m.text, tag: `${m.threadKind}:${m.threadId}` });
    }
}

function onUpdated(m) {
    if (isCurrent(m.threadKind, m.threadId)) {
        state.messages = state.messages.map((x) => (x.id === m.id ? m : x));
        renderMessages();
    }
    const t = state.threads.find((t) => t.lastMessage?.id === m.id);
    if (t) {
        t.lastMessage = m;
        renderThreads();
    }
}

// ---- Live updates ----

function connect() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${proto}://${location.host}/ws${token ? `?token=${encodeURIComponent(token)}` : ""}`);
    ws.addEventListener("message", (ev) => {
        const event = JSON.parse(ev.data);
        if (event.type === "status") {
            const wasLoggedIn = state.status && ["ready", "disconnected"].includes(state.status.state);
            state.status = event.status;
            renderStatus();
            if (event.status.state === "ready" && !wasLoggedIn) void loadThreads();
        } else if (event.type === "message") {
            onIncoming(event.message);
        } else if (event.type === "message_updated") {
            onUpdated(event.message);
        } else if (event.type === "message_deleted") {
            if (isCurrent(event.threadKind, event.threadId)) {
                state.messages = state.messages.filter((m) => m.id !== event.id);
                renderMessages();
            }
            void loadThreads();
        } else if (event.type === "threads") {
            void loadThreads();
        }
    });
    ws.addEventListener("close", () => setTimeout(connect, 2000));
}

// ---- Wiring ----

$("login-btn").addEventListener("click", async () => {
    $("login-error").textContent = "";
    try {
        state.status = await api("POST", "/login");
        renderStatus();
        if ("Notification" in window && Notification.permission === "default") void Notification.requestPermission();
    } catch (err) {
        $("login-error").textContent = err.message;
    }
});

$("logout-btn").addEventListener("click", async () => {
    if (!confirm("Log out and delete the saved session and message cache on the server?")) return;
    state.status = await api("POST", "/logout");
    state.threads = [];
    state.current = null;
    state.messages = [];
    renderStatus();
});

$("reconnect-btn").addEventListener("click", () => api("POST", "/reconnect").catch((err) => alert(err.message)));

$("back-btn").addEventListener("click", () => {
    state.current = null;
    $("app").classList.remove("chat-open");
    renderThreads();
});

$("search").addEventListener("input", renderThreads);

$("composer").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const text = $("text").value.trim();
    if (!text || !state.current) return;
    const { kind, id } = state.current;
    $("text").value = "";
    try {
        await api("POST", `/threads/${kind}/${encodeURIComponent(id)}/messages`, { text });
    } catch (err) {
        $("text").value = text;
        alert(`Could not send: ${err.message}`);
    }
});

$("text").addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey && !ev.isComposing) {
        ev.preventDefault();
        $("composer").requestSubmit();
    }
});

window.addEventListener("focus", () => void markRead().catch(() => {}));

api("GET", "/status")
    .then((s) => {
        state.status = s;
        renderStatus();
        if (s.state === "ready" || s.state === "disconnected") void loadThreads();
    })
    .catch((err) => {
        $("login").hidden = false;
        $("login-btn").hidden = true;
        $("login-error").textContent =
            err.message === "Unauthorized" ? "Open this page with ?token=YOUR_ACCESS_TOKEN" : err.message;
    });
connect();
