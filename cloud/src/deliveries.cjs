'use strict';
// Personal delivery is separate from event-gallery publication. URLs are bearer
// capabilities: never put them in diagnostics, analytics, or public event lists.
const crypto = require('node:crypto');
const {requireValue} = require('./store.cjs');
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function migrate(s){
  s.tx(()=>{
    s.db.exec(`CREATE TABLE IF NOT EXISTS personal_settings(
      event_id TEXT PRIMARY KEY REFERENCES events(id),auto_release INTEGER NOT NULL DEFAULT 0 CHECK(auto_release IN (0,1)));
      CREATE TABLE IF NOT EXISTS deliveries(
      id TEXT PRIMARY KEY,event_id TEXT NOT NULL REFERENCES events(id),device_id TEXT NOT NULL REFERENCES devices(id),
      kind TEXT NOT NULL CHECK(kind IN ('group','single')),label TEXT NOT NULL,token TEXT UNIQUE NOT NULL,
      state TEXT NOT NULL DEFAULT 'open' CHECK(state IN ('open','cancelled')),created INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS delivery_items(
      photo_id TEXT PRIMARY KEY REFERENCES photos(id),delivery_id TEXT NOT NULL REFERENCES deliveries(id),
      released INTEGER NOT NULL DEFAULT 0 CHECK(released IN (0,1)));
      CREATE INDEX IF NOT EXISTS delivery_owner ON deliveries(event_id,device_id,created);
      CREATE INDEX IF NOT EXISTS delivery_feed ON delivery_items(delivery_id,released);`);
    if(!s.all('PRAGMA table_info(uploads)').some(x=>x.name==='delivery_id'))s.db.exec('ALTER TABLE uploads ADD COLUMN delivery_id TEXT REFERENCES deliveries(id)');
    s.db.exec('CREATE INDEX IF NOT EXISTS upload_delivery ON uploads(delivery_id);');
    if(s.one('PRAGMA user_version').user_version<4)s.db.exec('PRAGMA user_version=4');
  });
}
function settings(s,event){return {auto_release:!!s.one('SELECT auto_release FROM personal_settings WHERE event_id=?',event)?.auto_release};}
function owned(s,dev,id){const d=s.one('SELECT * FROM deliveries WHERE id=? AND device_id=? AND event_id=?',id,dev.id,dev.event_id);requireValue(d,404,'delivery_not_found');return d;}
function describe(s,d){return {device_label:s.one('SELECT label FROM devices WHERE id=?',d.device_id)?.label||'',id:d.id,kind:d.kind,label:d.label,state:d.state,created:d.created,guest_path:'/p/'+d.token,
  received:s.one('SELECT count(*) n FROM uploads WHERE delivery_id=? OR photo_id IN (SELECT photo_id FROM delivery_items WHERE delivery_id=?)',d.id,d.id).n,
  verified:s.one('SELECT count(*) n FROM delivery_items WHERE delivery_id=?',d.id).n,
  available:d.state==='open'?s.one("SELECT count(*) n FROM delivery_items i JOIN photos p ON p.id=i.photo_id WHERE i.delivery_id=? AND i.released=1 AND p.is_test=0 AND p.status<>'hidden'",d.id).n:0};}
