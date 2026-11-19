'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const work=path.resolve(process.env.LENSDROP_UI_WORKDIR||'.ui-rehearsal');fs.mkdirSync(work,{recursive:true});
const root=path.resolve(process.env.LENSDROP_CLOUD_DIR||path.join(__dirname,'../..'));const {createApp,CHUNK}=require(root+'/src/server.cjs');
const key='h3-synthetic-browser-test-admin-key-not-production';
const app=createApp({dataDir:path.join(work,'ui-server-data'),adminKey:key,freeReserve:0});
(async()=>{await new Promise(r=>app.server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+app.server.address().port;
 const call=async(p,body,token=key)=>{const r=await fetch(base+p,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();if(!r.ok)throw Error(JSON.stringify(data));return data;};
 const e=await call('/v1/events',{name:'Luce Studio / Event Rehearsal',policy:'live'}),d=await call('/v1/events/'+e.id+'/devices',{label:'Photographer A / Synthetic test client'});const photos=[];
 for(let i=1;i<=4;i++){const bytes=fs.readFileSync(path.join(work,'ui-fixtures',`fixture_${i}.jpg`));const hash=b=>crypto.createHash('sha256').update(b).digest('hex');const u=await call('/v1/uploads',{client_id:crypto.randomUUID(),filename:`SYNTHETIC_${i}.jpg`,size:bytes.length,sha256:hash(bytes)},d.token);for(let off=0;off<bytes.length;off+=CHUNK){const part=bytes.subarray(off,off+CHUNK);const r=await fetch(base+`/v1/uploads/${u.upload_id}/chunks?offset=${off}`,{method:'POST',headers:{Authorization:'Bearer '+d.token,'X-Chunk-SHA256':hash(part)},body:part});if(!r.ok)throw Error(await r.text());}photos.push((await call('/v1/uploads/'+u.upload_id+'/complete',{},d.token)).receipt.photo_id);}
 await call('/v1/device/heartbeat',{received:4,uploaded:4,queued:0,blocked:0,last_received:Date.now()},d.token);
 await fetch(base+`/v1/events/${e.id}/logo`,{method:'POST',headers:{Authorization:'Bearer '+key,'Content-Type':'image/png'},body:fs.readFileSync(path.join(work,'ui-fixtures/logo.png'))});
 const cfg=await call(`/v1/events/${e.id}/presentation`,{enabled:true,settings:{interval_seconds:4,poll_seconds:5,show_gallery_qr:true}});
 fs.writeFileSync(path.join(work,'ui-config.json'),JSON.stringify({base,key,event:e,device:d,photos,presentation:cfg},null,2));console.log(base);
})();
for(const sig of ['SIGINT','SIGTERM'])process.on(sig,()=>app.close().then(()=>process.exit()));
