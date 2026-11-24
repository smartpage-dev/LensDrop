'use strict';
// Engineering alpha: one Node process and a persistent local filesystem only.
// No cloud durability/replication claim is made by an upload receipt.
const http = require('node:http');
const fs = require('node:fs');
const fsp = require('node:fs/promises');
const path = require('node:path');
const { pipeline } = require('node:stream/promises');
const { Transform } = require('node:stream');
const crypto = require('node:crypto');
const sharp = require('sharp');
const { Store, HttpError, requireValue, digest, id, syncDir } = require('./store.cjs');
const operations = require('./operations.cjs');
const presentation = require('./presentation.cjs');
const deliveries = require('./deliveries.cjs');
const onboarding = require('./onboarding-print.cjs');
const workflow = require('./event-workflow.cjs');
sharp.cache({ memory: 16, files: 0, items: 16 });
sharp.concurrency(1);
const CHUNK = 1024 * 1024;
const MAX_FILE = 64 * 1024 * 1024;
const MAX_JSON = 64 * 1024;
const VALID_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const shaOK = x => typeof x === 'string' && /^[0-9a-f]{64}$/.test(x);
const now = () => Date.now();
function safeEqual(a, b) {
  return crypto.timingSafeEqual(Buffer.from(digest(a)), Buffer.from(digest(b)));
}
function fail(status, code, detail) { throw new HttpError(status, code, detail); }
function text(v, max = 160) {
  requireValue(typeof v === 'string' && v.trim().length > 0 && v.length <= max && !/[\u0000-\u001f]/.test(v), 400, 'invalid_text');
  return v.trim();
}
function headers(res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Content-Security-Policy', "default-src 'self'; img-src 'self' blob:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
}
function json(res, status, data) {
  if (res.destroyed) return;
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(data));
}
async function readJSON(req) {
  requireValue((req.headers['content-type'] || '').split(';')[0] === 'application/json', 415, 'json_required');
  let size = 0; const parts = [];
  for await (const part of req) {
    size += part.length;
    requireValue(size <= MAX_JSON, 413, 'request_too_large');
    parts.push(part);
  }
  try { const data=JSON.parse(Buffer.concat(parts).toString('utf8')); requireValue(data && typeof data==='object' && !Array.isArray(data),400,'json_object_required'); return data; }
  catch (e) { if(e instanceof HttpError) throw e; fail(400, 'invalid_json'); }
}
async function fsyncFile(filename) {
  const fh = await fsp.open(filename, 'r');
  try { await fh.sync(); } finally { await fh.close(); }
}
async function atomicRename(from, to) {
  await fsyncFile(from);
  await fsp.rename(from, to);
  syncDir(path.dirname(to));
}
async function serveFile(res, filename, type, attachment) {
  const stat = await fsp.stat(filename).catch(() => fail(503, 'storage_unavailable'));
  if (attachment) res.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(attachment)}`);
  res.writeHead(200, { 'Content-Type': type, 'Content-Length': stat.size });
  await pipeline(fs.createReadStream(filename), res);
}
function createApp(options = {}) {
  const adminKey = options.adminKey || process.env.LENSDROP_ADMIN_KEY;
  requireValue(typeof adminKey === 'string' && adminKey.length >= 32, 500, 'admin_key_minimum_32_characters');
  const store = new Store(options.dataDir || process.env.LENSDROP_DATA_DIR || path.join(process.cwd(), 'var'));
  // Single-instance lock is already held. These are uncommitted scratch files only.
  for (const entry of fs.readdirSync(path.join(store.root, 'temp'))) fs.rmSync(path.join(store.root,'temp',entry),{force:true});
  const locks = new Set();
  let activeTransfers = 0, rendering = false;
  const maxEventBytes = options.maxEventBytes ?? Number(process.env.LENSDROP_EVENT_QUOTA_BYTES || 10 * 1024 ** 3);
  const freeReserve = options.freeReserve ?? Number(process.env.LENSDROP_FREE_RESERVE_BYTES || 512 * 1024 ** 2);
  const publicDir = path.join(__dirname, '..', 'public');
  function token(req) { return /^Bearer (.+)$/.exec(req.headers.authorization || '')?.[1] || ''; }
  function admin(req) { requireValue(safeEqual(token(req), adminKey), 401, 'admin_auth_required'); }
  function device(req) {
    const dev = store.one('SELECT * FROM devices WHERE token_hash=? AND revoked=0 AND is_service=0', digest(token(req)));
    requireValue(dev, 401, 'device_token_invalid_or_revoked');
    store.run('UPDATE devices SET last_seen=? WHERE id=?', now(), dev.id);
    return dev;
  }
  function ownedUpload(dev, uploadId) {
    const upload = store.one('SELECT * FROM uploads WHERE id=? AND device_id=?', uploadId, dev.id);
    requireValue(upload, 404, 'upload_not_found'); return upload;
  }
  function response(upload) {
    const receipt = upload.photo_id ? store.one('SELECT id,sha256,size,status,created,is_test FROM photos WHERE id=?', upload.photo_id) : null;
    return { upload_id: upload.id, offset: upload.offset, chunk_size: CHUNK, size: upload.size, sha256: upload.sha256,
      state: upload.state, archive_private: !!upload.archive_private, delivery_id: upload.delivery_id || null, is_test: !!upload.is_test, receipt: receipt ? { photo_id: receipt.id, sha256: receipt.sha256, bytes: receipt.size,
        status: receipt.status, is_test: !!receipt.is_test, stored_at: receipt.created, storage: 'single-instance-local-disk' } : null };
  }
  function checkSpace(bytes = 0) {
    const stat = fs.statfsSync(store.root);
    requireValue(stat.bavail * stat.bsize >= freeReserve + bytes, 507, 'cloud_storage_low');
  }
  async function locked(key, fn) {
    requireValue(!locks.has(key), 409, 'upload_busy');
    requireValue(activeTransfers < 4, 429, 'server_busy');
    locks.add(key); activeTransfers++;
    try { return await fn(); } finally { locks.delete(key); activeTransfers--; }
  }
  async function cleanupChunks(uploadId) {
    for (const part of store.all('SELECT offset FROM chunks WHERE upload_id=?', uploadId)) {
      await fsp.rm(store.file('chunks', `${uploadId}.${part.offset}`), { force: true });
    }
    store.run('DELETE FROM chunks WHERE upload_id=?', uploadId);
  }
  async function route(req, res) {
    headers(res);
    const url = new URL(req.url, 'http://localhost');
    const p = url.pathname;
    const method = req.method;
    if (method === 'GET' && p === '/health') return json(res, 200, { ok: true, version: require('../package.json').version, storage: 'single-instance', chunk_size: CHUNK });
    if (method === 'GET' && p === '/v1/admin/deployment') {
      admin(req);
      return json(res, 200, { version: require('../package.json').version,
        deployment: options.deployment || { profile: 'local-evaluation', persistence_verified_by_host: false },
        storage: 'single-instance-local-disk', data_dir: store.root,
        counts: { events: store.one('SELECT COUNT(*) AS n FROM events').n,
          photos: store.one('SELECT COUNT(*) AS n FROM photos').n,
          incomplete_uploads: store.one("SELECT COUNT(*) AS n FROM uploads WHERE state='receiving'").n },
        note: 'A successful health check or write probe does not establish persistence across host redeployments or backup durability.' });
    }
    if (method === 'GET' && ['/', '/admin', '/app.js', '/style.css', '/presentation-admin.js', '/qr.js', '/slideshow.js', '/slideshow.css', '/slideshow-model.js', '/personal.js', '/personal-admin.js', '/onboarding-admin.js', '/pair.js', '/staff-desk.js', '/staff-desk.css', '/operations-shell.js'].includes(p)) {
      const name = p === '/' || p === '/admin' ? 'index.html' : p.slice(1);
      return serveFile(res, path.join(publicDir, name), name.endsWith('.js') ? 'text/javascript' : name.endsWith('.css') ? 'text/css' : 'text/html');
    }
    if (method === 'GET' && p === '/pair') return serveFile(res,path.join(publicDir,'pair.html'),'text/html');
    if (await workflow.route({method,p,url,req,res,store,device,admin,readJSON,json,serveFile,locked})) return;
    if (await onboarding.route({method,p,url,req,res,store,device,admin,readJSON,json,serveFile,locked})) return;
    if (method === 'GET' && /^\/p\/[a-f0-9]{32}$/.test(p)) return serveFile(res, path.join(publicDir, 'personal.html'), 'text/html');
    if (method === 'GET' && /^\/s\/[a-f0-9]{32}$/.test(p)) return serveFile(res, path.join(publicDir, 'slideshow.html'), 'text/html');
    if (method === 'GET' && /^\/g\/[a-f0-9]{32}$/.test(p)) return serveFile(res, path.join(publicDir, 'gallery.html'), 'text/html');
    if (method === 'GET' && p === '/gallery.js') return serveFile(res, path.join(publicDir, 'gallery.js'), 'text/javascript');
    if (method === 'GET' && p === '/v1/device') {
      const dev = device(req), event = store.one('SELECT id,name,policy FROM events WHERE id=?', dev.event_id);
      return json(res, 200, { device_id: dev.id, label: dev.label, event, capabilities: {isolated_preflight:true,operations_summary:true,personal_delivery_v1:true,qr_pairing_v1:true,dual_print_v1:true,archive_delivery_v1:true,staff_print_desk_v1:true}, chunk_size: CHUNK, max_file_size: MAX_FILE });
    }
    if (await deliveries.route({method,p,url,req,res,store,device,admin,readJSON,json,serveFile,presentation})) return;
    if (method === 'POST' && p === '/v1/device/heartbeat') {
      const dev = device(req), body = await readJSON(req), stats = {};
      Object.assign(stats, operations.sanitizeHeartbeat(body));
      store.run('UPDATE devices SET heartbeat=?,heartbeat_at=? WHERE id=?', JSON.stringify(stats), now(), dev.id);
      return json(res, 200, { ok: true });
    }
    if (method === 'GET' && p === '/v1/device/summary') {
      const dev = device(req);
      return json(res, 200, operations.deviceSummary(store, dev.id));
    }
    if (method === 'GET' && p === '/v1/device/preflight') {
      const dev = device(req), clientId = url.searchParams.get('client_id');
      requireValue(VALID_ID.test(clientId || ''), 400, 'client_id_must_be_uuid');
      const u = store.one('SELECT * FROM uploads WHERE device_id=? AND client_id=? AND is_test=1', dev.id, clientId);
      requireValue(u, 404, 'preflight_not_found');
      return json(res, 200, { client_id: clientId, cloud_verified: u.state === 'complete',
        publication: 'isolated-test-never-public', receipt: response(u).receipt,
        scope: 'Accessible source file to verified cloud storage. Camera-card completeness and guest-browser operation are not tested.' });
    }
    const operationRoute = /^\/v1\/events\/([0-9a-f-]+)\/(reconciliation|diagnostics)$/.exec(p);
    if (method === 'GET' && operationRoute) {
      admin(req);
      requireValue(store.one('SELECT id FROM events WHERE id=?', operationRoute[1]), 404, 'event_not_found');
      return json(res, 200, operationRoute[2] === 'diagnostics'
        ? operations.diagnostics(store, operationRoute[1]) : operations.reconcile(store, operationRoute[1]));
    }
    if (p === '/v1/events' && method === 'POST') {
      admin(req); const body = await readJSON(req);
      const eventId = id(), slug = crypto.randomBytes(16).toString('hex'), policy = body.policy || 'review';
      requireValue(['review','live'].includes(policy), 400, 'invalid_publish_policy');
      store.run('INSERT INTO events(id,name,slug,policy,created) VALUES(?,?,?,?,?)', eventId, text(body.name), slug, policy, now());
      presentation.ensure(store, eventId);
      return json(res, 201, { id: eventId, slug, gallery_path: `/g/${slug}`, policy });
    }
    if (p === '/v1/events' && method === 'GET') { admin(req); return json(res, 200, store.all('SELECT * FROM events ORDER BY created DESC')); }
    // H3 projection and shared branding. Admin credentials never appear in share URLs.
    let pm = /^\/v1\/events\/([0-9a-f-]+)\/presentation$/.exec(p);
    if (pm && method === 'GET') { admin(req); return json(res,200,presentation.describe(store,pm[1])); }
    if (pm && method === 'POST') {
      admin(req); const eventId=pm[1], b=await readJSON(req), before=presentation.ensure(store,eventId);
      requireValue(Object.keys(b).every(k=>['enabled','settings','name','policy','revision'].includes(k)),400,'unknown_presentation_field');
      requireValue(b.revision === undefined || b.revision === before.revision,409,'presentation_changed_reload');
      requireValue(b.enabled === undefined || typeof b.enabled === 'boolean',400,'invalid_enabled');
      requireValue(b.policy === undefined || ['live','review'].includes(b.policy),400,'invalid_publish_policy');
      const config={...presentation.settings(before),...presentation.validate(b.settings || {})};
      const name=b.name === undefined ? null : text(b.name);
      store.tx(()=>{
        if(name!==null)store.run('UPDATE events SET name=? WHERE id=?',name,eventId);
        if(b.policy!==undefined)store.run('UPDATE events SET policy=? WHERE id=?',b.policy,eventId);
        store.run('UPDATE event_presentation SET enabled=?,settings=?,revision=revision+1,updated=? WHERE event_id=?',
          b.enabled===undefined?before.enabled:Number(b.enabled),JSON.stringify(config),now(),eventId);
      });
      return json(res,200,presentation.describe(store,eventId));
    }
    pm = /^\/v1\/events\/([0-9a-f-]+)\/presentation\/rotate$/.exec(p);
    if (pm && method === 'POST') {
      admin(req); presentation.ensure(store,pm[1]);
      store.run('UPDATE event_presentation SET share_token=?,revision=revision+1,updated=? WHERE event_id=?',crypto.randomBytes(16).toString('hex'),now(),pm[1]);
      return json(res,200,presentation.describe(store,pm[1]));
    }
    pm = /^\/v1\/events\/([0-9a-f-]+)\/logo$/.exec(p);
    if (pm && method === 'GET') {
      admin(req); const config=presentation.ensure(store,pm[1]);requireValue(config.logo_key,404,'logo_not_found');
      return serveFile(res,store.file('branding',config.logo_key),'image/png');
    }
    if (pm && method === 'POST') {
      admin(req); const eventId=pm[1]; presentation.ensure(store,eventId);
      requireValue(['image/png','image/jpeg','image/webp'].includes((req.headers['content-type']||'').split(';')[0]),415,'logo_png_jpeg_webp_only');
      requireValue(Number(req.headers['content-length'])>0&&Number(req.headers['content-length'])<=2*1024*1024,413,'logo_maximum_2_mib');
      const savedLogo = await locked('logo:'+eventId,async()=>{
        requireValue(!rendering,429,'image_processor_busy');rendering=true;
        let temp;
        try{
          let length=0;const parts=[];
          for await(const b of req){length+=b.length;requireValue(length<=2*1024*1024,413,'logo_maximum_2_mib');parts.push(b);}
          const bytes=Buffer.concat(parts);let output;
          try{
            const image=sharp(bytes,{limitInputPixels:16*1024*1024}),meta=await image.metadata();
            requireValue(['png','jpeg','webp'].includes(meta.format)&&(!meta.pages||meta.pages===1),415,'logo_png_jpeg_webp_only');
            output=await image.rotate().resize({width:1200,height:600,fit:'inside',withoutEnlargement:true}).png().toBuffer();
          }catch(e){if(e instanceof HttpError)throw e;fail(422,'invalid_logo_image');}
          checkSpace(output.length);const key=id()+'.png';temp=store.file('temp',key);
          await fsp.writeFile(temp,output,{flag:'wx',mode:0o600});await atomicRename(temp,store.file('branding',key));
          const old=presentation.ensure(store,eventId).logo_key;
          store.run('UPDATE event_presentation SET logo_key=?,revision=revision+1,updated=? WHERE event_id=?',key,now(),eventId);
          if(old)await fsp.rm(store.file('branding',old),{force:true}).catch(()=>{});
          return presentation.describe(store,eventId);
        }finally{rendering=false;if(temp)await fsp.rm(temp,{force:true}).catch(()=>{});}
      });
      return json(res,200,savedLogo);
    }
    pm = /^\/v1\/events\/([0-9a-f-]+)\/logo\/remove$/.exec(p);
    if(pm&&method==='POST'){
      admin(req);const before=presentation.ensure(store,pm[1]);
      store.run('UPDATE event_presentation SET logo_key=NULL,revision=revision+1,updated=? WHERE event_id=?',now(),pm[1]);
      if(before.logo_key)await fsp.rm(store.file('branding',before.logo_key),{force:true}).catch(()=>{});
      return json(res,200,presentation.describe(store,pm[1]));
    }
    pm = /^\/v1\/s\/([a-f0-9]{32})$/.exec(p);
    if(pm&&method==='GET')return json(res,200,presentation.feed(store,pm[1]));
    pm = /^\/v1\/(s|g)\/([a-f0-9]{32})\/logo$/.exec(p);
    if(pm&&method==='GET'){
      let config;
      if(pm[1]==='s')config=presentation.byToken(store,pm[2]);
      else{const event=store.one('SELECT id FROM events WHERE slug=?',pm[2]);requireValue(event,404,'gallery_not_found');config=presentation.ensure(store,event.id);}
      requireValue(config.logo_key,404,'logo_not_found');return serveFile(res,store.file('branding',config.logo_key),'image/png');
    }
    pm = /^\/v1\/s\/([a-f0-9]{32})\/photos\/([0-9a-f-]+)\/delivery$/.exec(p);
    if(pm&&method==='GET'){
      const config=presentation.byToken(store,pm[1]);
      const photo=store.one("SELECT id FROM photos WHERE event_id=? AND id=? AND status='live' AND is_test=0",config.event_id,pm[2]);
      requireValue(photo,404,'photo_not_found');return serveFile(res,store.file('delivery',photo.id+'.jpg'),'image/jpeg');
    }
    let m = /^\/v1\/events\/([0-9a-f-]+)\/devices$/.exec(p);
    if (m && method === 'POST') {
      admin(req); requireValue(store.one('SELECT id FROM events WHERE id=?', m[1]), 404, 'event_not_found');
      const body = await readJSON(req), deviceId = id(), secret = crypto.randomBytes(32).toString('base64url');
      store.run('INSERT INTO devices(id,event_id,label,token_hash) VALUES(?,?,?,?)', deviceId, m[1], text(body.label), digest(secret));
      return json(res, 201, { device_id: deviceId, event_id: m[1], token: secret, note: 'Save this device token now; it is not stored in plaintext.' });
    }
    m = /^\/v1\/devices\/([0-9a-f-]+)\/revoke$/.exec(p);
    if (m && method === 'POST') { admin(req); store.run('UPDATE devices SET revoked=1 WHERE id=? AND is_service=0', m[1]); return json(res, 200, { ok: true }); }
    m = /^\/v1\/devices\/([0-9a-f-]+)\/rotate$/.exec(p);
    if (m && method === 'POST') {
      admin(req); const dev=store.one('SELECT * FROM devices WHERE id=?',m[1]);
      requireValue(dev&&!dev.is_service,404,'device_not_found');
      const secret=crypto.randomBytes(32).toString('base64url');
      store.run('UPDATE devices SET token_hash=?,revoked=0 WHERE id=?',digest(secret),dev.id);
      return json(res,200,{device_id:dev.id,event_id:dev.event_id,token:secret});
    }
    m = /^\/v1\/events\/([0-9a-f-]+)\/status$/.exec(p);
    if (m && method === 'GET') {
      admin(req);
      requireValue(store.one('SELECT id FROM events WHERE id=?', m[1]), 404, 'event_not_found');
      const before = Number(url.searchParams.get('before') || Number.MAX_SAFE_INTEGER);
      requireValue(Number.isSafeInteger(before) && before > 0, 400, 'invalid_cursor');
      const photos = store.all('SELECT seq,id,filename,device_id,status,size,created,is_test FROM photos WHERE event_id=? AND seq<? ORDER BY seq DESC LIMIT 200', m[1], before);
      return json(res, 200, {
        devices: store.all('SELECT id FROM devices WHERE event_id=? AND is_service=0', m[1]).map(d => operations.deviceSummary(store,d.id)),
        photos, next_before: photos.length === 200 ? photos.at(-1).seq : null,
        counts: store.all('SELECT status,count(*) AS count FROM photos WHERE event_id=? AND is_test=0 GROUP BY status', m[1]),
        test_count: store.one('SELECT count(*) n FROM photos WHERE event_id=? AND is_test=1', m[1]).n });
    }
    if (p === '/v1/uploads' && method === 'POST') {
      const dev = device(req), b = await readJSON(req);
      requireValue(VALID_ID.test(b.client_id || ''), 400, 'client_id_must_be_uuid');
      requireValue(shaOK(b.sha256), 400, 'sha256_required');
      requireValue(Number.isSafeInteger(b.size) && b.size > 0 && b.size <= MAX_FILE, 413, 'file_size_out_of_range');
      requireValue(b.is_test === undefined || typeof b.is_test === 'boolean', 400, 'invalid_test_flag');
      const isTest = b.is_test === true ? 1 : 0;
      requireValue(b.archive_private===undefined||typeof b.archive_private==='boolean',400,'invalid_archive_flag');
      const archivePrivate=b.archive_private===true?1:0;
      requireValue(!archivePrivate||!isTest&&!b.delivery_id,400,'private_archive_must_bind_after_upload');
      const filename = text(b.filename, 240);
      requireValue(/\.jpe?g$/i.test(filename) && !/[\\/]/.test(filename), 415, 'jpeg_only_in_alpha');
      const old = store.one('SELECT * FROM uploads WHERE event_id=? AND device_id=? AND client_id=?', dev.event_id, dev.id, b.client_id);
      if (old) {
        requireValue(old.size === b.size && old.sha256 === b.sha256 && old.filename === filename && old.is_test === isTest && old.archive_private === archivePrivate && (old.delivery_id || null) === (b.delivery_id || null), 409, 'client_id_reused_for_different_content');
        return json(res, 200, response(old));
      }
      deliveries.validateUpload(store,dev,b,isTest);
      const conflict = store.one('SELECT id FROM uploads WHERE event_id=? AND device_id=? AND sha256=? AND is_test<>? LIMIT 1',dev.event_id,dev.id,b.sha256,isTest);
      requireValue(!conflict,409,'test_production_content_conflict','Use a different photograph for the preflight test; test content cannot be promoted.');
      const used = store.one("SELECT coalesce(sum(size),0) AS n FROM uploads WHERE event_id=?", dev.event_id).n;
      requireValue(used + b.size <= maxEventBytes, 507, 'event_quota_reached');
      checkSpace(b.size * 2);
      const uploadId = id(), t = now();
      store.run('INSERT INTO uploads(id,event_id,device_id,client_id,filename,size,sha256,created,updated,is_test,delivery_id,archive_private) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',
        uploadId, dev.event_id, dev.id, b.client_id, filename, b.size, b.sha256, t, t, isTest, b.delivery_id || null,archivePrivate);
      return json(res, 201, response(ownedUpload(dev, uploadId)));
    }
    m = /^\/v1\/uploads\/([0-9a-f-]+)$/.exec(p);
    if (m && method === 'GET') return json(res, 200, response(ownedUpload(device(req), m[1])));
    m = /^\/v1\/uploads\/([0-9a-f-]+)\/chunks$/.exec(p);
    if (m && method === 'POST') {
      const dev = device(req), uploadId = m[1];
      return locked(uploadId, async () => {
        const u = ownedUpload(dev, uploadId);
        requireValue(u.state === 'receiving', 409, 'upload_already_complete');
        const offset = Number(url.searchParams.get('offset'));
        requireValue(url.searchParams.has('offset') && Number.isSafeInteger(offset) && offset >= 0 && offset % CHUNK === 0 && offset < u.size, 400, 'invalid_offset');
        const chunkHash = req.headers['x-chunk-sha256'];
        requireValue(shaOK(chunkHash), 400, 'chunk_sha256_required');
        const old = store.one('SELECT * FROM chunks WHERE upload_id=? AND offset=?', uploadId, offset);
        const length = Number(req.headers['content-length']);
        const expected = Math.min(CHUNK, u.size - offset);
        requireValue(length === expected, 400, 'invalid_chunk_length');
        // A retried committed chunk may be acknowledged without re-reading the body.
        if (old) {
          requireValue(old.sha256 === chunkHash && old.size === length, 409, 'chunk_conflict');
          req.resume(); return response(u);
        }
        requireValue(offset === u.offset, 409, 'offset_mismatch');
        checkSpace(length);
        const temp = store.file('temp', `${uploadId}.${id()}.part`);
        let received = 0; const hasher = crypto.createHash('sha256');
        try {
          const meter = new Transform({ transform(chunk, enc, cb) {
            received += chunk.length;
            if (received > expected) return cb(new HttpError(413, 'chunk_too_large'));
            hasher.update(chunk); cb(null, chunk);
          }});
          await pipeline(req, meter, fs.createWriteStream(temp, { flags: 'wx', mode: 0o600 }));
          requireValue(received === expected, 400, 'truncated_chunk');
          requireValue(hasher.digest('hex') === chunkHash, 422, 'chunk_checksum_mismatch');
          // Disk data is durable before its offset is committed. An uncommitted orphan
          // chunk is overwritten by the next retry at the same offset.
          await atomicRename(temp, store.file('chunks', `${uploadId}.${offset}`));
          options.faultHook?.('chunk_renamed');
          store.tx(() => {
            requireValue(store.one('SELECT revoked FROM devices WHERE id=?', dev.id).revoked === 0, 401, 'device_revoked');
            store.run('INSERT INTO chunks VALUES(?,?,?,?)', uploadId, offset, received, chunkHash);
            store.run('UPDATE uploads SET offset=?,updated=? WHERE id=?', offset + received, now(), uploadId);
          });
          return response(ownedUpload(dev, uploadId));
        } finally { await fsp.rm(temp, { force: true }); }
      }).then(data => json(res, 200, data));
    }
    m = /^\/v1\/uploads\/([0-9a-f-]+)\/complete$/.exec(p);
    if (m && method === 'POST') {
      const dev = device(req), uploadId = m[1]; req.resume();
      return locked(uploadId, async () => {
        const u = ownedUpload(dev, uploadId);
        if (u.state === 'complete') { await cleanupChunks(uploadId).catch(() => {}); return response(u); }
        requireValue(u.offset === u.size, 409, 'upload_incomplete');
        requireValue(!rendering, 429, 'renderer_busy'); rendering = true;
        const photoId = uploadId; // stable across a crash during finalization
        const temp = store.file('temp', `${uploadId}.assembled`);
        try {
          checkSpace(u.size * 2);
          const handle = await fsp.open(temp, 'w', 0o600);
          const hasher = crypto.createHash('sha256'); let pos = 0;
          try {
            for (const c of store.all('SELECT * FROM chunks WHERE upload_id=? ORDER BY offset', uploadId)) {
              requireValue(c.offset === pos, 500, 'stored_chunk_gap');
              let actual = 0;
              for await (const part of fs.createReadStream(store.file('chunks', `${uploadId}.${c.offset}`), { highWaterMark: 64 * 1024 })) {
                hasher.update(part); let written = 0;
                while (written < part.length) {
                  const result = await handle.write(part, written, part.length - written, pos + written);
                  requireValue(result.bytesWritten > 0, 503, 'storage_write_stalled'); written += result.bytesWritten;
                }
                pos += part.length; actual += part.length;
              }
              requireValue(actual === c.size, 500, 'stored_chunk_size_changed');
            }
            requireValue(pos === u.size && hasher.digest('hex') === u.sha256, 422, 'file_checksum_mismatch');
            await handle.sync();
          } finally { await handle.close(); }
          const duplicate = store.one('SELECT * FROM photos WHERE event_id=? AND device_id=? AND sha256=?', dev.event_id, dev.id, u.sha256);
          if (duplicate) {
            deliveries.bindPhoto(store,u,duplicate.id);
            workflow.linkPrintsForPhoto(store,duplicate.id);
            store.run("UPDATE uploads SET state='complete',photo_id=?,updated=? WHERE id=?", duplicate.id, now(), uploadId);
            await cleanupChunks(uploadId);
            return response(ownedUpload(dev, uploadId));
          }
          let meta;
          try {
            meta = await sharp(temp, { limitInputPixels: 80_000_000, failOn: 'warning' }).metadata();
            requireValue(meta.format === 'jpeg', 415, 'jpeg_required');
            await sharp(temp, { limitInputPixels: 80_000_000, failOn: 'warning' }).rotate().resize({ width: 2560, height: 2560, fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 88 }).toFile(store.file('temp', `${photoId}.delivery`));
            await sharp(temp, { limitInputPixels: 80_000_000, failOn: 'warning' }).rotate().resize({ width: 480, height: 480, fit: 'inside', withoutEnlargement: true }).jpeg({ quality: 78 }).toFile(store.file('temp', `${photoId}.thumb`));
          } catch (e) { if (e instanceof HttpError) throw e; fail(422, 'invalid_or_unsupported_jpeg'); }
          await atomicRename(temp, store.file('originals', `${photoId}.jpg`));
          await atomicRename(store.file('temp', `${photoId}.delivery`), store.file('delivery', `${photoId}.jpg`));
          await atomicRename(store.file('temp', `${photoId}.thumb`), store.file('thumbs', `${photoId}.jpg`));
          options.faultHook?.('originals_renamed');
          const policy = u.is_test ? 'hidden' : u.delivery_id || u.archive_private ? 'review' : store.one('SELECT policy FROM events WHERE id=?', dev.event_id).policy;
          store.tx(() => {
            requireValue(store.one('SELECT revoked FROM devices WHERE id=?', dev.id).revoked === 0, 401, 'device_revoked');
            store.run('INSERT INTO photos(id,event_id,device_id,filename,size,sha256,status,width,height,created,is_test) VALUES(?,?,?,?,?,?,?,?,?,?,?)',
              photoId, dev.event_id, dev.id, u.filename, u.size, u.sha256, policy, meta.width, meta.height, now(), u.is_test);
            deliveries.bindPhoto(store,u,photoId);
            workflow.linkPrintsForPhoto(store,photoId);
            store.run("UPDATE uploads SET state='complete',photo_id=?,updated=? WHERE id=?", photoId, now(), uploadId);
          });
          options.faultHook?.('photo_committed');
          // Housekeeping failure must not invalidate the durable receipt.
          await cleanupChunks(uploadId).catch(() => {});
          return response(ownedUpload(dev, uploadId));
        } finally {
          rendering = false;
          for (const name of [`${uploadId}.assembled`, `${photoId}.delivery`, `${photoId}.thumb`]) await fsp.rm(store.file('temp', name), { force: true }).catch(() => {});
        }
      }).then(data => json(res, 200, data));
    }
    m = /^\/v1\/photos\/([0-9a-f-]+)\/status$/.exec(p);
    if (m && method === 'POST') {
      admin(req); const b = await readJSON(req);
      requireValue(['review','live','hidden'].includes(b.status), 400, 'invalid_status');
      const target = store.one('SELECT is_test FROM photos WHERE id=?', m[1]);
      requireValue(target,404,'photo_not_found');
      requireValue(!target.is_test || b.status === 'hidden',409,'test_photo_cannot_publish');
      store.tx(()=>{
        requireValue(store.run('UPDATE photos SET status=? WHERE id=?', b.status, m[1]).changes > 0, 404, 'photo_not_found');
        if(b.status==='hidden')store.run('UPDATE delivery_items SET released=0 WHERE photo_id=?',m[1]);
      });
      return json(res, 200, { ok: true });
    }
    m = /^\/v1\/photos\/([0-9a-f-]+)\/(original|delivery|thumb)$/.exec(p);
    if (m && method === 'GET') {
      admin(req); const photo = store.one('SELECT * FROM photos WHERE id=?', m[1]);
      requireValue(photo, 404, 'photo_not_found');
      return serveFile(res, store.file(m[2] === 'original' ? 'originals' : m[2] === 'thumb' ? 'thumbs' : 'delivery', `${photo.id}.jpg`), 'image/jpeg', m[2] === 'original' ? photo.filename : undefined);
    }
    m = /^\/v1\/g\/([a-f0-9]{32})$/.exec(p);
    if (m && method === 'GET') {
      const event = store.one('SELECT id,name FROM events WHERE slug=?', m[1]);
      requireValue(event, 404, 'gallery_not_found');
      const before = Number(url.searchParams.get('before') || Number.MAX_SAFE_INTEGER);
      requireValue(Number.isSafeInteger(before) && before > 0, 400, 'invalid_cursor');
      const photos = store.all("SELECT seq,id,filename,width,height,created FROM photos WHERE event_id=? AND status='live' AND is_test=0 AND seq<? ORDER BY seq DESC LIMIT 100", event.id, before);
      return json(res, 200, { name: event.name, branding:presentation.publicBrand(presentation.ensure(store,event.id),`/v1/g/${m[1]}`), refresh_seconds: 10, total: store.one("SELECT count(*) n FROM photos WHERE event_id=? AND status='live' AND is_test=0", event.id).n, photos, next_before: photos.length === 100 ? photos.at(-1).seq : null });
    }
    m = /^\/v1\/g\/([a-f0-9]{32})\/visible$/.exec(p);
    if (m && method === 'POST') {
      const e = store.one('SELECT id FROM events WHERE slug=?',m[1]);
      requireValue(e,404,'gallery_not_found');
      const b = await readJSON(req);
      requireValue(Array.isArray(b.ids) && b.ids.length <= 500 && b.ids.every(x => typeof x === 'string' && VALID_ID.test(x)),400,'invalid_photo_ids');
      const visible = b.ids.length ? store.all(`SELECT id FROM photos WHERE event_id=? AND status='live' AND is_test=0 AND id IN (${b.ids.map(() => '?').join(',')})`,e.id,...b.ids).map(x => x.id) : [];
      return json(res,200,{visible});
    }
    m = /^\/v1\/g\/([a-f0-9]{32})\/photos\/([0-9a-f-]+)\/(thumb|delivery)$/.exec(p);
    if (m && method === 'GET') {
      const photo = store.one("SELECT p.* FROM photos p JOIN events e ON e.id=p.event_id WHERE e.slug=? AND p.id=? AND p.status='live' AND p.is_test=0", m[1], m[2]);
      requireValue(photo, 404, 'photo_not_found');
      return serveFile(res, store.file(m[3] === 'thumb' ? 'thumbs' : 'delivery', `${photo.id}.jpg`), 'image/jpeg', url.searchParams.has('download') ? photo.filename : undefined);
    }
    fail(404, 'not_found');
  }
  const server = http.createServer((req, res) => {
    route(req, res).catch(err => {
      if (res.headersSent || res.destroyed) { if (!res.destroyed) res.destroy(); return; }
      const status = err.status || (['ENOSPC','EDQUOT'].includes(err.code) ? 507 : 500);
      if ([429,503,507].includes(status)) res.setHeader('Retry-After', status === 507 ? '60' : '5');
      if (status >= 500) console.error(JSON.stringify({ type: 'request_error', code: err.code || 'internal_error' }));
      json(res, status, { error: err instanceof HttpError ? err.code : status === 507 ? 'cloud_storage_low' : 'internal_error' });
    });
  });
  server.requestTimeout = 90_000; server.headersTimeout = 15_000; server.keepAliveTimeout = 5_000;
  return { server, store, close: async () => { await new Promise(r => server.close(r)); store.close(); } };
}
if (require.main === module) {
  const app = createApp();
  const host = process.env.HOST || '127.0.0.1', port = Number(process.env.PORT || 3000);
  app.server.listen(port, host, () => console.log(`LensDrop ${require('../package.json').version} listening on ${host}:${port}`));
  for (const signal of ['SIGINT','SIGTERM']) process.on(signal, () => app.close().then(() => process.exit(0)));
}
module.exports = { createApp, CHUNK, MAX_FILE };
