'use strict';
const crypto = require('node:crypto');
const version = require('../package.json').version;
const HEARTBEAT_TTL = 90_000;
function migrate(s) {
  const v = s.one('PRAGMA user_version').user_version;
  if (v > 9) throw new Error('Database is newer than this application. Restore the matching application; do not downgrade.');
  s.tx(() => {
    for (const [table, name, definition] of [
      ['uploads','is_test','INTEGER NOT NULL DEFAULT 0'],
      ['photos','is_test','INTEGER NOT NULL DEFAULT 0'],
      ['devices','heartbeat_at','INTEGER']]) {
      if (!s.all(`PRAGMA table_info(${table})`).some(x => x.name === name)) s.db.exec(`ALTER TABLE ${table} ADD COLUMN ${name} ${definition}`);
    }
    if (v < 2) s.db.exec('PRAGMA user_version=2');
  });
}
function sanitizeHeartbeat(b) {
  const out = {};
  for (const key of ['received','queued','uploaded','blocked','queue_bytes','battery_percent','thermal_status','last_scan','last_received','last_progress','scan_paused','test_received','test_uploaded','test_pending','candidates','skipped','retired','unassigned_archives','pending_assignments']) {
    if (Number.isSafeInteger(b[key]) && b[key] >= 0) out[key] = b[key];
  }
  return out;
}
function deviceSummary(s, deviceId, at = Date.now()) {
  const d = s.one('SELECT id,event_id,label,revoked,admin_hidden,admin_archived,last_seen,heartbeat,heartbeat_at FROM devices WHERE id=?',deviceId);
  let h = {};
  try { h = sanitizeHeartbeat(JSON.parse(d.heartbeat || '{}')); } catch {}
  const fresh = Number.isFinite(d.heartbeat_at) && at-d.heartbeat_at <= HEARTBEAT_TTL;
  const c = s.one(`SELECT count(*) total, coalesce(sum(status='live'),0) live, coalesce(sum(status='review'),0) review,
    coalesce(sum(status='hidden'),0) hidden FROM photos WHERE device_id=? AND is_test=0`,deviceId);
  const uploads = s.one(`SELECT coalesce(sum(state='complete'),0) complete, coalesce(sum(state='receiving'),0) receiving
    FROM uploads WHERE device_id=? AND is_test=0`,deviceId);
  const tests = s.one(`SELECT count(*) received,coalesce(sum(state='complete'),0) verified FROM uploads WHERE device_id=? AND is_test=1`,deviceId);
  const known = Number.isSafeInteger(h.received) && Number.isSafeInteger(h.queued) && Number.isSafeInteger(h.blocked) && Number.isSafeInteger(h.uploaded);
  return {id:d.id,event_id:d.event_id,label:d.label,revoked:!!d.revoked,admin_hidden:!!d.admin_hidden,admin_archived:!!d.admin_archived,last_seen:d.last_seen,
    heartbeat_at:d.heartbeat_at, heartbeat:JSON.stringify(h), heartbeat_fresh:fresh,
    source_received:known?h.received:null, source_counts_known:known,
    unassigned_archives:known?(h.unassigned_archives||0):null,pending_assignments:known?(h.pending_assignments||0):null,
    candidates:known?(h.candidates||0):null, skipped:known?(h.skipped||0):null,
    personal_available:s.one("SELECT count(*) n FROM delivery_items i JOIN photos p ON p.id=i.photo_id JOIN deliveries dl ON dl.id=i.delivery_id WHERE p.device_id=? AND i.released=1 AND p.is_test=0 AND p.status<>'hidden' AND dl.state='open'",deviceId).n,
    queued:known?h.queued:null, blocked:known?h.blocked:null, source_uploaded:known?h.uploaded:null,
    uploaded:c.total, verified_photos:c.total, verified_uploads:uploads.complete,
    incomplete:uploads.receiving, live:c.live, review:c.review, hidden:c.hidden, tests,
    source_claim:'Client-reported accessible files only; not camera-card inventory.',
    selection_accounted_for:(h.unassigned_archives||0)===0 && (h.pending_assignments||0)===0 && known && fresh && !d.revoked && h.received===h.uploaded+(h.skipped||0) && h.queued===0 && h.blocked===0 && (h.candidates||0)===0 && uploads.receiving===0 && h.uploaded===uploads.complete,
    reconciled:known && fresh && !d.revoked && (h.candidates||0)===0 && (h.skipped||0)===0 && h.received===h.uploaded && h.queued===0 && h.blocked===0 && uploads.receiving===0 && h.uploaded===uploads.complete};
}
function reconcile(s,eventId) {
  const at = Date.now();
  const devices = s.all('SELECT id FROM devices WHERE event_id=? AND is_service=0',eventId).map(x=>deviceSummary(s,x.id,at));
  return {version,generated_at:at,event_id:eventId,scope:'files-observed-by-paired-clients-only',
    camera_card_complete:false, source_originals_may_be_deleted:false,
    all_known_files_reconciled:devices.length>0 && devices.every(d=>d.reconciled),
    counts:{verified_photos:devices.reduce((n,d)=>n+d.verified_photos,0),verified_uploads:devices.reduce((n,d)=>n+d.verified_uploads,0),
      published:devices.reduce((n,d)=>n+d.live,0),review:devices.reduce((n,d)=>n+d.review,0),hidden:devices.reduce((n,d)=>n+d.hidden,0)},
    devices, note:'Do not delete camera or phone originals on the basis of this single-server snapshot. Stale or absent client counts prevent a complete result.'};
}
function diagnostics(s,eventId) {
  const r = reconcile(s,eventId);
  const ref = x=>crypto.createHash('sha256').update(x).digest('hex').slice(0,12);
  return {schema:'lensdrop-redacted-diagnostics-v1',version,generated_at:r.generated_at,
    scope:r.scope,event_ref:ref(eventId),counts:r.counts,all_known_files_reconciled:r.all_known_files_reconciled,
    devices:r.devices.map(d=>({device_ref:ref(d.id),revoked:d.revoked,heartbeat_at:d.heartbeat_at,heartbeat_fresh:d.heartbeat_fresh,
      source_received:d.source_received,queued:d.queued,blocked:d.blocked,verified_uploads:d.verified_uploads,verified_photos:d.verified_photos,
      live:d.live,review:d.review,hidden:d.hidden,incomplete:d.incomplete,tests:d.tests,telemetry:JSON.parse(d.heartbeat)})),
    omitted:['credentials','token hashes','event names','device labels','filenames','source paths','photos','public gallery URLs'],
    limits:'Snapshot only, not a historical crash log or proof of replicated backup.'};
}
module.exports={migrate,sanitizeHeartbeat,deviceSummary,reconcile,diagnostics,HEARTBEAT_TTL};
