# JOJ archive (joj.sk) — API notes

Research notes for the planned **JOJ archive** tab: poster-tile browsing of `joj.sk/relacie`,
like the Prehraj.to tab, with play/download per episode (no auto-download, unlike STVR/Tyzden).

Everything below was proven with `curl` and a logged-in Chrome session before writing any Kotlin.
See [`AGENT.md`](../../AGENT.md) → "Never Guess Endpoints".

## Browsing (no login, no API key needed)

Plain server-rendered HTML — parse with jsoup, same as `PrehrajScraper.kt` / `StvScraper.kt`.

- **Show list**: `https://www.joj.sk/relacie` — ~156 shows, each a poster tile:
  `<a href="https://www.joj.sk/relacie/<id>-<slug>">`, poster at
  `https://img.joj.sk/r150x230n/<uuid>` (also `.webp` via `<source srcset>`).
- **Episode list**: `https://www.joj.sk/relacie/<id>-<slug>/epizody`
  - Season filter + pagination: `.../epizody/strana-2?season=<seasonId>&sort=desc`
  - Each episode tile has a thumbnail (`img.joj.sk/r310x180n/<uuid>`), title ("Epizóda N"),
    season, and date.
  - **Two different episode link shapes** — this is the fork point for playback:
    - `https://www.joj.sk/relacia/<id>-<slug>/epizoda/<episodeId>-<slug>` — own page on joj.sk
    - `https://play.joj.sk/player/<tivioVideoId>?type=VIDEO` — goes straight to the Tivio player

In a small sample, most *older* archive episodes were Tivio-only; recent episodes and bonus clips
had a `joj.sk/relacia/.../epizoda/...` page. Both link shapes need to be handled.

## Playback path A — `joj.sk/relacia/.../epizoda/...` pages (embed HLS)

These pages embed `https://media.joj.sk/embed/<id>`, which is a plain page containing a public
HLS URL, no auth, no token exchange:

```
https://vod.cdn.joj.sk/vod/<date>/<uuid>-,720p,1080p,360p,540p,.mp4.urlset/master.m3u8
```

Fetch the `joj.sk/relacia/.../epizoda/...` page → extract the `media.joj.sk/embed/<id>` iframe src
→ fetch that page → regex out the `master.m3u8` URL. That's it; feed it straight to
`PlayResolver`/the download worker like any other m3u8.

## Playback path B — `play.joj.sk/player/<id>` (Tivio)

This is the harder path: Tivio (`tivio-production` Firebase project) backs JOJ's "JOJ play" platform.
The player page is an empty Next.js shell; everything happens client-side via Firebase JS SDK + a
Shaka Player instance. **No credentials of any kind are required for this content** — confirmed
with plain `curl`, no cookies, no JOJ login, not even the anonymous-auth step strictly (though we
use it for cleanliness / to match what the real client does).

### 1. Anonymous Firebase auth

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=AIzaSyB02udgMkNLADkLJ_w5YNBMR2VR1WHfusI
Content-Type: application/json
{"returnSecureToken":true}
```

→ `{"idToken": "...", "refreshToken": "...", "localId": "..."}`. The API key is Tivio's public
Firebase web key (shipped in every browser's JS bundle — not a secret, just a project identifier).

### 2. Look up the video's Firestore id (optional — only if you don't already have it)

The `tivioVideoId` from the `play.joj.sk/player/<id>` URL is already what you need for step 3.
If you ever need the raw Firestore doc (metadata, source list, etc.), it's readable with zero auth:

```
GET https://firestore.googleapis.com/v1/projects/tivio-production/databases/(default)/documents/videos/<tivioVideoId>
```

Its `sources` field lists *unsigned* URLs like `https://st01-2.iptv.joj.sk/<id>-pc.m3u8` — these
403 directly; they only become playable once signed by step 3.

### 3. `getSourceUrl` cloud function — the actual signer

This was the key discovery. The tokenised `/at/<hash>/<timestamp>/...` URL is **not** computed by
client-side JS (no hashing, no secret math to reverse-engineer) — it's returned directly by a
Firebase callable function, found by decoding the inline base64 source maps that the webpack
bundle ships (`src/firebase/rpc/getSourceUrl.ts`):

