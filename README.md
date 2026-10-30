# watchhrept

A self-hosted, third-party client for a **personal Zalo account**. It is a small Node server built on
[`zca-js`](https://github.com/RFS-ADRENO/zca-js), which logs in the same way Zalo Web does, plus a lightweight
web UI that works on desktop, phone and small watch-sized screens.

> [!WARNING]
> This is **unofficial**. It is not affiliated with or endorsed by Zalo/VNG, and using it is against Zalo's
> Terms of Service. Zalo may lock or ban accounts that use it. Use a secondary account if you can, and expect it
> to break when Zalo changes its web protocol.

## How it works

```
 browser / phone / watch ──HTTP + WebSocket──▶ watchhrept server ──zca-js──▶ Zalo Web endpoints
```

- **Login**: scan a QR code with the Zalo app on your phone, the same way you log in to chat.zalo.me.
  The session (cookies + device id) is saved to `data/credentials.json` so restarts log back in automatically.
- **Real-time**: the server holds Zalo's WebSocket and pushes new messages to every open client.
- **History**: Zalo Web has no history endpoint for 1:1 chats, so the server keeps its own cache
  (`data/messages.json`, last 500 messages per chat) and replays what was missed while offline.
  Group chats additionally pull recent history from Zalo when opened.

> [!IMPORTANT]
> Zalo allows only **one web listener per account**. Opening chat.zalo.me in a browser disconnects this
> server; the UI shows a banner with a *Reconnect* button.

## Run it

Requires Node.js 20+.

```bash
npm install
npm run build
npm start            # http://127.0.0.1:3000
```

Open the page, click **Log in with QR code**, and scan it from the Zalo app (*QR scanner* → confirm login).

For development: `npm run dev` (auto-reload), `npm test`, `npm run typecheck`.

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `HOST` | `127.0.0.1` | Interface to listen on |
| `PORT` | `3000` | Port |
| `DATA_DIR` | `./data` | Where the session and message cache are stored |
| `ACCESS_TOKEN` | *(none)* | Required for every API/WebSocket request when set. **Mandatory** if `HOST` is not loopback |

To use it from your phone or watch, expose it with a token and open `http://<server>:3000/?token=<ACCESS_TOKEN>`
once on each device (the token is remembered). Put it behind HTTPS (a reverse proxy, Tailscale, etc.) if it
leaves your home network: anyone with the token can read and send messages as you.

```bash
HOST=0.0.0.0 ACCESS_TOKEN=$(openssl rand -hex 24) npm start
```

## Wear OS app

`wear/` is a standalone Wear OS 3+ app (Kotlin, Compose for Wear OS) that talks to this server.

- **Chat list** with unread counts, **chat view** with the latest messages, live updates over the WebSocket.
- **Reply** by voice, keyboard, emoji, quick replies, or typing on the paired phone (the system Wear input screen).
- **Log in from the watch**: if the server isn't logged in yet, the watch shows the Zalo QR code; scan it with
  the Zalo app on your phone.
- The WebSocket is only open while the app is on screen, to save battery. For notifications while it's closed,
  keep relying on the normal Zalo phone app notifications that the watch mirrors.

### Install

1. Run the server so the watch can reach it, with a token, e.g.
   `HOST=0.0.0.0 ACCESS_TOKEN=$(openssl rand -hex 24) npm start`.
2. Get the APK: download `zalo-watch-debug-apk` from the latest **CI** run on GitHub, or build it with
   `cd wear && ./gradlew assembleDebug` (Android Studio / Android SDK required).
3. Enable *Developer options → ADB debugging* (and *Debug over Wi-Fi*) on the watch, then
   `adb connect <watch-ip>:5555 && adb install app-debug.apk`.
4. Open **Zalo Watch**, set the server address (e.g. `http://192.168.1.10:3000`) and the access token.
   In the input screen pick *type on phone* to paste the token from your phone.

The Wear OS emulator reaches a server on your computer at `http://10.0.2.2:3000`.

## Security notes

- `data/credentials.json` is equivalent to your logged-in Zalo session. It is written with `0600` permissions
  and is git-ignored. Never commit or share it.
- **Log out** in the UI deletes the saved session and the message cache.

## API

All endpoints are under `/api` and accept `Authorization: Bearer <token>` (or `?token=`) when a token is set.

| Method | Path | Description |
|---|---|---|
| `GET` | `/status` | Session state, QR image (data URL) while logging in |
| `POST` | `/login` | Start a QR login |
| `POST` | `/logout` | Log out and delete saved data |
| `POST` | `/reconnect` | Restart the real-time listener |
| `GET` | `/threads` | Chats and contacts, most recent first, with unread counts |
| `GET` | `/threads/:kind/:id/messages?before=&limit=` | Messages (`kind` is `user` or `group`) |
| `POST` | `/threads/:kind/:id/messages` | Send `{ "text": "..." }` |
| `POST` | `/threads/:kind/:id/read` | Mark a chat as read (locally) |

`GET /ws` is a WebSocket that pushes `{type:"status"}`, `{type:"message"}` and `{type:"threads"}` events.
A native watch or phone app can use this API directly instead of the web UI.

## Not yet supported

Sending photos, stickers, files or voice; reactions; read receipts to Zalo; calls. `zca-js` supports most of
these, so they can be added to `src/session.ts` and exposed through the API.
