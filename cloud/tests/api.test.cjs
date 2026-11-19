'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const sharp = require('sharp');
const { createApp, CHUNK } = require('../src/server.cjs');
const hash = b => crypto.createHash('sha256').update(b).digest('hex');
const key = 'test-admin-key-not-for-use-in-a-real-deployment';
async function fixture() {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'lensdrop-api-'));
  let app, base;
  async function start() { app=createApp({dataDir:dir,adminKey:key,freeReserve:0}); await new Promise(r=>app.server.listen(0,'127.0.0.1',r)); base=`http://127.0.0.1:${app.server.address().port}`; }
  await start();
  const request=async (route,token=key,body,raw=false)=>{
    const r=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{Authorization:`Bearer ${token}`,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});
    return {status:r.status,body:raw?Buffer.from(await r.arrayBuffer()):await r.json()};
  };
  const event=(name='Test event',policy='review')=>request('/v1/events',key,{name,policy}).then(x=>x.body);
  const dev=async(e,label='Camera A')=>(await request(`/v1/events/${e.id}/devices`,key,{label})).body;
  const init=(d,bytes,extra={})=>request('/v1/uploads',d.token,{client_id:crypto.randomUUID(),filename:'IMG_0001.JPG',size:bytes.length,sha256:hash(bytes),...extra});
  const chunk=async(d,u,bytes,offset=0,sha=hash(bytes))=>{
    const r=await fetch(`${base}/v1/uploads/${u.upload_id}/chunks?offset=${offset}`,{method:'POST',headers:{Authorization:`Bearer ${d.token}`,'Content-Type':'application/octet-stream','X-Chunk-SHA256':sha},body:bytes});
    return {status:r.status,body:await r.json()};
  };
  const upload=async(d,bytes,extra={})=>{const u=(await init(d,bytes,extra)).body;for(let off=0;off<bytes.length;off+=CHUNK)assert.equal((await chunk(d,u,bytes.subarray(off,off+CHUNK),off)).status,200);return request(`/v1/uploads/${u.upload_id}/complete`,d.token,{});};
  return {request,event,dev,init,chunk,upload,get app(){return app;},restart:async()=>{await app.close();await start();},close:async()=>{await app.close();fs.rmSync(dir,{recursive:true,force:true});}};
}
async function jpeg(colour='#b38c75') { return sharp({create:{width:160,height:120,channels:3,background:colour}}).jpeg().toBuffer(); }

