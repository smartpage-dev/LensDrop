'use strict';
// Private print-cache lifecycle. Metadata/dedup records are never removed here.
const fs=require('node:fs'),fsp=require('node:fs/promises');
const {requireValue:need,syncDir}=require('./store.cjs');
const SAFE=['simulated','operator_confirmed','cancelled'];
const RETAIN_MS=24*3600000;
const LIMITS={event:2*1024**3,device:512*1024**2,global:8*1024**3};
function migrate(s){
 const names=new Set(s.all('PRAGMA table_info(print_jobs)').map(x=>x.name));
 if(names.has('asset_reserved'))return;
 s.tx(()=>{
  s.db.exec("ALTER TABLE print_jobs ADD COLUMN asset_reserved INTEGER NOT NULL DEFAULT 0; ALTER TABLE print_jobs ADD COLUMN asset_cleanup TEXT NOT NULL DEFAULT 'none'; ALTER TABLE print_jobs ADD COLUMN terminal_at INTEGER NOT NULL DEFAULT 0;");
  for(const r of s.all('SELECT * FROM print_jobs')){
   const present=fs.existsSync(s.file('print-assets',r.id+'.part'));
   // Unknown legacy in-flight reservations are conservatively kept. Terminal LAN-only records cost zero.
   const reserve=present||r.asset_ready||['awaiting_file','receiving','ready'].includes(r.state);
   s.run('UPDATE print_jobs SET asset_reserved=?,terminal_at=? WHERE id=?',reserve?JSON.parse(r.spec).bytes:0,SAFE.includes(r.state)?r.updated:0,r.id);
  }
 });
}
function usage(s,eventId){return {reserved_or_retained_bytes:s.one('SELECT coalesce(sum(asset_reserved),0) n FROM print_jobs WHERE event_id=?',eventId).n,event_limit_bytes:LIMITS.event,device_limit_bytes:LIMITS.device,global_limit_bytes:LIMITS.global,retention_hours:24,cleanup_pending:s.one("SELECT count(*) n FROM print_jobs WHERE event_id=? AND asset_cleanup='pending'",eventId).n};}
// Call inside a write transaction, before opening/resuming an asset.
function reserve(s,row){
 if(row.asset_reserved>0||!['awaiting_file','receiving'].includes(row.state))return;
 need(row.asset_cleanup==='none',409,'print_asset_closed');
 const j=JSON.parse(row.spec),lim=s.printStorageLimits||LIMITS;
 for(const [key,where,args] of [['event','event_id=?',[j.event_id]],['device','device_id=?',[j.device_id]],['global','1=1',[]]])
  need(s.one('SELECT coalesce(sum(asset_reserved),0) n FROM print_jobs WHERE '+where,...args).n+j.bytes<=lim[key],507,'print_storage_quota');
 s.run('UPDATE print_jobs SET asset_reserved=? WHERE id=?',j.bytes,row.id);
}
async function cleanup(s,locked,{eventId=null,at=Date.now(),afterMark=null}={}){
 // Durable pending tombstone precedes unlink; reserved bytes are released only after unlink+directory sync.
 const rows=s.all("SELECT * FROM print_jobs WHERE state IN ('simulated','operator_confirmed','cancelled') AND asset_reserved>0 AND (asset_cleanup='pending' OR (terminal_at>0 AND terminal_at<=?))"+(eventId?' AND event_id=?':'')+' ORDER BY updated LIMIT 8',at-RETAIN_MS,...(eventId?[eventId]:[]));
 let removed=0,deferred=0;
 for(const r of rows){try{await locked('print:'+r.id,async()=>{
  const x=s.one('SELECT * FROM print_jobs WHERE id=?',r.id);
  if(!SAFE.includes(x.state)||!x.asset_reserved||x.asset_cleanup!=='pending'&&(!x.terminal_at||x.terminal_at>at-RETAIN_MS))return;
  s.run("UPDATE print_jobs SET asset_cleanup='pending' WHERE id=?",x.id);
  if(afterMark)await afterMark(x); // dependency-injected failure boundary for offline regression tests only
  await fsp.rm(s.file('print-assets',x.id+'.part'),{force:true});await fsp.rm(s.file('print-assets',x.id+'.copying'),{force:true});syncDir(s.file('','print-assets'));
  s.run("UPDATE print_jobs SET asset_reserved=0,asset_ready=0,offset=0,asset_cleanup='purged' WHERE id=? AND asset_cleanup='pending'",x.id);removed++;
 });}catch(e){if(afterMark)throw e;deferred++;}}
 return {removed,deferred,ledger_retained:true,retention_hours:24};
}
module.exports={migrate,usage,reserve,cleanup,LIMITS,RETAIN_MS};
