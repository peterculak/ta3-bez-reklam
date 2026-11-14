#!/bin/bash
# Registers the native messaging host for the YouTube own-player extension (macOS, Chrome).
# Writes ONE json file: ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.yt.player.json
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
ID="$(cat "$DIR/extension-id.txt")"
HOST="$DIR/host/yt_host.sh"
chmod +x "$HOST"
command -v yt-dlp >/dev/null || echo "WARNING: yt-dlp not found. Install it first:  brew install yt-dlp"
T="$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts"
mkdir -p "$T"
cat > "$T/sk.yt.player.json" <<JSON
{
  "name": "sk.yt.player",
  "description": "YouTube own player helper (yt-dlp)",
  "path": "$HOST",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://$ID/"]
}
JSON
echo "Host registered. Now: chrome://extensions → Developer mode → Load unpacked → $DIR/extension"
