// YouTube – replace the stock player with our own <video> fed by yt-dlp (via the native helper).
// Video-only (up to 1080p) and audio are separate streams, so we play them in two synced elements
// and draw our own small controls. If anything fails, the stock player comes back.
(function () {
  'use strict';
  if (window.__ytpOwnPlayer) return;
  window.__ytpOwnPlayer = true;

  const CSS = `
  #ytp-own{position:absolute;inset:0;z-index:2000;background:#000;overflow:hidden;font:13px system-ui,sans-serif;color:#fff;user-select:none}
  #ytp-own video{position:absolute;inset:0;width:100%;height:100%;background:#000;object-fit:contain}
  #ytp-own .ytp-status{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;text-align:center;padding:16px;pointer-events:none}
  #ytp-own .ytp-pills{position:absolute;right:8px;top:8px;display:flex;gap:6px;transition:opacity .2s}
  #ytp-own .ytp-pills a{color:#fff;background:rgba(0,0,0,.55);padding:4px 10px;border-radius:12px;text-decoration:none;white-space:nowrap;font-size:12px;cursor:pointer}
  #ytp-own .ytp-bar{position:absolute;left:0;right:0;bottom:0;padding:24px 10px 8px;background:linear-gradient(transparent,rgba(0,0,0,.75));display:flex;align-items:center;gap:10px;transition:opacity .2s}
  #ytp-own .ytp-bar button{background:none;border:0;color:#fff;font-size:18px;cursor:pointer;padding:2px 6px;min-width:28px}
  #ytp-own .ytp-seek{flex:1;accent-color:#f00;cursor:pointer}
  #ytp-own .ytp-vol{width:70px;accent-color:#fff;cursor:pointer}
  #ytp-own.idle .ytp-pills{opacity:0;pointer-events:none}
  #ytp-own.idle.playing .ytp-bar{opacity:0;pointer-events:none}
  #ytp-own.idle.playing{cursor:none}`;

  let current = null; // { id, token, teardown }
  let seq = 0;

  const videoId = () => (location.pathname === '/watch' ? new URLSearchParams(location.search).get('v') : null);

  function check() {
    const id = videoId();
    if (id === (current && current.id)) return;
    if (current) { current.teardown(); current = null; }
    if (id && /^[A-Za-z0-9_-]{11}$/.test(id)) start(id);
  }
  window.addEventListener('yt-navigate-finish', check);
  window.addEventListener('popstate', check);
  setInterval(check, 1000); // safety net for navigations we don't get an event for
  check();

  function start(id) {
    const token = ++seq;
    const ac = new AbortController();
    const on = (t, ev, fn, opts) => t.addEventListener(ev, fn, Object.assign({ signal: ac.signal }, opts));
    let wrap = null, original = null, restoreOriginal = () => {}, retry = null;

    const entry = { id, token, teardown: () => { clearTimeout(retry); ac.abort(); restoreOriginal(); if (wrap) { wrap.querySelectorAll('video').forEach(stop); wrap.remove(); } if (audio) stop(audio); } };
    current = entry;
    const alive = () => current === entry;
    let audio = null;
    const stop = (el) => { try { el.pause(); el.removeAttribute('src'); el.load(); } catch (_) {} };

    // the player container appears a moment after navigation
    let tries = 0;
    (function mount() {
      if (!alive()) return;
      const container = document.querySelector('#player-container');
      original = document.querySelector('ytd-player');
      if (!container || !original) { if (++tries < 40) retry = setTimeout(mount, 250); return; }
      build(container);
    })();

    function build(container) {
      const style = document.createElement('style');
      style.textContent = CSS;
      wrap = document.createElement('div');
      wrap.id = 'ytp-own';
      wrap.tabIndex = -1;
      wrap.appendChild(style);
      if (getComputedStyle(container).position === 'static') container.style.position = 'relative';

      const video = document.createElement('video');
      video.playsInline = true;
      wrap.appendChild(video);
      const status = document.createElement('div');
      status.className = 'ytp-status';
      status.textContent = 'Načítavam…';
      wrap.appendChild(status);

      const pills = document.createElement('div');
      pills.className = 'ytp-pills';
      const mkPill = (t) => { const a = document.createElement('a'); a.textContent = t; pills.appendChild(a); return a; };
      const vlcBtn = mkPill('▶ Open in VLC');
      const origBtn = mkPill('Pôvodný prehrávač');
      wrap.appendChild(pills);

      const bar = document.createElement('div');
      bar.className = 'ytp-bar';
      const btn = (t, title) => { const b = document.createElement('button'); b.textContent = t; b.title = title; bar.appendChild(b); return b; };
      const playBtn = btn('▶', 'Prehrať / pozastaviť (k)');
      const time = document.createElement('span');
      time.textContent = '0:00 / 0:00';
      bar.appendChild(time);
      const seek = document.createElement('input');
      Object.assign(seek, { type: 'range', min: 0, max: 0, step: 0.1, value: 0, className: 'ytp-seek' });
      bar.appendChild(seek);
      const muteBtn = btn('🔊', 'Stlmiť (m)');
      const vol = document.createElement('input');
      Object.assign(vol, { type: 'range', min: 0, max: 1, step: 0.05, value: 1, className: 'ytp-vol' });
      bar.appendChild(vol);
      const fsBtn = btn('⛶', 'Celá obrazovka (f)');
      wrap.appendChild(bar);

      // hide (not remove) the stock player: keeps the layout box, and its code keeps references to its DOM
      const prevVis = original.style.visibility;
      original.style.visibility = 'hidden';
      const prevMuted = new Map();
      const quiet = (v) => { if (v !== video && v !== audio) { if (!prevMuted.has(v)) prevMuted.set(v, v.muted); v.pause(); v.muted = true; } };
      document.querySelectorAll('video').forEach(quiet);
      on(document, 'play', (e) => { if (e.target instanceof HTMLVideoElement) quiet(e.target); }, { capture: true });
      restoreOriginal = () => {
        original.style.visibility = prevVis;
        prevMuted.forEach((m, v) => { v.muted = m; });
      };
      container.appendChild(wrap);

      // ---- UI helpers ----
      const fmt = (s) => { s = Math.max(0, Math.floor(s || 0)); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
        return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0'); };
      let idleTimer = null;
      const poke = () => { wrap.classList.remove('idle'); clearTimeout(idleTimer);
        idleTimer = setTimeout(() => { if (!pills.matches(':hover') && !bar.matches(':hover')) wrap.classList.add('idle'); }, 2500); };
      on(wrap, 'mousemove', poke);
      on(wrap, 'mouseleave', () => { clearTimeout(idleTimer); wrap.classList.add('idle'); });
      wrap.classList.add('idle');

      const fail = (msg) => {
        console.error('[YT own player]', msg);
        status.textContent = '✖ ' + msg + ' — vraciam pôvodný prehrávač';
        wrap.classList.remove('idle');
        setTimeout(() => { if (alive()) { restoreOriginal(); wrap.remove(); } }, 3000);
      };
      origBtn.addEventListener('click', (e) => { e.preventDefault(); entry.teardown(); });
      vlcBtn.addEventListener('click', (e) => {
        e.preventDefault();
        vlcBtn.textContent = 'Opening…';
        chrome.runtime.sendMessage({ type: 'vlc', id }, (r) => {
          const err = chrome.runtime.lastError ? chrome.runtime.lastError.message : (!r || !r.ok ? (r && r.error) || 'unknown' : null);
          if (err) { console.error('[YT own player]', err); vlcBtn.title = err; }
          vlcBtn.textContent = err ? '✖ Failed' : '✔ Sent to VLC';
          setTimeout(() => { vlcBtn.textContent = '▶ Open in VLC'; }, 3000);
        });
      });

      // ---- ask the helper for the streams ----
      // yt-dlp occasionally fails transiently (network, YouTube hiccup), so try once more before giving up
      const requestStreams = (attempt) => chrome.runtime.sendMessage({ type: 'stream', id }, (r) => {
        if (!alive()) return;
        if (chrome.runtime.lastError || !r || !r.ok) {
          const why = (chrome.runtime.lastError && chrome.runtime.lastError.message) || (r && r.error) || 'neznáma chyba';
          if (attempt < 2) {
            console.warn('[YT own player] stream request failed, retrying:', why);
            status.textContent = 'Skúšam znova…';
            return setTimeout(() => { if (alive()) requestStreams(attempt + 1); }, 1000);
          }
          return fail(why);
        }
        attach(r.urls);
      });
      requestStreams(1);

      function attach(urls) {
        status.remove();
        video.src = urls[0];
        const split = urls.length > 1; // separate audio stream
        if (split) { audio = new Audio(); audio.preload = 'auto'; audio.src = urls[1]; video.muted = true; }
        const sound = () => (split ? audio : video); // the element that carries the sound
        let want = false; // does the user want playback (as opposed to a buffering pause)?

        const startBoth = () => { want = true; video.play().catch(() => {}); if (split) audio.play().catch(() => {}); };
        const stopBoth = () => { want = false; video.pause(); if (split) audio.pause(); };
        const toggle = () => (video.paused ? startBoth() : stopBoth());
        const seekTo = (t) => { video.currentTime = Math.max(0, Math.min(video.duration || t, t)); if (split) audio.currentTime = video.currentTime; };

        on(video, 'play', () => { wrap.classList.add('playing'); playBtn.textContent = '❚❚'; if (split && audio.paused) audio.play().catch(() => {}); });
        on(video, 'pause', () => { wrap.classList.remove('playing'); playBtn.textContent = '▶'; if (split) audio.pause(); });
        on(video, 'ended', () => { wrap.classList.remove('playing'); playBtn.textContent = '▶'; if (split) audio.pause(); });
        on(video, 'seeking', () => { if (split) audio.currentTime = video.currentTime; });
        on(video, 'waiting', () => { if (split) audio.pause(); });
        on(video, 'playing', () => { if (split && want) audio.play().catch(() => {}); });
        on(video, 'ratechange', () => { if (split) audio.playbackRate = video.playbackRate; });
        on(video, 'durationchange', () => { seek.max = video.duration || 0; time.textContent = fmt(video.currentTime) + ' / ' + fmt(video.duration); });
        on(video, 'timeupdate', () => {
          if (!seek.matches(':active')) seek.value = video.currentTime;
          time.textContent = fmt(video.currentTime) + ' / ' + fmt(video.duration);
          if (split && Math.abs(audio.currentTime - video.currentTime) > 0.3) audio.currentTime = video.currentTime; // drift
        });
        if (split) {
          on(audio, 'waiting', () => { if (want) video.pause(); });
          on(audio, 'playing', () => { if (want && video.paused) video.play().catch(() => {}); });
        }
        on(video, 'error', () => fail('prehrávanie zlyhalo (video)'));
        if (split) on(audio, 'error', () => fail('prehrávanie zlyhalo (zvuk)'));

        on(seek, 'input', () => seekTo(parseFloat(seek.value)));
        on(playBtn, 'click', toggle);
        on(video, 'click', toggle);
        const applyVol = () => { sound().volume = parseFloat(vol.value); sound().muted = muteBtn.dataset.m === '1'; muteBtn.textContent = muteBtn.dataset.m === '1' || +vol.value === 0 ? '🔇' : '🔊'; };
        on(vol, 'input', () => { muteBtn.dataset.m = '0'; applyVol(); });
        const toggleMute = () => { muteBtn.dataset.m = muteBtn.dataset.m === '1' ? '0' : '1'; applyVol(); };
        on(muteBtn, 'click', toggleMute);
        const toggleFs = () => (document.fullscreenElement ? document.exitFullscreen() : wrap.requestFullscreen().catch(() => {}));
        on(fsBtn, 'click', toggleFs);
        on(video, 'dblclick', toggleFs);

        // keyboard: only while the pointer is over our player and nothing is being typed
        on(document, 'keydown', (e) => {
          if (!wrap.matches(':hover') && document.fullscreenElement !== wrap) return;
          const t = e.target; if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName)) && t !== seek && t !== vol) return;
          if (e.ctrlKey || e.metaKey || e.altKey) return;
          const k = e.key.toLowerCase();
          const acts = { ' ': toggle, k: toggle, arrowleft: () => seekTo(video.currentTime - 5), arrowright: () => seekTo(video.currentTime + 5),
            j: () => seekTo(video.currentTime - 10), l: () => seekTo(video.currentTime + 10), m: toggleMute, f: toggleFs };
          if (acts[k]) { e.preventDefault(); e.stopPropagation(); acts[k](); poke(); }
        }, { capture: true });

        startBoth();
      }
    }
  }
})();
