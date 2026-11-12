#!/bin/bash
# Chrome native messaging host (macOS): receives {"url":"..."} and opens it in VLC.
# No dependencies beyond what ships with macOS.
OPEN="${TA3_OPEN:-/usr/bin/open}"

reply() {  # reply '<json>'  (messages are < 256 bytes)
  local json="$1"
  printf "\\$(printf '%03o' "${#json}")\\0\\0\\0%s" "$json"
}

# 4-byte little-endian length prefix, then the JSON body
len=$(dd bs=1 count=4 2>/dev/null | od -An -tu4 | tr -d ' ')
[[ "$len" =~ ^[0-9]+$ ]] && (( len > 0 && len < 4096 )) || { reply '{"ok":false,"error":"bad length"}'; exit 0; }
msg=$(dd bs=1 count="$len" 2>/dev/null)

# Strict allow-list: https URL on *.livebox.sk with URL-safe characters only
pattern='"url"[[:space:]]*:[[:space:]]*"(https://[A-Za-z0-9.-]+\.livebox\.sk/[A-Za-z0-9._/:%?=&|~+,@-]*)"'
if [[ "$msg" =~ $pattern ]]; then
  if "$OPEN" -a VLC "${BASH_REMATCH[1]}" >/dev/null 2>&1; then
    reply '{"ok":true}'
  else
    reply '{"ok":false,"error":"could not launch VLC"}'
  fi
else
  reply '{"ok":false,"error":"refused: not a livebox.sk URL"}'
fi
