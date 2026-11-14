const HOST = 'sk.yt.player';

// content script -> background -> native helper (runs yt-dlp). type: 'stream' | 'vlc'
chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (!msg || (msg.type !== 'stream' && msg.type !== 'vlc') || !/^[A-Za-z0-9_-]{11}$/.test(msg.id || '')) return;
  chrome.runtime.sendNativeMessage(HOST, { action: msg.type, id: msg.id })
    .then((reply) => sendResponse(reply && reply.ok ? reply : { ok: false, error: (reply && reply.error) || 'no reply' }))
    .catch((e) => sendResponse({ ok: false, error: String((e && e.message) || e) }));
  return true; // async response
});
