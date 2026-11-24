'use strict';
// H5.5: private archive, explicit guest assignment and STAFF-only ordering.
// A guest capability can look up a collection here only WITH independent staff auth.
const fs=require('node:fs'),fsp=require('node:fs/promises'),crypto=require('node:crypto');
const {requireValue:need,digest,id,syncDir}=require('./store.cjs');
const P=require('../shared/print-protocol.cjs');
const OP=require('./onboarding-print.cjs');
function migrate(s){s.tx(()=>{
 const cols=s.all('PRAGMA table_info(uploads)').map(x=>x.name);
 if(!s.all('PRAGMA table_info(devices)').some(x=>x.name==='is_service'))s.db.exec('ALTER TABLE devices ADD COLUMN is_service INTEGER NOT NULL DEFAULT 0');
 if(!cols.includes('archive_private'))s.db.exec('ALTER TABLE uploads ADD COLUMN archive_private INTEGER NOT NULL DEFAULT 0');
 s.db.exec(`CREATE TABLE IF NOT EXISTS event_workflows(event_id TEXT PRIMARY KEY REFERENCES events(id),print_route TEXT NOT NULL DEFAULT 'hybrid',revision INTEGER NOT NULL DEFAULT 1);
 CREATE TABLE IF NOT EXISTS station_staff(station_id TEXT PRIMARY KEY REFERENCES print_stations(id),enabled INTEGER NOT NULL DEFAULT 0,max_copies INTEGER NOT NULL DEFAULT 1,actor_id TEXT REFERENCES devices(id));
 CREATE TABLE IF NOT EXISTS print_photo_links(job_id TEXT PRIMARY KEY REFERENCES print_jobs(id),photo_id TEXT NOT NULL REFERENCES photos(id));
 CREATE INDEX IF NOT EXISTS linked_photo_prints ON print_photo_links(photo_id);
 CREATE INDEX IF NOT EXISTS print_origin_image ON print_jobs(event_id,device_id,json_extract(spec,'$.sha256'),json_extract(spec,'$.bytes'));
 CREATE TABLE IF NOT EXISTS staff_orders(job_id TEXT PRIMARY KEY REFERENCES print_jobs(id),photo_id TEXT NOT NULL REFERENCES photos(id),request_hash TEXT NOT NULL,actor TEXT NOT NULL,delivery_id TEXT REFERENCES deliveries(id),reprint_of TEXT REFERENCES print_jobs(id),created INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS print_collection(job_id TEXT PRIMARY KEY REFERENCES print_jobs(id),actor TEXT NOT NULL,collected_at INTEGER NOT NULL);
 PRAGMA user_version=7;`);
 });}
function settings(s,event){const r=s.one('SELECT * FROM event_workflows WHERE event_id=?',event);return {print_route:r?.print_route||'hybrid',revision:r?.revision||1,archive_policy:'all_new_eligible_private',station_upload_hub:false};}
function linkPrint(s,jobId){
 const r=s.one('SELECT * FROM print_jobs WHERE id=?',jobId);if(!r)return;
 const j=JSON.parse(r.spec),p=s.one('SELECT id FROM photos WHERE event_id=? AND device_id=? AND sha256=? AND size=? AND is_test=0',j.event_id,j.device_id,j.sha256,j.bytes);
 if(p)s.run('INSERT OR IGNORE INTO print_photo_links VALUES(?,?)',j.id,p.id);
}
function linkPrintsForPhoto(s,pid){const p=s.one('SELECT * FROM photos WHERE id=?',pid);if(!p||p.is_test)return;
 // Indexed event/device scope; spec hashes are compared exactly, never filenames.
 for(const r of s.all("SELECT id FROM print_jobs WHERE event_id=? AND device_id=? AND json_extract(spec,'$.sha256')=? AND json_extract(spec,'$.bytes')=?",p.event_id,p.device_id,p.sha256,p.size)){
  s.run('INSERT OR IGNORE INTO print_photo_links VALUES(?,?)',r.id,p.id);
 }}
