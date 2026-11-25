'use strict';
// H5.12.1: device-scoped pre-upload Single identities. Public token is random,
// not a filename, photo hash, sequential label, or photographer identifier.
const crypto=require('node:crypto');
const {requireValue:need}=require('./store.cjs');
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const TOKEN=/^[a-f0-9]{32}$/;
function migrate(s){s.tx(()=>{
  s.db.exec(`CREATE TABLE IF NOT EXISTS preissued_singles(
    event_id TEXT NOT NULL REFERENCES events(id),device_id TEXT NOT NULL REFERENCES devices(id),
    client_id TEXT NOT NULL, delivery_id TEXT NOT NULL UNIQUE REFERENCES deliveries(id),
    filename TEXT NOT NULL, size INTEGER NOT NULL, created INTEGER NOT NULL,
    photo_id TEXT REFERENCES photos(id), conflict INTEGER NOT NULL DEFAULT 0 CHECK(conflict IN (0,1)),
    PRIMARY KEY(event_id,device_id,client_id));
    CREATE INDEX IF NOT EXISTS preissued_photo ON preissued_singles(photo_id);`);
  if(s.one('PRAGMA user_version').user_version<11)s.db.exec('PRAGMA user_version=11');
});}
function claim(s,u,photoId){
  const pre=s.one('SELECT * FROM preissued_singles WHERE event_id=? AND device_id=? AND client_id=?',u.event_id,u.device_id,u.client_id);
  if(!pre)return null;
  need(pre.filename===u.filename && pre.size===u.size && !u.is_test,409,'preissued_upload_identity_mismatch');
  const other=s.one("SELECT delivery_id FROM collection_memberships WHERE photo_id=? AND kind='single' AND active=1",photoId);
  if(other && other.delivery_id!==pre.delivery_id){s.run('UPDATE preissued_singles SET conflict=1 WHERE event_id=? AND device_id=? AND client_id=?',u.event_id,u.device_id,u.client_id);return null;}
  if(!other){
    const t=Date.now(),released=Number(!!s.one('SELECT auto_release FROM personal_settings WHERE event_id=?',u.event_id)?.auto_release);
    s.run("INSERT OR IGNORE INTO collection_memberships(photo_id,delivery_id,kind,released,created,updated) VALUES(?,?,'single',?,?,?)",photoId,pre.delivery_id,released,t,t);
  }
  s.run('UPDATE preissued_singles SET photo_id=?,conflict=0 WHERE event_id=? AND device_id=? AND client_id=?',photoId,u.event_id,u.device_id,u.client_id);
  return pre.delivery_id;
}
function ensure(s,dev,b){
  need(b&&typeof b==='object'&&!Array.isArray(b)&&Object.keys(b).sort().join(',')==='client_id,filename,size,token',400,'invalid_preissue_fields');
  need(UUID.test(b.client_id||'') && TOKEN.test(b.token||''),400,'invalid_preissue_identity');
  need(typeof b.filename==='string' && b.filename.length>0 && b.filename.length<=240 && /\.jpe?g$/i.test(b.filename) && !/[\\/\x00-\x1f]/.test(b.filename),400,'invalid_preissue_filename');
  need(Number.isSafeInteger(b.size)&&b.size>0&&b.size<=64*1024*1024,400,'invalid_preissue_size');
  return s.tx(()=>{
    let row=s.one('SELECT * FROM preissued_singles WHERE event_id=? AND device_id=? AND client_id=?',dev.event_id,dev.id,b.client_id);
    if(!row){
      need(s.one('SELECT count(*) n FROM preissued_singles WHERE event_id=?',dev.event_id).n<100000,429,'preissue_event_limit');
      need(!s.one('SELECT id FROM deliveries WHERE token=?',b.token),409,'preissue_token_conflict');
      const id=crypto.randomUUID(),label=require('./dual-collections.cjs').allocate(s,dev.event_id,'single'),t=Date.now();
      s.run("INSERT INTO deliveries(id,event_id,device_id,kind,label,token,created) VALUES(?,?,?,'single',?,?,?)",id,dev.event_id,dev.id,label,b.token,t);
      s.run('INSERT INTO preissued_singles(event_id,device_id,client_id,delivery_id,filename,size,created) VALUES(?,?,?,?,?,?,?)',dev.event_id,dev.id,b.client_id,id,b.filename,b.size,t);
      row=s.one('SELECT * FROM preissued_singles WHERE event_id=? AND device_id=? AND client_id=?',dev.event_id,dev.id,b.client_id);
    } else {
      const d=s.one('SELECT token FROM deliveries WHERE id=?',row.delivery_id);
      need(d?.token===b.token && row.filename===b.filename && row.size===b.size,409,'preissue_client_identity_conflict');
    }
    const uploaded=s.one("SELECT * FROM uploads WHERE event_id=? AND device_id=? AND client_id=? AND state='complete' AND photo_id IS NOT NULL",dev.event_id,dev.id,b.client_id);
    if(uploaded)claim(s,uploaded,uploaded.photo_id);
    const d=s.one('SELECT * FROM deliveries WHERE id=?',row.delivery_id);
    return {id:d.id,kind:'single',label:d.label,guest_path:'/p/'+d.token,photo_id:s.one('SELECT photo_id FROM preissued_singles WHERE event_id=? AND device_id=? AND client_id=?',dev.event_id,dev.id,b.client_id)?.photo_id||null,registered:true};
  });
}
async function route(c){
  if(c.p!=='/v1/device/single-preissue'||c.method!=='POST')return false;
  const dev=c.device(c.req);require('./event-management.cjs').active(c.store,dev.event_id);
  const b=await c.readJSON(c.req);c.json(c.res,200,ensure(c.store,dev,b));return true;
}
module.exports={migrate,claim,ensure,route};
