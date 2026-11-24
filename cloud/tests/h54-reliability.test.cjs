'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const {fixture}=require('./h5-fixture.cjs'),Assets=require('../src/print-assets.cjs');
const unlocked=async(k,fn)=>fn();
function t(name,fn){test('H5.4 '+name,async()=>{const f=await fixture();try{await fn(f);}finally{await f.close();}});}
const row=(f,j)=>f.app.store.one('SELECT * FROM print_jobs WHERE id=?',j.id);
async function report(f,j,st,state,revision=1){return f.call('/v1/station/jobs/report',{job:j,state,revision},st.token);}
async function clean(f){return f.call('/v1/events/'+f.event.id+'/print-cache/cleanup',{});}
function old(f,j){f.app.store.run('UPDATE print_jobs SET terminal_at=? WHERE id=?',Date.now()-Assets.RETAIN_MS-10000,j.id);}

t('schema 6 preserves a real schema-5 table and backfills reservations conservatively',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),held=f.job(st,dev,b),lan=f.job(st,dev,b),pending=f.job(st,dev,b);
 await f.uploadPrint(held,b,dev);await report(f,lan,st,'simulated');await f.call('/v1/device/print-jobs',pending,dev.token);
 const s=f.app.store;s.db.exec("ALTER TABLE print_jobs DROP COLUMN asset_reserved; ALTER TABLE print_jobs DROP COLUMN asset_cleanup; ALTER TABLE print_jobs DROP COLUMN terminal_at; ALTER TABLE pairing_invites DROP COLUMN approval_failures; ALTER TABLE pairing_invites DROP COLUMN approval_last_attempt; PRAGMA user_version=5;");
 await f.restart();assert.equal(f.app.store.one('PRAGMA user_version').user_version,7);assert.equal(row(f,held).asset_reserved,b.length);assert.equal(row(f,pending).asset_reserved,b.length);assert.equal(row(f,lan).asset_reserved,0);
 assert.equal(row(f,lan).state,'simulated');assert.equal(fs.readFileSync(f.app.store.file('print-assets',held.id+'.part')).compare(b),0);
});
t('incomplete bytes reserve capacity; metadata-only LAN receipts do not spend it',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),a=f.job(st,dev,b),c=f.job(st,dev,b),lan=f.job(st,dev,b);
 f.app.store.printStorageLimits={event:b.length,device:b.length,global:b.length};
 assert.equal((await f.call('/v1/device/print-jobs',a,dev.token)).status,201);assert.equal(row(f,a).offset,0);assert.equal(row(f,a).asset_reserved,b.length);
 assert.equal((await f.call('/v1/device/print-jobs',c,dev.token)).body.error,'print_storage_quota');assert.equal(row(f,c),undefined);
 assert.equal((await report(f,lan,st,'simulated')).status,200);assert.equal(row(f,lan).asset_reserved,0);assert.equal(row(f,lan).state,'simulated');
});
t('concurrent reservations cannot overbook event capacity and repeats keep one reservation',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),a=f.job(st,dev,b),c=f.job(st,dev,b);f.app.store.printStorageLimits={event:b.length,device:10*b.length,global:10*b.length};
 const rs=await Promise.all([a,c].map(j=>f.call('/v1/device/print-jobs',j,dev.token)));assert.deepEqual(rs.map(x=>x.status).sort(),[201,507]);const accepted=rs[0].status===201?a:c;
 assert.equal((await f.call('/v1/device/print-jobs',accepted,dev.token)).status,201);assert.equal(f.app.store.one('SELECT sum(asset_reserved) n FROM print_jobs').n,b.length);
});
t('per-device and global capacity limits are enforced separately',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg();f.app.store.printStorageLimits={event:100*b.length,device:b.length,global:100*b.length};await f.call('/v1/device/print-jobs',f.job(st,dev,b),dev.token);
 assert.equal((await f.call('/v1/device/print-jobs',f.job(st,dev,b),dev.token)).status,507);
 const other=await f.enroll('phone',{print:true,station_id:st.id,max_copies:1});assert.equal((await f.call('/v1/device/print-jobs',f.job(st,other,b),other.token)).status,201);
 f.app.store.printStorageLimits={event:100*b.length,device:100*b.length,global:2*b.length};assert.equal((await f.call('/v1/device/print-jobs',f.job(st,other,b),other.token)).status,507);
});
t('safe terminal cache waits 24h, removes only bytes, preserves identity and rejects resurrection',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),j=f.job(st,dev,b);await f.uploadPrint(j,b,dev);await report(f,j,st,'operator_confirmed',7);
 assert.equal((await clean(f)).body.removed,0);assert.ok(fs.existsSync(f.app.store.file('print-assets',j.id+'.part')));old(f,j);assert.equal((await clean(f)).body.removed,1);
 assert.equal(row(f,j).asset_reserved,0);assert.equal(row(f,j).asset_cleanup,'purged');assert.equal(row(f,j).report_revision,7);assert.equal(row(f,j).spec,JSON.stringify(j));
 assert.equal((await f.call('/v1/device/print-jobs',j,dev.token)).body.state,'operator_confirmed');assert.equal(row(f,j).asset_reserved,0);
 assert.equal((await report(f,j,st,'ready',8)).body.error,'terminal_print_state_conflict');assert.equal((await report(f,j,st,'ready',6)).status,200);assert.equal(row(f,j).state,'operator_confirmed');
 assert.equal((await f.putChunk(j,b,dev.token)).body.error,'print_asset_closed');assert.equal((await clean(f)).body.removed,0);
});
t('uncertain, submitted, expired, ready and partial assets are never retention-purged',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg();
 for(const state of ['outcome_unknown','submitted_unconfirmed','authorization_expired','ready','receiving']){const j=f.job(st,dev,b);await f.uploadPrint(j,b,dev);await report(f,j,st,state);f.app.store.run('UPDATE print_jobs SET terminal_at=? WHERE id=?',1,j.id);}
 assert.equal((await clean(f)).body.removed,0);assert.equal(f.app.store.one('SELECT count(*) n FROM print_jobs WHERE asset_reserved>0').n,5);
});
t('crash after tombstone retains reservation and restart completes idempotent cleanup',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),j=f.job(st,dev,b);await f.uploadPrint(j,b,dev);await report(f,j,st,'simulated');old(f,j);
 await assert.rejects(Assets.cleanup(f.app.store,unlocked,{afterMark:()=>{throw Error('simulated crash');}}));assert.equal(row(f,j).asset_cleanup,'pending');assert.equal(row(f,j).asset_reserved,b.length);
 await f.restart();assert.equal((await clean(f)).body.removed,1);assert.equal(row(f,j).asset_reserved,0);assert.equal(row(f,j).state,'simulated');
});
t('crash after unlink but before reservation release is recoverable',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),j=f.job(st,dev,b);await f.uploadPrint(j,b,dev);await report(f,j,st,'cancelled');old(f,j);
 await assert.rejects(Assets.cleanup(f.app.store,unlocked,{afterMark:x=>{fs.unlinkSync(f.app.store.file('print-assets',x.id+'.part'));throw Error('after unlink');}}));assert.equal(row(f,j).asset_reserved,b.length);
 await f.restart();assert.equal((await clean(f)).body.removed,1);assert.equal(row(f,j).asset_reserved,0);
});
t('busy asset lock defers cleanup without changing metadata or reservation',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg(),j=f.job(st,dev,b);await f.uploadPrint(j,b,dev);await report(f,j,st,'simulated');old(f,j);
 const result=await Assets.cleanup(f.app.store,async()=>{throw Error('busy');});assert.equal(result.deferred,1);assert.equal(row(f,j).asset_cleanup,'none');assert.equal(row(f,j).asset_reserved,b.length);assert.equal((await clean(f)).body.removed,1);
});
t('cleanup is bounded per invocation and administrative endpoint remains authenticated',async f=>{
 const {st,dev}=await f.ready(),b=await f.jpeg();for(let n=0;n<10;n++){const j=f.job(st,dev,b);await report(f,j,st,'simulated');f.app.store.run('UPDATE print_jobs SET asset_reserved=?,terminal_at=1 WHERE id=?',b.length,j.id);}
 assert.equal((await f.call('/v1/events/'+f.event.id+'/print-cache/cleanup',{},dev.token)).status,401);assert.equal((await clean(f)).body.removed,8);assert.equal((await clean(f)).body.removed,2);assert.equal(f.app.store.one('SELECT count(*) n FROM print_jobs').n,10);
});
t('admin list omits code; approved device uses claimant code and remains event-scoped',async f=>{
 const inv=(await f.invite()).body,c=await f.claim(inv),list=(await f.call('/v1/events/'+f.event.id+'/pairings')).body;
 assert.ok(!JSON.stringify(list).includes(c.response.body.code));assert.equal(list[0].code,undefined);
 assert.equal((await f.call('/v1/pairings/'+inv.id+'/approve',{code:c.response.body.code},c.token)).status,401);
 assert.equal((await f.call('/v1/pairings/'+inv.id+'/approve',{code:c.response.body.code})).status,200);
 assert.equal((await f.call('/v1/pairings/'+inv.id+'/status',undefined,c.secret)).body.event.id,f.event.id);
});
t('wrong-code budget survives restart and blocks even a subsequently correct code',async f=>{
 const inv=(await f.invite()).body,c=await f.claim(inv),bad=c.response.body.code==='000000'?'111111':'000000';
 for(let n=0;n<3;n++)assert.equal((await f.call('/v1/pairings/'+inv.id+'/approve',{code:bad})).status,409);
 await f.restart();assert.equal((await f.call('/v1/pairings/'+inv.id+'/approve',{code:bad})).status,409);assert.equal((await f.call('/v1/pairings/'+inv.id+'/approve',{code:bad})).status,429);
 assert.equal((await f.call('/v1/pairings/'+inv.id+'/approve',{code:c.response.body.code})).body.error,'pairing_approval_locked');assert.equal(f.app.store.one('SELECT count(*) n FROM devices').n,0);
});
t('parallel wrong attempts cannot reset or exceed the durable approval budget',async f=>{
 const inv=(await f.invite()).body,c=await f.claim(inv),bad=c.response.body.code==='000000'?'111111':'000000';
 const rs=await Promise.all(Array.from({length:8},()=>f.call('/v1/pairings/'+inv.id+'/approve',{code:bad})));assert.equal(rs.filter(x=>x.status===409).length,4);assert.equal(rs.filter(x=>x.status===429).length,4);assert.equal(f.app.store.one('SELECT approval_failures n FROM pairing_invites WHERE id=?',inv.id).n,5);
});
t('concurrent correct approval creates one identity; expired invitation cannot approve',async f=>{
 const inv=(await f.invite()).body,c=await f.claim(inv);const rs=await Promise.all([1,2].map(()=>f.call('/v1/pairings/'+inv.id+'/approve',{code:c.response.body.code})));assert.deepEqual(rs.map(x=>x.status).sort(),[200,409]);assert.equal(f.app.store.one('SELECT count(*) n FROM devices').n,1);
 const other=(await f.invite()).body,d=await f.claim(other);f.app.store.run('UPDATE pairing_invites SET expires=0 WHERE id=?',other.id);assert.equal((await f.call('/v1/pairings/'+other.id+'/approve',{code:d.response.body.code})).status,409);
});
