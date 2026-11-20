# watchhrept

A self-hosted, third-party client for a **personal Zalo account**. It is a small Node server built on
[`zca-js`](https://github.com/RFS-ADRENO/zca-js), which logs in the same way Zalo Web does, plus a lightweight
web UI, and an Android phone app (Material 3) that can run the whole thing on the phone itself.

> [!WARNING]
> This is **unofficial**. It is not affiliated with or endorsed by Zalo/VNG, and using it is against Zalo's
> Terms of Service. Zalo may lock or ban accounts that use it. Use a secondary account if you can, and expect it
> to break when Zalo changes its web protocol.

## How it works

```
 browser / Android app ──HTTP + WebSocket──▶ watchhrept server ──zca-js──▶ Zalo Web endpoints
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

To use it from another device, expose it with a token and open `http://<server>:3000/?token=<ACCESS_TOKEN>`
once on each device (the token is remembered). Put it behind HTTPS (a reverse proxy, Tailscale, etc.) if it
leaves your home network: anyone with the token can read and send messages as you.

```bash
HOST=0.0.0.0 ACCESS_TOKEN=$(openssl rand -hex 24) npm start
```

## Android app

`android/` is an Android phone app (Android 8.0+, Kotlin, Jetpack Compose with **Material 3**, Material You
colors on Android 12+). By default it **runs the Zalo server on the phone itself** (the same code as above,
bundled into the app with an embedded Node.js from [nodejs-mobile](https://github.com/nodejs-mobile/nodejs-mobile)),
so no computer is needed. It can also connect to a server you run elsewhere (*Settings → Zalo runs on → A server*).

It covers text chat (no mini apps, stickers, media or calls):

| Feature | How in the app |
|---|---|
| Chat list | Pinned first, muted icon, unread badges, `@` when you're mentioned, live "typing…" |
| New chat / search | **New chat** button or the search icon: any friend or group (diacritics optional: "duc" finds "Đức") |
| Read & reply | Message bar at the bottom of a chat |
| Reply to a message (quote) | Long-press a message → **Reply** |
| Reactions | Long-press → ❤️ 👍 😆 😮 😭 😡 (tap yours again to remove) |
| Copy, forward | Long-press → **Copy text** / **Forward** → pick a chat |
| Recall for everyone / delete for me | Long-press → confirm |
| @mention in groups | Type `@` (or tap the `@` button) and pick a member; mention several people anywhere in the message |
| Seen / delivered | Under your latest message ("Seen", "Seen by 3" in groups); others see your seen receipts too |
| Last online | Under the name in 1:1 chats |
| Pin, mute (1 h, 4 h, until 8 AM, until turned on), mark unread, delete conversation | ⋮ menu in a chat |
| Older messages | Scroll up |

### Install

1. Download `zalo-lite-debug-apk` from the latest **CI** run on GitHub. It holds one APK per CPU type; almost every
   current phone is `arm64-v8a` (check with `adb shell getprop ro.product.cpu.abi`).
2. Copy `app-arm64-v8a-debug.apk` to the phone and open it (allow installing from that app when asked), or
   `adb install app-arm64-v8a-debug.apk`.
3. Open **Zalo Lite** and log in (below).

### Logging in

Zalo logs in third-party clients like Zalo Web: with a QR code that you confirm in the official Zalo app.

- **Zalo is on another phone:** tap **Get QR code** and scan it with that phone (Zalo → QR icon next to search).
- **Zalo is on this same phone:** tap **Get QR code**, then **Save image**. In Zalo, open the QR scanner, tap the
  picture button and pick the saved code. **Share** sends the image elsewhere (e.g. to a computer to scan from).
- Confirm on the phone. Only one web session can listen at a time, so this disconnects Zalo Web elsewhere.

The login is kept on the phone. If the phone is offline when the app (or the phone) starts, the app keeps the
saved login and retries on its own, right away when a network comes back, instead of asking for a new QR code.

Zalo stays connected in the background (a small "Connected to Zalo" notification shows this) and reconnects after a
reboot, so 1:1 messages keep landing in the history while the app is closed. What it costs: each APK is about 35 MB,
the app takes roughly 90 MB once installed (the Node.js runtime is ~50 MB unpacked) and on the order of 100 MB of
RAM. *Settings → Restart Zalo service* restarts it; *Log out of Zalo* removes the session from the phone.

### Using a server elsewhere instead

Run the server as described above (with `HOST=0.0.0.0` and an `ACCESS_TOKEN`), then in the app choose
*Settings → Zalo runs on → A server* and enter its address and token. The Android emulator reaches a server on your
computer at `http://10.0.2.2:3000`.

### Building the app

`cd android && ./gradlew assembleDebug` needs the Android SDK/NDK and Node.js with `npm ci` run at the repo root:
the build bundles `src/` with esbuild and downloads nodejs-mobile (checked against a pinned SHA-256).

## Security notes

- `data/credentials.json` is equivalent to your logged-in Zalo session. It is written with `0600` permissions
  and is git-ignored. Never commit or share it.
- **Log out** in the UI deletes the saved session and the message cache.
- In the Android app, the session lives in the app's private storage, and the on-phone server listens only on
  `127.0.0.1` with a random token that only the app knows.

## API

All endpoints are under `/api` and accept `Authorization: Bearer <token>` (or `?token=`) when a token is set.

| Method | Path | Description |
|---|---|---|
| `GET` | `/status` | Session state, QR image (data URL) while logging in |
| `POST` | `/login` | Start a QR login (also replaces a saved session that is waiting to retry) |
| `POST` | `/retry` | Retry a saved session that couldn't reach Zalo now, instead of waiting for the next attempt |
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
The Android app uses this API; other clients can too.

## Not yet supported

Sending photos, stickers, files or voice; calls; mini apps; editing your profile or friend requests. `zca-js`
supports most of these, so they can be added to `src/session.ts` and exposed through the API.
