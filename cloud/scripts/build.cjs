'use strict';
// No cloud credentials needed and no event storage is opened by this build check.
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { DatabaseSync } = require('node:sqlite');
const sharp = require('sharp');
const root = path.join(__dirname, '..');
(async () => {
  const [major, minor] = process.versions.node.split('.').map(Number);
  if (!((major === 22 && minor >= 13) || major === 24)) throw new Error('Use Node 22.13+ within 22.x, or Node 24.x.');
  for (const file of ['server.js', 'tools/personal-qr-rehearsal.cjs', 'scripts/generate-secrets.cjs', 'src/server.cjs', 'src/store.cjs', 'src/hosting.cjs', 'src/operations.cjs', 'src/presentation.cjs', 'src/event-management.cjs', 'src/qr.cjs', 'src/deliveries.cjs', 'src/onboarding-print.cjs', 'shared/print-protocol.cjs', 'public/onboarding-admin.js', 'public/personal.js', 'public/personal-admin.js', 'public/app.js', 'public/gallery.js', 'public/presentation-admin.js', 'public/slideshow.js', 'public/slideshow-model.js', 'public/qr.js']) {
    execFileSync(process.execPath, ['--check', path.join(root, file)]);
  }
  for (const file of ['public/index.html', 'public/gallery.html', 'public/app.js', 'public/gallery.js', 'public/style.css', 'public/slideshow.html', 'public/slideshow.css', 'public/qr.js']) {
    if (!fs.statSync(path.join(root, file)).size) throw new Error('Missing or empty asset: ' + file);
  }
  const db = new DatabaseSync(':memory:');
  try { db.exec('CREATE TABLE check_build(x INTEGER); INSERT INTO check_build VALUES(1)');
    if (db.prepare('SELECT x FROM check_build').get().x !== 1) throw new Error('SQLite check failed');
  } finally { db.close(); }
  const svg=require('../src/qr.cjs').toSvg('https://example.invalid/s/'+'a'.repeat(32));
  if(!svg.includes('<svg')||!svg.includes('<path'))throw new Error('Local QR encoder check failed');
  const jpeg = await sharp({ create: { width: 8, height: 8, channels: 3, background: '#808080' } }).jpeg().toBuffer();
  const info = await sharp(jpeg).metadata();
  if (info.format !== 'jpeg' || info.width !== 8) throw new Error('sharp native JPEG check failed');
  console.log(JSON.stringify({ ok: true, version: require('../package.json').version,
    node: process.version, sharp: require('sharp/package.json').version,
    checked: ['CommonJS startup syntax', 'in-memory SQLite', 'native JPEG encoding and decoding', 'web assets', 'local QR encoder'],
    runtime_storage: 'not opened by build; runtime and redeploy checks still required' }, null, 2));
})().catch(e => { console.error(e.message); process.exitCode = 1; });
