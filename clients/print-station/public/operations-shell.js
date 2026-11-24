'use strict';
// Print Station operations shell. Read-only lamps derive from /api/status;
// the station's native queue, print profiles and safety actions are unchanged.
(()=>{
 const main=document.querySelector('main'),$=id=>document.getElementById(id);
 if(!main||!$('pair')||!$('staffDesk'))return;
 main.classList.add('ops-main');
 const make=(tag,cls,txt)=>{const x=document.createElement(tag);if(cls)x.className=cls;if(txt!==undefined)x.textContent=txt;return x;};
 const grid=main.querySelector(':scope > .grid'),setup=[...grid.querySelectorAll(':scope > section')],others=[...main.querySelectorAll(':scope > section')],footer=main.querySelector('footer');
 if(setup.length!==2||others.length<4)return;
 const lamps=make('div','ops-status');lamps.setAttribute('role','status');lamps.innerHTML='<div class="ops-light" id="station-local"><i class="light unknown"></i><span><b>Local dashboard</b><small>Waiting for status</small></span></div><div class="ops-light" id="station-cloud"><i class="light unknown"></i><span><b>Hostinger sync</b><small>Not checked</small></span></div><div class="ops-light" id="station-pair"><i class="light unknown"></i><span><b>Event pairing</b><small>Not checked</small></span></div><div class="ops-light" id="station-queue"><i class="light unknown"></i><span><b>Print queue</b><small>Not checked</small></span></div>';
 main.querySelector('header').after(lamps);
 const nav=make('nav','ops-nav');nav.setAttribute('role','tablist');nav.setAttribute('aria-label','Station workspace');lamps.after(nav);
 const tabs=[['overview','01 Overview'],['setup','02 Setup & pairing'],['printer','03 Printer profiles'],['jobs','04 Print queue'],['desk','05 Staff Photo Desk']];
 const panes={};for(const [id,title] of tabs){const b=make('button','ops-tab',title);b.type='button';b.setAttribute('role','tab');b.dataset.target=id;b.setAttribute('aria-controls','station-'+id);nav.append(b);const p=make('div','ops-pane');p.id='station-'+id;p.hidden=true;p.setAttribute('role','tabpanel');main.insertBefore(p,footer);panes[id]=p;b.onclick=()=>select(id);}
 const guide=make('section','ops-guide');guide.innerHTML='<p class="ops-kicker">STATION AT A GLANCE</p><h2>One event. One controlled print queue.</h2><p>Pair with the cloud, configure your local network, capture the actual Windows driver settings, then rehearse in Simulation. Status is refreshed from this Windows station; it does not prove paper output.</p><div class="ops-steps"><div><b>1 · Pair</b><small>Claim a short-lived station invitation and get Admin approval.</small></div><div><b>2 · Prepare</b><small>Set LAN address and capture printer profiles.</small></div><div><b>3 · Simulate</b><small>Leave the queue paused until the event is ready.</small></div><div><b>4 · Operate</b><small>Check queued, unknown and confirmed output separately.</small></div></div>';
 panes.overview.append(guide);
 panes.setup.append(setup[0],setup[1],others[0]);grid.remove();
 panes.printer.append(others[1]);panes.jobs.append(others[2]);panes.desk.append($('staffDesk'));
 const bar=make('div','ops-refresh');bar.innerHTML='<label for="station-interval">Status refresh <select id="station-interval"><option value="5000">5 seconds</option><option value="15000">15 seconds</option><option value="0">Off</option></select></label><button id="station-refresh" class="secondary">Refresh now</button><small id="station-checked">No successful check yet</small>';nav.after(bar);
 let selected='overview',busy=false,interval=5000,last=0;
 function select(id){selected=id;for(const [key] of tabs){panes[key].hidden=key!==id;const b=nav.querySelector('[data-target="'+key+'"]');b.setAttribute('aria-selected',String(key===id));b.tabIndex=key===id?0:-1;}}
 function light(id,level,txt){const x=$(id);if(!x)return;x.querySelector('.light').className='light '+level;x.querySelector('small').textContent=txt;x.title=txt;}
 function update(s){last=Date.now();$('station-checked').textContent='Checked: '+new Date(last).toLocaleTimeString();light('station-local','good','Local status API responding');
  light('station-pair',s.revoked?'bad':s.paired?'good':'unknown',s.revoked?'Station revoked':s.paired?'Paired to '+(s.label||'event'):'Not paired yet');
  const age=Date.now()-Number(s.cloud_seen||0),synced=!!s.paired&&Number.isFinite(age)&&age<90000;
  light('station-cloud',s.revoked?'bad':!s.paired?'unknown':synced?'good':age<300000?'warn':'bad',s.revoked?'Station revoked':!s.paired?'Pair station first':synced?'Cloud sync verified recently':'Cloud sync not recently verified');
  const jobs=s.jobs||[],unknown=jobs.filter(j=>['outcome_unknown','submitted_unconfirmed'].includes(j.state)).length;
  light('station-queue',unknown?'warn':s.paused?'warn':s.simulation?'warn':'good',unknown?unknown+' physical outcomes need review':s.paused?'Paused — safe for setup':s.simulation?'Simulation only — no paper':'Queue enabled; output not yet verified');
 }
 async function poll(force=false){if(busy||document.hidden)return;const focus=document.activeElement;if(!force&&focus&&focus.matches('input,select,textarea'))return;
  busy=true;try{await window.LensDropStationRefresh();}catch(e){last=Date.now();light('station-local','bad','Local status unavailable');$('station-checked').textContent='Refresh failed: '+(e.message||'unknown');}finally{busy=false;}}
 $('station-interval').onchange=e=>{interval=Number(e.target.value);$('station-checked').textContent=interval?'Refreshing every '+interval/1000+' seconds':'Auto refresh off';};
 $('station-refresh').onclick=()=>poll(true);
 setInterval(()=>{if(interval&&Date.now()-last>=interval)poll(false);},1000);
 nav.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight'].includes(e.key))return;const ids=tabs.map(x=>x[0]);const i=ids.indexOf(selected);const next=ids[(i+(e.key==='ArrowRight'?1:-1)+ids.length)%ids.length];select(next);nav.querySelector('[data-target="'+next+'"]').focus();e.preventDefault();});
 window.LensDropStationOperations={update,select};select('overview');
})();
