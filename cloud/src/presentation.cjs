'use strict';
// Shared event branding plus a separate, revocable read-only projection capability.
const crypto = require('node:crypto');
const { requireValue } = require('./store.cjs');
const DEFAULTS = Object.freeze({
  style: 'fade', interval_seconds: 8, poll_seconds: 10, fit: 'contain',
  background: 'black', logo_background:'dark', order: 'newest', brand_mode: 'both',
  brand_position: 'bottom-right', show_gallery_qr: false, pool_limit: 200
});
function migrate(s) {
  s.tx(() => {
    s.db.exec(`CREATE TABLE IF NOT EXISTS event_presentation(
      event_id TEXT PRIMARY KEY REFERENCES events(id),
      share_token TEXT UNIQUE NOT NULL, enabled INTEGER NOT NULL DEFAULT 0,
      settings TEXT NOT NULL, logo_key TEXT, revision INTEGER NOT NULL DEFAULT 1,
      updated INTEGER NOT NULL);
      `);
    if (s.one("PRAGMA user_version").user_version < 3) s.db.exec("PRAGMA user_version=3");
    for (const e of s.all('SELECT id FROM events')) ensure(s, e.id);
  });
}
function ensure(s, eventId) {
  const exists = s.one('SELECT * FROM event_presentation WHERE event_id=?', eventId);
  if (exists) return exists;
  requireValue(s.one('SELECT id FROM events WHERE id=?', eventId), 404, 'event_not_found');
  s.run('INSERT INTO event_presentation(event_id,share_token,settings,updated) VALUES(?,?,?,?)',
    eventId, crypto.randomBytes(16).toString('hex'), JSON.stringify(DEFAULTS), Date.now());
  return s.one('SELECT * FROM event_presentation WHERE event_id=?', eventId);
}
function settings(p) { return { ...DEFAULTS, ...JSON.parse(p.settings) }; }
function validate(input) {
  requireValue(input && typeof input === 'object' && !Array.isArray(input), 400, 'invalid_presentation_settings');
  const allowed = Object.keys(DEFAULTS);
  requireValue(Object.keys(input).every(k => allowed.includes(k)), 400, 'unknown_presentation_setting');
  const oneOf = {
    logo_background:['transparent','light','dark'], style: ['fade','zoom','slide'], fit: ['contain','cover'], background: ['black','charcoal','white'],
    order: ['newest','oldest','shuffle'], brand_mode: ['both','logo','name','none'],
    brand_position: ['top-left','top-right','bottom-left','bottom-right']
  };
  for (const [k,v] of Object.entries(input)) {
    if (oneOf[k]) requireValue(oneOf[k].includes(v), 400, 'invalid_' + k);
    else if (k === 'interval_seconds') requireValue(Number.isInteger(v) && v >= 3 && v <= 30, 400, 'invalid_interval_seconds');
    else if (k === 'poll_seconds') requireValue([5,10,15].includes(v), 400, 'invalid_poll_seconds');
    else if (k === 'pool_limit') requireValue([50,100,200,500].includes(v), 400, 'invalid_pool_limit');
    else if (k === 'show_gallery_qr') requireValue(typeof v === 'boolean', 400, 'invalid_show_gallery_qr');
  }
  return input;
}
function describe(s, eventId) {
  const p = ensure(s, eventId), e = s.one('SELECT name,slug,policy FROM events WHERE id=?', eventId);
  return { event_id:eventId, name:e.name, policy:e.policy, enabled:!!p.enabled, settings:settings(p),
    revision:p.revision, updated:p.updated, has_logo:!!p.logo_key,
    logo_path:p.logo_key ? `/v1/events/${eventId}/logo?v=${p.revision}` : null,
    slideshow_path:`/s/${p.share_token}`, gallery_path:`/g/${e.slug}` };
}
function publicBrand(p, base) {
  return { logo_background:settings(p).logo_background, mode:settings(p).brand_mode, position:settings(p).brand_position,
    logo_path:p.logo_key ? `${base}/logo?v=${p.revision}` : null, revision:p.revision };
}
function byToken(s, token) {
  const p = s.one('SELECT ep.*,e.name,e.slug FROM event_presentation ep JOIN events e ON e.id=ep.event_id WHERE share_token=?',token);
  requireValue(p && p.enabled,404,'slideshow_unavailable'); return p;
}
function feed(s, token) {
  const p = byToken(s,token), config = settings(p);
  const photos = s.all("SELECT seq,id,width,height,created FROM photos WHERE event_id=? AND status='live' AND is_test=0 ORDER BY seq DESC LIMIT ?",p.event_id,config.pool_limit);
  // Never expose the guest capability unless an administrator opts into an audience QR.
  return { name:p.name, settings:config, revision:p.revision, branding:publicBrand(p,`/v1/s/${token}`),
    gallery_path:config.show_gallery_qr ? `/g/${p.slug}` : null,
    total:s.one("SELECT count(*) n FROM photos WHERE event_id=? AND status='live' AND is_test=0",p.event_id).n,
    window_count:photos.length, window_scope:'latest-uploaded-live-photos',
    lease_seconds:30, generated_at:Date.now(), photos };
}
module.exports = { DEFAULTS, migrate, ensure, settings, validate, describe, publicBrand, byToken, feed };
