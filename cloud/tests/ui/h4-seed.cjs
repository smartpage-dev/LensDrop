'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),sharp=require('sharp');
const {createApp}=require('../../src/server.cjs');
const root=process.env.H4_UI_WORKDIR,key='h4-ui-only-synthetic-key-not-for-production-use';
const app=createApp({dataDir:path.join(root,'data'),adminKey:key,freeReserve:0});
(async()=>{
 await new Promise(r=>app.server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+app.server.address().port;
 const call=async(p,b,token=key)=>{let r=await fetch(base+p,{method:b===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:b===undefined?undefined:JSON.stringify(b)});const d=await r.json();if(!r.ok)throw Error(JSON.stringify(d));return d;};
 const event=await call('/v1/events',{name:'Luce Studio / H4 controlled rehearsal',policy:'live'}),device=await call('/v1/events/'+event.id+'/devices',{label:'Android source rehearsal'});
 const group=await call('/v1/device/deliveries',{id:crypto.randomUUID(),kind:'group',label:'Group 001'},device.token),single=await call('/v1/device/deliveries',{id:crypto.randomUUID(),kind:'single',label:'Photo 001'},device.token);let photos=[];
 for(const [i,color] of ['#365f70','#b48656'].entries()){
  const svg=Buffer.from(`<svg width="1280" height="850"><rect width="1280" height="850" fill="${color}"/><circle cx="900" cy="280" r="120" fill="#e5dcc3"/><text x="60" y="680" fill="white" font-family="sans-serif" font-size="36">SYNTHETIC TEST IMAGE ${i+1}</text><text x="60" y="740" fill="white" font-family="sans-serif" font-size="24">Not a real event / H4 browser verification</text></svg>`);
  const bytes=await sharp(svg).jpeg().toBuffer(),sha=crypto.createHash('sha256').update(bytes).digest('hex');
  const u=await call('/v1/uploads',{client_id:crypto.randomUUID(),filename:'SYNTHETIC_'+i+'.jpg',size:bytes.length,sha256:sha,delivery_id:group.id},device.token);
  const r=await fetch(base+'/v1/uploads/'+u.upload_id+'/chunks?offset=0',{method:'POST',headers:{Authorization:'Bearer '+device.token,'X-Chunk-SHA256':sha},body:bytes});if(!r.ok)throw Error(await r.text());photos.push((await call('/v1/uploads/'+u.upload_id+'/complete',{},device.token)).receipt.photo_id);
 }
 const logo=await sharp({create:{width:240,height:80,channels:3,background:'#215c51'}}).png().toBuffer();await fetch(base+'/v1/events/'+event.id+'/logo',{method:'POST',headers:{Authorization:'Bearer '+key,'Content-Type':'image/png'},body:logo});
 fs.writeFileSync(path.join(root,'config.json'),JSON.stringify({base,key,event,device,group,single,photos}));console.log('READY '+base);
})();
for(const signal of ['SIGTERM','SIGINT'])process.on(signal,()=>app.close().then(()=>process.exit(0)));