function bind(s,photo,deliveryId,owner=null){return s.tx(()=>{
 const p=s.one('SELECT * FROM photos WHERE id=?',photo),d=s.one("SELECT * FROM deliveries WHERE id=? AND state='open'",deliveryId);
 need(p&&!p.is_test&&p.status!=='hidden',404,'photo_not_available');need(d&&d.event_id===p.event_id,404,'delivery_not_found');
 if(owner)need(p.device_id===owner.id&&d.device_id===owner.id&&p.event_id===owner.event_id,403,'delivery_owner_mismatch');
 const old=s.one('SELECT delivery_id FROM delivery_items WHERE photo_id=?',p.id);
 need(!old||old.delivery_id===d.id,409,'photo_already_bound_elsewhere');
 if(d.kind==='single'){
  need(!s.one('SELECT photo_id FROM delivery_items WHERE delivery_id=? AND photo_id<>?',d.id,p.id),409,'single_delivery_already_bound');
  need(!s.one("SELECT id FROM uploads WHERE delivery_id=? AND (photo_id IS NULL OR photo_id<>?)",d.id,p.id),409,'single_delivery_already_bound');
 }
 if(!old)s.run('INSERT INTO delivery_items(photo_id,delivery_id,released) VALUES(?,?,?)',p.id,d.id,Number(!!s.one('SELECT auto_release FROM personal_settings WHERE event_id=?',p.event_id)?.auto_release));
 return {photo_id:p.id,delivery_id:d.id,released:!!s.one('SELECT released FROM delivery_items WHERE photo_id=?',p.id).released,public_status:p.status};
 });}
function staffScope(c,match){
 const s=c.store;
 if(match[1]){c.admin(c.req);need(s.one('SELECT id FROM events WHERE id=?',match[1]),404,'event_not_found');return {event:match[1],actor:'admin',station:null,maxCopies:5};}
 const st=OP.station(s,c.req),access=s.one('SELECT * FROM station_staff WHERE station_id=?',st.id);
 need(access?.enabled,403,'staff_desk_not_enabled');return {event:st.event_id,actor:'station:'+st.id,station:st.id,maxCopies:access.max_copies};
}
function cursor(url){const n=Number(url.searchParams.get('before')||Number.MAX_SAFE_INTEGER);need(Number.isSafeInteger(n)&&n>0,400,'invalid_cursor');return n;}
function photoRecord(s,p){const d=s.one('SELECT d.id,d.label,d.kind,d.token,d.state,i.released FROM delivery_items i JOIN deliveries d ON d.id=i.delivery_id WHERE i.photo_id=?',p.id);
 return {id:p.id,seq:p.seq,filename:p.filename,width:p.width,height:p.height,bytes:p.size,status:p.status,device_label:p.device_label||'',delivery:d?{id:d.id,label:d.label,kind:d.kind,state:d.state,released:!!d.released}:null};}