```
POST https://europe-west3-tivio-production.cloudfunctions.net/getSourceUrl
Content-Type: application/json
Authorization: Bearer <idToken from step 1>

{
  "data": {
    "id": "<tivioVideoId>",
    "documentType": "video",
    "capabilities": [
      {"codec": "h264", "protocol": "hls", "encryption": "none"}
    ]
  }
}
```

→

```json
{"result":{"url":"https://st02-2.iptv.joj.sk/at/<hash>/<ts>/<id>-pc.m3u8", ...}}
```

That URL is immediately fetchable with a plain HTTP client — verified end to end with curl,
resolving a real HLS master playlist with no cookies/headers. The same call with
`{"codec":"h264","protocol":"dash","encryption":"none"}` returns the `.mpd` variant instead.

`capabilities` is a required field (returns `400 capabilities is a required field` if omitted) —
it's the client's claimed set of supported codec/protocol/encryption combos; the function presumably
picks a `sources` entry from the Firestore doc matching one of them.

### Why this matters

This replaces what was originally assumed to need a hidden WebView (loading the real player page
and sniffing the manifest request, like `ta3-vlc-extension/extension/joj.js` does for the Chrome
extension). Instead it's two small HTTP calls + a GET, exactly like `PrehrajScraper`'s login flow.
No WebView, no JOJ account, no JS execution on-device.

### Paywalled (subscription) content — confirmed, and deliberately unsupported

Some Tivio episodes need a *real* (non-anonymous) JOJ play account — confirmed by testing with
curl: `getSourceUrl` returns `401 {"error":{"message":"User not found","status":"UNAUTHENTICATED"}}`
for a brand-new anonymous token on such a video, even though the exact same call succeeds
immediately for a free one. (The error message is misleading — it's not about the token being
invalid, it's the cloud function rejecting an anonymous user for paid content.)

The reliable signal is the video's Firestore doc: a "Zadarmo" ("free") badge in the joj.sk HTML
is *not* reliable (seen missing on a perfectly free embed-HLS episode), but the `monetizations`
array is — a `"subscription"`-typed entry means it needs a paid account:

```
GET https://firestore.googleapis.com/v1/projects/tivio-production/databases/(default)/documents/videos/<tivioId>
```
→ `fields.monetizations.arrayValue.values[].mapValue.fields.type.stringValue == "subscription"`

Firestore's `:batchGet` endpoint is blocked by the project's security rules (`403 PERMISSION_DENIED`,
confirmed with curl) — only single-document `GET`s are allowed, so this is one small
unauthenticated request per Tivio episode, done in parallel per page of episodes.

**Decision: the app has no JOJ login UI, and doesn't collect the user's JOJ play credentials.**
`JojScraper.fetchEpisodes()` filters out any Tivio episode whose doc has a `"subscription"`
monetization before it ever reaches the UI — paid content is simply absent from the list, not
shown-but-broken. `resolveTivioUrl()` still has a defensive check that turns the same 401 into a
clear Slovak error message, in case of a race between list-fetch and tap (unlikely but cheap to
handle) — belt and braces, not the primary mechanism.

### Other open question

- Token/URL lifetime (the `<ts>` looks like issue-time in ms; unknown how long it stays valid —
  probably fine since we resolve right before play/download, same pattern as other scrapers).

## Implementation plan

- `JojScraper.kt` (new): show list + episode list (jsoup, path A), embed-HLS resolution (path A
  playback), and the Tivio `getSourceUrl` flow (path B playback) — cache the anonymous `idToken`
  in memory like `PrehrajScraper.sessionCookie`, refresh on 401/expiry.
- `Tab.JOJ` added to `MainViewModel.Tab` / `mainTabs`.
- UI: reuse `PrehrajBrowse.kt`'s poster-grid pattern (TMDB-browse is JOJ-specific here — no TMDB,
  just `joj.sk/relacie` shows) for show browsing, then an episode list screen per show.
- `PlayResolver.kt`: branch on show origin (JOJ show) → `JojScraper.resolvePlayableUrl(episode)`,
  which internally picks path A or B based on the stored episode URL shape.
- **No auto-download** — these are watch-driven, play or manually-triggered download only, unlike
  STVR/Tyzden's `AutoDownloadWorker`.
