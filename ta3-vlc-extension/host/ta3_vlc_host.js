#!/usr/bin/env node
// Chrome native messaging host: receives {url}, opens it in VLC.
const { execFile } = require('child_process');

function reply(obj) {
  const body = Buffer.from(JSON.stringify(obj));
  const len = Buffer.alloc(4);
  len.writeUInt32LE(body.length, 0);
  process.stdout.write(Buffer.concat([len, body]));
}

let buf = Buffer.alloc(0);
process.stdin.on('data', (chunk) => {
  buf = Buffer.concat([buf, chunk]);
  if (buf.length < 4) return;
  const len = buf.readUInt32LE(0);
  if (buf.length < 4 + len) return;
  let msg;
  try { msg = JSON.parse(buf.slice(4, 4 + len).toString()); } catch { return reply({ ok: false, error: 'bad json' }); }
  let u;
  try { u = new URL(msg.url); } catch { return reply({ ok: false, error: 'bad url' }); }
  if (u.protocol !== 'https:' || !u.hostname.endsWith('.livebox.sk')) {
    return reply({ ok: false, error: 'refused: not a livebox.sk URL' });
  }
  execFile('/usr/bin/open', ['-a', 'VLC', u.href], (err) => {
    reply(err ? { ok: false, error: err.message } : { ok: true });
    setTimeout(() => process.exit(0), 100);
  });
});