test('review-first, exact private original, hidden access denial and idempotent finalization',async()=>{
  const f=await fixture();try{
    const e=await f.event(),d=await f.dev(e),b=await jpeg();const u=(await f.init(d,b)).body;
    assert.equal((await f.chunk(d,u,b)).status,200);
    const done=await f.request(`/v1/uploads/${u.upload_id}/complete`,d.token,{});assert.equal(done.status,200);assert.equal(done.body.receipt.status,'review');
    assert.equal((await f.request(`/v1/g/${e.slug}`,'')).body.photos.length,0);
    const pid=done.body.receipt.photo_id;
    assert.equal((await f.request(`/v1/g/${e.slug}/photos/${pid}/delivery`,'')).status,404);
    assert.equal((await f.request(`/v1/photos/${pid}/original`,'')).status,401);
    const original=await f.request(`/v1/photos/${pid}/original`,key,undefined,true);assert.equal(hash(original.body),hash(b));
    const repeat=await f.request(`/v1/uploads/${u.upload_id}/complete`,d.token,{});assert.equal(repeat.body.receipt.photo_id,pid);
    await f.request(`/v1/photos/${pid}/status`,key,{status:'live'});
    assert.equal((await f.request(`/v1/g/${e.slug}`,'')).body.photos.length,1);
    await f.request(`/v1/photos/${pid}/status`,key,{status:'hidden'});
    assert.equal((await f.request(`/v1/g/${e.slug}/photos/${pid}/thumb`,'')).status,404);
  }finally{await f.close();}
});
test('committed chunk replay, checksum rejection, future offset and process restart recovery',async()=>{
  const f=await fixture();try{
    const e=await f.event(),d=await f.dev(e);const b=await sharp(crypto.randomBytes(1800*1400*3),{raw:{width:1800,height:1400,channels:3}}).jpeg({quality:97}).toBuffer();assert.ok(b.length>CHUNK);
    const client_id=crypto.randomUUID(),u=(await f.init(d,b,{client_id})).body,first=b.subarray(0,CHUNK);
    assert.equal((await f.chunk(d,u,b.subarray(CHUNK,CHUNK*2),CHUNK)).status,409);
    assert.equal((await f.chunk(d,u,first,0,'0'.repeat(64))).status,422);
    assert.equal((await f.chunk(d,u,first)).body.offset,CHUNK);
    assert.equal((await f.chunk(d,u,first)).body.offset,CHUNK);
    assert.equal((await f.chunk(d,u,first,0,'f'.repeat(64))).status,409);
    await f.restart();assert.equal((await f.request(`/v1/uploads/${u.upload_id}`,d.token)).body.offset,CHUNK);
    assert.equal((await f.init(d,b,{client_id})).body.upload_id,u.upload_id);
    for(let off=CHUNK;off<b.length;off+=CHUNK)assert.equal((await f.chunk(d,u,b.subarray(off,off+CHUNK),off)).status,200);
    const done=await f.request(`/v1/uploads/${u.upload_id}/complete`,d.token,{});assert.equal(done.status,200);assert.equal(done.body.receipt.sha256,hash(b));
  }finally{await f.close();}
});
test('same filename from different devices is safe; reimport by one device is deduplicated',async()=>{
  const f=await fixture();try{
    const e=await f.event(),a=await f.dev(e,'A'),b=await f.dev(e,'B'),bytes=await jpeg();
    const one=await f.upload(a,bytes),two=await f.upload(b,bytes),again=await f.upload(a,bytes);
    assert.notEqual(one.body.receipt.photo_id,two.body.receipt.photo_id);assert.equal(one.body.receipt.photo_id,again.body.receipt.photo_id);
    const s=await f.request(`/v1/events/${e.id}/status`);assert.equal(s.body.photos.length,2);
  }finally{await f.close();}
});
test('event/device authorization, revocation and client ID misuse',async()=>{
  const f=await fixture();try{
    const e=await f.event(),other=await f.event('Other'),a=await f.dev(e),b=await f.dev(other),bytes=await jpeg();const client_id=crypto.randomUUID();const u=(await f.init(a,bytes,{client_id})).body;
    assert.equal((await f.request(`/v1/uploads/${u.upload_id}`,b.token)).status,404);
    assert.equal((await f.request('/v1/events',a.token)).status,401);
    assert.equal((await f.init(a,await jpeg('#444444'),{client_id})).status,409);
    await f.request(`/v1/devices/${a.device_id}/revoke`,key,{});assert.equal((await f.request('/v1/device',a.token)).status,401);
  }finally{await f.close();}
});
test('1,000 pending upload sessions retained without a 400-item eviction cap',async()=>{
  const f=await fixture();try{
    const e=await f.event(),d=await f.dev(e),bytes=await jpeg();
    for(let i=0;i<1000;i++)assert.equal((await f.init(d,bytes,{filename:`IMG_${i}.JPG`})).status,201);
    await f.restart();const s=await f.request(`/v1/events/${e.id}/status`);assert.equal(s.body.devices[0].incomplete,1000);
  }finally{await f.close();}
});
test('invalid JPEG, wrong file checksum and incomplete completion never publish',async()=>{
  const f=await fixture();try{
    const e=await f.event('Live','live'),d=await f.dev(e),bytes=Buffer.from('not a jpeg'),u=(await f.init(d,bytes)).body;
    assert.equal((await f.request(`/v1/uploads/${u.upload_id}/complete`,d.token,{})).status,409);
    await f.chunk(d,u,bytes);assert.equal((await f.request(`/v1/uploads/${u.upload_id}/complete`,d.token,{})).status,422);
    const good=await jpeg(),bad=(await f.init(d,good,{sha256:'0'.repeat(64)})).body;await f.chunk(d,bad,good);
    assert.equal((await f.request(`/v1/uploads/${bad.upload_id}/complete`,d.token,{})).status,422);
    assert.equal((await f.request(`/v1/g/${e.slug}`,'')).body.photos.length,0);
  }finally{await f.close();}
});
test('one process per data directory enforced',async()=>{
  const f=await fixture();try{assert.throws(()=>createApp({dataDir:f.app.store.root,adminKey:key}),/already using/);}finally{await f.close();}
});
