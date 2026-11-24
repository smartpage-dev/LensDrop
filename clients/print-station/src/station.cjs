'use strict';
const http=require('node:http'),https=require('node:https'),fs=require('node:fs'),fsp=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {spawn,execFileSync,execFile}=require('node:child_process');const execAsync=require('node:util').promisify(execFile);const Profiles=require('./printer-profiles.cjs');const {Ledger}=require('./ledger.cjs'),P=require('./print-protocol.cjs');
async function jsonBody(req,limit=65536){P.check((req.headers['content-type']||'').split(';')[0]==='application/json','json_required',415);let bytes=0,parts=[];for await(const b of req){bytes+=b.length;P.check(bytes<=limit,'request_too_large',413);parts.push(b);}let x;try{x=JSON.parse(Buffer.concat(parts));}catch{throw new P.ProtocolError('invalid_json');}P.check(x&&typeof x==='object'&&!Array.isArray(x),'json_object_required');return x;}
function send(res,status,obj){res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(obj));}
async function hashFile(f){const h=crypto.createHash('sha256');for await(const b of fs.createReadStream(f))h.update(b);return h.digest('hex');}
function saveFile(file,obj){const tmp=file+'.new';fs.writeFileSync(tmp,JSON.stringify(obj,null,2),{mode:0o600});const fd=fs.openSync(tmp,'r');fs.fsyncSync(fd);fs.closeSync(fd);fs.renameSync(tmp,file);if(process.platform!=='win32'){const d=fs.openSync(path.dirname(file),'r');fs.fsyncSync(d);fs.closeSync(d);}}
async function cloudCall(cfg,route,body,credential){P.check(cfg.cloud&&cfg.cloud.startsWith('https://')||cfg.testLoopback&&/^http:\/\/127\.0\.0\.1:\d+$/.test(cfg.cloud),'cloud_https_required');const r=await fetch(cfg.cloud+route,{method:body===undefined?'GET':'POST',redirect:'error',signal:AbortSignal.timeout(20000),headers:{Authorization:'Bearer '+(credential===undefined?cfg.token:credential),'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.text();P.check(Buffer.byteLength(data)<2*1024*1024,'cloud_response_too_large',502);let j;try{j=JSON.parse(data);}catch{throw new P.ProtocolError('invalid_cloud_response',502);}if(!r.ok)throw new P.ProtocolError(j.error||'cloud_error',r.status);return j;}
function createStation(options){
 const root=options.root,ledger=new Ledger(root),settingsFile=path.join(root,'settings.json');
 let cfg=options.config|| (fs.existsSync(settingsFile)?JSON.parse(fs.readFileSync(settingsFile)):{});
 cfg={paused:true,simulation:true,profiles:[],cloudRevision:1,...cfg};
 cfg.profileRevisions=Object.assign(Object.create(null),cfg.profileRevisions||{});
 for(const p of cfg.profiles)cfg.profileRevisions[p.id]=Math.max(cfg.profileRevisions[p.id]||0,p.revision);
 for(const r of ledger.all('SELECT profile FROM jobs')){const p=JSON.parse(r.profile);cfg.profileRevisions[p.id]=Math.max(cfg.profileRevisions[p.id]||0,p.revision);}
 const native=options.native||path.join(__dirname,'..','LensDropStation.exe');
 const uiSecret=crypto.randomBytes(32).toString('hex'),locks=new Set();let active=0,spooling=false,syncing=false,lastCloud='Not connected',closed=false,revoked=false;
 function unprotect(value){return execFileSync(native,['--unprotect'],{input:value,encoding:'utf8',timeout:5000}).trim();}
 if(cfg.protected_token){try{cfg.token=unprotect(cfg.protected_token);}catch{lastCloud='Credential unlock failed; same Windows user required';}}
 if(cfg.protected_pairing){try{cfg.pending=JSON.parse(unprotect(cfg.protected_pairing));}catch{lastCloud='Pairing unlock failed';}}
 function persist(){let out={...cfg};delete out.token;delete out.pending;delete out.testLoopback;
 if(options.save){options.save(out);return;}
 if(cfg.token)out.protected_token=execFileSync(native,['--protect'],{input:cfg.token,encoding:'utf8',timeout:5000}).trim();
 if(cfg.pending)out.protected_pairing=execFileSync(native,['--protect'],{input:JSON.stringify(cfg.pending),encoding:'utf8',timeout:5000}).trim();else delete out.protected_pairing;
 saveFile(settingsFile,out);
 }
 const pem=options.cert||fs.readFileSync(path.join(root,'station-cert.pem')),key=options.key||fs.readFileSync(path.join(root,'station-key.pem'));const cert=new crypto.X509Certificate(pem);const certHash=P.hash(cert.raw);
 function profileFor(j){const x=cfg.profiles.find(p=>p.id===j.profile_id&&p.revision===j.profile_revision);P.check(x,'profile_changed_reselect',409);return x;}
 function authorized(req,job){P.check(cfg.station_id&&cfg.public_key&&!revoked,'station_not_paired',503);const grant=P.verify(req.headers['x-lensdrop-grant'],cfg.public_key);const secret=/^Bearer (.+)$/.exec(req.headers.authorization||'')?.[1]||'';P.check(P.SHA.test(secret)&&P.hash(secret)===grant.auth_hash,'lan_auth_required',401);P.check(grant.station_id===cfg.station_id&&grant.event_id===cfg.event_id,'station_scope_mismatch',403);P.check(!(cfg.revoked_devices||[]).includes(grant.device_id),'device_revoked',403);if(cfg.permissions){const perm=cfg.permissions.find(d=>d.id===grant.device_id);P.check(perm?.print&&perm.station_id===cfg.station_id,'printing_not_authorized',403);P.check(!job||job.copies<=perm.max_copies,'copies_not_permitted',403);}
 if(job){P.allowed(job,grant);profileFor(job);}return grant;}
 async function lock(id,fn){P.check(!locks.has(id),'print_job_busy',409);P.check(active<4,'station_busy',429);locks.add(id);active++;try{return await fn();}finally{locks.delete(id);active--;}}
 async function receive(req,url,row,res){return lock(row.id,async()=>{row=ledger.get(row.id);P.check(row.state==='receiving','print_asset_closed',409);const j=JSON.parse(row.spec),offset=Number(url.searchParams.get('offset')),length=Number(req.headers['content-length']);P.check(Number.isSafeInteger(offset)&&offset===row.offset,'print_offset_mismatch',409);P.check((req.headers['content-type']||'').split(';')[0]==='application/octet-stream','binary_required',415);P.check(Number.isInteger(length)&&length>0&&length<=P.CHUNK&&offset+length<=j.bytes,'invalid_chunk_size',413);const stat=fs.statfsSync(root);P.check(stat.bavail*stat.bsize>length+(options.freeReserve??256*1024**2),'station_storage_low',507);let n=0,parts=[];for await(const b of req){n+=b.length;P.check(n<=length,'invalid_chunk_size',413);parts.push(b);}const data=Buffer.concat(parts);P.check(n===length&&P.hash(data)===req.headers['x-chunk-sha256'],'chunk_checksum_mismatch',422);const file=ledger.asset(row.id);let fh;try{fh=await fsp.open(file,fs.existsSync(file)?'r+':'w',0o600);P.check((await fh.stat()).size>=offset,'station_asset_lost',409);await fh.truncate(offset);await fh.write(data,0,data.length,offset);await fh.sync();}finally{await fh?.close();}send(res,200,ledger.public(ledger.offset(row.id,offset+length)));});}
 async function finish(row){const j=JSON.parse(row.spec);P.check(row.offset===j.bytes,'print_image_incomplete',409);P.check(await hashFile(ledger.asset(row.id))===j.sha256,'print_image_checksum_mismatch',422);const fd=fs.openSync(ledger.asset(row.id),'r'),header=Buffer.alloc(3);fs.readSync(fd,header,0,3,0);fs.closeSync(fd);P.check(header.equals(Buffer.from([255,216,255])),'print_jpeg_required',422);return ledger.state(row.id,'ready');}
 async function localRoute(req,res){res.setHeader('Cache-Control','no-store');const u=new URL(req.url,'https://station'),p=u.pathname;let m;
 if(p==='/v1/info'&&req.method==='GET'){authorized(req);return send(res,200,{station_id:cfg.station_id,event_id:cfg.event_id,label:cfg.label,paused:cfg.paused,simulation:cfg.simulation,profiles:P.profiles(cfg.profiles)});}
 if(p==='/v1/print/jobs'&&req.method==='POST'){const j=P.job(await jsonBody(req)),g=authorized(req,j);return send(res,201,ledger.public(ledger.put(j,{...profileFor(j),simulation:cfg.simulation},g.exp)));}
 if((m=/^\/v1\/print\/jobs\/([0-9a-f-]+)(\/(chunks|complete))?$/.exec(p))){const row=ledger.get(m[1]);P.check(row,'print_job_not_found',404);const j=JSON.parse(row.spec),g=authorized(req);P.check(g.device_id===j.device_id&&g.event_id===j.event_id&&g.station_id===j.station_id,'print_scope_mismatch',403);
 if(!m[2]&&req.method==='GET'){if(g.exp>row.expires&&g.profiles.some(p=>p.id===j.profile_id&&p.revision===j.profile_revision)&&j.copies<=g.max_copies)ledger.db.prepare('UPDATE jobs SET expires=? WHERE id=?').run(g.exp,row.id);return send(res,200,ledger.public(row));}
 if(m[3]==='chunks'&&req.method==='POST'){P.allowed(j,g);return receive(req,u,row,res);}
 if(m[3]==='complete'&&req.method==='POST'){P.allowed(j,g);await jsonBody(req);return lock(row.id,async()=>{const r=ledger.get(row.id);return send(res,200,ledger.public(r.state==='receiving'?await finish(r):r));});}}
 throw new P.ProtocolError('not_found',404);
 }
 const handle=fn=>(req,res)=>fn(req,res).catch(e=>{if(res.headersSent){res.destroy();return;}send(res,e.status||500,{error:e instanceof P.ProtocolError?e.code:'station_error'});});
 const lan=https.createServer({key,cert:pem,minVersion:'TLSv1.2'},handle(localRoute));lan.requestTimeout=60000;lan.headersTimeout=10000;
 const publicDir=path.join(__dirname,'..','public');
 async function uiRoute(req,res){const u=new URL(req.url,'http://localhost');const p=u.pathname;
 res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; img-src 'self' blob:; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");res.setHeader('X-Frame-Options','DENY');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('X-Content-Type-Options','nosniff');
 if(req.method==='GET'&&['/','/app.js','/style.css','/qr.js','/staff-desk.js','/staff-desk.css','/operations-shell.js'].includes(p)){const file=p==='/'?'index.html':p.slice(1);res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html','Cache-Control':'no-store'});return res.end(fs.readFileSync(path.join(publicDir,file)));}
 P.check(req.headers['x-station-ui']===uiSecret,'local_ui_auth_required',401);
 if(/^\/api\/staff\/(?:config|photos|lookup|orders|assign|collect|cancel-prepared|groups(?:\/[0-9a-f-]+\/members)?|photo\/[0-9a-f-]+\/(?:thumb|delivery))$/.test(p)){
  P.check(cfg.token&&!revoked,'station_not_paired',403);
  const remote='/v1/station/staff/'+p.slice('/api/staff/'.length)+u.search;
  if(/\/photo\//.test(p)&&req.method==='GET'){
   const r=await fetch(cfg.cloud+remote,{headers:{Authorization:'Bearer '+cfg.token},redirect:'error',signal:AbortSignal.timeout(20000)});
   if(!r.ok){let err;try{err=await r.json();}catch{}throw new P.ProtocolError(err?.error||'staff_preview_unavailable',r.status);}
   let bytes=0;res.writeHead(200,{'Content-Type':'image/jpeg','Cache-Control':'no-store'});const {Readable,Transform}=require('node:stream');const cap=new Transform({transform(b,enc,done){bytes+=b.length;done(bytes>12*1024*1024?new P.ProtocolError('preview_too_large',413):null,b);}});await require('node:stream/promises').pipeline(Readable.fromWeb(r.body),cap,res);return;
  }
  P.check(['GET','POST'].includes(req.method),'method_not_allowed',405);
  const body=req.method==='POST'?await jsonBody(req):undefined;
  return send(res,200,await cloudCall(cfg,remote,body));
 }
 if(p==='/api/status'&&req.method==='GET')return send(res,200,{station_id:cfg.station_id,event_id:cfg.event_id,label:cfg.label,cloud:cfg.cloud,endpoint:cfg.endpoint,certificate_sha256:certHash,paused:cfg.paused,simulation:cfg.simulation,profiles:cfg.profiles,profile_revisions:cfg.profileRevisions,connection:lastCloud,revoked,cloud_seen:cfg.cloud_seen,paired:!!cfg.token,pairing:cfg.pending?{code:cfg.pending.code,kind:cfg.pending.kind}:null,jobs:ledger.all('SELECT * FROM jobs ORDER BY created DESC LIMIT 100').map(r=>ledger.public(r))});
 if(p==='/api/printer-presets'&&req.method==='GET')return send(res,200,{presets:Profiles.PRESETS});
 if(p==='/api/capture-printer'&&req.method==='POST'){const b=await jsonBody(req);P.check(process.platform==='win32','windows_printers_only',409);P.check(typeof b.printer==='string'&&b.printer.length>0&&b.printer.length<=256,'invalid_printer');P.check(cfg.paused&&!spooling,'pause_before_capturing_driver',409);P.check(!ledger.all("SELECT profile FROM jobs WHERE state IN ('receiving','ready','submitting','outcome_unknown','submitted_unconfirmed','failed_before_submit')").some(r=>!JSON.parse(r.profile).driver_snapshot),'resolve_legacy_jobs_before_driver_change',409);const out=await execAsync(native,['--capture-settings',b.printer],{encoding:'utf8',timeout:30000,maxBuffer:262144,windowsHide:true});const capture=JSON.parse(out.stdout);return send(res,200,Profiles.snapshot(capture,b.printer));}
 if(p==='/api/phone-invite'&&req.method==='POST'){const b=await jsonBody(req);return send(res,201,await cloudCall(cfg,'/v1/station/phone-invite',{origin:cfg.cloud,label:b.label}));}
 if(p==='/api/printers'&&req.method==='GET'){P.check(process.platform==='win32','windows_printers_only',409);let value=JSON.parse(execFileSync(native,['--printers'],{encoding:'utf8',timeout:15000}));return send(res,200,value);}
 if(p==='/api/pair'&&req.method==='POST'){const b=await jsonBody(req);P.check(!ledger.all("SELECT id FROM jobs WHERE state IN ('receiving','ready','submitting','outcome_unknown','submitted_unconfirmed') LIMIT 1").length,'resolve_pending_prints_before_pairing',409);P.check(!cfg.station_id,'station_already_paired',409);const u=new URL(b.url),parts=u.hash.slice(1).split('.');P.check(u.protocol==='https:'&&u.pathname==='/pair'&&!u.username&&!u.password&&P.UUID.test(parts[0])&&P.SHA.test(parts[1]),'invalid_staff_invitation');
 const credential=crypto.randomBytes(32).toString('hex'),secret=crypto.randomBytes(32).toString('hex');cfg.cloud=u.origin;cfg.pending={id:parts[0],secret:parts[1],claim_secret:secret,credential,client_id:crypto.randomUUID()};persist();return claimPending(res);}
 if(p==='/api/pair/retry'&&req.method==='POST'){await jsonBody(req);P.check(cfg.pending,'no_pending_pairing',409);return claimPending(res);}
 if(p==='/api/pair/check'&&req.method==='POST'){await jsonBody(req);P.check(cfg.pending,'no_pending_pairing',409);const a=await cloudCall(cfg,'/v1/pairings/'+cfg.pending.id+'/status',undefined,cfg.pending.claim_secret);P.check(a.kind==='station','phone_invitation_not_station',409);if(a.state==='approved'){cfg.token=cfg.pending.credential;cfg.station_id=a.entity_id;cfg.event_id=a.event.id;cfg.label=a.label;cfg.public_key=a.print_public_key;delete cfg.pending;persist();}return send(res,200,a);}
 if(p==='/api/settings'&&req.method==='POST'){const b=await jsonBody(req,1048576);P.check(typeof b.paused==='boolean'&&typeof b.simulation==='boolean','invalid_settings');const u=new URL(b.endpoint);P.check(u.protocol==='https:'&&require('node:net').isIP(u.hostname)===4&&/^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(u.hostname)&&!u.username&&!u.password&&!u.search&&!u.hash&&u.pathname==='/'&&u.port==='54173','private_ipv4_port_54173_required');P.check(Array.isArray(b.profiles),'invalid_profiles');const safe=Profiles.normalize(b.profiles,cfg.profiles);for(const p of safe)if(!cfg.profiles.some(x=>x.id===p.id))P.check(p.revision>(cfg.profileRevisions[p.id]||0),'retired_profile_revision_must_increase',409);
 if(!b.simulation)P.check(process.platform==='win32'&&fs.existsSync(native),'native_printer_unavailable',409);cfg.endpoint=u.origin;cfg.paused=b.paused;cfg.simulation=b.simulation;cfg.profiles=safe;for(const p of safe)cfg.profileRevisions[p.id]=Math.max(cfg.profileRevisions[p.id]||0,p.revision);persist();return send(res,200,{ok:true});}
 if(p==='/api/job-action'&&req.method==='POST'){const b=await jsonBody(req),r=ledger.get(b.id);P.check(r,'job_not_found',404);if(b.action==='confirm'){P.check(['submitted_unconfirmed','outcome_unknown'].includes(r.state),'job_not_confirmable',409);ledger.state(r.id,'operator_confirmed');}else if(b.action==='cancel'){P.check(['receiving','ready','failed_before_submit','authorization_expired'].includes(r.state),'cannot_cancel_submitted_print',409);ledger.state(r.id,'cancelled');}else if(b.action==='retry'){P.check(['failed_before_submit','authorization_expired'].includes(r.state),'manual_review_required',409);
 const j=JSON.parse(r.spec);P.check(r.expires>Date.now(),'refresh_authorization_before_retry',409);profileFor(j);
 P.check(!(cfg.revoked_devices||[]).includes(j.device_id),'device_revoked',403);
 if(cfg.permissions){const perm=cfg.permissions.find(x=>x.id===j.device_id);P.check(perm?.print&&perm.station_id===cfg.station_id&&j.copies<=perm.max_copies,'printing_not_authorized',403);}ledger.state(r.id,'ready');}else throw new P.ProtocolError('invalid_action');return send(res,200,{ok:true});}
 if(p==='/api/cleanup'&&req.method==='POST'){await jsonBody(req);let n=0;for(const r of ledger.all("SELECT * FROM jobs WHERE state IN ('simulated','operator_confirmed','cancelled') AND reported>=revision")){await fsp.rm(ledger.asset(r.id),{force:true});n++;}return send(res,200,{removed_files:n,note:'Durable job identities were retained.'});}
 if(p==='/api/sync'&&req.method==='POST'){await jsonBody(req);await sync();return send(res,200,{ok:true,connection:lastCloud});}
 throw new P.ProtocolError('not_found',404);
 }
 async function claimPending(res){const v=cfg.pending,a=await cloudCall(cfg,'/v1/pairings/claim',{id:v.id,secret:v.secret,claim_secret:v.claim_secret,credential_hash:P.hash(v.credential),client_id:v.client_id},'');cfg.pending.code=a.code;cfg.pending.kind=a.kind;persist();P.check(a.kind==='station','phone_invitation_not_station',409);send(res,200,a);}
 const ui=http.createServer(handle(uiRoute));ui.requestTimeout=60000;ui.headersTimeout=10000;
 async function spool(){if(closed||cfg.paused||spooling||revoked)return;spooling=true;try{const r=ledger.all("SELECT * FROM jobs WHERE state='ready' ORDER BY created LIMIT 1")[0];if(!r)return;const j=JSON.parse(r.spec);if(r.expires<=Date.now()){ledger.state(j.id,'authorization_expired','refresh_permission_then_operator_retry');lastCloud='A print authorization expired; other ready jobs continue.';return;}if((cfg.revoked_devices||[]).includes(j.device_id)){ledger.state(j.id,'failed_before_submit','device_revoked');return;}if(cfg.permissions){const p=cfg.permissions.find(x=>x.id===j.device_id);if(!p?.print||p.station_id!==cfg.station_id||j.copies>p.max_copies){ledger.state(j.id,'failed_before_submit','printing_not_authorized');return;}}
 try{profileFor(j);}catch{ledger.state(j.id,'failed_before_submit','profile_changed_reselect');return;}
 try{P.check(fs.statSync(ledger.asset(j.id)).size===j.bytes&&await hashFile(ledger.asset(j.id))===j.sha256,'local_image_corrupt');}catch{ledger.state(j.id,'failed_before_submit');return;}
 if(closed||cfg.paused||revoked||ledger.get(j.id).state!=='ready')return;
 if(JSON.parse(r.profile).simulation){ledger.state(j.id,'simulated');return;}
 // Commit BEFORE calling the driver; a crash after this line becomes unknown.
 ledger.state(j.id,'submitting');
 const descriptor={id:j.id,file:ledger.asset(j.id),copies:j.copies,profile:JSON.parse(r.profile)};
 const file=path.join(root,'native-job-'+j.id+'.json');let child=null;
 try{
  const work=options.print?Promise.resolve().then(()=>options.print(descriptor)):(()=>{
   saveFile(file,descriptor);
   return new Promise((resolve,reject)=>{child=spawn(native,['--print',file],{windowsHide:true,stdio:'ignore'});
    child.once('error',e=>reject(Object.assign(e,{beforeSubmit:true})));
    child.once('exit',code=>code===0?resolve():reject(Object.assign(Error('native_print_uncertain'),{beforeSubmit:code===20})));});
  })();
  await require('./submission-watchdog.cjs').bounded(work,options.printTimeoutMs??180000,()=>{try{child?.kill('SIGKILL');}catch{}});
  if(ledger.get(j.id).state==='submitting')ledger.state(j.id,'submitted_unconfirmed');
 }catch(e){
  if(ledger.get(j.id).state==='submitting')ledger.state(j.id,e.beforeSubmit?'failed_before_submit':'outcome_unknown',e.code==='native_print_timeout'?'native_print_timeout':'native_submission_needs_review');
  if(e.code==='native_print_timeout'){cfg.paused=true;lastCloud='Native print timed out. Queue paused; verify paper before any retry.';persist();}
 }finally{await fsp.rm(file,{force:true});}

 }finally{spooling=false;}}
 async function sync(){if(syncing||closed||!cfg.token)return;syncing=true;try{
 const info=await cloudCall(cfg,'/v1/station/config');P.check(info.station.id===cfg.station_id&&info.station.event_id===cfg.event_id,'cloud_identity_changed',409);P.check(info.print_public_key===cfg.public_key,'cloud_signing_identity_changed',409);revoked=false;cfg.revoked_devices=info.revoked_devices;cfg.permissions=info.permissions;cfg.cloud_seen=Date.now();cfg.cloudRevision=info.station.revision;
 if(cfg.endpoint){const hb=await cloudCall(cfg,'/v1/station/heartbeat',{revision:cfg.cloudRevision,profiles:P.profiles(cfg.profiles),endpoint:cfg.endpoint,certificate_sha256:certHash,paused:cfg.paused});cfg.cloudRevision=hb.revision;}
 // Renew only the expiry of ready jobs whose original profile and current permission still match.
 for(const ready of ledger.all("SELECT * FROM jobs WHERE state IN ('ready','authorization_expired')")){const j=JSON.parse(ready.spec),p=cfg.permissions.find(x=>x.id===j.device_id);if(p?.print&&p.station_id===cfg.station_id&&j.copies<=p.max_copies&&cfg.profiles.some(x=>x.id===j.profile_id&&x.revision===j.profile_revision)&&!cfg.revoked_devices.includes(j.device_id))ledger.db.prepare('UPDATE jobs SET expires=? WHERE id=?').run(Date.now()+12*3600000,j.id);}
 persist();
 // Sync our ledger before fetching the queue. A LAN receipt suppresses stale cloud delivery.
 let reportFailures=0,fileFailures=0;
 for(const r of ledger.all('SELECT * FROM jobs WHERE reported<revision AND report_next<=? ORDER BY updated LIMIT 50',Date.now())){
  try{await cloudCall(cfg,'/v1/station/jobs/report',{job:JSON.parse(r.spec),state:r.state,revision:r.revision});ledger.markReported(r.id,r.revision);}
  catch(e){if(e.status===401||e.code==='cloud_identity_changed'||e.code==='cloud_signing_identity_changed')throw e;ledger.reportFailed(r.id,e.code);reportFailures++;}
 }
 if(!cfg.paused){const list=await cloudCall(cfg,'/v1/station/jobs');for(const remote of list.jobs){const spec=Object.fromEntries(['id','event_id','device_id','station_id','sha256','bytes','profile_id','profile_revision','copies'].map(k=>[k,remote[k]]));const perm=cfg.permissions.find(x=>x.id===spec.device_id);if(!perm?.print||perm.station_id!==cfg.station_id)continue;
 let row=ledger.get(spec.id);if(row&&row.state!=='receiving')continue;
 try{P.allowed(spec,{...perm,device_id:spec.device_id,event_id:cfg.event_id,station_id:cfg.station_id,profiles:P.profiles(cfg.profiles)});row=ledger.put(spec,{...profileFor(spec),simulation:cfg.simulation},Date.now()+12*3600000);await lock(spec.id,async()=>{const r=await fetch(cfg.cloud+'/v1/station/jobs/'+spec.id+'/file',{headers:{Authorization:'Bearer '+cfg.token},redirect:'error',signal:AbortSignal.timeout(90000)});P.check(r.ok,'cloud_print_file_unavailable',r.status);const tmp=ledger.asset(spec.id)+'.cloud';let fh,n=0;try{fh=await fsp.open(tmp,'w',0o600);const h=crypto.createHash('sha256');for await(const b of r.body){n+=b.length;P.check(n<=spec.bytes,'cloud_asset_too_large');h.update(b);await fh.write(b);}P.check(n===spec.bytes&&h.digest('hex')===spec.sha256,'cloud_asset_checksum_mismatch');await fh.sync();await fh.close();fh=null;await fsp.rename(tmp,ledger.asset(spec.id));row=ledger.offset(spec.id,n);await finish(row);}finally{await fh?.close();await fsp.rm(tmp,{force:true});}});}catch(e){if(e.status===401)throw e;fileFailures++;lastCloud=e.code||'cloud_file_retry';}
 }}const pending=ledger.all('SELECT count(*) n FROM jobs WHERE reported<revision')[0].n;lastCloud=pending||fileFailures?'Connected; '+pending+' receipt(s) pending, '+fileFailures+' file retry/review(s)':'Connected';
 }catch(e){lastCloud=e.code||'Cloud unavailable; local queue retained';if(e.status===401||e.code==='cloud_signing_identity_changed'||e.code==='cloud_identity_changed'){revoked=true;cfg.paused=true;persist();}}
 finally{syncing=false;}}
 let timer;
 return {lan,ui,ledger,get config(){return cfg;},uiSecret,certificateHash:certHash,spool,sync,start:async()=>{await new Promise(r=>lan.listen(options.lanPort??54173,options.lanHost||'0.0.0.0',r));await new Promise(r=>ui.listen(options.uiPort??54172,'127.0.0.1',r));if(!options.noTimers)timer=setInterval(()=>{spool().catch(()=>{});if(Date.now()-(cfg.last_sync_attempt||0)>5000){cfg.last_sync_attempt=Date.now();sync().catch(()=>{});}},1000);return {ui:'http://127.0.0.1:'+ui.address().port+'/#'+uiSecret};},close:async()=>{closed=true;clearInterval(timer);while(spooling||syncing||active)await new Promise(r=>setTimeout(r,20));await Promise.all([new Promise(r=>lan.close(r)),new Promise(r=>ui.close(r))]);ledger.close();}};
}
module.exports={createStation,jsonBody,hashFile,saveFile};
