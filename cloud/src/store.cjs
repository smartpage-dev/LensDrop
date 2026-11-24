'use strict';
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const digest = x => crypto.createHash('sha256').update(x).digest('hex');
const id = () => crypto.randomUUID();
class HttpError extends Error {
  constructor(status, code, detail = code) { super(detail); this.status = status; this.code = code; }
}
function requireValue(value, status, code, detail) {
  if (!value) throw new HttpError(status, code, detail);
}
function syncDir(dir) {
  // Windows does not expose directory fsync through Node in the same way as POSIX.
  if (process.platform === 'win32') return;
  const fd = fs.openSync(dir, 'r');
  try { fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
}
class Store {
  constructor(root) {
    this.root = path.resolve(root);
    fs.mkdirSync(this.root, { recursive: true, mode: 0o700 });
    this.lockPath = path.join(this.root, 'server.lock');
    this.lockValue = JSON.stringify({ pid: process.pid, nonce: id() });
    for (let attempt = 0; attempt < 2; attempt++) {
      try { fs.writeFileSync(this.lockPath, this.lockValue, { flag: 'wx', mode: 0o600 }); break; }
      catch (e) {
        if (e.code !== 'EEXIST' || attempt) throw new Error('Data directory is locked by another server. Use one process only.');
        let previous;
        try { previous = JSON.parse(fs.readFileSync(this.lockPath, 'utf8')); }
        catch { throw new Error('Unreadable server.lock; investigate before removing it.'); }
        let alive = true;
        try { process.kill(previous.pid, 0); } catch (err) { if (err.code === 'ESRCH') alive = false; }
        if (alive) throw new Error('A LensDrop server is already using this data directory.');
        fs.unlinkSync(this.lockPath);
      }
    }
    for (const dir of ['chunks', 'originals', 'delivery', 'thumbs', 'temp', 'branding']) {
      fs.mkdirSync(path.join(this.root, dir), { recursive: true, mode: 0o700 });
    }
    this.db = new DatabaseSync(path.join(this.root, 'lensdrop.sqlite'));
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
      PRAGMA foreign_keys=ON; PRAGMA busy_timeout=5000;
      CREATE TABLE IF NOT EXISTS events(
        id TEXT PRIMARY KEY, name TEXT NOT NULL, slug TEXT UNIQUE NOT NULL,
        policy TEXT NOT NULL CHECK(policy IN ('review','live')), created INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS devices(
        id TEXT PRIMARY KEY, event_id TEXT NOT NULL REFERENCES events(id),
        label TEXT NOT NULL, token_hash TEXT UNIQUE NOT NULL, revoked INTEGER NOT NULL DEFAULT 0,
        last_seen INTEGER, heartbeat TEXT);
      CREATE TABLE IF NOT EXISTS uploads(
        id TEXT PRIMARY KEY, event_id TEXT NOT NULL REFERENCES events(id),
        device_id TEXT NOT NULL REFERENCES devices(id), client_id TEXT NOT NULL,
        filename TEXT NOT NULL, size INTEGER NOT NULL, sha256 TEXT NOT NULL,
        offset INTEGER NOT NULL DEFAULT 0,
        state TEXT NOT NULL DEFAULT 'receiving' CHECK(state IN ('receiving','complete')),
        photo_id TEXT, created INTEGER NOT NULL, updated INTEGER NOT NULL,
        UNIQUE(event_id,device_id,client_id));
      CREATE TABLE IF NOT EXISTS chunks(
        upload_id TEXT NOT NULL REFERENCES uploads(id), offset INTEGER NOT NULL,
        size INTEGER NOT NULL, sha256 TEXT NOT NULL, PRIMARY KEY(upload_id,offset));
      CREATE TABLE IF NOT EXISTS photos(
        seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT UNIQUE NOT NULL,
        event_id TEXT NOT NULL REFERENCES events(id), device_id TEXT NOT NULL REFERENCES devices(id),
        filename TEXT NOT NULL, size INTEGER NOT NULL, sha256 TEXT NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('review','live','hidden')),
        width INTEGER NOT NULL, height INTEGER NOT NULL, created INTEGER NOT NULL,
        UNIQUE(event_id,device_id,sha256));
      CREATE INDEX IF NOT EXISTS upload_owner ON uploads(device_id,state);
      CREATE INDEX IF NOT EXISTS gallery ON photos(event_id,status,seq);
      `);
    require('./operations.cjs').migrate(this);
    require('./presentation.cjs').migrate(this);
    require('./deliveries.cjs').migrate(this);
    require('./onboarding-print.cjs').migrate(this);
    require('./event-workflow.cjs').migrate(this);
    require('./event-management.cjs').migrate(this);
  }
  one(sql, ...args) { return this.db.prepare(sql).get(...args); }
  all(sql, ...args) { return this.db.prepare(sql).all(...args); }
  run(sql, ...args) { return this.db.prepare(sql).run(...args); }
  tx(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try { const value = fn(); this.db.exec('COMMIT'); return value; }
    catch (err) { this.db.exec('ROLLBACK'); throw err; }
  }
  file(dir, key) {
    requireValue(/^[a-zA-Z0-9_.-]+$/.test(key), 400, 'invalid_file_key');
    return path.join(this.root, dir, key);
  }
  close() {
    this.db.close();
    try { if (fs.readFileSync(this.lockPath, 'utf8') === this.lockValue) fs.unlinkSync(this.lockPath); } catch {}
  }
}
module.exports = { Store, HttpError, requireValue, digest, id, syncDir };
