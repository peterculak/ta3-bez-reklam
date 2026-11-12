#!/bin/bash
# Registers the native messaging host for the TA3 VLC extension (macOS, Chrome).
# Writes ONE json file: ~/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.ta3.vlc.json
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
NODE="$(command -v node)" || { echo "node not found"; exit 1; }
ID="$(cat "$DIR/extension-id.txt")"
WRAP="$DIR/host/run_host.sh"
printf '#!/bin/bash\nexec "%s" "%s/host/ta3_vlc_host.js"\n' "$NODE" "$DIR" > "$WRAP"
chmod +x "$WRAP"
T="$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts"
mkdir -p "$T"
cat > "$T/sk.ta3.vlc.json" <<JSON
{
  "name": "sk.ta3.vlc",
  "description": "Open TA3 streams in VLC",
  "path": "$WRAP",
  "type": "stdio",
  "allowed_origins": ["chrome-extension://$ID/"]
}
JSON
echo "Host registered. Now: chrome://extensions → Developer mode → Load unpacked → $DIR/extension"
