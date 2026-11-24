'use strict';
// H5.11: additive, independently addressable single and group collections.
// All mutations are server-authorized, event-scoped, idempotent and auditable.
const crypto=require('node:crypto');
const {requireValue:need}=require('./store.cjs');
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
function migrate(s){s.tx(()=>{
 s.db.exec(`CREATE TABLE IF NOT EXISTS collection_memberships(
  photo_id TEXT NOT NULL REFERENCES photos(id),delivery_id TEXT NOT NULL REFERENCES deliveries(id),
  kind TEXT NOT NULL CHECK(kind IN ('single','group')),active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
  released INTEGER NOT NULL DEFAULT 0 CHECK(released IN (0,1)),revision INTEGER NOT NULL DEFAULT 1,
  created INTEGER NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(photo_id,delivery_id));
 CREATE UNIQUE INDEX IF NOT EXISTS one_single_per_photo ON collection_memberships(photo_id) WHERE kind='single' AND active=1;
 CREATE UNIQUE INDEX IF NOT EXISTS one_group_per_photo ON collection_memberships(photo_id) WHERE kind='group' AND active=1;
 CREATE INDEX IF NOT EXISTS membership_delivery ON collection_memberships(delivery_id,active,released);
 CREATE TABLE IF NOT EXISTS event_collection_sequence(event_id TEXT NOT NULL REFERENCES events(id),kind TEXT NOT NULL,seq INTEGER NOT NULL,PRIMARY KEY(event_id,kind));
 CREATE TABLE IF NOT EXISTS collection_membership_audit(id TEXT PRIMARY KEY,event_id TEXT NOT NULL,photo_id TEXT NOT NULL,delivery_id TEXT NOT NULL,action TEXT NOT NULL,actor TEXT NOT NULL,created INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS collection_mutation_requests(event_id TEXT NOT NULL,request_id TEXT NOT NULL,request_hash TEXT NOT NULL,response TEXT NOT NULL,PRIMARY KEY(event_id,request_id));`);
 // Existing personal URLs and release state remain unchanged; backfill is idempotent.
 s.run(`INSERT OR IGNORE INTO collection_memberships(photo_id,delivery_id,kind,active,released,revision,created,updated)
 SELECT i.photo_id,i.delivery_id,d.kind,1,i.released,1,d.created,d.created FROM delivery_items i JOIN deliveries d ON d.id=i.delivery_id`);
 // Existing labels are not rewritten. Sequence starts beyond legacy count, avoiding clashes.
 for(const e of s.all('SELECT id FROM events'))for(const kind of ['single','group']){
  const count=s.one('SELECT count(*) n FROM deliveries WHERE event_id=? AND kind=?',e.id,kind).n;
  s.run('INSERT OR IGNORE INTO event_collection_sequence(event_id,kind,seq) VALUES(?,?,?)',e.id,kind,count);
 }
 if(s.one('PRAGMA user_version').user_version<10)s.db.exec('PRAGMA user_version=10');
 });}
function list(s,photo){return s.all(`SELECT d.id,d.kind,d.label,d.state,m.released,m.revision FROM collection_memberships m JOIN deliveries d ON d.id=m.delivery_id WHERE m.photo_id=? AND m.active=1 ORDER BY d.kind DESC`,photo).map(r=>({...r,released:!!r.released}));}
function members(s,delivery){return s.all('SELECT m.photo_id,m.released,m.revision,p.filename,p.seq FROM collection_memberships m JOIN photos p ON p.id=m.photo_id WHERE m.delivery_id=? AND m.active=1 ORDER BY p.seq DESC LIMIT 200',delivery);}
function allocate(s,event,kind){
 s.run('INSERT OR IGNORE INTO event_collection_sequence VALUES(?,?,0)',event,kind);
 s.run('UPDATE event_collection_sequence SET seq=seq+1 WHERE event_id=? AND kind=?',event,kind);
 return (kind==='single'?'S':'G')+String(s.one('SELECT seq FROM event_collection_sequence WHERE event_id=? AND kind=?',event,kind).seq).padStart(4,'0');
}
function ensureSingle(s,photoId,owner){return s.tx(()=>{
 const p=s.one('SELECT * FROM photos WHERE id=? AND event_id=? AND device_id=? AND is_test=0',photoId,owner.event_id,owner.id);
 need(p,404,'photo_not_found');
 let m=s.one("SELECT d.* FROM collection_memberships m JOIN deliveries d ON d.id=m.delivery_id WHERE m.photo_id=? AND m.kind='single' AND m.active=1",photoId);
 if(!m){const label=allocate(s,p.event_id,'single'),id=crypto.randomUUID();
  s.run('INSERT INTO deliveries(id,event_id,device_id,kind,label,token,created) VALUES(?,?,?,?,?,?,?)',id,p.event_id,p.device_id,'single',label,crypto.randomBytes(16).toString('hex'),Date.now());
  const released=Number(!!s.one('SELECT auto_release FROM personal_settings WHERE event_id=?',p.event_id)?.auto_release);
  s.run('INSERT INTO collection_memberships(photo_id,delivery_id,kind,released,created,updated) VALUES(?,?,?,?,?,?)',photoId,id,'single',released,Date.now(),Date.now());
  m=s.one('SELECT * FROM deliveries WHERE id=?',id);
 }
 return {photo_id:photoId,id:m.id,kind:'single',label:m.label,guest_path:'/p/'+m.token,ready:true};
 });}
