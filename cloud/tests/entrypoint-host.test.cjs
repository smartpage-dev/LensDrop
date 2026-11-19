'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');
const vm = require('node:vm');
const { spawn, spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const entry = path.join(root, 'server.js');
function fixture() {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'ld-h31-'));
  const env = Object.fromEntries(Object.entries(process.env).filter(([k]) => !/^(LENSDROP_|LSNODE_|PORT$|HOST$)/.test(k)));
  Object.assign(env, { PORT: '0', HOST: '127.0.0.1',
    LENSDROP_ADMIN_KEY: crypto.randomBytes(32).toString('hex'),
    LENSDROP_STORAGE_ID: crypto.randomBytes(16).toString('hex'),
    LENSDROP_DATA_DIR: path.join(temp, 'private-data'),
    LENSDROP_INITIALIZE_STORAGE: '1', LENSDROP_FREE_RESERVE_BYTES: '1024' });
  return { temp, env, cleanup: () => fs.rmSync(temp, { recursive: true, force: true }) };
}
// Reproduce only the relevant LiteSpeed CommonJS loader contract. This is an
// independently authored test adapter, not the deployed Hostinger loader.
const rejectingLoader = `(function () {
  global.LsNode = { listenDone: false };
  let app;
  try { app = require(process.argv[1]); }
  catch (error) {
    console.error(JSON.stringify({ type: 'loader_rejected', code: error.code }));
    process.exitCode = 1; return;
  }
  if (!global.LsNode.listenDone && typeof app.listen === 'function') app.listen(3000);
})();`;
function rejectCase(change, code, direct = false) {
  const f = fixture();
  try {
    change(f.env);
    const run = spawnSync(process.execPath, direct ? [entry] : ['-e', rejectingLoader, entry],
      { env: f.env, encoding: 'utf8', timeout: 8000 });
    assert.equal(run.error, undefined);
    assert.equal(run.status, 1, run.stderr);
    assert.ok(run.stderr.includes('"code":"' + code + '"'), run.stderr);
    assert.ok(!run.stderr.includes("Cannot read properties of undefined"), run.stderr);
    assert.ok(!run.stderr.includes('"type":"lensdrop_ready"'));
    if (!direct) assert.ok(run.stderr.includes('"type":"loader_rejected"'));
    assert.equal(fs.existsSync(f.env.LENSDROP_DATA_DIR), false);
    if (f.env.LENSDROP_ADMIN_KEY) assert.equal(run.stderr.includes(f.env.LENSDROP_ADMIN_KEY), false);
  } finally { f.cleanup(); }
}
test('entry: missing admin key rejects the host loader without undefined.listen', () => {
  rejectCase(env => { delete env.LENSDROP_ADMIN_KEY; }, 'admin_key_required');
});
test('entry: empty admin key rejects without opening storage', () => {
  rejectCase(env => { env.LENSDROP_ADMIN_KEY = ''; }, 'admin_key_required');
});
test('entry: short admin key rejects without printing the key', () => {
  rejectCase(env => { env.LENSDROP_ADMIN_KEY = 'short-secret-not-for-logs'; }, 'admin_key_required');
});
test('entry: example admin key is not accepted as a real secret', () => {
  rejectCase(env => { env.LENSDROP_ADMIN_KEY = 'REPLACE_WITH_A_PRIVATE_RANDOM_64_HEX_SECRET'; }, 'admin_key_required');
});
test('entry: direct node invocation fails cleanly on missing admin key', () => {
  rejectCase(env => { delete env.LENSDROP_ADMIN_KEY; }, 'admin_key_required', true);
});
test('entry: correct key does not bypass private-storage configuration', () => {
  rejectCase(env => { delete env.LENSDROP_DATA_DIR; env.LENSDROP_DATA_DIR = ''; }, 'absolute_data_dir_required');
});
test('entry: correct key does not bypass saved storage identity', () => {
  rejectCase(env => { delete env.LENSDROP_STORAGE_ID; }, 'storage_id_required');
});
test('entry: restart mode never initializes a missing data directory', () => {
  rejectCase(env => { env.LENSDROP_INITIALIZE_STORAGE = '0'; }, 'storage_not_initialized');
});
test('entry: invalid TCP port fails before event storage opens', () => {
  rejectCase(env => { env.PORT = 'not-a-port'; }, 'invalid_port');
});
const socketLoader = `
  const http = require('node:http');
  const assert = require('node:assert/strict');
  const realListen = http.Server.prototype.listen;
  const socket = process.argv[2], addressMode = process.argv[3];
  global.LsNode = { listenDone: false };
  let calls = 0;
  http.Server.prototype.listen = function(...args) {
    calls += 1;
    assert.equal(calls, 1, 'must bind exactly once');
    global.LsNode.listenDone = true;
    const callback = typeof args[args.length - 1] === 'function' ? args[args.length - 1] : undefined;
    return realListen.call(this, socket, callback);
  };
  http.Server.prototype.address = function() {
    if (addressMode === 'undefined') return undefined;
    if (addressMode === 'null') return null;
    return socket;
  };
  const app = require(process.argv[1]);
  if (!global.LsNode.listenDone && typeof app.listen === 'function') app.listen(3000);
  assert.ok(app instanceof http.Server, 'export must be a Node HTTP server');
  assert.equal(typeof app.listen, 'function');
  assert.equal(calls, 1);
  console.log(JSON.stringify({ type: 'loader_contract', calls, valid_export: true }));
`;
function startChild(args, env) {
  const child = spawn(process.execPath, args, { env, stdio: ['ignore', 'pipe', 'pipe'] });
  let stdout = '', stderr = '';
  child.stderr.on('data', b => { stderr += b.toString(); });
  const ready = new Promise((resolve, reject) => {
    const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('startup timeout: ' + stderr)); }, 10000);
    child.once('exit', code => { clearTimeout(timer); reject(new Error('startup exited ' + code + ': ' + stderr)); });
    child.stdout.on('data', b => {
      stdout += b.toString();
      for (const line of stdout.split('\n')) {
        let data; try { data = JSON.parse(line); } catch { continue; }
        if (data.type === 'lensdrop_ready') { clearTimeout(timer); resolve(data); }
      }
    });
  });
  async function stop() {
    if (child.exitCode !== null) return;
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => { child.kill('SIGKILL'); reject(new Error('shutdown timeout')); }, 10000);
      child.once('exit', code => { clearTimeout(timer); code === 0 ? resolve() : reject(new Error(stderr)); });
      child.kill('SIGTERM');
    });
  }
  return { child, ready, stop, output: () => ({ stdout, stderr }) };
}
async function socketGet(socketPath, route) {
  return new Promise((resolve, reject) => {
    const request = http.get({ socketPath, path: route }, response => {
      const chunks = []; response.on('data', b => chunks.push(b));
      response.on('end', () => resolve({ status: response.statusCode, body: Buffer.concat(chunks).toString() }));
    });
    request.on('error', reject); request.setTimeout(5000, () => request.destroy(new Error('request timeout')));
  });
}
for (const mode of ['string', 'undefined', 'null']) test('entry: managed socket address ' + mode + ' binds once and serves real HTTP', async () => {
  const f = fixture(); const socketPath = path.join(f.temp, 'app.sock'); let run;
  try {
    run = startChild(['-e', socketLoader, entry, socketPath, mode], f.env);
    const ready = await run.ready;
    assert.equal(ready.port, null); assert.equal(ready.transport, 'managed-socket');
    assert.equal((await socketGet(socketPath, '/health')).status, 200);
    assert.equal((await socketGet(socketPath, '/admin')).status, 200);
    assert.equal((await socketGet(socketPath, '/v1/admin/deployment')).status, 401);
    assert.equal(run.output().stdout.includes(socketPath), false);
    assert.ok(run.output().stdout.includes('"valid_export":true'));
    assert.equal(run.output().stdout.includes(f.env.LENSDROP_ADMIN_KEY), false);
    await run.stop(); run = null;
    assert.equal(fs.existsSync(path.join(f.env.LENSDROP_DATA_DIR, 'server.lock')), false);
  } finally { if (run) await run.stop(); f.cleanup(); }
});
test('entry: direct TCP startup retains ready port and clean shutdown', async () => {
  const f = fixture(); let run;
  try {
    run = startChild([entry], f.env); const ready = await run.ready;
    assert.equal(ready.transport, 'tcp'); assert.ok(ready.port > 0);
    const response = await fetch('http://127.0.0.1:' + ready.port + '/health');
    assert.equal(response.status, 200);
    await run.stop(); run = null;
    assert.equal(fs.existsSync(path.join(f.env.LENSDROP_DATA_DIR, 'server.lock')), false);
  } finally { if (run) await run.stop(); f.cleanup(); }
});
test('secrets: admin-only command generates a private 64-hex value without storage identity', () => {
  const script = path.join(root, 'scripts/generate-secrets.cjs');
  const a = spawnSync(process.execPath, [script, '--admin-only'], { encoding: 'utf8' });
  const b = spawnSync(process.execPath, [script, '--admin-only'], { encoding: 'utf8' });
  assert.equal(a.status, 0); assert.equal(b.status, 0);
  assert.match(a.stdout, /^LENSDROP_ADMIN_KEY=[a-f0-9]{64}\n$/);
  assert.notEqual(a.stdout, b.stdout);
  assert.equal(a.stdout.includes('LENSDROP_STORAGE_ID'), false);
});
test('secrets: original new-install command remains available; invalid switches fail closed', () => {
  const script = path.join(root, 'scripts/generate-secrets.cjs');
  const a = spawnSync(process.execPath, [script], { encoding: 'utf8' });
  assert.equal(a.status, 0);
  assert.match(a.stdout, /^LENSDROP_ADMIN_KEY=[a-f0-9]{64}\nLENSDROP_STORAGE_ID=[a-f0-9]{32}\n$/);
  const b = spawnSync(process.execPath, [script, '--bad-option'], { encoding: 'utf8' });
  assert.equal(b.status, 1); assert.equal(b.stdout, '');
});
function helper() {
  const html = fs.readFileSync(path.join(root, 'tools/Hostinger_Setup_Helper.html'), 'utf8');
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const nodes = new Map();
  const get = id => { if (!nodes.has(id)) nodes.set(id, { value: '', textContent: '', className: '', focus() {}, select() {} }); return nodes.get(id); };
  let confirmations = true, copied = '';
  const context = { document: { getElementById: get }, crypto: crypto.webcrypto, Uint8Array,
    confirm: () => confirmations, navigator: { clipboard: { writeText: async text => { copied = text; } } } };
  vm.runInNewContext(script, context, { timeout: 1000 });
  return { get, copied: () => copied, confirm: v => { confirmations = v; } };
}
test('helper: admin-only generation requires no data path and changes no storage configuration', async () => {
  const h = helper(); h.get('values').value = 'KEEP EXISTING STORAGE CONFIGURATION';
  h.get('adminGenerate').onclick();
  assert.match(h.get('adminValue').value, /^[a-f0-9]{64}$/);
  assert.equal(h.get('path').value, '');
  assert.equal(h.get('values').value, 'KEEP EXISTING STORAGE CONFIGURATION');
  await h.get('adminCopy').onclick(); assert.equal(h.copied(), h.get('adminValue').value);
});
test('helper: cancelled key regeneration preserves the selected key', () => {
  const h = helper(); h.get('adminGenerate').onclick(); const saved = h.get('adminValue').value;
  h.confirm(false); h.get('adminGenerate').onclick(); assert.equal(h.get('adminValue').value, saved);
});
