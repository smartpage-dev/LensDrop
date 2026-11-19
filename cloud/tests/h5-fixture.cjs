'use strict';
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto'),sharp=require('sharp');
const {createApp}=require('../src/server.cjs');const P=require('../shared/print-protocol.cjs');
const adminKey='h5-only-test-admin-key-not-a-real-credential';
async function fixture(){const root=fs.mkdtempSync(path.join(os.tmpdir(),'lensdrop-h5-'));let app,base;
async function start(){app=createApp({adminKey,dataDir:root,freeReserve:0});await new Promise(r=>app.server.listen(0,'127.0.0.1',r));base='http://127.0.0.1:'+app.server.address().port;}
await start();
async function call(route,body,auth=adminKey){const r=await fetch(base+route,{method:body===undefined?'GET':'POST',headers:{Authorization:'Bearer '+auth,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,body:await r.json()};}
const event=(await call('/v1/events',{name:'H5 test event',policy:'review'})).body;
async function invite(kind='phone',permissions={},eventId=event.id){return call('/v1/events/'+eventId+'/pairings',{kind,label:'Test '+kind,origin:'https://test.example.invalid',permissions});}
async function claim(inv,credential=crypto.randomBytes(32).toString('hex')){const secret=new URL(inv.pairing_url).hash.slice(1).split('.')[1],claim_secret=crypto.randomBytes(32).toString('hex');const body={id:inv.id,secret,claim_secret,credential_hash:P.hash(credential),client_id:crypto.randomUUID()};return {request:body,token:credential,secret:claim_secret,response:await call('/v1/pairings/claim',body,'')};}
async function enroll(kind='phone',permissions={},eventId=event.id){const inv=(await invite(kind,permissions,eventId)).body,c=await claim(inv);if(c.response.status!==200)throw Error(JSON.stringify(c.response));const approve=await call('/v1/pairings/'+inv.id+'/approve',{code:c.response.body.code});if(approve.status!==200)throw Error(JSON.stringify(approve));const status=(await call('/v1/pairings/'+inv.id+'/status',undefined,c.secret)).body;return {...status,id:status.entity_id,token:c.token,invite:inv,claim:c};}
async function jpeg(color='#436b76'){return sharp({create:{width:128,height:96,channels:3,background:color}}).jpeg().toBuffer();}
const profile={id:'photo-4x6',revision:1,label:'4x6',fit:'contain',width_hundredths:400,height_hundredths:600};
async function ready(){const st=await enroll('station');const hb=await call('/v1/station/heartbeat',{revision:1,profiles:[profile],endpoint:'https://192.168.10.20:54173',certificate_sha256:'a'.repeat(64),paused:false},st.token);if(hb.status!==200)throw Error(JSON.stringify(hb));const dev=await enroll('phone',{print:true,station_id:st.id,max_copies:2});return {st,dev};}
function job(st,dev,bytes,extra={}){return {id:crypto.randomUUID(),event_id:event.id,device_id:dev.id,station_id:st.id,sha256:P.hash(bytes),bytes:bytes.length,profile_id:profile.id,profile_revision:profile.revision,copies:1,...extra};}
async function putChunk(j,bytes,token,offset=0,hash=P.hash(bytes)){const r=await fetch(base+'/v1/device/print-jobs/'+j.id+'/chunks?offset='+offset,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/octet-stream','X-Chunk-SHA256':hash},body:bytes});return {status:r.status,body:await r.json()};}
async function uploadPrint(j,bytes,dev){let r=await call('/v1/device/print-jobs',j,dev.token);if(r.status!==201)throw Error(JSON.stringify(r));for(let off=0;off<bytes.length;off+=P.CHUNK){r=await putChunk(j,bytes.subarray(off,off+P.CHUNK),dev.token,off);if(r.status!==200)throw Error(JSON.stringify(r));}return call('/v1/device/print-jobs/'+j.id+'/complete',{},dev.token);}
return {root,call,event,invite,claim,enroll,jpeg,profile,ready,job,putChunk,uploadPrint,get app(){return app;},get base(){return base;},restart:async()=>{await app.close();await start();},close:async()=>{await app.close();fs.rmSync(root,{recursive:true,force:true});}};
}
module.exports={fixture,P,adminKey};
