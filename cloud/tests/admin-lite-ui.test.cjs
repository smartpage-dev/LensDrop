'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {fixture,adminKey}=require('./h5-fixture.cjs');

test('Admin Lite serves only explicit same-origin static assets; Admin API still requires existing credential',async()=>{
 const f=await fixture();
 try{
  for(const [name,type] of [['/admin-lite','text/html'],['/admin-lite.html','text/html'],['/admin-lite.js','text/javascript'],['/admin-lite.css','text/css']]){
   const r=await fetch(f.base+name);assert.equal(r.status,200,name);
   assert.ok(r.headers.get('content-type').startsWith(type));
   assert.match(r.headers.get('content-security-policy'),/script-src 'self'/);
   assert.equal(r.headers.get('cache-control'),'no-store');
  }
  const unauth=await fetch(f.base+'/v1/events');assert.equal(unauth.status,401);
  const authed=await fetch(f.base+'/v1/events',{headers:{Authorization:'Bearer '+adminKey}});
  assert.equal(authed.status,200);
  const invalid=await fetch(f.base+'/admin-lite/../../private-file');assert.notEqual(invalid.status,200);
 }finally{await f.close();}
});
