(function () {
  if (document.getElementById('ta3-vlc-btn')) return;

  let videoId = null, domId = null, poster = null;
  for (const s of document.scripts) {
    const t = s.textContent;
    const m = t.match(/LiveboxPlayer\.archiv\(\{([^}]*)\}/);
    if (!m) continue;
    const cfg = m[1];
    const v = cfg.match(/"videoId"\s*:\s*"([^"]+)"/);
    if (!v) continue;
    videoId = v[1];
    const d = cfg.match(/"dom_id"\s*:\s*"([^"]+)"/);
    const p = cfg.match(/"poster"\s*:\s*"([^"]+)"/);
    domId = d && d[1];
    poster = p && p[1].replace(/\\\//g, '/');
    break;
  }
  if (!videoId) return; // not a video page

  const live = document.querySelector('a.ta32026_header-live');
  const btn = document.createElement(live ? 'a' : 'button');
  btn.id = 'ta3-vlc-btn';
  btn.textContent = '▶ Open in VLC';
  if (live) {
    // reuse TA3's own "Live" pill styling, just recolour it orange
    btn.className = live.className;
    btn.href = '#';
    btn.title = 'Open in VLC';
    btn.style.background = '#f28e2b';
    btn.style.borderColor = '#f28e2b';
    btn.style.color = '#fff';
    btn.style.marginLeft = '8px';
    btn.style.cursor = 'pointer';
    btn.style.whiteSpace = 'nowrap';
  } else {
    // fallback: floating button
    Object.assign(btn.style, {
      position: 'fixed', top: '90px', right: '16px', zIndex: 2147483647,
      padding: '10px 16px', border: 'none', borderRadius: '8px',
      background: '#f28e2b', color: '#fff', font: '600 14px system-ui, sans-serif',
      cursor: 'pointer', boxShadow: '0 2px 8px rgba(0,0,0,.35)'
    });
  }
  btn.addEventListener('click', (ev) => {
    ev.preventDefault();
    btn.textContent = 'Opening…';
    chrome.runtime.sendMessage({ type: 'open-vlc', videoId }, (r) => {
      if (chrome.runtime.lastError || !r || !r.ok) {
        const err = (chrome.runtime.lastError && chrome.runtime.lastError.message) || (r && r.error) || 'unknown';
        btn.textContent = '✖ Failed';
        btn.title = err;
        console.error('[TA3 VLC]', err);
        alert('TA3 Open in VLC failed: ' + err + '\n\nDid you run ta3-vlc-extension/install.sh ?');
      } else {
        btn.textContent = '✔ Sent to VLC';
      }
      setTimeout(() => { btn.textContent = '▶ Open in VLC'; }, 3000);
    });
  });
  if (live) live.insertAdjacentElement('afterend', btn); else document.body.appendChild(btn);

  // ---- POC: replace TA3's player with a plain <video> + hls.js (archive videos only) ----
  if (domId) setupOwnPlayer(videoId, domId, poster);

  function setupOwnPlayer(videoId, domId, poster) {
    const original = document.getElementById(domId);
    if (!original || !window.Hls) return;
    const wrap = document.createElement('div');
    wrap.id = 'ta3-own-player';
    // match TA3's own sizing: fixed 16:9 up to 720px next to the side list, full width on narrow screens
    const css = document.createElement('style');
    css.textContent = '#ta3-own-player{position:relative;background:#000;aspect-ratio:16/9;width:min(720px,calc(100vw - 360px));min-width:0}' +
      '#ta3-own-player>video{position:absolute;inset:0;display:block}' +
      '@media (max-width:1024px){#ta3-own-player{width:100%}}';
    wrap.appendChild(css);
    const video = document.createElement('video');
    video.controls = true;
    video.playsInline = true;
    video.preload = 'metadata';
    if (poster) video.poster = poster;
    Object.assign(video.style, { width: '100%', height: '100%', background: '#000' });
    wrap.appendChild(video);

    const link = document.createElement('a');
    link.href = '#';
    link.textContent = 'Pôvodný prehrávač TA3';
    Object.assign(link.style, { position: 'absolute', right: '8px', top: '8px', zIndex: 5, font: '12px system-ui,sans-serif',
      color: '#fff', background: 'rgba(0,0,0,.55)', padding: '3px 8px', borderRadius: '10px', textDecoration: 'none', opacity: .75 });
    wrap.appendChild(link);

    let hls = null;
    const restore = () => {
      if (hls) hls.destroy();
      wrap.remove();
      original.style.removeProperty('display');
    };
    link.addEventListener('click', (e) => { e.preventDefault(); restore(); });

    original.style.setProperty('display', 'none', 'important');
    original.parentNode.insertBefore(wrap, original);

    chrome.runtime.sendMessage({ type: 'get-stream-url', videoId }, (r) => {
      if (chrome.runtime.lastError || !r || !r.ok) { console.error('[TA3 VLC] stream url failed', chrome.runtime.lastError || r); return restore(); }
      if (Hls.isSupported()) {
        hls = new Hls();
        hls.on(Hls.Events.ERROR, (_e, d) => { if (d.fatal) { console.error('[TA3 VLC] hls fatal', d.type, d.details); restore(); } });
        hls.loadSource(r.url);
        hls.attachMedia(video);
      } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = r.url;
      } else {
        restore();
      }
    });
  }
})();
