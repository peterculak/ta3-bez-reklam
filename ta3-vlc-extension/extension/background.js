const HOST = 'sk.ta3.vlc';

async function buildStreamUrl(videoId) {
  const res = await fetch('https://embed.livebox.cz/ta3_v2_test/vod-source.js');
  const js = await res.text();
  const m = js.match(/"src"\s*:\s*"([^"]+)"/);
  if (!m) throw new Error('Livebox source not found');
  let url = 'https:' + m[1].replace('{0}', videoId);
  // same quirk as Livebox's own player
  if (/^[A-F0-9]{8}(?:-[A-F0-9]{4}){3}-[A-F0-9]{12}/.test(videoId)) {
    url = url.replace('VideotekaEncoder', 'Videoteka');
  }
  return url;
}

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg.type !== 'open-vlc') return;
  (async () => {
    try {
      const url = await buildStreamUrl(msg.videoId);
      const reply = await chrome.runtime.sendNativeMessage(HOST, { url });
      sendResponse(reply && reply.ok ? { ok: true } : { ok: false, error: (reply && reply.error) || 'no reply' });
    } catch (e) {
      sendResponse({ ok: false, error: String(e.message || e) });
    }
  })();
  return true; // async response
});
