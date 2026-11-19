'use strict';
const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const os=require('node:os');const path=require('node:path');const {spawn}=require('node:child_process');const crypto=require('node:crypto');const sharp=require('sharp');
const key='kill-test-key-not-for-real-deployment-123456';
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
for(const point of ['chunk_renamed','originals_renamed','photo_committed'])test(`SIGKILL at ${point}: restart reconciles without lost or duplicate photo`,{skip:process.platform==='win32'},async()=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'lensdrop-kill-'));let child,base,exited;
  async function launch(fault=''){
    child=spawn(process.execPath,[path.join(__dirname,'crash-worker.cjs')],{env:{...process.env,TEST_DATA:root,TEST_KEY:key,TEST_FAULT:fault},stdio:['ignore','pipe','pipe']});
    exited=new Promise(r=>child.once('exit',r));
    base=await new Promise((resolve,reject)=>{let log='';const timer=setTimeout(()=>reject(new Error('server startup timeout')),10000);child.stdout.on('data',d=>{log+=d;const m=/READY:(\d+)/.exec(log);if(m){clearTimeout(timer);resolve('http://127.0.0.1:'+m[1]);}});child.once('error',reject);child.once('exit',code=>{clearTimeout(timer);if(!/READY:/.test(log))reject(new Error('server startup failed '+code));});});
  }
  async function call(route,token=key,body){const r=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});assert.equal(r.ok,true,`HTTP ${r.status}`);return r.json();}
  async function chunk(u,d,b){const r=await fetch(`${base}/v1/uploads/${u.upload_id}/chunks?offset=0`,{method:'POST',headers:{Authorization:'Bearer '+d.token,'X-Chunk-SHA256':hash(b)},body:b});assert.equal(r.status,200);return r.json();}
  try{
    await launch(point);const e=await call('/v1/events',key,{name:'Crash Test',policy:'live'}),d=await call(`/v1/events/${e.id}/devices`,key,{label:'Camera'});
    const bytes=await sharp({create:{width:200,height:150,channels:3,background:'#8899aa'}}).jpeg().toBuffer();
    const u=await call('/v1/uploads',d.token,{client_id:crypto.randomUUID(),filename:'IMG_0001.JPG',size:bytes.length,sha256:hash(bytes)});
    if(point==='chunk_renamed')await assert.rejects(chunk(u,d,bytes));
    else{await chunk(u,d,bytes);await assert.rejects(call(`/v1/uploads/${u.upload_id}/complete`,d.token,{}));}
    await exited;assert.equal(fs.readFileSync(root+'/fault-fired','utf8'),point);
    await launch();let status=await call(`/v1/uploads/${u.upload_id}`,d.token);
    assert.equal(status.offset,point==='chunk_renamed'?0:bytes.length);
    if(status.offset===0)await chunk(u,d,bytes);
    const done=await call(`/v1/uploads/${u.upload_id}/complete`,d.token,{});assert.equal(done.receipt.sha256,hash(bytes));
    const again=await call(`/v1/uploads/${u.upload_id}/complete`,d.token,{});assert.equal(done.receipt.photo_id,again.receipt.photo_id);
    const gallery=await call(`/v1/g/${e.slug}`,'');assert.equal(gallery.photos.length,1);
    assert.equal(fs.readdirSync(root+'/chunks').length,0);
  }finally{if(child&&child.exitCode===null&&child.signalCode===null){child.kill('SIGTERM');await exited;}fs.rmSync(root,{recursive:true,force:true});}
});
