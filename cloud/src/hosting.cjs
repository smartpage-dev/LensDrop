'use strict';
// These checks reject obvious unsafe deployments. They do not certify a host's
// filesystem durability, backups, SQLite locking, or process isolation.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const MARKER = '.lensdrop-storage.json';
function check(value, code, detail) {
  if (!value) { const e = new Error(detail); e.code = code; throw e; }
}
function syncDir(dir) {
  if (process.platform === 'win32') return;
  const fd = fs.openSync(dir, 'r');
  try { fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
}
function canonical(p) {
  let current = path.resolve(p); const suffix = [];
  while (!fs.existsSync(current)) {
    suffix.unshift(path.basename(current));
    const parent = path.dirname(current);
    check(parent !== current, 'storage_parent_missing', 'No existing parent for the storage path.');
    current = parent;
  }
  return path.join(fs.realpathSync(current), ...suffix);
}
function inside(child, parent) {
  const rel = path.relative(parent, child);
  return rel === '' || (!rel.startsWith('..' + path.sep) && rel !== '..' && !path.isAbsolute(rel));
}
function validatePath(value, appRoot) {
  check(typeof value === 'string' && path.isAbsolute(value), 'absolute_data_dir_required',
    'Set LENSDROP_DATA_DIR to an absolute private, persistent path confirmed for your hosting account.');
  check(!/[<>]|REPLACE|CONFIRMED_PATH|your[_-](?:user|domain)/i.test(value), 'data_dir_placeholder', 'Replace the example data path with a confirmed real path.');
  const root = canonical(value), app = canonical(appRoot);
  const unsafe = p => p.split(/[\\/]+/).some(x => ['hbuilds', 'public_html', 'nodejs'].includes(x.toLowerCase()));
  check(!unsafe(value) && !unsafe(root), 'deployment_tree_forbidden',
    'Data cannot be kept under hbuilds, public_html, or nodejs. Ask Hostinger for private persistent storage.');
  check(!inside(root, app) && !inside(app, root), 'data_dir_overlaps_app', 'The data directory must be separate from the application tree.');
  return root;
}
function positiveInteger(value, fallback, name) {
  const n = value === undefined || value === '' ? fallback : Number(value);
  check(Number.isSafeInteger(n) && n > 0, 'invalid_numeric_setting', name + ' must be a positive integer.');
  return n;
}
function prepareHosting(env = process.env, appRoot = path.join(__dirname, '..')) {
  const adminKey = env.LENSDROP_ADMIN_KEY;
  check(typeof adminKey === 'string' && adminKey.length >= 32 && !/REPLACE|YOUR[_ -]|CHANGEME|example|test-admin/i.test(adminKey),
    'admin_key_required', 'Set LENSDROP_ADMIN_KEY in Hostinger Environment Variables to your saved private random key (at least 32 characters; no placeholder). Apply changes and restart. Never publish the key.');
  const dataDir = validatePath(env.LENSDROP_DATA_DIR, appRoot);
  const storageId = env.LENSDROP_STORAGE_ID;
  check(typeof storageId === 'string' && /^[a-f0-9]{32}$/.test(storageId), 'storage_id_required',
    'LENSDROP_STORAGE_ID must be a saved, randomly generated 32-character lowercase hex identifier.');
  check(env.LENSDROP_INITIALIZE_STORAGE === undefined || ['0', '1'].includes(env.LENSDROP_INITIALIZE_STORAGE),
    'invalid_initialize_setting', 'LENSDROP_INITIALIZE_STORAGE must be 0 or 1.');
  const initialize = env.LENSDROP_INITIALIZE_STORAGE === '1';
  const maxEventBytes = positiveInteger(env.LENSDROP_EVENT_QUOTA_BYTES, 10 * 1024 ** 3, 'LENSDROP_EVENT_QUOTA_BYTES');
  const freeReserve = positiveInteger(env.LENSDROP_FREE_RESERVE_BYTES, 512 * 1024 ** 2, 'LENSDROP_FREE_RESERVE_BYTES');
  const markerPath = path.join(dataDir, MARKER), dbPath = path.join(dataDir, 'lensdrop.sqlite');
  if (!fs.existsSync(markerPath)) {
    check(initialize, 'storage_not_initialized', 'Storage marker missing. Do not reinitialize existing events; restore the correct path. For a NEW EMPTY installation only, set LENSDROP_INITIALIZE_STORAGE=1 once.');
    fs.mkdirSync(dataDir, { recursive: true, mode: 0o700 });
    check(fs.readdirSync(dataDir).length === 0, 'unmarked_nonempty_storage',
      'This unmarked directory is not empty. Stop and back it up; automatic adoption is refused.');
    const fd = fs.openSync(markerPath, 'wx', 0o600);
    try { fs.writeFileSync(fd, JSON.stringify({ schema: 1, storage_id: storageId, created_at: new Date().toISOString() }) + '\n'); fs.fsyncSync(fd); }
    finally { fs.closeSync(fd); }
    syncDir(dataDir);
  }
  let marker;
  try { marker = JSON.parse(fs.readFileSync(markerPath, 'utf8')); }
  catch { check(false, 'invalid_storage_marker', 'Storage marker is unreadable. Preserve the directory and investigate.'); }
  check(marker.schema === 1 && marker.storage_id === storageId, 'storage_identity_mismatch',
    'Storage identity differs from the saved LENSDROP_STORAGE_ID. Do not overwrite the marker.');
  if (!fs.existsSync(dbPath)) {
    check(initialize && fs.readdirSync(dataDir).every(x => x === MARKER), 'database_missing',
      'The initialized directory has no database. Restore the complete data directory; do not create a replacement empty gallery.');
  } else {
    check(fs.statSync(dbPath).isFile() && fs.statSync(dbPath).size > 0, 'database_invalid', 'Existing database is empty or not a regular file. Restore or investigate it.');
  }
  // Probe the exact runtime path, not a build-time folder. Never touch event files.
  const name = '.lensdrop-probe-' + crypto.randomUUID();
  const first = path.join(dataDir, name), second = first + '.renamed';
  try {
    const fd = fs.openSync(first, 'wx', 0o600);
    try { fs.writeFileSync(fd, 'write-fsync-rename-probe\n'); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
    fs.renameSync(first, second); syncDir(dataDir);
    check(fs.readFileSync(second, 'utf8') === 'write-fsync-rename-probe\n', 'storage_probe_failed', 'Storage probe readback failed.');
  } finally { fs.rmSync(first, { force: true }); fs.rmSync(second, { force: true }); }
  const stat = fs.statfsSync(dataDir);
  check(stat.bavail * stat.bsize >= freeReserve, 'storage_low', 'Not enough free disk space for the configured safety reserve.');
  return { adminKey, dataDir, maxEventBytes, freeReserve,
    deployment: { profile: 'hostinger-candidate', storage_id: storageId, initialized_at: marker.created_at,
      initialization_enabled: initialize, runtime_node: process.version, boot_id: crypto.randomUUID(),
      persistence_verified_by_host: false, backup_verified: false } };
}
module.exports = { prepareHosting, validatePath, MARKER };
