import crypto from "node:crypto";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import express, { type NextFunction, type Request, type Response } from "express";
import { WebSocketServer, type WebSocket } from "ws";
import type { Config } from "./config.js";
import type { ZaloSession } from "./session.js";
import type { ThreadKind } from "./store.js";

const PUBLIC_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "public");
const MAX_TEXT_LENGTH = 4000;

export function createServer(config: Config, session: ZaloSession): http.Server {
    const app = express();
    app.disable("x-powered-by");
    app.use(express.json({ limit: "64kb" }));
    app.use(express.static(PUBLIC_DIR));

    const api = express.Router();
    api.use((req, res, next) => {
        if (!isAuthorized(config, req.headers.authorization, req.query.token)) {
            res.status(401).json({ error: "Unauthorized" });
            return;
        }
        next();
    });

    api.get("/status", (_req, res) => {
        res.json(session.getStatus());
    });

    api.post("/login", (_req, res) => {
        void session.startQRLogin();
        res.status(202).json(session.getStatus());
    });

    api.post("/logout", wrap(async (_req, res) => {
        await session.logout();
        res.json(session.getStatus());
    }));

    api.post("/reconnect", (_req, res) => {
        session.reconnect();
        res.status(202).json(session.getStatus());
    });

    api.get("/threads", (_req, res) => {
        res.json(session.store.listThreads());
    });

    api.get("/threads/:kind/:id/messages", wrap(async (req, res) => {
        const kind = parseKind(req.params.kind);
        const { id } = req.params;
        const before = req.query.before !== undefined ? Number(req.query.before) : undefined;
        const limit = req.query.limit !== undefined ? Number(req.query.limit) : undefined;

        // Groups have a history endpoint, so fill a thin cache from the server on first open.
        if (kind === "group" && before === undefined && session.store.getMessages(kind, id).length < 20) {
            await session.fetchGroupHistory(id).catch((err) => console.warn("Group history failed:", err));
        }
        res.json({
            thread: session.store.getThread(kind, id) ?? null,
            messages: session.store.getMessages(kind, id, { before, limit }),
        });
    }));

    api.post("/threads/:kind/:id/messages", wrap(async (req, res) => {
        const kind = parseKind(req.params.kind);
        const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
        if (!text) throw new HttpError(400, "text is required");
        if (text.length > MAX_TEXT_LENGTH) throw new HttpError(400, `text is longer than ${MAX_TEXT_LENGTH} characters`);

        const msgId = await session.sendText(kind, req.params.id, text);
        res.status(201).json({ msgId });
    }));

    api.post("/threads/:kind/:id/read", (req, res) => {
        session.store.markRead(parseKind(req.params.kind), req.params.id);
        res.status(204).end();
    });

    app.use("/api", api);
    app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
        const status = err instanceof HttpError ? err.status : 500;
        const message = err instanceof Error ? err.message : String(err);
        if (status === 500) console.error(err);
        res.status(status).json({ error: message });
    });

    const server = http.createServer(app);
    attachWebSocket(server, config, session);
    return server;
}

/** Pushes session status, new messages and thread-list changes to connected clients. */
function attachWebSocket(server: http.Server, config: Config, session: ZaloSession): void {
    const wss = new WebSocketServer({ noServer: true });

    server.on("upgrade", (req, socket, head) => {
        const url = new URL(req.url ?? "/", "http://localhost");
        if (url.pathname !== "/ws" || !isAuthorized(config, req.headers.authorization, url.searchParams.get("token"))) {
            socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
            socket.destroy();
            return;
        }
        wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws, req));
    });

    const broadcast = (payload: unknown) => {
        const data = JSON.stringify(payload);
        for (const client of wss.clients) {
            if (client.readyState === client.OPEN) client.send(data);
        }
    };

    session.on("status", (status) => broadcast({ type: "status", status }));
    session.on("message", (message) => broadcast({ type: "message", message }));
    session.on("threads", () => broadcast({ type: "threads" }));

    wss.on("connection", (ws: WebSocket) => {
        ws.send(JSON.stringify({ type: "status", status: session.getStatus() }));
    });
}

/** Express 4 does not forward rejected promises to the error handler on its own. */
function wrap(handler: (req: Request, res: Response) => Promise<void>) {
    return (req: Request, res: Response, next: NextFunction) => {
        handler(req, res).catch(next);
    };
}

function isAuthorized(config: Config, header: string | undefined, queryToken: unknown): boolean {
    if (!config.accessToken) return true;
    const presented = header?.startsWith("Bearer ") ? header.slice(7) : typeof queryToken === "string" ? queryToken : "";
    const a = Buffer.from(presented);
    const b = Buffer.from(config.accessToken);
    return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function parseKind(kind: string): ThreadKind {
    if (kind === "user" || kind === "group") return kind;
    throw new HttpError(400, `Unknown thread kind: ${kind}`);
}

class HttpError extends Error {
    constructor(
        readonly status: number,
        message: string,
    ) {
        super(message);
    }
}
