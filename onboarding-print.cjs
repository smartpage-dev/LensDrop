'use strict';
const crypto=require('node:crypto'),fs=require('node:fs'),fsp=require('node:fs/promises'),path=require('node:path');
const {requireValue:need,HttpError,id,digest,syncDir}=require('./store.cjs');
const P=require('../shared/print-protocol.cjs');
const now=()=>Date.now(),token=req=>/^Bearer (.+)$/.exec(req.headers.authorization||'')?.[1]||'';
const obj=s=>JSON.parse(s||'{}');
function migrate(s){s.db.exec(`
CREATE TABLE IF NOT EXISTS pairing_invites(id TEXT PRIMARY KEY,event_id TEXT NOT NULL REFERENCES events(id),kind TEXT NOT NULL,label TEXT NOT NULL,secret_hash TEXT NOT NULL,expires INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'open',claim_hash TEXT,credential_hash TEXT,client_id TEXT,code TEXT,permissions TEXT NOT NULL,station_id TEXT,entity_id TEXT,created INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS pairing_event ON pairing_invites(event_id,created);
CREATE TABLE IF NOT EXISTS device_permissions(device_id TEXT PRIMARY KEY REFERENCES devices(id),settings TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS print_stations(id TEXT PRIMARY KEY,event_id TEXT NOT NULL REFERENCES events(id),label TEXT NOT NULL,token_hash TEXT UNIQUE NOT NULL,revoked INTEGER NOT NULL DEFAULT 0,profiles TEXT NOT NULL DEFAULT '[]',revision INTEGER NOT NULL DEFAULT 1,endpoint TEXT,certificate_hash TEXT,last_seen INTEGER,paused INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS print_jobs(id TEXT PRIMARY KEY,event_id TEXT NOT NULL REFERENCES events(id),device_id TEXT NOT NULL REFERENCES devices(id),station_id TEXT NOT NULL REFERENCES print_stations(id),spec TEXT NOT NULL,spec_hash TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'awaiting_file',offset INTEGER NOT NULL DEFAULT 0,asset_ready INTEGER NOT NULL DEFAULT 0,report_revision INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL,updated INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS print_station_queue ON print_jobs(station_id,state,created);
`);
require('./print-assets.cjs').migrate(s);
const columns=new Set(s.all('PRAGMA table_info(pairing_invites)').map(x=>x.name));
if(!columns.has('approval_failures'))s.db.exec('ALTER TABLE pairing_invites ADD COLUMN approval_failures INTEGER NOT NULL DEFAULT 0');
if(!columns.has('approval_last_attempt'))s.db.exec('ALTER TABLE pairing_invites ADD COLUMN approval_last_attempt INTEGER NOT NULL DEFAULT 0');
if(!columns.has('replacement_device_id'))s.db.exec('ALTER TABLE pairing_invites ADD COLUMN replacement_device_id TEXT');
if(s.one('PRAGMA user_version').user_version<6)s.db.exec('PRAGMA user_version=6');
fs.mkdirSync(s.file('','print-assets'),{recursive:true,mode:0o700});
const file=path.join(s.root,'print-signing-key.pem');
if(!fs.existsSync(file)){need(s.one('SELECT count(*) n FROM print_stations').n===0,500,'print_signing_identity_missing_restore_backup');const pair=crypto.generateKeyPairSync('ed25519');fs.writeFileSync(file,pair.privateKey.export({format:'pem',type:'pkcs8'}),{flag:'wx',mode:0o600});const fd=fs.openSync(file,'r');fs.fsyncSync(fd);fs.closeSync(fd);syncDir(s.root);}
s.printPrivateKey=crypto.createPrivateKey(fs.readFileSync(file));s.printPublicKey=crypto.createPublicKey(s.printPrivateKey).export({type:'spki',format:'pem'});
}
function permissions(b={}){need(b&&typeof b==='object'&&!Array.isArray(b),400,'invalid_permissions');need(Object.keys(b).every(k=>['print','max_copies','station_id'].includes(k)),400,'unknown_permission');need(b.print===undefined||typeof b.print==='boolean',400,'invalid_print_permission');need(b.max_copies===undefined||Number.isInteger(b.max_copies)&&b.max_copies>=1&&b.max_copies<=5,400,'invalid_max_copies');need(!b.station_id||P.UUID.test(b.station_id),400,'invalid_station');return {upload:true,personal:true,print:!!b.print,max_copies:b.max_copies||1,station_id:b.station_id||null};}
function devicePermissions(s,dev){return obj(s.one('SELECT settings FROM device_permissions WHERE device_id=?',dev.id)?.settings);}
function station(s,req){const st=s.one('SELECT * FROM print_stations WHERE token_hash=? AND revoked=0',digest(token(req)));need(st,401,'station_token_invalid_or_revoked');return st;}
function stationPublic(st){return {id:st.id,event_id:st.event_id,label:st.label,profiles:JSON.parse(st.profiles),revision:st.revision,endpoint:st.endpoint,certificate_sha256:st.certificate_hash,last_seen:st.last_seen,paused:!!st.paused,revoked:!!st.revoked};}
function label(x){need(typeof x==='string'&&x.trim()&&x.length<=80&&!/[\x00-\x1f]/.test(x),400,'invalid_label');return x.trim();}
function origin(x){let u;try{u=new URL(x);}catch{}need(u&&u.protocol==='https:'&&!u.username&&!u.password&&!u.search&&!u.hash&&(!u.pathname||u.pathname==='/'),400,'https_origin_required');return u.origin;}
function privateEndpoint(x){let u;try{u=new URL(x);}catch{}need(u&&u.protocol==='https:'&&!u.username&&!u.password&&!u.search&&!u.hash&&u.pathname==='/'&&require('node:net').isIP(u.hostname)===4&&/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(u.hostname),400,'private_ipv4_https_endpoint_required');return u.origin;}
function printAuth(s,dev,stationId){const perm=devicePermissions(s,dev);need(perm.print&&perm.station_id===stationId,403,'printing_not_authorized');const st=s.one('SELECT * FROM print_stations WHERE id=? AND event_id=? AND revoked=0',stationId,dev.event_id);need(st,404,'station_not_found');return {perm,st};}
function jobRow(s,identifier){return s.one('SELECT * FROM print_jobs WHERE id=?',identifier);}
function describeJob(row){return {...JSON.parse(row.spec),state:row.state,offset:row.offset,report_revision:row.report_revision,updated:row.updated};}
function putJob(s,spec,reserve=false){
 const j=P.job(spec),h=P.fingerprint(j);
 return s.tx(()=>{
  let row=jobRow(s,j.id);
  if(row)need(row.spec_hash===h,409,'print_job_identity_conflict');
  else {
   if(reserve)need(s.one("SELECT count(*) n FROM print_jobs WHERE device_id=? AND state IN ('awaiting_file','ready','receiving')",j.device_id).n<100,429,'print_queue_limit');
   // Metadata has its own limit; LAN receipts never reserve absent cloud image bytes.
   need(s.one('SELECT count(*) n FROM print_jobs WHERE event_id=?',j.event_id).n<100000,429,'print_history_limit');
   s.run('INSERT INTO print_jobs(id,event_id,device_id,station_id,spec,spec_hash,created,updated) VALUES(?,?,?,?,?,?,?,?)',j.id,j.event_id,j.device_id,j.station_id,JSON.stringify(j),h,now(),now());
   row=jobRow(s,j.id);
  }
  if(reserve)require('./print-assets.cjs').reserve(s,row);
  require('./event-workflow.cjs').linkPrint(s,j.id);
  return jobRow(s,j.id);
 });
}
async function fileHash(file){const h=crypto.createHash('sha256');for await(const b of fs.createReadStream(file))h.update(b);return h.digest('hex');}
// One active request per asset. Accepted offset, not filesystem length, is authoritative.
async function chunk(c,row){const {req,res,url,store:s,json,locked}=c;return locked('print:'+row.id,async()=>{row=jobRow(s,row.id);need(!row.asset_ready&&row.asset_cleanup==='none'&&['awaiting_file','receiving'].includes(row.state),409,'print_asset_closed');s.tx(()=>require('./print-assets.cjs').reserve(s,row));const j=obj(row.spec),offset=Number(url.searchParams.get('offset'));need(Number.isSafeInteger(offset)&&offset>=0&&offset===row.offset,409,'print_offset_mismatch');need((req.headers['content-type']||'').split(';')[0]==='application/octet-stream',415,'binary_required');const len=Number(req.headers['content-length']);need(Number.isInteger(len)&&len>0&&len<=P.CHUNK&&offset+len<=j.bytes,413,'invalid_chunk_size');need(P.SHA.test(req.headers['x-chunk-sha256']||''),400,'chunk_hash_required');const stat=fs.statfsSync(s.root);need(stat.bavail*stat.bsize>len+128*1024**2,507,'cloud_storage_low');let total=0,parts=[];for await(const b of req){total+=b.length;need(total<=len,413,'invalid_chunk_size');parts.push(b);}const data=Buffer.concat(parts);need(total===len&&digest(data)===req.headers['x-chunk-sha256'],422,'chunk_checksum_mismatch');const file=s.file('print-assets',row.id+'.part');let fh;try{fh=await fsp.open(file,fs.existsSync(file)?'r+':'w',0o600);const stat=await fh.stat();need(stat.size>=offset,409,'print_asset_lost');await fh.truncate(offset);await fh.write(data,0,data.length,offset);await fh.sync();}finally{await fh?.close();}s.run('UPDATE print_jobs SET offset=?,updated=? WHERE id=?',offset+len,now(),row.id);json(res,200,describeJob(jobRow(s,row.id)));});}
const attemptBuckets=new Map();
function rate(req,s){const attemptBuckets=s.pairingBuckets||(s.pairingBuckets=new Map());const k=req.socket.remoteAddress||'host';const t=now();let b=attemptBuckets.get(k);if(!b||t-b.at>60000){if(attemptBuckets.size>2048)attemptBuckets.clear();b={at:t,n:0};attemptBuckets.set(k,b);}need(++b.n<=120,429,'pairing_rate_limit');}
async function route(c){const {method,p,url,req,res,store:s,device,admin,readJSON,json,serveFile,locked}=c;let m;
try{
if((m=/^\/v1\/events\/([0-9a-f-]+)\/pairings$/.exec(p))&&['GET','POST'].includes(method)){
 admin(req);need(s.one('SELECT id FROM events WHERE id=?',m[1]),404,'event_not_found');
 if(method==='GET'){json(res,200,s.all('SELECT id,kind,label,state,expires,entity_id,created,approval_failures FROM pairing_invites WHERE event_id=? ORDER BY created DESC LIMIT 100',m[1]));return true;}
 require('./event-management.cjs').active(s,m[1]);
 const b=await readJSON(req),kind=b.kind||'phone';need(['phone','station'].includes(kind),400,'invalid_pairing_kind');const perm=permissions(b.permissions),name=label(b.label),base=origin(b.origin);
 let replacement=null;
 if(b.replace_device_id!==undefined){
   need(kind==='phone'&&P.UUID.test(b.replace_device_id),400,'invalid_repair_target');
   replacement=s.one('SELECT * FROM devices WHERE id=? AND event_id=? AND is_service=0',b.replace_device_id,m[1]);
   need(replacement&&!replacement.admin_archived,404,'repair_target_unavailable');
   need(!replacement.revoked||b.reactivate===true,409,'revoke_requires_explicit_reactivation');
   need(name===replacement.label,409,'repair_label_mismatch');
 }
 if(perm.station_id)need(s.one('SELECT id FROM print_stations WHERE id=? AND event_id=? AND revoked=0',perm.station_id,m[1]),400,'station_not_in_event');
 need(s.one("SELECT count(*) n FROM pairing_invites WHERE event_id=? AND state IN ('open','claimed') AND expires>?",m[1],now()).n<32,429,'too_many_open_invitations');
 const identifier=id(),secret=crypto.randomBytes(32).toString('hex'),expires=now()+300000;
 s.run('INSERT INTO pairing_invites(id,event_id,kind,label,secret_hash,expires,permissions,station_id,created,replacement_device_id) VALUES(?,?,?,?,?,?,?,?,?,?)',identifier,m[1],kind,name,digest(secret),expires,JSON.stringify(perm),perm.station_id,now(),replacement?.id||null);
 json(res,201,{id:identifier,expires,kind,pairing_url:base+'/pair#'+identifier+'.'+secret,warning:'Staff enrollment only. Enter the code displayed on the intended device in authenticated Admin; not a guest photo QR.'});return true;
}
if(p==='/v1/pairings/claim'&&method==='POST'){
 rate(req,s);const b=await readJSON(req);need(P.UUID.test(b.id||'')&&P.UUID.test(b.client_id||'')&&P.SHA.test(b.secret||'')&&P.SHA.test(b.claim_secret||'')&&P.SHA.test(b.credential_hash||''),400,'invalid_claim');
 const inv=s.one('SELECT * FROM pairing_invites WHERE id=?',b.id);need(inv&&inv.secret_hash===digest(b.secret)&&inv.expires>now()&&!['revoked'].includes(inv.state),401,'invitation_expired_or_invalid');
 if(inv.state==='open'){const code=String(crypto.randomInt(100000,1000000));s.run("UPDATE pairing_invites SET state='claimed',claim_hash=?,credential_hash=?,client_id=?,code=? WHERE id=? AND state='open'",digest(b.claim_secret),b.credential_hash,b.client_id,code,inv.id);}
 const row=s.one('SELECT * FROM pairing_invites WHERE id=?',b.id);need(row.claim_hash===digest(b.claim_secret)&&row.client_id===b.client_id&&row.credential_hash===b.credential_hash,409,'invitation_already_claimed');
 json(res,200,{id:row.id,kind:row.kind,label:row.label,code:row.code,state:row.state,expires:row.expires,event:s.one('SELECT id,name FROM events WHERE id=?',row.event_id)});return true;
}
if((m=/^\/v1\/pairings\/([0-9a-f-]+)\/status$/.exec(p))&&method==='GET'){
 rate(req,s);const inv=s.one('SELECT * FROM pairing_invites WHERE id=?',m[1]);need(inv&&inv.claim_hash===digest(token(req)),401,'claim_auth_required');need(inv.expires+600000>now(),410,'claim_recovery_expired');need(inv.state!=='revoked',410,'invitation_revoked');
 if(inv.state!=='approved')need(inv.expires>now(),410,'invitation_expired');
 const payload={state:inv.state,code:inv.code,kind:inv.kind,event:s.one('SELECT id,name FROM events WHERE id=?',inv.event_id),label:inv.label,entity_id:inv.entity_id,permissions:obj(inv.permissions)};
 if(inv.state==='approved'){const entity=s.one(inv.kind==='station'?'SELECT revoked FROM print_stations WHERE id=?':'SELECT revoked FROM devices WHERE id=?',inv.entity_id);need(entity&&!entity.revoked,401,'credential_revoked');if(inv.kind==='station')payload.print_public_key=s.printPublicKey;}
 json(res,200,payload);return true;
}
if((m=/^\/v1\/pairings\/([0-9a-f-]+)\/(approve|revoke)$/.exec(p))&&method==='POST'){
 admin(req);const b=await readJSON(req),inv=s.one('SELECT * FROM pairing_invites WHERE id=?',m[1]);need(inv,404,'invitation_not_found');
 if(m[2]==='revoke'){s.tx(()=>{s.run("UPDATE pairing_invites SET state='revoked' WHERE id=?",inv.id);if(inv.entity_id)s.run(inv.kind==='station'?'UPDATE print_stations SET revoked=1 WHERE id=?':'UPDATE devices SET revoked=1 WHERE id=?',inv.entity_id);});json(res,200,{ok:true});return true;}
 need(inv.state==='claimed'&&inv.expires>now(),409,'claim_not_approvable');
 need(inv.approval_failures<5,429,'pairing_approval_locked');
 // Persist the failure before throwing: transaction rollback must not reset the counter.
 if(typeof b.code!=='string'||!/^\d{6}$/.test(b.code)||b.code!==inv.code){
  s.run('UPDATE pairing_invites SET approval_failures=approval_failures+1,approval_last_attempt=? WHERE id=?',now(),inv.id);
  need(false,inv.approval_failures+1>=5?429:409,inv.approval_failures+1>=5?'pairing_approval_locked':'confirmation_code_mismatch');
 }
 const entity=inv.replacement_device_id||id();
 s.tx(()=>{if(inv.replacement_device_id){
   const previous=s.one('SELECT * FROM devices WHERE id=? AND event_id=? AND is_service=0 AND admin_archived=0',entity,inv.event_id);
   need(previous,409,'repair_target_unavailable');
   s.run('UPDATE devices SET token_hash=?,revoked=0,admin_hidden=0,last_seen=NULL,heartbeat=NULL WHERE id=?',inv.credential_hash,entity);
 }else if(inv.kind==='station')s.run('INSERT INTO print_stations(id,event_id,label,token_hash) VALUES(?,?,?,?)',entity,inv.event_id,inv.label,inv.credential_hash);else{s.run('INSERT INTO devices(id,event_id,label,token_hash) VALUES(?,?,?,?)',entity,inv.event_id,inv.label,inv.credential_hash);s.run('INSERT INTO device_permissions VALUES(?,?)',entity,inv.permissions);}s.run("UPDATE pairing_invites SET state='approved',entity_id=? WHERE id=?",entity,inv.id);});
 json(res,200,{ok:true,entity_id:entity,kind:inv.kind});return true;
}
if((m=/^\/v1\/events\/([0-9a-f-]+)\/print-cache\/cleanup$/.exec(p))&&method==='POST'){
 admin(req);await readJSON(req);need(s.one('SELECT id FROM events WHERE id=?',m[1]),404,'event_not_found');
 const result=await require('./print-assets.cjs').cleanup(s,locked,{eventId:m[1]});json(res,200,{...result,usage:require('./print-assets.cjs').usage(s,m[1])});return true;
}
if((m=/^\/v1\/events\/([0-9a-f-]+)\/printing$/.exec(p))&&method==='GET'){
 admin(req);json(res,200,{print_storage:require('./print-assets.cjs').usage(s,m[1]),stations:s.all('SELECT * FROM print_stations WHERE event_id=? ORDER BY label',m[1]).map(stationPublic),jobs:s.all('SELECT * FROM print_jobs WHERE event_id=? ORDER BY created DESC LIMIT 100',m[1]).map(describeJob),devices:s.all('SELECT d.id,d.label,d.revoked,p.settings FROM devices d LEFT JOIN device_permissions p ON p.device_id=d.id WHERE d.event_id=? AND d.is_service=0',m[1]).map(d=>({id:d.id,label:d.label,revoked:!!d.revoked,permissions:obj(d.settings)}))});return true;
}
if((m=/^\/v1\/devices\/([0-9a-f-]+)\/print-permissions$/.exec(p))&&method==='POST'){
 admin(req);const dev=s.one('SELECT * FROM devices WHERE id=?',m[1]);need(dev&&!dev.revoked&&!dev.is_service,404,'device_not_found');const b=permissions(await readJSON(req));if(b.station_id)need(s.one('SELECT id FROM print_stations WHERE id=? AND event_id=? AND revoked=0',b.station_id,dev.event_id),404,'station_not_found');s.run('INSERT INTO device_permissions VALUES(?,?) ON CONFLICT(device_id) DO UPDATE SET settings=excluded.settings',dev.id,JSON.stringify(b));json(res,200,b);return true;
}
if((m=/^\/v1\/stations\/([0-9a-f-]+)\/revoke$/.exec(p))&&method==='POST'){admin(req);await readJSON(req);s.run('UPDATE print_stations SET revoked=1 WHERE id=?',m[1]);json(res,200,{ok:true});return true;}
if(p==='/v1/station/phone-invite'&&method==='POST'){
 const st=station(s,req),b=await readJSON(req),base=origin(b.origin),name=label(b.label);
 require('./event-management.cjs').active(s,st.event_id);
 need(s.one("SELECT count(*) n FROM pairing_invites WHERE station_id=? AND state IN ('open','claimed') AND expires>?",st.id,now()).n<16,429,'too_many_open_invitations');
 const identifier=id(),secret=crypto.randomBytes(32).toString('hex'),expires=now()+300000,perm=permissions({print:true,station_id:st.id,max_copies:1});
 s.run('INSERT INTO pairing_invites(id,event_id,kind,label,secret_hash,expires,permissions,station_id,created) VALUES(?,?,?,?,?,?,?,?,?)',identifier,st.event_id,'phone',name,digest(secret),expires,JSON.stringify(perm),st.id,now());
 json(res,201,{id:identifier,expires,pairing_url:base+'/pair#'+identifier+'.'+secret,note:'Web Admin must approve the matching device code.'});return true;
}
if(p==='/v1/device/print-config'&&method==='GET'){const dev=device(req),perm=devicePermissions(s,dev);json(res,200,{workflow:require('./event-workflow.cjs').settings(s,dev.event_id),permissions:perm,station:perm.station_id?s.all('SELECT * FROM print_stations WHERE id=? AND event_id=? AND revoked=0',perm.station_id,dev.event_id).map(stationPublic)[0]||null:null});return true;}
if(p==='/v1/device/print-grant'&&method==='POST'){
 const dev=device(req),b=await readJSON(req),{perm,st}=printAuth(s,dev,b.station_id);need(P.SHA.test(b.auth_hash||''),400,'invalid_lan_secret_hash');const profiles=P.profiles(JSON.parse(st.profiles));need(profiles.length&&st.endpoint&&st.certificate_hash,409,'station_not_prepared');
 const payload={v:1,event_id:dev.event_id,device_id:dev.id,station_id:st.id,auth_hash:b.auth_hash,max_copies:perm.max_copies,profiles,exp:now()+12*3600000};json(res,200,{grant:P.sign(payload,s.printPrivateKey),expires:payload.exp,station:stationPublic(st)});return true;
}
if(p==='/v1/station/config'&&method==='GET'){
 const st=station(s,req);await require('./print-assets.cjs').cleanup(s,locked);json(res,200,{station:stationPublic(st),print_storage:require('./print-assets.cjs').usage(s,st.event_id),event:s.one('SELECT id,name FROM events WHERE id=?',st.event_id),print_public_key:s.printPublicKey,revoked_devices:s.all('SELECT id FROM devices WHERE event_id=? AND revoked=1',st.event_id).map(d=>d.id),permissions:s.all('SELECT d.id,p.settings FROM devices d JOIN device_permissions p ON p.device_id=d.id WHERE d.event_id=? AND d.revoked=0',st.event_id).map(d=>({id:d.id,...obj(d.settings)}))});return true;
}
if(p==='/v1/station/heartbeat'&&method==='POST'){
 const st=station(s,req),b=await readJSON(req);need(Number.isInteger(b.revision)&&b.revision===st.revision,409,'station_settings_changed');const list=P.profiles(b.profiles),endpoint=privateEndpoint(b.endpoint);need(P.SHA.test(b.certificate_sha256||''),400,'invalid_certificate_pin');need(typeof b.paused==='boolean',400,'invalid_paused');
 const changed=JSON.stringify(list)!==st.profiles||endpoint!==st.endpoint||b.certificate_sha256!==st.certificate_hash;
 s.run('UPDATE print_stations SET profiles=?,endpoint=?,certificate_hash=?,last_seen=?,paused=?,revision=revision+? WHERE id=?',JSON.stringify(list),endpoint,b.certificate_sha256,now(),Number(b.paused),changed?1:0,st.id);json(res,200,stationPublic(s.one('SELECT * FROM print_stations WHERE id=?',st.id)));return true;
}
if(p==='/v1/device/print-jobs'&&method==='POST'){
 const dev=device(req),j=P.job(await readJSON(req)),{perm,st}=printAuth(s,dev,j.station_id);need(j.event_id===dev.event_id&&j.device_id===dev.id,403,'print_scope_mismatch');P.allowed(j,{...perm,device_id:dev.id,event_id:dev.event_id,station_id:st.id,profiles:JSON.parse(st.profiles)});await require('./print-assets.cjs').cleanup(s,locked);const row=putJob(s,j,true);json(res,201,describeJob(row));return true;
}
if((m=/^\/v1\/device\/print-jobs\/([0-9a-f-]+)(\/(chunks|complete))?$/.exec(p))){
 const dev=device(req),row=jobRow(s,m[1]);need(row&&row.device_id===dev.id&&row.event_id===dev.event_id,404,'print_job_not_found');
 if(!m[2]&&method==='GET'){json(res,200,describeJob(row));return true;}
 printAuth(s,dev,row.station_id);
 if(m[3]==='chunks'&&method==='POST'){await chunk(c,row);return true;}
 if(m[3]==='complete'&&method==='POST'){await readJSON(req);await locked('print:'+row.id,async()=>{const r=jobRow(s,row.id);if(r.asset_ready||!['awaiting_file','receiving'].includes(r.state)){json(res,200,describeJob(r));return;}const j=obj(r.spec);need(r.offset===j.bytes,409,'print_image_incomplete');const file=s.file('print-assets',r.id+'.part');need(await fileHash(file)===j.sha256,422,'print_image_checksum_mismatch');const meta=await require('sharp')(file,{limitInputPixels:60*1024*1024}).metadata().catch(()=>null);need(meta&&meta.format==='jpeg'&&meta.width>0&&meta.height>0,422,'print_jpeg_required');s.run("UPDATE print_jobs SET state='ready',asset_ready=1,updated=? WHERE id=?",now(),r.id);json(res,200,describeJob(jobRow(s,r.id)));});return true;}
}
if(p==='/v1/station/jobs'&&method==='GET'){const st=station(s,req);json(res,200,{jobs:s.all("SELECT * FROM print_jobs WHERE station_id=? AND asset_ready=1 AND state IN ('ready','receiving') ORDER BY created LIMIT 20",st.id).map(describeJob)});return true;}
if((m=/^\/v1\/station\/jobs\/([0-9a-f-]+)\/file$/.exec(p))&&method==='GET'){
 const st=station(s,req),row=jobRow(s,m[1]);need(row&&row.station_id===st.id&&row.event_id===st.event_id&&row.asset_ready&&row.offset===obj(row.spec).bytes&&['ready','receiving'].includes(row.state),404,'print_file_not_available');await locked('print:'+row.id,async()=>{const current=jobRow(s,row.id);need(current.asset_cleanup==='none'&&current.asset_ready&&['ready','receiving'].includes(current.state),404,'print_file_not_available');await serveFile(res,s.file('print-assets',row.id+'.part'),'image/jpeg');});return true;
}
if(p==='/v1/station/jobs/report'&&method==='POST'){
 const st=station(s,req),b=await readJSON(req),j=P.job(b.job);need(j.station_id===st.id&&j.event_id===st.event_id&&s.one('SELECT id FROM devices WHERE id=? AND event_id=?',j.device_id,j.event_id),403,'print_scope_mismatch');need(['receiving','ready','submitting','submitted_unconfirmed','outcome_unknown','simulated','operator_confirmed','cancelled','failed_before_submit','authorization_expired'].includes(b.state),400,'invalid_print_state');need(Number.isSafeInteger(b.revision)&&b.revision>0,400,'invalid_report_revision');
 await locked('print:'+j.id,async()=>{const row=putJob(s,j,false);
 if(b.revision>row.report_revision){
  const safe=['simulated','operator_confirmed','cancelled'];
  need(!safe.includes(row.state)||b.state===row.state,409,'terminal_print_state_conflict');
  s.run('UPDATE print_jobs SET state=?,report_revision=?,updated=?,terminal_at=CASE WHEN terminal_at=0 AND ? THEN ? ELSE terminal_at END WHERE id=?',b.state,b.revision,now(),safe.includes(b.state)?1:0,now(),row.id);
 }
 json(res,200,describeJob(jobRow(s,j.id)));});return true;
}
return false;
}catch(e){if(e instanceof P.ProtocolError)throw new HttpError(e.status,e.code);throw e;}
}
module.exports={migrate,route,permissions,devicePermissions,stationPublic,fileHash,station,putJob,describeJob};
