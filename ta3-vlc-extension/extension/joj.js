// JOJ play (Tivio) – replace the stock player with a plain <video> + dash.js, plus "Open in VLC".
// The stock player gets a tokenised DASH manifest at runtime, so we pick up the .mpd URL it requests
// (Resource Timing is shared with the page) and play that same URL ourselves.
(function () {
  if (document.getElementById('joj-own-player')) return;

  const MPD_RE = /^https:\/\/[A-Za-z0-9.-]+\.joj\.sk\/[^?#]*\.mpd(?:[?#]|$)/;
  const findMpd = () => {
    const e = performance.getEntriesByType('resource').find((r) => MPD_RE.test(r.name));
    return e && e.name;
  };

  let tries = 0;
  const timer = setInterval(() => {
    const url = findMpd();
    if (url) { clearInterval(timer); takeOver(url); }
    else if (++tries > 120) clearInterval(timer); // give up after ~30 s, stock player stays
  }, 250);

  function takeOver(mpdUrl) {
    if (!window.dashjs) return;
    const root = document.getElementById('__next');

    const wrap = document.createElement('div');
    wrap.id = 'joj-own-player';
    Object.assign(wrap.style, { position: 'fixed', inset: '0', zIndex: 2147483646, background: '#000' });

    const video = document.createElement('video');
    video.controls = true;
    video.playsInline = true;
    video.autoplay = true;
    Object.assign(video.style, { width: '100%', height: '100%', background: '#000' });
    wrap.appendChild(video);

    // top-right pill row: Open in VLC | original player
    const bar = document.createElement('div');
    Object.assign(bar.style, { position: 'absolute', right: '8px', top: '8px', zIndex: 5, display: 'flex', gap: '6px', opacity: .8 });
    const pill = (text) => {
      const a = document.createElement('a');
      a.href = '#';
      a.textContent = text;
      Object.assign(a.style, { font: '12px system-ui,sans-serif', color: '#fff', background: 'rgba(0,0,0,.55)',
        padding: '4px 10px', borderRadius: '12px', textDecoration: 'none', whiteSpace: 'nowrap' });
      bar.appendChild(a);
      return a;
    };
    const vlcBtn = pill('▶ Open in VLC');
    const origBtn = pill('Pôvodný prehrávač');
    wrap.appendChild(bar);

    // keep the stock player quiet: pause anything that isn't ours, now and whenever it tries to play again
    const quiet = (v) => { if (v !== video) { v.pause(); v.muted = true; } };
    const onPlay = (e) => { if (e.target instanceof HTMLVideoElement) quiet(e.target); };
    document.addEventListener('play', onPlay, true);
    document.querySelectorAll('video').forEach(quiet);

    let player = null;
    const restore = () => {
      document.removeEventListener('play', onPlay, true);
      if (player) player.destroy();
      wrap.remove();
      if (root) root.style.removeProperty('display');
    };
    origBtn.addEventListener('click', (e) => { e.preventDefault(); restore(); });

    vlcBtn.addEventListener('click', (e) => {
      e.preventDefault();
      vlcBtn.textContent = 'Opening…';
      chrome.runtime.sendMessage({ type: 'open-vlc-url', url: mpdUrl }, (r) => {
        const err = chrome.runtime.lastError ? chrome.runtime.lastError.message : (!r || !r.ok ? (r && r.error) || 'unknown' : null);
        if (err) { console.error('[JOJ VLC]', err); vlcBtn.title = err; }
        vlcBtn.textContent = err ? '✖ Failed' : '✔ Sent to VLC';
        setTimeout(() => { vlcBtn.textContent = '▶ Open in VLC'; }, 3000);
      });
    });

    if (root) root.style.setProperty('display', 'none', 'important');
    document.body.appendChild(wrap);

    player = dashjs.MediaPlayer().create();
    player.on(dashjs.MediaPlayer.events.ERROR, (e) => {
      console.error('[JOJ VLC] dash error', e && e.error);
      restore();
    });
    player.initialize(video, mpdUrl, true);
  }
})();
