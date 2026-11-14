#!/bin/bash
# Chrome native messaging host (macOS) for the YouTube own-player extension.
# Receives {"action":"stream"|"vlc","id":"<11-char video id>"}, runs yt-dlp and either
#   stream -> replies {"ok":true,"urls":[video, audio?]}   (the extension plays them itself)
#   vlc    -> opens the same streams in VLC and replies {"ok":true}
# Only a validated video id ever reaches yt-dlp, and only googlevideo.com URLs are returned/opened.
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
export LC_ALL=C

YTDLP="${YT_DLP:-$(command -v yt-dlp)}"
VLC="${YT_VLC:-/Applications/VLC.app/Contents/MacOS/VLC}"
# up to 1080p H.264 video-only + AAC audio (played in two synced elements), else a combined mp4
FORMAT='bv*[height<=1080][vcodec^=avc1][ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b'

reply() {  # reply '<json>'  (native messaging: 4-byte little-endian length, then the body)
  local json="$1" n
  n=$(printf '%s' "$json" | wc -c | tr -d ' ')
  printf "$(printf '\\%03o\\%03o\\%03o\\%03o' $((n & 255)) $(((n >> 8) & 255)) $(((n >> 16) & 255)) $(((n >> 24) & 255)))"
  printf '%s' "$json"
}

len=$(dd bs=1 count=4 2>/dev/null | od -An -tu4 | tr -d ' ')
[[ "$len" =~ ^[0-9]+$ ]] && (( len > 0 && len < 4096 )) || { reply '{"ok":false,"error":"bad length"}'; exit 0; }
msg=$(dd bs=1 count="$len" 2>/dev/null)

[[ "$msg" =~ \"id\"[[:space:]]*:[[:space:]]*\"([A-Za-z0-9_-]{11})\" ]] || { reply '{"ok":false,"error":"bad video id"}'; exit 0; }
id="${BASH_REMATCH[1]}"
[[ "$msg" =~ \"action\"[[:space:]]*:[[:space:]]*\"(stream|vlc)\" ]] || { reply '{"ok":false,"error":"bad action"}'; exit 0; }
action="${BASH_REMATCH[1]}"

[[ -x "$YTDLP" ]] || { reply '{"ok":false,"error":"yt-dlp not found (brew install yt-dlp)"}'; exit 0; }

out=$("$YTDLP" --no-playlist --no-warnings --socket-timeout 15 -f "$FORMAT" -g "https://www.youtube.com/watch?v=$id" 2>/dev/null)
urls=()
while IFS= read -r line; do
  [[ "$line" =~ ^https://[A-Za-z0-9.-]+\.googlevideo\.com/[A-Za-z0-9._/:%?=\&~+,@-]+$ ]] && urls+=("$line")
done <<< "$out"
(( ${#urls[@]} >= 1 && ${#urls[@]} <= 2 )) || { reply '{"ok":false,"error":"yt-dlp returned no playable stream"}'; exit 0; }

if [[ "$action" == "stream" ]]; then
  json='{"ok":true,"urls":["'"${urls[0]}"'"'
  (( ${#urls[@]} == 2 )) && json+=',"'"${urls[1]}"'"'
  reply "$json]}"
else
  [[ -x "$VLC" ]] || { reply '{"ok":false,"error":"VLC not found in /Applications"}'; exit 0; }
  args=("${urls[0]}")
  (( ${#urls[@]} == 2 )) && args+=("--input-slave=${urls[1]}")
  nohup "$VLC" "${args[@]}" >/dev/null 2>&1 &
  disown
  reply '{"ok":true}'
fi
