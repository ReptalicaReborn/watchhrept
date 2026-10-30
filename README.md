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
| `READ_RECEIPTS` | `true` | Send "seen" receipts to Zalo when you read a chat, like the official app. `false` to read silently |

To use it from your phone or watch, expose it with a token and open `http://<server>:3000/?token=<ACCESS_TOKEN>`
once on each device (the token is remembered). Put it behind HTTPS (a reverse proxy, Tailscale, etc.) if it
leaves your home network: anyone with the token can read and send messages as you.

```bash
HOST=0.0.0.0 ACCESS_TOKEN=$(openssl rand -hex 24) npm start
```

## Wear OS app

`wear/` is a standalone Wear OS 3+ app (Kotlin, Compose for Wear OS) that talks to this server. It covers
everything the official app does for text chat (no mini apps, stickers, media or calls):

| Feature | How on the watch |
|---|---|
| Chat list | Pinned first (📌), muted (🔕), unread counts, `@` when you're mentioned, live "typing…" |
| New chat | **New chat** → search any friend or group by voice or keyboard (diacritics optional: "duc" finds "Đức") |
| Read & reply | Voice, keyboard, emoji, quick replies, or type on the paired phone |
| Reply to a message (quote) | Tap a message → **Reply** |
| Reactions | Tap a message → ❤️ 👍 😆 😮 😭 😡 (tap yours again to remove) |
| Recall for everyone / delete for me | Tap a message → confirm with a second tap |
| Forward | Tap a text message → **Forward** → pick a chat |
| @mention in groups | **@ Mention someone** → pick a member → type |
| Seen / delivered | Under your latest message ("Seen", "Seen by 3" in groups); others see your seen receipts too |
| Last online | Under the name in 1:1 chats |
| Pin, mute (1 h, 4 h, until 8 AM, until turned on), mark unread, delete conversation | **Chat options** |
| Older messages | **Load earlier** at the top of a chat |
| Log in | If the server isn't logged in, the watch shows the Zalo QR code to scan with your phone |

Pinned and muted chats come from your Zalo account, so they match the phone app. The WebSocket is only open while
the app is on screen, to save battery; for notifications while it's closed, keep relying on the Zalo phone app's
notifications that the watch mirrors.

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
| `GET` | `/threads` | Chats and contacts: pinned first, then most recent; unread counts, `pinned`, `mutedUntil` |
| `GET` | `/threads/:kind/:id/info` | `{lastOnline}` for users, `{members}` for groups |
| `GET` | `/threads/:kind/:id/messages?before=&limit=` | Messages (`kind` is `user` or `group`) |
| `POST` | `/threads/:kind/:id/messages` | Send `{ "text", "quoteId"?, "mentions"?: [{uid, pos, len}] }` |
| `PUT` | `/threads/:kind/:id/messages/:msgId/reaction` | `{ "reaction": "heart" }` (see `GET /reactions`), `null` to remove |
| `POST` | `/threads/:kind/:id/messages/:msgId/recall` | Recall your own message for everyone |
| `DELETE` | `/threads/:kind/:id/messages/:msgId` | Delete a message for yourself |
| `POST` | `/threads/:kind/:id/messages/:msgId/forward` | `{ "to": [{kind, id}] }` (text messages) |
| `POST` | `/threads/:kind/:id/read` | Mark read (sends seen receipts unless `READ_RECEIPTS=false`) |
| `POST` | `/threads/:kind/:id/unread` | Mark as unread |
| `POST` | `/threads/:kind/:id/typing` | Show "typing…" to the other side |
| `PUT` | `/threads/:kind/:id/pin` | `{ "pinned": true }` |
| `PUT` | `/threads/:kind/:id/mute` | `{ "duration": "1h" \| "4h" \| "8am" \| "forever" \| null }` |
| `DELETE` | `/threads/:kind/:id` | Delete the conversation |

`GET /ws` is a WebSocket that pushes `status`, `message`, `message_updated` (reactions, recalls, receipts),
`message_deleted`, `typing` and `threads` events.
A native watch or phone app can use this API directly instead of the web UI.

## Not yet supported

Sending photos, stickers, files or voice; calls; mini apps; editing your profile or friend requests. `zca-js`
supports most of these, so they can be added to `src/session.ts` and exposed through the API.
