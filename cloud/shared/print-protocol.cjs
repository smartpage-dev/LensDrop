'use strict';
// This module is copied byte-for-byte into the station package at release time.
const crypto = require('node:crypto');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SHA = /^[0-9a-f]{64}$/;
const MAX_FILE = 32 * 1024 * 1024, CHUNK = 1024 * 1024;
class ProtocolError extends Error { constructor(code,status=400){super(code);this.code=code;this.status=status;} }
function check(v,code,status=400){if(!v)throw new ProtocolError(code,status);}
const hash = x=>crypto.createHash('sha256').update(x).digest('hex');
const stable = obj=>JSON.stringify(Object.fromEntries(Object.keys(obj).sort().map(k=>[k,obj[k]])));
function job(b){
  const fields=['id','event_id','device_id','station_id','sha256','bytes','profile_id','profile_revision','copies'];
  check(b && typeof b==='object' && Object.keys(b).length===fields.length && fields.every(k=>Object.hasOwn(b,k)),'invalid_print_job');
  for(const k of ['id','event_id','device_id','station_id'])check(typeof b[k]==='string'&&UUID.test(b[k]),'invalid_'+k);
  check(typeof b.sha256==='string'&&SHA.test(b.sha256),'invalid_image_hash');
  check(Number.isSafeInteger(b.bytes)&&b.bytes>0&&b.bytes<=MAX_FILE,'print_jpeg_maximum_32_mib');
  check(typeof b.profile_id==='string'&&/^[a-zA-Z0-9_-]{1,48}$/.test(b.profile_id),'invalid_profile_id');
  check(Number.isSafeInteger(b.profile_revision)&&b.profile_revision>0,'invalid_profile_revision');
  check(Number.isSafeInteger(b.copies)&&b.copies>=1&&b.copies<=5,'invalid_copies');
  return Object.fromEntries(fields.map(k=>[k,b[k]]));
}
function fingerprint(b){return hash(stable(job(b)));}
function profiles(list){
  check(Array.isArray(list)&&list.length<=12,'invalid_profiles');const seen=new Set();
  return list.map(x=>{check(x&&/^[a-zA-Z0-9_-]{1,48}$/.test(x.id)&&!seen.has(x.id),'invalid_profile_id');seen.add(x.id);
    check(Number.isInteger(x.revision)&&x.revision>0,'invalid_profile_revision');
    check(typeof x.label==='string'&&x.label.length>0&&x.label.length<=80,'invalid_profile_label');
    check(['contain'].includes(x.fit),'unsupported_fit');
    check(Number.isInteger(x.width_hundredths)&&x.width_hundredths>=100&&x.width_hundredths<=1200,'invalid_paper_width');
    check(Number.isInteger(x.height_hundredths)&&x.height_hundredths>=100&&x.height_hundredths<=1800,'invalid_paper_height');
    return {id:x.id,revision:x.revision,label:x.label,fit:x.fit,width_hundredths:x.width_hundredths,height_hundredths:x.height_hundredths};});
}
function sign(payload,key){const data=Buffer.from(JSON.stringify(payload)).toString('base64url');return data+'.'+crypto.sign(null,Buffer.from(data),key).toString('base64url');}
function verify(grant,publicKey,now=Date.now()){
  check(typeof grant==='string'&&grant.length<=8192,'invalid_print_grant',401);
  const [data,sig,extra]=grant.split('.');check(data&&sig&&!extra,'invalid_print_grant',401);
  let valid=false,p;try{valid=crypto.verify(null,Buffer.from(data),publicKey,Buffer.from(sig,'base64url'));p=JSON.parse(Buffer.from(data,'base64url'));}catch{}
  check(valid&&p&&p.v===1&&Number.isSafeInteger(p.exp)&&p.exp>now&&p.exp<=now+25*3600000,'print_grant_expired_or_invalid',401);
  check(UUID.test(p.device_id)&&UUID.test(p.event_id)&&UUID.test(p.station_id)&&SHA.test(p.auth_hash),'invalid_print_grant',401);return p;
}
function allowed(j,g){check(j.device_id===g.device_id&&j.event_id===g.event_id&&j.station_id===g.station_id,'print_scope_mismatch',403);check(j.copies<=g.max_copies,'copies_not_permitted',403);check(g.profiles.some(p=>p.id===j.profile_id&&p.revision===j.profile_revision),'profile_not_authorized',409);}
module.exports={UUID,SHA,MAX_FILE,CHUNK,ProtocolError,check,hash,stable,job,fingerprint,profiles,sign,verify,allowed};