function link(s,photoId,deliveryId,actor){return s.tx(()=>{
 const p=s.one('SELECT * FROM photos WHERE id=? AND is_test=0',photoId),d=s.one("SELECT * FROM deliveries WHERE id=? AND state='open'",deliveryId);
 need(p&&d&&p.event_id===d.event_id,404,'photo_or_collection_not_found');
 need(p.status!=='hidden',409,'photo_hidden');
 const prior=s.one('SELECT * FROM collection_memberships WHERE photo_id=? AND delivery_id=?',photoId,deliveryId);
 const other=s.one('SELECT delivery_id FROM collection_memberships WHERE photo_id=? AND kind=? AND active=1 AND delivery_id<>?',photoId,d.kind,deliveryId);
 need(!other,409,'photo_already_in_other_'+d.kind);
 if(d.kind==='single')need(!s.one("SELECT photo_id FROM collection_memberships WHERE delivery_id=? AND active=1 AND photo_id<>?",deliveryId,photoId),409,'single_collection_full');
 const now=Date.now(),released=Number(!!s.one('SELECT auto_release FROM personal_settings WHERE event_id=?',p.event_id)?.auto_release);
 if(prior)s.run('UPDATE collection_memberships SET active=1,updated=?,revision=revision+1 WHERE photo_id=? AND delivery_id=?',now,photoId,deliveryId);
 else s.run('INSERT INTO collection_memberships(photo_id,delivery_id,kind,released,created,updated) VALUES(?,?,?,?,?,?)',photoId,deliveryId,d.kind,released,now,now);
 s.run('INSERT INTO collection_membership_audit VALUES(?,?,?,?,?,?,?)',crypto.randomUUID(),p.event_id,photoId,deliveryId,prior?'restore':'add',actor,now);
 return {photo_id:photoId,delivery_id:deliveryId,collections:list(s,photoId)};
 });}
function mutate(s,event,b,actor){
 need(b&&Object.keys(b).sort().join(',')==='action,delivery_id,expected_revision,photo_id,request_id',400,'invalid_membership_mutation');
 need(['remove','add'].includes(b.action)&&UUID.test(b.photo_id||'')&&UUID.test(b.delivery_id||'')&&UUID.test(b.request_id||'')&&Number.isSafeInteger(b.expected_revision)&&b.expected_revision>=0,400,'invalid_membership_mutation');
 const hash=crypto.createHash('sha256').update(JSON.stringify([b.action,b.photo_id,b.delivery_id,b.expected_revision])).digest('hex');
 return s.tx(()=>{
  const old=s.one('SELECT * FROM collection_mutation_requests WHERE event_id=? AND request_id=?',event,b.request_id);
  if(old){need(old.request_hash===hash,409,'request_identity_conflict');return JSON.parse(old.response);}
  const p=s.one('SELECT * FROM photos WHERE id=? AND event_id=? AND is_test=0',b.photo_id,event),d=s.one("SELECT * FROM deliveries WHERE id=? AND event_id=? AND state='open'",b.delivery_id,event);
  need(p&&d&&d.kind==='group',404,'group_or_photo_not_found');
  const member=s.one('SELECT * FROM collection_memberships WHERE photo_id=? AND delivery_id=?',p.id,d.id);
  need((member&&member.active?member.revision:0)===b.expected_revision,409,'membership_changed_reload');
  if(b.action==='remove')need(member&&member.active,409,'not_in_group');
  else need(!member||!member.active,409,'already_in_group');
  if(b.action==='add')need(!s.one("SELECT 1 FROM collection_memberships WHERE photo_id=? AND kind='group' AND active=1 AND delivery_id<>?",p.id,d.id),409,'photo_in_other_group_use_explicit_move');
  const now=Date.now();
  if(b.action==='remove')s.run('UPDATE collection_memberships SET active=0,revision=revision+1,updated=? WHERE photo_id=? AND delivery_id=?',now,p.id,d.id);
  else if(member)s.run('UPDATE collection_memberships SET active=1,revision=revision+1,updated=? WHERE photo_id=? AND delivery_id=?',now,p.id,d.id);
  else s.run('INSERT INTO collection_memberships(photo_id,delivery_id,kind,released,created,updated) VALUES(?,?,?,?,?,?)',p.id,d.id,'group',Number(!!s.one('SELECT auto_release FROM personal_settings WHERE event_id=?',event)?.auto_release),now,now);
  s.run('INSERT INTO collection_membership_audit VALUES(?,?,?,?,?,?,?)',crypto.randomUUID(),event,p.id,d.id,b.action,actor,now);
  const response={ok:true,photo_id:p.id,group_id:d.id,collections:list(s,p.id)};
  s.run('INSERT INTO collection_mutation_requests VALUES(?,?,?,?)',event,b.request_id,hash,JSON.stringify(response));return response;
 });
}
module.exports={migrate,list,members,ensureSingle,link,mutate,allocate};
