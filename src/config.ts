import path from "node:path";

export type Config = {
    host: string;
    port: number;
    dataDir: string;
    /** If set, every API/WebSocket request must present this token. */
    accessToken: string | null;
};

const LOOPBACK_HOSTS = new Set(["127.0.0.1", "localhost", "::1"]);

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
    const host = env.HOST ?? "127.0.0.1";
    const port = Number(env.PORT ?? 3000);
    const accessToken = env.ACCESS_TOKEN?.trim() || null;

    if (!Number.isInteger(port) || port <= 0 || port > 65535) {
        throw new Error(`Invalid PORT: ${env.PORT}`);
    }
    // Whoever reaches the server can read and send messages as you,
    // so refuse to listen beyond this machine without a token.
    if (!LOOPBACK_HOSTS.has(host) && !accessToken) {
        throw new Error(`HOST=${host} is not loopback; set ACCESS_TOKEN to expose the server.`);
    }

    return {
        host,
        port,
        dataDir: path.resolve(env.DATA_DIR ?? "data"),
        accessToken,
    };
}