function orders(s,event,delivery=null,stationId=null){
 return s.all(`SELECT j.*,l.photo_id,c.collected_at FROM print_jobs j JOIN print_photo_links l ON l.job_id=j.id LEFT JOIN print_collection c ON c.job_id=j.id ${delivery?'JOIN delivery_items di ON di.photo_id=l.photo_id':''} WHERE j.event_id=? ${delivery?'AND di.delivery_id=?':''} ${stationId?'AND j.station_id=?':''} ORDER BY j.created DESC LIMIT 100`,event,...(delivery?[delivery]:[]),...(stationId?[stationId]:[])).map(r=>({...OP.describeJob(r),photo_id:r.photo_id,collected_at:r.collected_at||null}));
}
function serviceActor(s,st){
 let row=s.one('SELECT * FROM station_staff WHERE station_id=?',st.id);
 if(!row){s.run('INSERT INTO station_staff(station_id) VALUES(?)',st.id);row=s.one('SELECT * FROM station_staff WHERE station_id=?',st.id);}
 if(!row.actor_id){const actor=id();s.run('INSERT INTO devices(id,event_id,label,token_hash,is_service) VALUES(?,?,?,?,1)',actor,st.event_id,'Staff desk / '+st.label,digest(crypto.randomBytes(32)));s.run('UPDATE station_staff SET actor_id=? WHERE station_id=?',actor,st.id);row.actor_id=actor;}
 // Service identity has no disclosed credential. Only authenticated staff creates its orders.
 s.run('INSERT INTO device_permissions VALUES(?,?) ON CONFLICT(device_id) DO UPDATE SET settings=excluded.settings',row.actor_id,JSON.stringify({upload:false,personal:false,print:true,max_copies:5,station_id:st.id}));
 return row.actor_id;
}
async function createOrder(c,scope,b){const s=c.store;
 need(Object.keys(b).every(k=>['id','photo_id','station_id','profile_id','profile_revision','copies','delivery_id','reprint_of'].includes(k)),400,'unknown_order_field');
 need(P.UUID.test(b.id||'')&&P.UUID.test(b.photo_id||'')&&P.UUID.test(b.station_id||''),400,'invalid_order_identity');
 need(Number.isInteger(b.copies)&&b.copies>=1&&b.copies<=scope.maxCopies,403,'copies_not_permitted');
 need(!scope.station||b.station_id===scope.station,403,'staff_station_scope_mismatch');
 const key=P.hash(P.stable({photo_id:b.photo_id,station_id:b.station_id,profile_id:b.profile_id,profile_revision:b.profile_revision,copies:b.copies,delivery_id:b.delivery_id||null,reprint_of:b.reprint_of||null}));
 return c.locked('print:'+b.id,async()=>{
  const st=s.one('SELECT * FROM print_stations WHERE id=? AND event_id=? AND revoked=0',b.station_id,scope.event);need(st,404,'station_not_found');
  const saved=s.one('SELECT * FROM staff_orders WHERE job_id=?',b.id);
  if(saved){need(saved.request_hash===key&&saved.actor===scope.actor,409,'staff_order_identity_conflict');const job=s.one('SELECT * FROM print_jobs WHERE id=?',b.id);if(!['awaiting_file','receiving'].includes(job.state))return {job:OP.describeJob(job),created:false};}
  const p=s.one('SELECT * FROM photos WHERE id=? AND event_id=? AND is_test=0 AND status<>?',b.photo_id,scope.event,'hidden');need(p,404,'photo_not_available');
  need(p.size<=P.MAX_FILE,413,'print_jpeg_maximum_32_mib');
  const binding=s.one('SELECT delivery_id FROM delivery_items WHERE photo_id=?',p.id);
  need((binding?.delivery_id||null)===(b.delivery_id||null),409,'photo_collection_changed_reload');
  if(b.delivery_id)need(s.one("SELECT id FROM deliveries WHERE id=? AND state='open'",b.delivery_id),409,'delivery_cancelled');
  const profile=JSON.parse(st.profiles).find(x=>x.id===b.profile_id&&x.revision===b.profile_revision);need(profile,409,'profile_changed_reselect');
  if(['cp9550-strip-pair-auto','cp9550-strip-pair-manual'].includes(profile.id))need(Math.abs(Math.max(p.width,p.height)/Math.min(p.width,p.height)-3)<=0.04,422,'finished_single_strip_required');
  if(b.reprint_of)need(P.UUID.test(b.reprint_of)&&s.one('SELECT j.id FROM print_jobs j JOIN print_photo_links l ON l.job_id=j.id WHERE j.id=? AND j.event_id=? AND l.photo_id=?',b.reprint_of,scope.event,p.id),400,'invalid_reprint_reference');
  let job;
  s.tx(()=>{
   const actor=serviceActor(s,st),spec=P.job({id:b.id,event_id:scope.event,device_id:actor,station_id:st.id,sha256:p.sha256,bytes:p.size,profile_id:profile.id,profile_revision:profile.revision,copies:b.copies});
   // Reserve atomically with order identity; putJob's own transaction is used outside this one.
   const current=s.one('SELECT * FROM print_jobs WHERE id=?',b.id);
   if(current)need(saved&&current.spec_hash===P.fingerprint(spec),409,'print_job_identity_conflict');
   else {need(s.one("SELECT count(*) n FROM print_jobs WHERE device_id=? AND state IN ('awaiting_file','ready','receiving')",actor).n<100,429,'print_queue_limit');need(s.one('SELECT count(*) n FROM print_jobs WHERE event_id=?',scope.event).n<100000,429,'print_history_limit');s.run('INSERT INTO print_jobs(id,event_id,device_id,station_id,spec,spec_hash,created,updated) VALUES(?,?,?,?,?,?,?,?)',spec.id,spec.event_id,spec.device_id,spec.station_id,JSON.stringify(spec),P.fingerprint(spec),Date.now(),Date.now());}
   job=s.one('SELECT * FROM print_jobs WHERE id=?',b.id);require('./print-assets.cjs').reserve(s,job);
   s.run('INSERT OR IGNORE INTO staff_orders VALUES(?,?,?,?,?,?,?)',b.id,p.id,key,scope.actor,b.delivery_id||null,b.reprint_of||null,Date.now());
   s.run('INSERT OR IGNORE INTO print_photo_links VALUES(?,?)',b.id,p.id);
  });
  const dir=s.file('','print-assets'),dest=s.file('print-assets',b.id+'.part'),tmp=s.file('print-assets',b.id+'.copying');
  try{
   const space=fs.statfsSync(dir);need(space.bavail*space.bsize>p.size+128*1024**2,507,'cloud_storage_low');
   await fsp.copyFile(s.file('originals',p.id+'.jpg'),tmp);const h=await OP.fileHash(tmp);need(h===p.sha256&&(await fsp.stat(tmp)).size===p.size,422,'archive_image_checksum_mismatch');
   const f=await fsp.open(tmp,'r');try{await f.sync();}finally{await f.close();}
   await fsp.rename(tmp,dest);syncDir(dir);
   // Revalidate privacy/scope after async I/O, before making the new job executable.
   need(s.one("SELECT id FROM photos WHERE id=? AND status<>'hidden'",p.id),409,'photo_hidden_during_order');
   if(scope.station)need(s.one('SELECT enabled FROM station_staff WHERE station_id=?',scope.station)?.enabled,403,'staff_desk_not_enabled');
   const latest=s.one('SELECT * FROM print_stations WHERE id=? AND revoked=0',st.id);need(latest,403,'station_revoked');need(JSON.parse(latest.profiles).some(x=>x.id===profile.id&&x.revision===profile.revision),409,'profile_changed_reselect');if(b.delivery_id)need(s.one("SELECT id FROM deliveries WHERE id=? AND state='open'",b.delivery_id),409,'delivery_cancelled');const currentBinding=s.one('SELECT delivery_id FROM delivery_items WHERE photo_id=?',p.id);need((currentBinding?.delivery_id||null)===(b.delivery_id||null),409,'photo_collection_changed_reload');if(scope.station)need(s.one('SELECT max_copies FROM station_staff WHERE station_id=?',st.id).max_copies>=b.copies,403,'copies_not_permitted');
   s.run("UPDATE print_jobs SET asset_ready=1,offset=?,state='ready',updated=? WHERE id=? AND state IN ('awaiting_file','receiving')",p.size,Date.now(),b.id);
   return {job:OP.describeJob(s.one('SELECT * FROM print_jobs WHERE id=?',b.id)),created:!saved};
  }finally{await fsp.rm(tmp,{force:true}).catch(()=>{});}
 });
}
async function routeInner(c){const {store:s,p,method,url,req,res,admin,device,readJSON,json,serveFile}=c;let m;
 if((m=/^\/v1\/events\/([0-9a-f-]+)\/workflow$/.exec(p))&&['GET','POST'].includes(method)){
  admin(req);need(s.one('SELECT id FROM events WHERE id=?',m[1]),404,'event_not_found');
  if(method==='POST'){const b=await readJSON(req),old=settings(s,m[1]);need(Object.keys(b).length===2&&['hybrid','cloud'].includes(b.print_route)&&b.revision===old.revision,409,'workflow_changed_reload');s.run('INSERT INTO event_workflows VALUES(?,?,?) ON CONFLICT(event_id) DO UPDATE SET print_route=excluded.print_route,revision=excluded.revision',m[1],b.print_route,old.revision+1);}
  json(res,200,settings(s,m[1]));return true;
 }
 if(p==='/v1/device/workflow'&&method==='GET'){const d=device(req);json(res,200,settings(s,d.event_id));return true;}
 if((m=/^\/v1\/device\/photos\/([0-9a-f-]+)\/(delivery|thumb)$/.exec(p))){const d=device(req);need(s.one('SELECT id FROM photos WHERE id=? AND event_id=? AND device_id=? AND is_test=0',m[1],d.event_id,d.id),404,'photo_not_found');
  if(m[2]==='delivery'&&method==='POST'){const b=await readJSON(req);need(Object.keys(b).length===1&&P.UUID.test(b.delivery_id||''),400,'invalid_delivery_id');json(res,200,bind(s,m[1],b.delivery_id,d));return true;}
  if(m[2]==='thumb'&&method==='GET'){await serveFile(res,s.file('thumbs',m[1]+'.jpg'),'image/jpeg');return true;}
 }
 if((m=/^\/v1\/events\/([0-9a-f-]+)\/stations\/([0-9a-f-]+)\/staff-desk$/.exec(p))&&method==='POST'){
  admin(req);const b=await readJSON(req);need(Object.keys(b).length===2&&typeof b.enabled==='boolean'&&Number.isInteger(b.max_copies)&&b.max_copies>=1&&b.max_copies<=5,400,'invalid_staff_permission');need(s.one('SELECT id FROM print_stations WHERE id=? AND event_id=? AND revoked=0',m[2],m[1]),404,'station_not_found');
  s.run('INSERT INTO station_staff(station_id,enabled,max_copies) VALUES(?,?,?) ON CONFLICT(station_id) DO UPDATE SET enabled=excluded.enabled,max_copies=excluded.max_copies',m[2],Number(b.enabled),b.max_copies);
  json(res,200,{ok:true});return true;
 }
 // Admin endpoint has an event segment; station endpoint derives its scope from the credential.
 m=/^\/v1\/(?:events\/([0-9a-f-]+)\/staff|station\/staff)\/(config|photos|lookup|orders|assign|collect|cancel-prepared|photo\/([0-9a-f-]+)\/(thumb|delivery))$/.exec(p);
 if(!m)return false;const scope=staffScope(c,m),action=m[2];
 if(action==='config'&&method==='GET'){json(res,200,{event:s.one('SELECT id,name FROM events WHERE id=?',scope.event),workflow:settings(s,scope.event),max_copies:scope.maxCopies,stations:s.all('SELECT st.*,ss.enabled AS staff_enabled,ss.max_copies AS staff_max_copies FROM print_stations st LEFT JOIN station_staff ss ON ss.station_id=st.id WHERE st.event_id=? AND st.revoked=0'+(scope.station?' AND st.id=?':''),scope.event,...(scope.station?[scope.station]:[])).map(st=>({...OP.stationPublic(st),staff_enabled:!!st.staff_enabled,staff_max_copies:st.staff_max_copies||1})),note:'Staff operated. Ticket lookup NEVER prints. No quota/payment redemption system.'});return true;}
 if(action.startsWith('photo/')&&method==='GET'){need(s.one("SELECT id FROM photos WHERE id=? AND event_id=? AND is_test=0 AND status<>'hidden'",m[3],scope.event),404,'photo_not_available');await serveFile(res,s.file(m[4]==='thumb'?'thumbs':'delivery',m[3]+'.jpg'),'image/jpeg');return true;}
 if(action==='photos'&&method==='GET'){
  const before=cursor(url),rows=s.all("SELECT p.*,d.label AS device_label FROM photos p JOIN devices d ON d.id=p.device_id WHERE p.event_id=? AND p.is_test=0 AND p.status<>'hidden' AND p.seq<? ORDER BY p.seq DESC LIMIT 50",scope.event,before);
  json(res,200,{photos:rows.map(p=>photoRecord(s,p)),next_before:rows.length===50?rows.at(-1).seq:null});return true;
 }
 if(action==='lookup'&&method==='POST'){
  const b=await readJSON(req);need(typeof b.reference==='string'&&b.reference.length<=512,400,'invalid_ticket_reference');let ref=b.reference.trim(),d;
  const short=/^(?:REF\s+)?([0-9a-f]{8})$/i.exec(ref);
  if(short){const matches=s.all('SELECT * FROM deliveries WHERE event_id=? AND substr(id,1,8)=? LIMIT 2',scope.event,short[1].toLowerCase());need(matches.length<=1,409,'ambiguous_reference_scan_full_qr');d=matches[0];}
  else if(P.UUID.test(ref))d=s.one('SELECT * FROM deliveries WHERE id=? AND event_id=?',ref,scope.event);
  else{let token=/^\/p\/([0-9a-f]{32})$/.exec(ref)?.[1];if(!token){try{const u=new URL(ref);need(u.protocol==='https:'&&!u.username&&!u.password&&!u.search&&!u.hash,400,'invalid_guest_url');token=/^\/p\/([0-9a-f]{32})$/.exec(u.pathname)?.[1];}catch{need(false,400,'invalid_guest_url');}}need(token,400,'full_guest_link_or_collection_id_required');d=s.one('SELECT * FROM deliveries WHERE token=? AND event_id=?',token,scope.event);}
  need(d&&d.state==='open',404,'delivery_not_available');const before=b.before===undefined?Number.MAX_SAFE_INTEGER:b.before;need(Number.isSafeInteger(before)&&before>0,400,'invalid_cursor');
  const rows=s.all("SELECT p.*,dev.label AS device_label FROM delivery_items i JOIN photos p ON p.id=i.photo_id JOIN devices dev ON dev.id=p.device_id WHERE i.delivery_id=? AND p.is_test=0 AND p.status<>'hidden' AND p.seq<? ORDER BY p.seq DESC LIMIT 50",d.id,before);
  json(res,200,{collection:{id:d.id,label:d.label,kind:d.kind},photos:rows.map(p=>photoRecord(s,p)),next_before:rows.length===50?rows.at(-1).seq:null,orders:orders(s,scope.event,d.id,scope.station),note:'Lookup only. Review the collection and photo before confirming any print.'});return true;
 }
 if(action==='assign'&&method==='POST'){const b=await readJSON(req);need(P.UUID.test(b.photo_id||'')&&P.UUID.test(b.delivery_id||'')&&Object.keys(b).length===2,400,'invalid_assignment');need(s.one('SELECT id FROM photos WHERE id=? AND event_id=?',b.photo_id,scope.event),404,'photo_not_found');json(res,200,bind(s,b.photo_id,b.delivery_id));return true;}
 if(action==='orders'&&method==='GET'){json(res,200,{orders:orders(s,scope.event,null,scope.station)});return true;}
 if(action==='orders'&&method==='POST'){json(res,201,await createOrder(c,scope,await readJSON(req)));return true;}
 if(action==='cancel-prepared'&&method==='POST'){const b=await readJSON(req);need(Object.keys(b).length===1&&P.UUID.test(b.job_id||''),400,'invalid_order_identity');await c.locked('print:'+b.job_id,async()=>{const row=s.one('SELECT j.*,o.actor FROM print_jobs j JOIN staff_orders o ON o.job_id=j.id WHERE j.id=? AND j.event_id=?',b.job_id,scope.event);need(row&&(!scope.station||row.station_id===scope.station)&&row.actor===scope.actor,404,'print_job_not_found');need(['awaiting_file','receiving','cancelled'].includes(row.state)&&row.report_revision===0&&!row.asset_ready,409,'station_may_have_job_cancel_at_station');s.run("UPDATE print_jobs SET state='cancelled',terminal_at=CASE WHEN terminal_at=0 THEN ? ELSE terminal_at END,updated=? WHERE id=?",Date.now(),Date.now(),row.id);});json(res,200,{ok:true,note:'Only unexecutable staff preparation cancelled. No station job was recalled.'});return true;}
 if(action==='collect'&&method==='POST'){const b=await readJSON(req);need(Object.keys(b).length===1&&P.UUID.test(b.job_id||''),400,'invalid_collection');const row=s.one('SELECT * FROM print_jobs WHERE id=? AND event_id=?',b.job_id,scope.event);need(row&&(!scope.station||row.station_id===scope.station),404,'print_job_not_found');need(row.state==='operator_confirmed',409,'verify_physical_output_before_collection');s.run('INSERT OR IGNORE INTO print_collection VALUES(?,?,?)',row.id,scope.actor,Date.now());json(res,200,{job_id:row.id,...s.one('SELECT collected_at FROM print_collection WHERE job_id=?',row.id)});return true;}
 return false;
}
async function route(c){try{return await routeInner(c);}catch(e){if(e instanceof P.ProtocolError){const {HttpError}=require('./store.cjs');throw new HttpError(e.status,e.code);}throw e;}}
module.exports={migrate,settings,bind,linkPrint,linkPrintsForPhoto,route,createOrder};
