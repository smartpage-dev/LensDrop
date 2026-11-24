'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),P=require('../src/print-protocol.cjs'),R=require('../src/printer-profiles.cjs');
function capture(){const b=Buffer.alloc(240);b.writeUInt16LE(220,68);b.writeUInt16LE(20,70);return {schema:1,printer:'CP9550 test queue',driver_name:'MITSUBISHI CP9550DW',driver_fingerprint:'a'.repeat(64),paper_name:'10x15 test',raw_kind:256,width_hundredths:400,height_hundredths:600,landscape:false,devmode_base64:b.toString('base64'),sha256:P.hash(b)};}
function profile(i=0){const t=R.PRESETS[i];return {id:t.id,revision:1,label:t.label,fit:'contain',width_hundredths:400,height_hundredths:600,printer:'CP9550 test queue',model:'Mitsubishi CP9550DW',layout:t.layout,cut:t.cut,driver_snapshot:capture(),driver_mode_confirmed:true};}
function bad(name,modify,error){test(name,()=>{const p=profile(1);modify(p);assert.throws(()=>R.normalize([p]),error);});}
test('three distinct 4x6/auto-pair/manual-pair presets retain matching snapshots',()=>{const list=R.PRESETS.map((_,i)=>profile(i));assert.deepEqual(R.normalize(list),list);});
test('opaque local driver settings never enter public profile or grant data',()=>{const publicProfile=P.profiles(R.normalize([profile(1)]))[0];assert.equal(Object.keys(publicProfile).length,6);assert.ok(!('driver_snapshot' in publicProfile));assert.ok(!('printer' in publicProfile));});
bad('wrong printer snapshot is rejected',p=>p.driver_snapshot.printer='Other',/recapture_selected/);
bad('a changed opaque byte fails checksum',p=>{const b=Buffer.from(p.driver_snapshot.devmode_base64,'base64');b[80]=1;p.driver_snapshot.devmode_base64=b.toString('base64');},/checksum/);
bad('invalid DEVMODE length is rejected',p=>{const b=Buffer.from(p.driver_snapshot.devmode_base64,'base64');b.writeUInt16LE(200,68);p.driver_snapshot.devmode_base64=b.toString('base64');p.driver_snapshot.sha256=P.hash(b);},/checksum/);
bad('single 2x6 paper setting is not mistaken for two-up sheet',p=>p.driver_snapshot.width_hundredths=200,/capture_4x6/);
bad('cut mode requires explicit operator confirmation',p=>p.driver_mode_confirmed=false,/confirm_driver/);
bad('automatic profile cannot be relabelled as manual internally',p=>p.cut='manual',/invalid_cp9550/);
bad('a different printer driver cannot enter the model preset',p=>p.driver_snapshot.driver_name='PDF writer',/cp9550_driver/);
bad('effective paper dimensions must match capture',p=>p.width_hundredths=410,/paper_snapshot/);
bad('fit cover cannot silently crop the strip',p=>p.fit='cover',/unsupported_fit/);
test('landscape capture publishes effective sheet dimensions',()=>{const p=profile(2);p.driver_snapshot.landscape=true;p.width_hundredths=600;p.height_hundredths=400;assert.equal(R.normalize([p])[0].width_hundredths,600);});
test('existing generic printer profile remains unchanged',()=>{const p={id:'photo-4x6',revision:1,label:'Existing ASK',fit:'contain',width_hundredths:400,height_hundredths:600,printer:'ASK-300'};assert.deepEqual(R.normalize([p]),[p]);});
test('new image layout cannot be smuggled into a generic profile',()=>{const p={...profile(),id:'something-else'};assert.throws(()=>R.normalize([p]),/use_builtin/);});
test('a snapshot change needs a new revision',()=>{const old=profile(),p=profile();p.driver_snapshot.driver_fingerprint='b'.repeat(64);assert.throws(()=>R.normalize([p],[old]),/increment_profile/);p.revision=2;assert.equal(R.normalize([p],[old])[0].revision,2);});
test('no extra fields are added to immutable nine-field jobs',()=>{const j={id:'a'.repeat(8)+'-aaaa-aaaa-aaaa-'+'a'.repeat(12),event_id:'b'.repeat(8)+'-bbbb-bbbb-bbbb-'+'b'.repeat(12),device_id:'c'.repeat(8)+'-cccc-cccc-cccc-'+'c'.repeat(12),station_id:'d'.repeat(8)+'-dddd-dddd-dddd-'+'d'.repeat(12),sha256:'e'.repeat(64),bytes:100,profile_id:profile(1).id,profile_revision:1,copies:2};assert.equal(Object.keys(P.job(j)).length,9);assert.throws(()=>P.job({...j,strip_count:4}),/invalid_print_job/);});
module.exports={capture,profile};
