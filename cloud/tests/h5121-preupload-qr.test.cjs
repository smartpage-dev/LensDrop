'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto');
const {fixture,P}=require('./h5-fixture.cjs');
const rand=()=>crypto.randomBytes(16).toString('hex');
async function upload(f,dev,client,bytes){
 const b={client_id:client,filename:'IMG_0001.JPG',size:bytes.length,sha256:P.hash(bytes),archive_private:true};
 const start=await f.call('/v1/uploads',b,dev.token);assert.equal(start.status,201,JSON.stringify(start));
 const chunk=await fetch(f.base+'/v1/uploads/'+start.body.upload_id+'/chunks?offset=0',{method:'POST',headers:{Authorization:'Bearer '+dev.token,'Content-Type':'application/octet-stream','X-Chunk-SHA256':P.hash(bytes)},body:bytes});
 assert.equal(chunk.status,200,await chunk.text());
 const done=await f.call('/v1/uploads/'+start.body.upload_id+'/complete',{},dev.token);assert.equal(done.status,200,JSON.stringify(done));return done.body.receipt.photo_id;
}
test('H5.12.1 preissued Single: preparing before upload, same QR after restart, ready after release, JPEG downloadable',async()=>{
 const f=await fixture();try{
  const d=await f.enroll(),client=crypto.randomUUID(),bytes=await f.jpeg('#347896');const token=rand(),body={client_id:client,filename:'IMG_0001.JPG',size:bytes.length,token};
  const issued=await f.call('/v1/device/single-preissue',body,d.token);assert.equal(issued.status,200,JSON.stringify(issued));assert.equal(issued.body.guest_path,'/p/'+token);
  assert.match(issued.body.label,/^S\d+$/);assert.equal((await f.call('/v1'+issued.body.guest_path,undefined,'')).body.delivery_status,'preparing');
  await f.restart();const repeated=await f.call('/v1/device/single-preissue',body,d.token);assert.equal(repeated.status,200);assert.equal(repeated.body.id,issued.body.id);
  const photo=await upload(f,d,client,bytes);let guest=await f.call('/v1'+issued.body.guest_path,undefined,'');assert.equal(guest.body.delivery_status,'awaiting_approval');assert.equal(guest.body.photos.length,0);
  const ensured=await f.call('/v1/device/photos/'+photo+'/single',{},d.token);assert.equal(ensured.body.id,issued.body.id);
  assert.equal((await f.call('/v1/photos/'+photo+'/personal-release',{released:true})).status,200);
  guest=await f.call('/v1'+issued.body.guest_path,undefined,'');assert.equal(guest.body.delivery_status,'ready');assert.equal(guest.body.photos.length,1);
  const jpeg=await fetch(f.base+'/v1'+issued.body.guest_path+'/photos/'+photo+'/delivery');assert.equal(jpeg.status,200);assert.match(jpeg.headers.get('content-type'),/image\/jpeg/);assert.ok((await jpeg.arrayBuffer()).byteLength>100);
 }finally{await f.close();}
});
test('H5.12.1 two photographers with same filename keep unique random links; mismatched identity and cross-device access blocked',async()=>{
 const f=await fixture();try{
  const a=await f.enroll(),b=await f.enroll(),client=crypto.randomUUID(),bytes=await f.jpeg();
  const pa={client_id:client,filename:'IMG_0001.JPG',size:bytes.length,token:rand()},pb={...pa,token:rand()};
  const x=await f.call('/v1/device/single-preissue',pa,a.token),y=await f.call('/v1/device/single-preissue',pb,b.token);
  assert.equal(x.status,200);assert.equal(y.status,200);assert.notEqual(x.body.guest_path,y.body.guest_path);
  assert.equal((await f.call('/v1/device/single-preissue',{...pa,filename:'WRONG.JPG'},a.token)).status,409);
  assert.equal((await f.call('/v1/device/single-preissue',{...pa,token:rand()},a.token)).status,409);
  assert.equal((await f.call('/v1/device/single-preissue',{...pb,token:pa.token},b.token)).status,409);
  const photo=await upload(f,a,client,bytes);const owner=await f.call('/v1/device/photos/'+photo+'/single',{},a.token);assert.equal(owner.body.guest_path,x.body.guest_path);
  assert.equal((await f.call('/v1/device/photos/'+photo+'/single',{},b.token)).status,404);
 }finally{await f.close();}
});
test('H5.12.1 Group QR is stable pre-upload and supports multiple released photos; no incorrect inferred group',async()=>{
 const f=await fixture();try{
  const a=await f.enroll(),token=rand(),gid=crypto.randomUUID();
  const g=await f.call('/v1/device/deliveries',{id:gid,kind:'group',label:'Guest',auto_label:true,token},a.token);
  assert.equal(g.status,201,JSON.stringify(g));assert.equal(g.body.guest_path,'/p/'+token);
  assert.equal((await f.call('/v1'+g.body.guest_path,undefined,'')).body.delivery_status,'preparing');
  const photo=await upload(f,a,crypto.randomUUID(),await f.jpeg());
  assert.equal((await f.call('/v1'+g.body.guest_path,undefined,'')).body.delivery_status,'preparing');
  assert.equal((await f.call('/v1/device/photos/'+photo+'/delivery',{delivery_id:g.body.id},a.token)).status,200);
  assert.equal((await f.call('/v1'+g.body.guest_path,undefined,'')).body.delivery_status,'awaiting_approval');
  await f.call('/v1/photos/'+photo+'/personal-release',{released:true});
  assert.equal((await f.call('/v1'+g.body.guest_path,undefined,'')).body.delivery_status,'ready');
  const again=await f.call('/v1/device/deliveries',{id:gid,kind:'group',label:'Guest',auto_label:true,token},a.token);
  assert.equal(again.status,200);assert.equal(again.body.guest_path,g.body.guest_path);
 }finally{await f.close();}
});
test('H5.12.1 re-pair rotates credential on same photographer id; old credential revoked',async()=>{
 const f=await fixture();try{
  const a=await f.enroll(),newCredential=crypto.randomBytes(32).toString('hex');
  const inv=(await f.call('/v1/events/'+f.event.id+'/pairings',{kind:'phone',label:'Test phone',origin:'https://test.example.invalid',permissions:{},replace_device_id:a.id}));
  assert.equal(inv.status,201,JSON.stringify(inv));
  const claim=await f.claim(inv.body,newCredential);assert.equal(claim.response.status,200,JSON.stringify(claim.response));
  const ok=await f.call('/v1/pairings/'+inv.body.id+'/approve',{code:claim.response.body.code});assert.equal(ok.status,200,JSON.stringify(ok));assert.equal(ok.body.entity_id,a.id);
  assert.equal((await f.call('/v1/device',undefined,a.token)).status,401);
  assert.equal((await f.call('/v1/device',undefined,newCredential)).status,200);
  assert.equal(f.app.store.one('SELECT count(*) n FROM devices WHERE event_id=? AND is_service=0',f.event.id).n,1);
 }finally{await f.close();}
});
test('H5.12.1 archived empty photographer can be deleted; linked media blocks deletion',async()=>{
 const f=await fixture();try{
  const d=await f.enroll(),k='/v1/devices/'+d.id;
  assert.equal((await f.call(k+'/rename',{label:'Portrait Photographer'})).status,200);
  assert.equal((await f.call(k+'/delete-empty',{confirm_id:d.id,confirm_label:'Portrait Photographer'})).status,409);
  assert.equal((await f.call(k+'/archive',{confirm:'ARCHIVE_AND_REVOKE'})).status,200);
  assert.equal((await f.call(k+'/delete-empty',{confirm_id:d.id,confirm_label:'Portrait Photographer'})).status,200);
  const other=await f.enroll(),client=crypto.randomUUID();await upload(f,other,client,await f.jpeg());
  const route='/v1/devices/'+other.id;assert.equal((await f.call(route+'/archive',{confirm:'ARCHIVE_AND_REVOKE'})).status,200);
  assert.equal((await f.call(route+'/delete-empty',{confirm_id:other.id,confirm_label:'Test phone'})).status,409);
 }finally{await f.close();}
});
test('H5.12.1 unauthenticated guest cannot enumerate preissued identities; gallery link is scoped to paired event',async()=>{
 const f=await fixture();try{
  const a=await f.enroll(),b=await f.enroll('phone',{},(await f.call('/v1/events',{name:'other',policy:'review'})).body.id);
  assert.equal((await f.call('/v1/device/single-preissue',{client_id:crypto.randomUUID(),filename:'IMG_0001.JPG',size:512,token:rand()},'')).status,401);
  const one=await f.call('/v1/device/gallery',undefined,a.token),two=await f.call('/v1/device/gallery',undefined,b.token);
  assert.equal(one.status,200,JSON.stringify(one));assert.equal(two.status,200);assert.notEqual(one.body.event_id,two.body.event_id);
  assert.equal(one.body.gallery_path,'/g/'+f.event.slug);
 }finally{await f.close();}
});
