'use strict';
// H5.7 operations shell: presentation and read-only status only. Existing APIs and
// form IDs stay untouched; never persist admin keys or create jobs automatically.
(()=>{
 const main=document.querySelector('main'),$=id=>document.getElementById(id);
 if(!main||!$('events')||!$('h5-panel'))return;
 const make=(tag,cls,txt)=>{const e=document.createElement(tag);if(cls)e.className=cls;if(txt!==undefined)e.textContent=txt;return e;};
 const intro=main.querySelector('.intro'),auth=intro?.nextElementSibling,workspace=main.querySelector('.workspace'),panels=[...workspace.querySelectorAll(':scope > aside > section')],core=[...workspace.querySelectorAll(':scope > div > section')],photos=$('photos')?.closest('section'),footer=main.querySelector('footer');
 if(!auth||panels.length<2||core.length<2||!photos)return;
 main.classList.add('ops-main');
 const top=make('div','ops-top');top.innerHTML='<div><p class="ops-kicker">EVENT CONTROL CENTER / H5.7</p><h2>Set up once. Run the event with confidence.</h2><p>Choose an event, enroll staff, then monitor photos and printing. Status lights show reported connectivity, not camera or physical print proof.</p></div>';
 intro.after(top);
 auth.classList.add('ops-auth');
 const strip=make('div','ops-status');strip.setAttribute('role','status');strip.setAttribute('aria-live','polite');
 strip.innerHTML='<div class="ops-light" id="ops-cloud"><i class="light unknown"></i><span><b>Cloud API</b><small>Connect to check</small></span></div><div class="ops-light" id="ops-phones"><i class="light unknown"></i><span><b>Photographers</b><small>No event selected</small></span></div><div class="ops-light" id="ops-stations"><i class="light unknown"></i><span><b>Print Stations</b><small>No event selected</small></span></div><div class="ops-light" id="ops-jobs"><i class="light unknown"></i><span><b>Print attention</b><small>Not checked</small></span></div>';
 auth.after(strip);
 const tabs=[['overview','01 Overview','Event, photos and live staff status'],['setup','02 Setup & pairing','Create event and issue staff QR'],['photos','03 Photos & guests','Review and personal delivery'],['print','04 Staff printing','Lookup and confirm orders'],['projection','05 Slideshow','Branding and projector']];
 const nav=make('nav','ops-nav');nav.setAttribute('aria-label','LensDrop workspace');nav.setAttribute('role','tablist');strip.after(nav);
 const bodies={};
 for(const [id,label,hint] of tabs){const b=make('button','ops-tab',label);b.type='button';b.setAttribute('role','tab');b.setAttribute('aria-controls','ops-'+id);b.dataset.target=id;b.title=hint;nav.append(b);const pane=make('div','ops-pane');pane.id='ops-'+id;pane.setAttribute('role','tabpanel');pane.hidden=true;main.insertBefore(pane,footer);b.onclick=()=>select(id);bodies[id]=pane;}
 const help=make('section','ops-help');help.innerHTML='<p class="ops-kicker">RECOMMENDED ORDER</p><div class="ops-steps"><div><b>1. Select or create event</b><small>Use the event selector on Overview, or create a new event below.</small></div><div><b>2. Pair staff by QR</b><small>Use the five-minute QR in Staff QR pairing; enter the code shown on the actual device.</small></div><div><b>3. Prepare phones and station</b><small>Each phone selects its photo folder; Windows starts paused and in Simulation.</small></div><div><b>4. Rehearse before printing</b><small>Check private cloud archiving, guest links and one simulated print request.</small></div></div>';
 bodies.setup.append(help,panels[0],$('h5-panel'));
 const legacy=make('details','ops-advanced'),summary=make('summary',null,'Advanced: legacy manual device token (not QR pairing)');legacy.append(summary,panels[1]);bodies.setup.append(legacy);
 const caution=make('p','ops-tip','To pair a phone, use “Create 5-minute pairing QR” above. “Create device token” is a legacy manual route and does not generate a QR.');legacy.before(caution);
 core.forEach(x=>bodies.overview.append(x));workspace.remove();
 bodies.photos.append(photos,$('h510Policy'),$('personalPanel'));bodies.print.append($('staffDesk'));bodies.projection.append($('presentationPanel'));
 const refreshbar=make('div','ops-refresh');refreshbar.innerHTML='<label for="ops-interval">Auto refresh <select id="ops-interval"><option value="15000">15 seconds</option><option value="30000">30 seconds</option><option value="0">Off</option></select></label><button id="ops-now" class="secondary">Refresh now</button><small id="ops-checked">No successful check yet</small>';
 nav.after(refreshbar);
 let selected='overview',connected=false,busy=false,interval=15000,stamp=0;
 function select(id){selected=id;for(const [name] of tabs){bodies[name].hidden=name!==id;const btn=nav.querySelector('[data-target="'+name+'"]');btn.setAttribute('aria-selected',String(name===id));btn.tabIndex=name===id?0:-1;} }
 function light(id,level,detail){const box=$(id);if(!box)return;const dot=box.querySelector('.light');dot.className='light '+level;box.querySelector('small').textContent=detail;box.title=detail;}
 function checkTime(){stamp=Date.now();$('ops-checked').textContent='Last successful status check: '+new Date(stamp).toLocaleTimeString();}
 function updatePhotographers(devices){if(!Array.isArray(devices))return;const active=devices.filter(x=>!x.revoked),fresh=active.filter(x=>x.heartbeat_fresh).length;
  if(!active.length)light('ops-phones','unknown','No active photographer phones');
  else if(fresh===active.length)light('ops-phones','good',fresh+'/'+active.length+' recent heartbeats');
  else if(fresh)light('ops-phones','warn',fresh+'/'+active.length+' recent; others stale');
  else light('ops-phones','bad','No recent heartbeat ('+active.length+' phones)');
 }
 function updateStations(stations,jobs){if(!Array.isArray(stations))return;const active=stations.filter(s=>!s.revoked),now=Date.now(),recent=active.filter(s=>Number.isFinite(Number(s.last_seen))&&now-Number(s.last_seen)<90000),unpaused=recent.filter(s=>!s.paused);
  if(!active.length)light('ops-stations','unknown','No paired stations');
  else if(unpaused.length===active.length)light('ops-stations','good',active.length+' recently seen; accepting work');
  else if(recent.length)light('ops-stations','warn',recent.length+'/'+active.length+' recently seen; some paused/stale');
  else light('ops-stations','bad','No recent station heartbeat (not proof offline)');
  if(Array.isArray(jobs)){const attention=jobs.filter(j=>['outcome_unknown','authorization_expired','failed_before_submit','submitted_unconfirmed'].includes(j.state));light('ops-jobs',attention.length?'warn':'good',attention.length?attention.length+' require verification/review':'No flagged jobs in latest list');}
 }
 function updateCloud(ok){connected=!!ok;light('ops-cloud',ok?'good':'bad',ok?'Authenticated API responding':'API check failed; inspect connection');if(ok)checkTime();}
 async function poll(force=false){if(busy||document.hidden||!$('key').value.trim())return;const focused=document.activeElement;
  if(!force&&focused&&focused.matches('input,textarea,select,[contenteditable="true"]'))return;
  let ev;try{ev=window.LensDropAdmin.current();}catch{return;}
  busy=true;const id=ev.id;
  try{
   if(selected==='overview'||selected==='photos'){
    await window.LensDropAdmin.refresh();
   }else{
    const s=await window.LensDropAdmin.api('/v1/events/'+id+'/status');
    if(window.LensDropAdmin.current().id!==id)return;
    updatePhotographers(s.devices);updateCloud(true);
   }
  }catch(e){stamp=Date.now();updateCloud(false);$('ops-checked').textContent='Refresh failed: '+(e.message||'check credentials/network');}
  finally{busy=false;}
 }
 $('ops-interval').onchange=e=>{interval=Number(e.target.value);$('ops-checked').textContent=interval?'Auto refresh every '+interval/1000+' seconds':'Auto refresh off';};
 $('ops-now').onclick=()=>poll(true);
 setInterval(()=>{if(interval&&Date.now()-stamp>=interval)poll(false);},5000);
 nav.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight'].includes(e.key))return;const ids=tabs.map(x=>x[0]);const i=ids.indexOf(selected),next=ids[(i+(e.key==='ArrowRight'?1:-1)+ids.length)%ids.length];select(next);nav.querySelector('[data-target="'+next+'"]').focus();e.preventDefault();});
 const english={overview:'01 Overview',setup:'02 Setup & pairing',photos:'03 Photos & guests',print:'04 Staff printing',projection:'05 Slideshow'};
 const chinese={overview:'01 總覽',setup:'02 設定及配對',photos:'03 相片及客人',print:'04 員工打印',projection:'05 Slideshow'};
 const headings={en:'Set up once. Run the event with confidence.',zh:'一次設定，清晰管理整個活動。'};
 function translate(){const isZh=document.documentElement.lang==='zh-Hant';for(const [id] of tabs)nav.querySelector('[data-target="'+id+'"]').textContent=(isZh?chinese:english)[id];top.querySelector('h2').textContent=headings[isZh?'zh':'en'];}
 $('language').addEventListener('click',translate);
 window.LensDropOperations={updatePhotographers,updateStations,updateCloud,select};
 select('overview');translate();
})();