function validateUpload(s,dev,b,isTest){
  requireValue(b.delivery_id===undefined||b.delivery_id===null||typeof b.delivery_id==='string'&&UUID.test(b.delivery_id),400,'invalid_delivery_id');
  if(!b.delivery_id)return;
  requireValue(!isTest,409,'test_cannot_have_delivery');
  const d=owned(s,dev,b.delivery_id);requireValue(d.state==='open',409,'delivery_cancelled');
  if(d.kind==='single')requireValue(!s.one('SELECT id FROM uploads WHERE delivery_id=? LIMIT 1',d.id),409,'single_delivery_already_bound');
  // A duplicate source in another delivery is never silently repurposed.
  const prior=s.all('SELECT delivery_id FROM uploads WHERE event_id=? AND device_id=? AND sha256=?',dev.event_id,dev.id,b.sha256);
  requireValue(prior.every(p=>p.delivery_id===d.id),409,'photo_already_bound_elsewhere');
}
function bindPhoto(s,u,photo){
  if(!u.delivery_id)return;
  const old=s.one('SELECT delivery_id FROM delivery_items WHERE photo_id=?',photo);
  requireValue(!old||old.delivery_id===u.delivery_id,409,'photo_already_bound_elsewhere');
  s.run('INSERT OR IGNORE INTO delivery_items(photo_id,delivery_id,released) VALUES(?,?,?)',photo,u.delivery_id,Number(settings(s,u.event_id).auto_release));
}
function publicDelivery(s,token){const d=s.one("SELECT * FROM deliveries WHERE token=? AND state='open'",token);requireValue(d,404,'delivery_unavailable');return d;}
async function route(c){
  const {method,p,url,req,res,store:s,device,admin,readJSON,json,serveFile,presentation}=c;
  let m;
  if(p==='/v1/device/deliveries'&&method==='POST'){
    const dev=device(req),b=await readJSON(req);
    require('./event-management.cjs').active(s,dev.event_id);
    requireValue(Object.keys(b).every(k=>['id','kind','label'].includes(k)),400,'unknown_delivery_field');
    requireValue(typeof b.id==='string'&&UUID.test(b.id),400,'delivery_id_must_be_uuid');
    requireValue(['group','single'].includes(b.kind),400,'invalid_delivery_kind');
    requireValue(typeof b.label==='string'&&b.label.trim().length>0&&b.label.length<=60&&!/[\x00-\x1f]/.test(b.label),400,'invalid_delivery_label');
    const existing=s.one('SELECT * FROM deliveries WHERE id=?',b.id);
    if(existing){
      const d=owned(s,dev,b.id);requireValue(d.kind===b.kind&&d.label===b.label,409,'delivery_identity_conflict');json(res,200,describe(s,d));return true;
    }
    requireValue(s.one('SELECT count(*) n FROM deliveries WHERE event_id=?',dev.event_id).n<100000,429,'delivery_limit_reached');
    s.run('INSERT INTO deliveries(id,event_id,device_id,kind,label,token,created) VALUES(?,?,?,?,?,?,?)',b.id,dev.event_id,dev.id,b.kind,b.label,crypto.randomBytes(16).toString('hex'),Date.now());
    json(res,201,describe(s,owned(s,dev,b.id)));return true;
  }
  m=/^\/v1\/device\/deliveries\/([0-9a-f-]+)(\/cancel)?$/.exec(p);
  if(m&&((method==='GET'&&!m[2])||(method==='POST'&&m[2]))){
    const d=owned(s,device(req),m[1]);
    if(m[2]){await readJSON(req);requireValue(!s.one('SELECT id FROM uploads WHERE delivery_id=? LIMIT 1',d.id)&&!s.one('SELECT photo_id FROM delivery_items WHERE delivery_id=? LIMIT 1',d.id),409,'delivery_has_assigned_photos');s.run("UPDATE deliveries SET state='cancelled' WHERE id=?",d.id);d.state='cancelled';}
    json(res,200,describe(s,d));return true;
  }
  m=/^\/v1\/events\/([0-9a-f-]+)\/personal-settings$/.exec(p);
  if(m&&['GET','POST'].includes(method)){
    admin(req);requireValue(s.one('SELECT id FROM events WHERE id=?',m[1]),404,'event_not_found');
    if(method==='POST'){const b=await readJSON(req);requireValue(Object.keys(b).length===1&&typeof b.auto_release==='boolean',400,'invalid_personal_settings');s.run('INSERT INTO personal_settings VALUES(?,?) ON CONFLICT(event_id) DO UPDATE SET auto_release=excluded.auto_release',m[1],Number(b.auto_release));}
    json(res,200,{...settings(s,m[1]),note:'Applies to future personal-photo completion only. Does not publish to gallery/slideshow or release older photos.'});return true;
  }
  m=/^\/v1\/events\/([0-9a-f-]+)\/deliveries$/.exec(p);
  if(m&&method==='GET'){
    admin(req);requireValue(s.one('SELECT id FROM events WHERE id=?',m[1]),404,'event_not_found');
    const offset=Number(url.searchParams.get('offset')||0);requireValue(Number.isSafeInteger(offset)&&offset>=0,400,'invalid_cursor');
    const rows=s.all('SELECT * FROM deliveries WHERE event_id=? ORDER BY created DESC,id DESC LIMIT 50 OFFSET ?',m[1],offset);
    json(res,200,{deliveries:rows.map(d=>describe(s,d)),next_offset:rows.length===50?offset+50:null});return true;
  }
  m=/^\/v1\/deliveries\/([0-9a-f-]+)(\/revoke)?$/.exec(p);
  if(m&&((method==='GET'&&!m[2])||(method==='POST'&&m[2]))){
    admin(req);const d=s.one('SELECT * FROM deliveries WHERE id=?',m[1]);requireValue(d,404,'delivery_not_found');
    if(m[2]){await readJSON(req);s.run("UPDATE deliveries SET state='cancelled' WHERE id=?",d.id);d.state='cancelled';}
    const photos=s.all('SELECT p.id,p.filename,p.status,p.is_test,i.released FROM delivery_items i JOIN photos p ON p.id=i.photo_id WHERE i.delivery_id=? ORDER BY p.seq DESC LIMIT 200',d.id);
    json(res,200,{...describe(s,d),photos,photo_limit:200});return true;
  }
  m=/^\/v1\/photos\/([0-9a-f-]+)\/personal-release$/.exec(p);
  if(m&&method==='POST'){
    admin(req);const b=await readJSON(req);requireValue(typeof b.released==='boolean'&&Object.keys(b).length===1,400,'invalid_release');
    const photo=s.one('SELECT * FROM photos WHERE id=?',m[1]);requireValue(photo&&!photo.is_test,404,'photo_not_found');
    requireValue(!b.released||photo.status!=='hidden',409,'unhide_before_personal_release');
    requireValue(s.run('UPDATE delivery_items SET released=? WHERE photo_id=?',Number(b.released),m[1]).changes>0,404,'personal_photo_not_found');
    json(res,200,{ok:true,public_status:photo.status});return true;
  }
  m=/^\/v1\/p\/([a-f0-9]{32})\/photos\/([0-9a-f-]+)\/visibility$/.exec(p);
  if(m&&method==='POST'){
    const d=publicDelivery(s,m[1]),b=await readJSON(req);
    requireValue(require('./photo-controls.cjs').policy(s,d.event_id).guest_hide_enabled,403,'guest_public_hide_not_enabled');
    requireValue(Object.keys(b).length===2&&['gallery_visible','projector_visible'].includes(b.channel)&&b.visible===false,400,'guest_can_only_hide_public_channels');
    requireValue(s.one('SELECT 1 FROM delivery_items WHERE delivery_id=? AND photo_id=? AND released=1',d.id,m[2]),404,'photo_not_in_delivery');
    json(res,200,require('./photo-controls.cjs').setPhoto(s,m[2],{[b.channel]:false},'guest',d.event_id));return true;
  }
  m=/^\/v1\/p\/([a-f0-9]{32})(?:\/(logo|photos\/([0-9a-f-]+)\/(thumb|delivery)))?$/.exec(p);
  if(m&&method==='GET'){
    const d=publicDelivery(s,m[1]);
    if(m[2]==='logo'){
      const brand=presentation.ensure(s,d.event_id);requireValue(brand.logo_key,404,'logo_not_found');await serveFile(res,s.file('branding',brand.logo_key),'image/png');return true;
    }
    if(m[3]){
      const photo=s.one("SELECT p.* FROM delivery_items i JOIN photos p ON p.id=i.photo_id WHERE i.delivery_id=? AND p.id=? AND i.released=1 AND p.is_test=0 AND p.status<>'hidden'",d.id,m[3]);requireValue(photo,404,'photo_not_found');
      await serveFile(res,s.file(m[4]==='thumb'?'thumbs':'delivery',photo.id+'.jpg'),'image/jpeg',url.searchParams.has('download')?'LensDrop-'+photo.id+'.jpg':undefined);return true;
    }
    const before=Number(url.searchParams.get('before')||Number.MAX_SAFE_INTEGER);requireValue(Number.isSafeInteger(before)&&before>0,400,'invalid_cursor');
    const photos=s.all("SELECT p.seq,p.id,p.width,p.height,p.created FROM delivery_items i JOIN photos p ON p.id=i.photo_id WHERE i.delivery_id=? AND i.released=1 AND p.is_test=0 AND p.status<>'hidden' AND p.seq<? ORDER BY p.seq DESC LIMIT 100",d.id,before);
    const event=s.one('SELECT name FROM events WHERE id=?',d.event_id);
    json(res,200,{name:event.name,label:d.label,kind:d.kind,branding:presentation.publicBrand(presentation.ensure(s,d.event_id),'/v1/p/'+m[1]),photos,next_before:photos.length===100?photos.at(-1).seq:null,refresh_seconds:5});return true;
  }
  return false;
}
module.exports={migrate,route,validateUpload,bindPhoto};
