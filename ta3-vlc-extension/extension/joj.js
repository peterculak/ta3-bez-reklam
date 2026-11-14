// JOJ – replace the stock players with a plain <video> + dash.js / hls.js, plus "Open in VLC".
// Runs in two places:
//  - play.joj.sk/player/*  (Tivio, DASH .mpd with a tokenised URL known only at runtime)
//  - media.joj.sk/embed/*  (iframe on www.joj.sk, plain HLS master.m3u8)
// Either way we pick up the manifest URL the stock player requests (Resource Timing is shared with the page)
// and play that same stream ourselves.
(function () {
  if (document.getElementById('joj-own-player')) return;

  const STREAM_RE = /^https:\/\/[A-Za-z0-9.-]+\.joj\.sk\/[^?#]*(?:\.mpd|master\.m3u8)(?:[?#]|$)/;
  const findStream = () => {
    const e = performance.getEntriesByType('resource').find((r) => STREAM_RE.test(r.name));
    return e && e.name;
  };

  let tries = 0;
  const timer = setInterval(() => {
    const url = findStream();
    if (url) { clearInterval(timer); takeOver(url); }
    else if (++tries > 120) clearInterval(timer); // give up after ~30 s, stock player stays
  }, 250);

  function takeOver(streamUrl) {
    const isDash = /\.mpd(?:[?#]|$)/.test(streamUrl);
    if (isDash ? !window.dashjs : !window.Hls) return;

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
    Object.assign(bar.style, { position: 'absolute', right: '8px', top: '8px', zIndex: 5, display: 'flex', gap: '6px',
      opacity: 0, pointerEvents: 'none', transition: 'opacity .2s' });
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

    // show the pills only while the mouse is over the player (like the native controls):
    // fade in on movement, fade out after 2.5 s idle or when the mouse leaves (paused or not),
    // except while the pointer is actually on the pills
    let hideTimer = null;
    const showBar = (on) => { bar.style.opacity = on ? '.85' : '0'; bar.style.pointerEvents = on ? 'auto' : 'none'; };
    const hideNow = () => { clearTimeout(hideTimer); showBar(false); };
    const poke = () => {
      showBar(true);
      clearTimeout(hideTimer);
      hideTimer = setTimeout(() => { if (!bar.matches(':hover')) showBar(false); }, 2500);
    };
    wrap.addEventListener('mousemove', poke);
    // leaving the (iframe) document is reported differently across browsers, so listen to all of them
    wrap.addEventListener('mouseleave', hideNow);
    document.addEventListener('mouseleave', hideNow);
    document.documentElement.addEventListener('mouseleave', hideNow);
    document.addEventListener('mouseout', (e) => { if (!e.relatedTarget) hideNow(); });
    window.addEventListener('blur', hideNow);

    // keep the stock player quiet: pause anything that isn't ours, now and whenever it tries to play again
    const quiet = (v) => { if (v !== video) { v.pause(); v.muted = true; } };
    const onPlay = (e) => { if (e.target instanceof HTMLVideoElement) quiet(e.target); };
    document.addEventListener('play', onPlay, true);
    document.querySelectorAll('video').forEach(quiet);

    // hide the stock page content (don't remove it: the stock apps keep references to their DOM)
    const hidden = [...document.body.children].filter((el) => el !== wrap && getComputedStyle(el).display !== 'none');
    hidden.forEach((el) => el.style.setProperty('display', 'none', 'important'));

    let player = null;
    const restore = () => {
      document.removeEventListener('play', onPlay, true);
      if (player) player.destroy();
      wrap.remove();
      hidden.forEach((el) => el.style.removeProperty('display'));
    };
    origBtn.addEventListener('click', (e) => { e.preventDefault(); restore(); });

    vlcBtn.addEventListener('click', (e) => {
      e.preventDefault();
      vlcBtn.textContent = 'Opening…';
      chrome.runtime.sendMessage({ type: 'open-vlc-url', url: streamUrl }, (r) => {
        const err = chrome.runtime.lastError ? chrome.runtime.lastError.message : (!r || !r.ok ? (r && r.error) || 'unknown' : null);
        if (err) { console.error('[JOJ VLC]', err); vlcBtn.title = err; }
        vlcBtn.textContent = err ? '✖ Failed' : '✔ Sent to VLC';
        setTimeout(() => { vlcBtn.textContent = '▶ Open in VLC'; }, 3000);
      });
    });

    document.body.appendChild(wrap);

    if (isDash) {
      player = dashjs.MediaPlayer().create();
      player.on(dashjs.MediaPlayer.events.ERROR, (e) => { console.error('[JOJ VLC] dash error', e && e.error); restore(); });
      player.initialize(video, streamUrl, true);
    } else if (Hls.isSupported()) {
      player = new Hls();
      player.on(Hls.Events.ERROR, (_e, d) => { if (d.fatal) { console.error('[JOJ VLC] hls fatal', d.type, d.details); restore(); } });
      player.loadSource(streamUrl);
      player.attachMedia(video);
      video.play().catch(() => {}); // autoplay may be blocked, the user can press play
    } else {
      restore();
    }
  }
})();
