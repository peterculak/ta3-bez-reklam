(function () {
  if (document.getElementById('ta3-vlc-btn')) return;

  let videoId = null;
  for (const s of document.scripts) {
    const m = s.textContent.match(/LiveboxPlayer\.archiv\(\{[^}]*?"videoId"\s*:\s*"([^"]+)"/);
    if (m) { videoId = m[1]; break; }
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
})();
