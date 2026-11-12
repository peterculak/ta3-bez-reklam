# TA3 – Open in VLC (Chrome extension)

Adds an orange **▶ Open in VLC** button next to the red *Live* button in the TA3 header on
video pages (`ta3.com/relacie/...`). Clicking it opens the episode's HLS stream directly in VLC,
bypassing TA3's web player (useful when the web player just spins).

## How it works

1. `content.js` finds the episode's `videoId` in the page and shows the button.
2. On click, `background.js` fetches Livebox's `vod-source.js` and builds the `.m3u8` URL
   (same way the downloader does).
3. Chrome can't launch desktop apps, so the URL is sent via
   [native messaging](https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging)
   to a small Bash helper (`host/ta3_vlc_host.sh`) that runs `open -a VLC <url>`.
   The helper only accepts `https://*.livebox.sk` URLs (strict regex allow-list).

`key.pem` / the `"key"` in `manifest.json` exist only to pin the extension ID
(`extension-id.txt`) so the native-messaging registration can allow exactly this extension.

## Requirements

- macOS (Windows/Linux need their own helper — not implemented yet)
- Google Chrome
- [VLC](https://www.videolan.org/) installed in `/Applications`

## Install

### 1. Register the native helper (once)

```sh
cd ta3-vlc-extension
./install.sh
```

This writes `~/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.ta3.vlc.json`,
which points Chrome at `host/ta3_vlc_host.sh` and allows only this extension's ID to talk to it.
No other dependencies are needed (the helper uses only tools that ship with macOS).

### 2. Load the extension in Chrome

1. Open `chrome://extensions`
2. Turn on **Developer mode** (top-right toggle)
3. Click **Load unpacked** and select the `ta3-vlc-extension/extension` folder
4. Make sure the extension is **enabled** (toggle on its card). Optionally pin it via the
   puzzle-piece menu — it has no toolbar UI, so this isn't required.

The extension ID shown on its card must be `hmeepeicibmdpfpbcjmiaaekmkaiijle`
(see `extension-id.txt`). If it differs, the helper will refuse to connect.

### 3. Use it

Open any TA3 episode page, e.g.
`https://www.ta3.com/relacie/hlavne-spravy/relacia/1082413/hlavne-spravy-z-12-novembra`.
Reload the page if it was already open. Click **▶ Open in VLC** — VLC starts playing the stream.

## Updating

After pulling changes to the extension files, click the refresh icon on its card in
`chrome://extensions`, then reload the TA3 tab. Re-run `./install.sh` only if you move the
project folder.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| No button on the page | Reload the tab; check the extension is enabled; the page must contain a video player |
| Button appears as a floating box instead of next to *Live* | TA3 changed its header markup; the fallback is intentional |
| Alert: `Specified native messaging host not found` | Run `./install.sh`, then restart Chrome |
| Alert: `Access to the specified native messaging host is forbidden` | Extension ID differs from `extension-id.txt` — reload it from this folder |
| Alert: `Native host has exited` | Re-run `./install.sh` (folder moved?). Check VLC is in `/Applications` |
| VLC opens but shows an error | The stream auth token expired or TA3 changed the URL scheme; retry, and check `background.js` against `ta3-cli/downloader.js` |

Check the real error in the page's DevTools console (`[TA3 VLC]`) or in the service worker log
(`chrome://extensions` → *service worker*).

## Uninstall

```sh
rm "$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.ta3.vlc.json"
```

then remove the extension in `chrome://extensions`.
