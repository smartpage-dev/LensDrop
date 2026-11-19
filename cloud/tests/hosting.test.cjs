'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');
const sharp = require('sharp');
const { prepareHosting, validatePath, MARKER } = require('../src/hosting.cjs');
const { createApp, CHUNK } = require('../src/server.cjs');
const root = path.resolve(__dirname, '..');
function fixture() {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'lensdrop-hosting-'));
  const env = { LENSDROP_ADMIN_KEY: crypto.randomBytes(32).toString('hex'),
    LENSDROP_DATA_DIR: path.join(temp, 'private-data'), LENSDROP_STORAGE_ID: crypto.randomBytes(16).toString('hex'),
    LENSDROP_INITIALIZE_STORAGE: '1', LENSDROP_FREE_RESERVE_BYTES: '1024' };
  return { temp, env, cleanup: () => fs.rmSync(temp, { recursive: true, force: true }) };
}
function code(fn, expected) { assert.throws(fn, e => e.code === expected); }
test('hosting: rejects relative paths, release paths, and overlapping app storage', () => {
  for (const value of [undefined, 'var', './data']) code(() => validatePath(value, root), 'absolute_data_dir_required');
  for (const value of ['/tmp/public_html/data', '/tmp/hbuilds/release/data', '/tmp/nodejs/data']) code(() => validatePath(value, root), 'deployment_tree_forbidden');
  code(() => validatePath(path.join(root, 'private-data'), root), 'data_dir_overlaps_app');
  code(() => validatePath(path.dirname(root), root), 'data_dir_overlaps_app');
});
test('hosting: symlink aliases cannot disguise deployment-directory storage', () => {
  const f = fixture(); try {
    fs.mkdirSync(path.join(f.temp, 'hbuilds')); fs.symlinkSync(path.join(f.temp, 'hbuilds'), path.join(f.temp, 'alias'));
    code(() => validatePath(path.join(f.temp, 'alias', 'data'), root), 'deployment_tree_forbidden');
  } finally { f.cleanup(); }
});
test('hosting: placeholder administrator keys and invalid identities are rejected', () => {
  const f = fixture(); try {
    code(() => prepareHosting({ ...f.env, LENSDROP_ADMIN_KEY: 'REPLACE_WITH_YOUR_OWN_RANDOM_SECRET' }, root), 'admin_key_required');
    code(() => prepareHosting({ ...f.env, LENSDROP_STORAGE_ID: 'not-an-id' }, root), 'storage_id_required');
    assert.equal(fs.existsSync(f.env.LENSDROP_DATA_DIR), false);
  } finally { f.cleanup(); }
});
test('hosting: missing storage fails closed without creating a new directory', () => {
  const f = fixture(); try {
    code(() => prepareHosting({ ...f.env, LENSDROP_INITIALIZE_STORAGE: '0' }, root), 'storage_not_initialized');
    assert.equal(fs.existsSync(f.env.LENSDROP_DATA_DIR), false);
  } finally { f.cleanup(); }
});
test('hosting: existing unmarked data is never automatically adopted or erased', () => {
  const f = fixture(); try {
    fs.mkdirSync(f.env.LENSDROP_DATA_DIR); const p = path.join(f.env.LENSDROP_DATA_DIR, 'keep.jpg'); fs.writeFileSync(p, 'KEEP');
    code(() => prepareHosting(f.env, root), 'unmarked_nonempty_storage'); assert.equal(fs.readFileSync(p, 'utf8'), 'KEEP');
    assert.equal(fs.existsSync(path.join(f.env.LENSDROP_DATA_DIR, MARKER)), false);
  } finally { f.cleanup(); }
});
test('hosting: first initialization and subsequent safe boot preserve identity and database', async () => {
  const f = fixture(); try {
    const cfg = prepareHosting(f.env, root), app = createApp(cfg); await app.close();
    const marker = fs.readFileSync(path.join(f.env.LENSDROP_DATA_DIR, MARKER), 'utf8');
    const next = prepareHosting({ ...f.env, LENSDROP_INITIALIZE_STORAGE: '0' }, root);
    assert.equal(next.deployment.storage_id, f.env.LENSDROP_STORAGE_ID);
    assert.equal(next.deployment.initialization_enabled, false);
    assert.equal(next.deployment.persistence_verified_by_host, false);
    assert.equal(fs.readFileSync(path.join(f.env.LENSDROP_DATA_DIR, MARKER), 'utf8'), marker);
    assert.ok(!fs.readdirSync(f.env.LENSDROP_DATA_DIR).some(x => x.startsWith('.lensdrop-probe-')));
  } finally { f.cleanup(); }
});
test('hosting: wrong storage identity fails without rewriting the marker', () => {
  const f = fixture(); try {
    prepareHosting(f.env, root);
    const before = fs.readFileSync(path.join(f.env.LENSDROP_DATA_DIR, MARKER), 'utf8');
    code(() => prepareHosting({ ...f.env, LENSDROP_STORAGE_ID: crypto.randomBytes(16).toString('hex') }, root), 'storage_identity_mismatch');
    assert.equal(fs.readFileSync(path.join(f.env.LENSDROP_DATA_DIR, MARKER), 'utf8'), before);
  } finally { f.cleanup(); }
});
test('hosting: missing database cannot be mistaken for an empty healthy gallery', () => {
  const f = fixture(); try {
    prepareHosting(f.env, root);
    code(() => prepareHosting({ ...f.env, LENSDROP_INITIALIZE_STORAGE: '0' }, root), 'database_missing');
  } finally { f.cleanup(); }
});
test('hosting: invalid quotas and initialization flags fail before data creation', () => {
  const f = fixture(); try {
    for (const val of ['-1', '0', 'abc', 'Infinity', '1.5']) code(() => prepareHosting({ ...f.env, LENSDROP_EVENT_QUOTA_BYTES: val }, root), 'invalid_numeric_setting');
    code(() => prepareHosting({ ...f.env, LENSDROP_INITIALIZE_STORAGE: 'yes' }, root), 'invalid_initialize_setting');
    assert.equal(fs.existsSync(f.env.LENSDROP_DATA_DIR), false);
  } finally { f.cleanup(); }
});
async function start(entryDir, env) {
  const child = spawn(process.execPath, ['-e', 'require(process.argv[1])', path.join(entryDir, 'server.js')], {
    env: { ...process.env, ...env, PORT: '0', HOST: '127.0.0.1' }, stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '', error = '';
  child.stderr.on('data', b => { error += b.toString(); });
  const port = await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('startup timeout: ' + error)); }, 8000);
    child.once('exit', code => { clearTimeout(timeout); reject(new Error('startup exited: ' + code + ' ' + error)); });
    child.stdout.on('data', b => {
      output += b.toString();
      for (const line of output.split('\n')) {
        try { const value = JSON.parse(line); if (value.type === 'lensdrop_ready') { clearTimeout(timeout); resolve(value.port); } } catch {}
      }
    });
  });
  return { child, base: 'http://127.0.0.1:' + port, stop: async () => {
    if (child.exitCode !== null) return;
    const exited = new Promise((resolve, reject) => {
      const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('stop timeout')); }, 8000);
      child.once('exit', code => { clearTimeout(timer); assert.equal(code, 0, error); resolve(); });
    }); child.kill('SIGTERM'); await exited;
  } };
}
test('hosting: CommonJS host entry, private diagnostics and release replacement preserve an interrupted upload', async () => {
  const f = fixture(); let active;
  try {
    active = await start(root, f.env);
    async function req(route, token, body, raw = false) {
      const r = await fetch(active.base + route, { method: body === undefined ? 'GET' : 'POST',
        headers: { Authorization: 'Bearer ' + (token || ''), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
        body: body === undefined ? undefined : JSON.stringify(body) });
      return { status: r.status, body: raw ? Buffer.from(await r.arrayBuffer()) : await r.json() };
    }
    assert.equal((await req('/health')).status, 200);
    assert.equal((await req('/v1/admin/deployment')).status, 401);
    for (const route of ['/sample.hostinger.env', '/package.json', '/src/store.cjs', '/.lensdrop-storage.json']) assert.equal((await req(route)).status, 404);
    const before = (await req('/v1/admin/deployment', f.env.LENSDROP_ADMIN_KEY)).body;
    const e = (await req('/v1/events', f.env.LENSDROP_ADMIN_KEY, { name: 'Persistence test', policy: 'review' })).body;
    const d = (await req('/v1/events/' + e.id + '/devices', f.env.LENSDROP_ADMIN_KEY, { label: 'Desktop pilot' })).body;
    const bytes = await sharp(crypto.randomBytes(1600 * 1400 * 3), { raw: { width: 1600, height: 1400, channels: 3 } }).jpeg({ quality: 97 }).toBuffer();
    assert.ok(bytes.length > CHUNK);
    const sha = b => crypto.createHash('sha256').update(b).digest('hex');
    const u = (await req('/v1/uploads', d.token, { client_id: crypto.randomUUID(), filename: 'HOSTINGER_PILOT.JPG', size: bytes.length, sha256: sha(bytes) })).body;
    async function chunk(offset) {
      const b = bytes.subarray(offset, offset + CHUNK);
      const r = await fetch(active.base + '/v1/uploads/' + u.upload_id + '/chunks?offset=' + offset, {
        method: 'POST', headers: { Authorization: 'Bearer ' + d.token, 'Content-Type': 'application/octet-stream', 'X-Chunk-SHA256': sha(b) }, body: b });
      assert.equal(r.status, 200); return r.json();
    }
    assert.equal((await chunk(0)).offset, CHUNK);
    await active.stop(); active = null;
    const replacement = path.join(f.temp, 'new-release'); fs.mkdirSync(replacement);
    for (const name of ['server.js', 'package.json', 'src', 'shared', 'public']) fs.cpSync(path.join(root, name), path.join(replacement, name), { recursive: true });
    active = await start(replacement, { ...f.env, LENSDROP_INITIALIZE_STORAGE: '0' });
    const after = (await req('/v1/admin/deployment', f.env.LENSDROP_ADMIN_KEY)).body;
    assert.equal(after.deployment.storage_id, before.deployment.storage_id);
    assert.notEqual(after.deployment.boot_id, before.deployment.boot_id);
    assert.equal(after.deployment.initialization_enabled, false);
    assert.equal(after.counts.events, 1); assert.equal(after.counts.incomplete_uploads, 1);
    assert.equal((await req('/v1/uploads/' + u.upload_id, d.token)).body.offset, CHUNK);
    for (let offset = CHUNK; offset < bytes.length; offset += CHUNK) await chunk(offset);
    const done = await req('/v1/uploads/' + u.upload_id + '/complete', d.token, {}); assert.equal(done.status, 200);
    const original = await req('/v1/photos/' + done.body.receipt.photo_id + '/original', f.env.LENSDROP_ADMIN_KEY, undefined, true);
    assert.equal(sha(original.body), sha(bytes));
  } finally { if (active) await active.stop(); f.cleanup(); }
});
