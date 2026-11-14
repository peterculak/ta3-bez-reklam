const HOST = 'sk.yt.player';
const TTL_MS = 30 * 60 * 1000; // stream URLs stay valid for hours; 30 min is plenty and keeps them fresh

const inflight = new Map(); // video id -> Promise<reply>, so a double request runs yt-dlp once

async function cached(id) {
  try {
    const key = 'stream:' + id;
    const got = (await chrome.storage.session.get(key))[key];
    if (got && Date.now() - got.t < TTL_MS) return got.reply;
    if (got) await chrome.storage.session.remove(key);
  } catch (_) { /* storage unavailable: just skip the cache */ }
  return null;
}

async function getStreams(id, fresh) {
  if (fresh) { try { await chrome.storage.session.remove('stream:' + id); } catch (_) {} } // the cached URLs were rejected
  const hit = fresh ? null : await cached(id);
  if (hit) return hit;
  if (inflight.has(id)) return inflight.get(id);
  const p = chrome.runtime.sendNativeMessage(HOST, { action: 'stream', id })
    .then(async (reply) => {
      if (!reply || !reply.ok) return { ok: false, error: (reply && reply.error) || 'no reply' };
      try { await chrome.storage.session.set({ ['stream:' + id]: { t: Date.now(), reply } }); } catch (_) {}
      return reply;
    })
    .catch((e) => ({ ok: false, error: String((e && e.message) || e) }))
    .finally(() => inflight.delete(id));
  inflight.set(id, p);
  return p;
}

// content script -> background -> native helper (runs yt-dlp). type: 'stream' | 'vlc'
chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (!msg || (msg.type !== 'stream' && msg.type !== 'vlc') || !/^[A-Za-z0-9_-]{11}$/.test(msg.id || '')) return;
  const run = msg.type === 'stream'
    ? getStreams(msg.id, !!msg.fresh)
    : chrome.runtime.sendNativeMessage(HOST, { action: 'vlc', id: msg.id })
        .then((reply) => (reply && reply.ok ? reply : { ok: false, error: (reply && reply.error) || 'no reply' }))
        .catch((e) => ({ ok: false, error: String((e && e.message) || e) }));
  run.then(sendResponse);
  return true; // async response
});
