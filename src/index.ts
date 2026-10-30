import path from "node:path";
import { loadConfig } from "./config.js";
import { createServer } from "./server.js";
import { ZaloSession } from "./session.js";
import { ChatStore } from "./store.js";

const config = loadConfig();
const store = new ChatStore(path.join(config.dataDir, "messages.json"));
const session = new ZaloSession(store, config.dataDir);
const server = createServer(config, session);

server.listen(config.port, config.host, () => {
    console.log(`watchhrept listening on http://${config.host}:${config.port}`);
});

void session.resume();

for (const signal of ["SIGINT", "SIGTERM"] as const) {
    process.on(signal, () => {
        store.flush();
        process.exit(0);
    });
}
