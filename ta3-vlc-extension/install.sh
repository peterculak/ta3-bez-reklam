#!/bin/bash
# Registers the native messaging host for the TA3 VLC extension (macOS, Chrome).
# Writes ONE json file: ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.ta3.vlc.json
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
ID="$(cat "$DIR/extension-id.txt")"
HOST="$DIR/host/ta3_vlc_host.sh"
chmod +x "$HOST"
T="$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts"
mkdir -p "$T"
cat > "$T/sk.ta3.vlc.json" <<JSON
{
  "name": "sk.ta3.vlc",
  "description": "Open TA3 streams in VLC",
  "path": "$HOST",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://$ID/"]
}
JSON
echo "Host registered. Now: chrome://extensions → Developer mode → Load unpacked → $DIR/extension"
