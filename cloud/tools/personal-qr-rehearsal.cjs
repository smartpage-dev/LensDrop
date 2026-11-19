'use strict';
// Small, resumable operator rehearsal. NOT an Android folder watcher.
// Device token is read from a local file and never saved in state or output.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {toSvg}=require('../src/qr.cjs');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
function args(){const out={photo:[]};for(let i=2;i<process.argv.length;i++){const k=process.argv[i];if(k==='--allow-loopback-http'){out.local=true;continue;}if(!['--server','--token-file','--state','--mode','--photo'].includes(k)||!process.argv[i+1])throw Error('Use --server HTTPS_ORIGIN --token-file PRIVATE_FILE --state NEW_LOCAL_DIRECTORY --mode group|single --photo FILE (repeat --photo for group).');const value=process.argv[++i];if(k==='--photo')out.photo.push(path.resolve(value));else out[k.slice(2)]=value;}return out;}
function save(file,state){const tmp=file+'.tmp';fs.writeFileSync(tmp,JSON.stringify(state,null,2)+'\n',{mode:0o600});const fd=fs.openSync(tmp,'r');try{fs.fsyncSync(fd);}finally{fs.closeSync(fd);}fs.renameSync(tmp,file);if(process.platform!=='win32'){const dir=fs.openSync(path.dirname(file),'r');try{fs.fsyncSync(dir);}finally{fs.closeSync(dir);}}}
async function shaFile(file){const h=crypto.createHash('sha256');for await(const p of fs.createReadStream(file,{highWaterMark:64*1024}))h.update(p);return h.digest('hex');}
(async()=>{
 const a=args(),kind=a.mode||'single';if(!['group','single'].includes(kind)||!a.photo.length||a.photo.length>20||kind==='single'&&a.photo.length!==1)throw Error('Choose one JPEG for single mode, or 1-20 JPEGs for a group rehearsal.');
 const url=new URL(a.server);if(url.username||url.password||url.search||url.hash||!['','/'].includes(url.pathname)||!(url.protocol==='https:'||a.local&&url.protocol==='http:'&&['localhost','127.0.0.1','[::1]'].includes(url.hostname)))throw Error('Use an HTTPS origin; HTTP is allowed only for explicit local tests.');
 const origin=url.origin,token=fs.readFileSync(a['token-file'],'utf8').trim();if(!token)throw Error('Device token file is empty.');
 const dir=path.resolve(a.state||'h4-personal-rehearsal-state');fs.mkdirSync(dir,{recursive:true,mode:0o700});const stateFile=path.join(dir,'session.json');
 async function request(route,body,raw){const r=await fetch(origin+route,{method:body===undefined?'GET':'POST',redirect:'manual',signal:AbortSignal.timeout(60000),headers:{Authorization:'Bearer '+token,'Content-Type':raw?'application/octet-stream':'application/json',...(raw?{'X-Chunk-SHA256':hash(body)}:{})},body:body===undefined?undefined:raw?body:JSON.stringify(body)});const d=await r.json().catch(()=>({error:'non_json_response'}));if(!r.ok)throw Error(d.error||'http_'+r.status);return d;}
 const identity=await request('/v1/device');if(!identity.capabilities?.personal_delivery_v1)throw Error('Cloud H4 is required.');
 const signature={origin,event_id:identity.event.id,device_id:identity.device_id,kind};let state;
 if(fs.existsSync(stateFile)){state=JSON.parse(fs.readFileSync(stateFile,'utf8'));if(JSON.stringify(state.identity)!==JSON.stringify(signature))throw Error('Saved state belongs to another event/device/mode. Use a separate directory; do not overwrite it.');if(state.files.length!==a.photo.length)throw Error('Saved photo selection is immutable. Use a new state directory for another delivery.');}
 else{state={identity:signature,delivery_id:crypto.randomUUID(),files:[]};for(const file of a.photo){const stat=fs.statSync(file);if(!stat.isFile()||stat.size<=0||stat.size>64*1024*1024||!/^.+\.jpe?g$/i.test(path.basename(file)))throw Error('Each source must be a completed JPEG no larger than 64 MiB.');state.files.push({client_id:crypto.randomUUID(),filename:path.basename(file),size:stat.size,sha256:await shaFile(file)});}save(stateFile,state);}
 for(let i=0;i<a.photo.length;i++)if(await shaFile(a.photo[i])!==state.files[i].sha256)throw Error('Source changed since this selection was saved. Do not reuse this delivery.');
 const d=await request('/v1/device/deliveries',{id:state.delivery_id,kind,label:kind==='group'?'Rehearsal group':'Rehearsal selected photo'});if(d.state!=='open')throw Error('This delivery was cancelled; do not repurpose its QR.');state.guest_path=d.guest_path;save(stateFile,state);fs.writeFileSync(path.join(dir,'qr.svg'),toSvg(origin+d.guest_path),{mode:0o600});
 console.log('Personal link (keep private): '+origin+d.guest_path);console.log('QR SVG saved in your local state directory. The page can wait for upload/approval.');
 for(let i=0;i<a.photo.length;i++){
  const item=state.files[i];let u=await request('/v1/uploads',{...item,receipt:undefined,delivery_id:state.delivery_id});
  if(u.delivery_id!==state.delivery_id||u.sha256!==item.sha256||u.size!==item.size)throw Error('Server receipt identity mismatch.');
  const fd=fs.openSync(a.photo[i],'r');try{while(u.state!=='complete'&&u.offset<item.size){if(!Number.isSafeInteger(u.offset)||u.offset<0||u.offset%u.chunk_size!==0)throw Error('Invalid server offset.');const n=Math.min(u.chunk_size,item.size-u.offset),part=Buffer.alloc(n);let read=0;while(read<n){const got=fs.readSync(fd,part,read,n-read,u.offset+read);if(!got)throw Error('Source truncated while reading.');read+=got;}u=await request('/v1/uploads/'+u.upload_id+'/chunks?offset='+u.offset,part,true);}}finally{fs.closeSync(fd);}
  if(u.state!=='complete')u=await request('/v1/uploads/'+u.upload_id+'/complete',{});
  if(u.receipt?.sha256!==item.sha256||u.receipt?.bytes!==item.size)throw Error('Final checksum/size receipt mismatch.');state.files[i].receipt=u.receipt;save(stateFile,state);console.log('Verified '+(i+1)+'/'+a.photo.length+'.');
 }
 console.log('Upload complete. Default personal policy requires approval in Admin > Personal Delivery. Gallery/slideshow remain separate. Re-running with the same state resumes without a new QR.');
})().catch(e=>{console.error('Rehearsal stopped: '+e.message+'. Keep state and source photos; after a transient error, rerun the same command.');process.exitCode=1;});
