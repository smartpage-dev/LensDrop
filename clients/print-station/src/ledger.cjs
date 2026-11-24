'use strict';
const {DatabaseSync}=require('node:sqlite'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const P=require('./print-protocol.cjs');
class Ledger{
 constructor(root){this.root=path.resolve(root);fs.mkdirSync(this.root,{recursive:true,mode:0o700});fs.mkdirSync(path.join(root,'assets'),{recursive:true,mode:0o700});this.lock=path.join(root,'worker.lock');this.nonce=crypto.randomUUID();
 for(let n=0;n<2;n++){try{fs.writeFileSync(this.lock,JSON.stringify({pid:process.pid,nonce:this.nonce}),{flag:'wx',mode:0o600});break;}catch(e){if(e.code!=='EEXIST'||n)throw Error('station_already_running_or_lock_invalid');let old;try{old=JSON.parse(fs.readFileSync(this.lock));process.kill(old.pid,0);}catch(e){if(e.code==='ESRCH'){fs.unlinkSync(this.lock);continue;}}throw Error('station_already_running_or_lock_invalid');}}
 this.db=new DatabaseSync(path.join(root,'print-ledger.sqlite'));this.db.exec(`PRAGMA journal_mode=WAL;PRAGMA synchronous=FULL;PRAGMA busy_timeout=5000;
 CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,spec TEXT NOT NULL,spec_hash TEXT NOT NULL,profile TEXT NOT NULL,state TEXT NOT NULL,offset INTEGER NOT NULL DEFAULT 0,revision INTEGER NOT NULL DEFAULT 1,reported INTEGER NOT NULL DEFAULT 0,expires INTEGER NOT NULL,created INTEGER NOT NULL,updated INTEGER NOT NULL);
 CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY,v TEXT NOT NULL);`);
 const cols=new Set(this.db.prepare('PRAGMA table_info(jobs)').all().map(x=>x.name));
 this.db.exec('BEGIN IMMEDIATE');try{for(const [name,type] of [['report_next','INTEGER NOT NULL DEFAULT 0'],['report_failures','INTEGER NOT NULL DEFAULT 0'],['report_error',"TEXT NOT NULL DEFAULT ''"],['attention',"TEXT NOT NULL DEFAULT ''"]])if(!cols.has(name))this.db.exec('ALTER TABLE jobs ADD COLUMN '+name+' '+type);this.db.exec('COMMIT');}catch(e){this.db.exec('ROLLBACK');throw e;}
 // A print call may already have reached the driver. Never resubmit automatically.
 this.db.prepare("UPDATE jobs SET state='outcome_unknown',revision=revision+1,updated=? WHERE state='submitting'").run(Date.now());
 }
 get(id){return this.db.prepare('SELECT * FROM jobs WHERE id=?').get(id);}
 all(sql,...args){return this.db.prepare(sql).all(...args);}
 count(){return this.db.prepare('SELECT count(*) n FROM jobs').get().n;}
 asset(id){P.check(P.UUID.test(id),'invalid_id');return path.join(this.root,'assets',id+'.jpg');}
 put(spec,profile,expires){const j=P.job(spec),old=this.get(j.id);if(old){P.check(old.spec_hash===P.fingerprint(j),'print_job_identity_conflict',409);if(expires>old.expires)this.db.prepare('UPDATE jobs SET expires=? WHERE id=?').run(expires,j.id);return this.get(j.id);}
 P.check(this.db.prepare("SELECT count(*) n FROM jobs WHERE state IN ('receiving','ready','failed_before_submit','authorization_expired')").get().n<1000,'station_queue_full',429);
 let used=0;for(const x of fs.readdirSync(path.join(this.root,'assets')))used+=fs.statSync(path.join(this.root,'assets',x)).size;
 P.check(used+j.bytes<=2*1024**3,'station_cache_full',507);
 this.db.prepare('INSERT INTO jobs(id,spec,spec_hash,profile,state,expires,created,updated) VALUES(?,?,?,?,?,?,?,?)').run(j.id,JSON.stringify(j),P.fingerprint(j),JSON.stringify(profile),'receiving',expires,Date.now(),Date.now());return this.get(j.id);}
 offset(id,n){this.db.prepare('UPDATE jobs SET offset=?,updated=? WHERE id=?').run(n,Date.now(),id);return this.get(id);}
 state(id,state,attention=''){this.db.prepare("UPDATE jobs SET state=?,attention=?,revision=revision+1,updated=?,report_next=0 WHERE id=?").run(state,attention,Date.now(),id);return this.get(id);}
 markReported(id,revision){this.db.prepare("UPDATE jobs SET reported=max(reported,?),report_next=0,report_failures=0,report_error='' WHERE id=?").run(revision,id);}
 reportFailed(id,code){const r=this.get(id);const delay=Math.min(300000,5000*2**Math.min(6,r.report_failures));this.db.prepare('UPDATE jobs SET report_next=?,report_failures=report_failures+1,report_error=? WHERE id=?').run(Date.now()+delay,/^[a-z0-9_]{1,80}$/.test(code||'')?code:'report_failed',id);}
 public(row){return {...JSON.parse(row.spec),state:row.state,offset:row.offset,revision:row.revision,updated:row.updated,attention:row.attention,report_error:row.report_error,report_pending:row.reported<row.revision};}
 close(){this.db.close();try{if(JSON.parse(fs.readFileSync(this.lock)).nonce===this.nonce)fs.unlinkSync(this.lock);}catch{}}
}
module.exports={Ledger};
