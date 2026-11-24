'use strict';
const {requireValue}=require('./store.cjs');
const STATES=['active','closed','archived'];
function migrate(s){
  s.tx(()=>{
    const ev=new Set(s.all('PRAGMA table_info(events)').map(r=>r.name));
    if(!ev.has('lifecycle'))s.db.exec("ALTER TABLE events ADD COLUMN lifecycle TEXT NOT NULL DEFAULT 'active' CHECK(lifecycle IN ('active','closed','archived'))");
    const cols=new Set(s.all('PRAGMA table_info(photos)').map(r=>r.name));
    if(!cols.has('projector_visible')){
      s.db.exec('ALTER TABLE photos ADD COLUMN projector_visible INTEGER NOT NULL DEFAULT 0 CHECK(projector_visible IN (0,1))');
      // Preserve the original slideshow audience of already-published photos.
      s.run("UPDATE photos SET projector_visible=1 WHERE status='live' AND is_test=0");
    }
    if(s.one('PRAGMA user_version').user_version<8)s.db.exec('PRAGMA user_version=8');
  });
}
function event(s,id){const e=s.one('SELECT * FROM events WHERE id=?',id);requireValue(e,404,'event_not_found');return e;}
function active(s,id){requireValue(event(s,id).lifecycle==='active',409,'event_not_active');}
function transition(s,id,next){
  requireValue(STATES.includes(next),400,'invalid_event_lifecycle');
  const e=event(s,id),allowed={active:['closed'],closed:['active','archived'],archived:['closed']};
  requireValue(e.lifecycle===next||allowed[e.lifecycle].includes(next),409,'invalid_event_transition');
  if(next==='archived'&&e.lifecycle!=='archived'){
    const pending=s.one("SELECT COUNT(*) n FROM print_jobs WHERE event_id=? AND state IN ('ready','receiving','awaiting_file','submitting','submitted_unconfirmed','outcome_unknown','authorization_expired')",id).n;
    requireValue(pending===0,409,'event_prints_require_reconciliation');
  }
  if(e.lifecycle!==next)s.run('UPDATE events SET lifecycle=? WHERE id=?',next,id);
  return event(s,id);
}
function deleteEmpty(s,id,name){
  const e=event(s,id);requireValue(e.lifecycle==='archived'&&e.name===name,409,'archived_event_name_required');
  const tables=['photos','uploads','devices','deliveries','print_jobs','print_stations','pairing_invites'];
  for(const table of tables)requireValue(s.one(`SELECT COUNT(*) n FROM ${table} WHERE event_id=?`,id).n===0,409,'event_has_records_archive_instead');
  const logo=s.one('SELECT logo_key FROM event_presentation WHERE event_id=?',id);requireValue(!logo?.logo_key,409,'remove_event_logo_first');
  s.tx(()=>{
    s.run('DELETE FROM event_presentation WHERE event_id=?',id);
    s.run('DELETE FROM event_workflows WHERE event_id=?',id);
    s.run('DELETE FROM personal_settings WHERE event_id=?',id);
    requireValue(s.run('DELETE FROM events WHERE id=?',id).changes===1,409,'event_changed');
  });
  return {ok:true,deleted_empty_event:id};
}
function visibility(s,id,value){
  requireValue(typeof value==='boolean',400,'invalid_projector_visibility');
  const p=s.one('SELECT * FROM photos WHERE id=?',id);requireValue(p,404,'photo_not_found');
  requireValue(!value||(!p.is_test&&p.status!=='hidden'),409,'photo_not_projector_eligible');
  s.run('UPDATE photos SET projector_visible=? WHERE id=?',Number(value),id);
  return {photo_id:id,projector_visible:value,public_gallery:p.status==='live'};
}
function counts(s,eventId){const e=event(s,eventId);return {event_id:e.id,lifecycle:e.lifecycle,cloud_stored:s.one('SELECT count(*) n FROM photos WHERE event_id=? AND is_test=0',eventId).n,review:s.one("SELECT count(*) n FROM photos WHERE event_id=? AND status='review' AND is_test=0",eventId).n,public_gallery:s.one("SELECT count(*) n FROM photos WHERE event_id=? AND status='live' AND is_test=0",eventId).n,projector:s.one("SELECT count(*) n FROM photos WHERE event_id=? AND projector_visible=1 AND status<>'hidden' AND is_test=0",eventId).n};}
module.exports={migrate,event,active,transition,deleteEmpty,visibility,counts};
