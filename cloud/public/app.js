'use strict';
const $=id=>document.getElementById(id);
let events=[],nextBefore=null,currentBefore=null,language='en',generation=0;
const zh={title:'\u6bcf\u5f35\u5df2\u63a5\u6536\u7167\u7247\uff0c\u90fd\u6709\u6e05\u695a\u53bb\u5411\u3002',subtitle:'\u624b\u6a5f\u6536\u5230\u3001\u96f2\u7aef\u9a57\u8b49\u3001\u5ba2\u4eba\u53ef\u898b\u5206\u958b\u8a08\u6578\u3002\u6536\u5230\u6a94\u6848\u4e0d\u4ee3\u8868\u76f8\u6a5f\u4ecd\u7136\u9023\u7dda\u3002',connectTitle:'\u9023\u63a5\u96f2\u7aef',adminLabel:'\u7ba1\u7406\u54e1\u91d1\u9470',connect:'\u9023\u63a5',deployment:'\u6aa2\u67e5\u90e8\u7f72',newEvent:'\u5efa\u7acb\u6d3b\u52d5',eventName:'\u6d3b\u52d5\u540d\u7a31',policy:'\u516c\u958b\u65b9\u5f0f',create:'\u5efa\u7acb\u6d3b\u52d5',testNote:'\u6e2c\u8a66\u76f8\u7247\u4e00\u5f8b\u9694\u96e2\uff0c\u5373\u6642\u516c\u958b\u6d3b\u52d5\u4ea6\u7136\u3002',deviceTitle:'\u914d\u5c0d\u651d\u5f71\u5e2b',deviceLabel:'\u651d\u5f71\u5e2b / \u624b\u6a5f\u540d\u7a31',pair:'\u5efa\u7acb\u624b\u6a5f Token',tokenNote:'\u6bcf\u90e8\u624b\u6a5f\u3001\u6bcf\u500b\u6d3b\u52d5\u7368\u7acb\u6191\u8b49\uff0c\u4e0d\u8981\u5206\u4eab\u7ba1\u7406\u54e1\u91d1\u9470\u3002',workspace:'\u6d3b\u52d5\u5de5\u4f5c\u5340',selectedEvent:'\u76ee\u524d\u6d3b\u52d5',refresh:'\u66f4\u65b0\u72c0\u614b',wrapup:'\u532f\u51fa\u6536\u5de5\u5c0d\u6578',diagnostics:'\u532f\u51fa\u8131\u654f\u8a3a\u65b7',wrapNote:'\u5c0d\u6578\u53ea\u662f\u7576\u523b\u5feb\u7167\uff0c\u4e0d\u6703\u505c\u6b62\u624b\u6a5f\u6216\u522a\u9664\u539f\u76f8\u3002',devices:'\u651d\u5f71\u5e2b\u72c0\u614b',photos:'\u7167\u7247',pageNote:'\u6bcf\u9801 200 \u5f35\uff1b\u7e3d\u6578\u5305\u542b\u6574\u500b\u6d3b\u52d5\u3002\u6e2c\u8a66\u76f8\u4e0d\u80fd\u516c\u958b\u3002',newest:'\u6700\u65b0\u4e00\u9801',older:'\u8f03\u65e9\u7167\u7247',footer:'H5 \u53ea\u4f9b\u53d7\u63a7\u6e2c\u8a66\u3002\u9700\u6301\u4e45\u5132\u5b58\u53ca\u55ae\u4e00\u4f3a\u670d\u5668\u7a0b\u5e8f\uff0c\u771f\u6a5f\u76f8\u5bb9\u6027\u5fc5\u9808\u53e6\u884c\u9a57\u8b49\u3002'};
const en={};document.querySelectorAll('[data-i]').forEach(e=>en[e.dataset.i]=e.textContent);
const words={source:['Source received','\u5df2\u63a5\u6536'],cloud:['Cloud verified','\u96f2\u7aef\u5df2\u9a57\u8b49'],live:['Public gallery-visible','\u5ba2\u4eba\u53ef\u898b'],unknown:['Not reported','\u672a\u56de\u5831'],fresh:['Recent heartbeat','\u6700\u8fd1\u6709\u56de\u5831'],stale:['Stale / no heartbeat','\u56de\u5831\u5df2\u904e\u6642\u6216\u672a\u6536\u5230'],queued:['queued','\u5f85\u50b3'],blocked:['need attention','\u9700\u8655\u7406'],review:['review','\u5f85\u5be9'],hidden:['hidden','\u96b1\u85cf'],tests:['isolated tests','\u9694\u96e2\u6e2c\u8a66'],file:['File','\u6a94\u6848'],state:['State','\u72c0\u614b'],actions:['Actions','\u64cd\u4f5c'],openGallery:['Open guest gallery','\u958b\u555f\u5ba2\u4eba\u76f8\u7c3f'],lastReceived:['Last source receipt','\u6700\u8fd1\u6536\u5230\u6a94\u6848']};
const w=k=>words[k]?.[language==='zh'?1:0]||k;
const number=n=>Number.isFinite(n)?n.toLocaleString():'?';
const time=t=>t?new Date(t).toLocaleTimeString():w('unknown');
function tell(s,error=false){$('message').textContent=s;$('message').className=error?'error':'';}
function el(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;}
// Replacement invitations are staff-only capability URLs. Keep them in memory, never in
// localStorage, diagnostics, public photo APIs, or the legacy manual-token panel.
const inlineRepairInvites=new Map();
const inlineRepairKey=(eventId,deviceId)=>eventId+':'+deviceId;
function discardInlineRepair(key){const previous=inlineRepairInvites.get(key);if(previous?.timeout)clearTimeout(previous.timeout);inlineRepairInvites.delete(key);}
function clearInlineRepairs(){for(const key of inlineRepairInvites.keys())discardInlineRepair(key);}
function rememberInlineRepair(eventId,deviceId,invite){
 const key=inlineRepairKey(eventId,deviceId),url=String(invite.pairing_url||''),expires=Number(invite.expires);
 if(!url.startsWith(location.origin+'/pair#')||!Number.isFinite(expires)||expires<=Date.now())throw Error('Invalid or expired replacement invitation. Try Re-pair again.');
 discardInlineRepair(key);
 const record={url,id:String(invite.id||''),expires:Math.min(expires,Date.now()+300000)};
 record.timeout=setTimeout(()=>{
   if(inlineRepairInvites.get(key)!==record)return;
   discardInlineRepair(key);
   for(const panel of document.querySelectorAll('.inline-repair-qr[data-repair-key]')){
     if(panel.dataset.repairKey===key)panel.replaceWith(el('p','Replacement QR expired. Use Re-pair again to create a new one.','muted'));
   }
 },Math.max(0,record.expires-Date.now())+50);
 inlineRepairInvites.set(key,record);
 return record;
}
function renderInlineRepair(card,eventId,device){
 const key=inlineRepairKey(eventId,device.id),record=inlineRepairInvites.get(key);
 card.querySelector('.inline-repair-qr')?.remove();
 if(!record)return null;
 if(Date.now()>=record.expires){discardInlineRepair(key);return null;}
 const panel=el('div',undefined,'inline-repair-qr');panel.dataset.repairKey=key;
 const heading=el('div',undefined,'inline-repair-heading');heading.append(el('strong','Replacement QR for '+device.label),el('span','5-minute staff invitation','badge good'));
 panel.append(heading,el('p','Use LensDrop Android → Staff Setup to scan this QR. Use the code shown on the phone to approve below. Do not share with guests.','muted'));
 const qr=el('div',undefined,'inline-repair-code');
 try{
   if(!window.LensDropQR?.toSvg)throw Error('QR library unavailable');
   qr.innerHTML=window.LensDropQR.toSvg(record.url);
   const svg=qr.querySelector('svg');if(!svg)throw Error('QR image unavailable');
   svg.setAttribute('role','img');svg.setAttribute('aria-label','Replacement pairing QR for '+device.label);
 }catch(_){qr.textContent='QR graphic unavailable; use Copy invitation below or reload Admin.';}
 panel.append(qr);
 const link=el('input');link.type='text';link.readOnly=true;link.value=record.url;link.setAttribute('aria-label','Replacement pairing invitation URL');link.setAttribute('autocomplete','off');link.spellcheck=false;
 const tools=el('div',undefined,'inline-repair-actions');
 const copy=el('button','Copy invitation','secondary small');copy.type='button';copy.onclick=async()=>{
   try{await navigator.clipboard.writeText(record.url);tell('Replacement invitation copied for '+device.label+'.');}
   catch(_){link.focus();link.select();tell('Browser clipboard blocked. Select and copy the invitation URL manually.',true);}
 };
 const approve=el('button','Approve 6-digit code','small');approve.type='button';approve.onclick=async()=>{
   const code=prompt('Enter the six-digit code displayed on THIS photographer phone (not in the QR):');
   if(code===null)return;
   if(!/^[0-9]{6}$/.test(code)){tell('Enter exactly six digits from the intended phone.',true);return;}
   if(!record.id||Date.now()>=record.expires){tell('Invitation expired; issue a new Re-pair QR.',true);return;}
   approve.disabled=true;
   try{
     await api('/v1/pairings/'+record.id+'/approve',{code});
     discardInlineRepair(key);panel.remove();tell('Re-pair approved for '+device.label+'. Existing photo/group history is retained.');
     await refresh();
   }catch(e){tell('Approval not completed: '+e.message,true);}
   finally{approve.disabled=false;}
 };
 const hide=el('button','Hide QR','secondary small');hide.type='button';hide.onclick=()=>{discardInlineRepair(key);panel.remove();tell('Replacement QR hidden locally. The invitation may remain valid until expiration.');};
 tools.append(copy,approve,hide);panel.append(link,tools);
 panel.append(el('p','Expires '+new Date(record.expires).toLocaleTimeString()+' (one use; old credential is revoked only after approved re-pair).','muted'));
 card.append(panel);return panel;
}
async function api(route,body){const r=await fetch(route,{method:body===undefined?'GET':'POST',headers:{Authorization:`Bearer ${$('key').value}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const data=await r.json();if(!r.ok)throw Error(data.error||`HTTP ${r.status}`);return data;}
function bind(id,fn){$(id).onclick=async()=>{$(id).disabled=true;try{await fn();}catch(e){tell(e.message,true);}finally{$(id).disabled=false;}};}
function download(name,obj){const a=el('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(obj,null,2)],{type:'application/json'}));a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),1000);}
function current(){const e=events.find(x=>x.id===$('events').value);if(!e)throw Error('Choose an event first.');return e;}
async function loadEvents(){const old=$('events').value;events=await api('/v1/events');$('events').replaceChildren(...events.map(e=>{const o=el('option',e.name+' / '+e.policy);o.value=e.id;o.textContent=e.name+' / '+(e.lifecycle||'active')+' / '+e.policy;return o;}));if(events.some(e=>e.id===old))$('events').value=old;currentBefore=null;if(events.length)await refresh();}
function renderStages(s){const total=status=>s.counts.find(c=>c.status===status)?.count||0;const known=s.devices.length>0&&s.devices.every(d=>d.source_counts_known);const received=known?s.devices.reduce((n,d)=>n+d.source_received,0):null;const verified=s.devices.reduce((n,d)=>n+d.verified_photos,0);const definitions=[['01',w('source'),received,known?'Client report / camera card not checked':w('unknown')],['02',w('cloud'),verified,`${total('review')} ${w('review')} / ${total('hidden')} ${w('hidden')}`],['03',w('live'),total('live'),'Published delivery images']];$('stages').replaceChildren(...definitions.map(([n,title,val,note])=>{const d=el('div',undefined,'stage');d.append(el('span',n,'step'),el('h3',title),el('strong',number(val)),el('p',note,'muted'));return d;}));$('stageNote').textContent=language==='zh'?'\u5df2\u63a5\u6536\u6578\u4f86\u81ea\u624b\u6a5f\u56de\u5831\uff0c\u53ef\u80fd\u5df2\u904e\u6642\u3002\u96f2\u7aef\u8a08\u7b97\u7368\u7acb\u7167\u7247\uff0c\u91cd\u8907\u4e0a\u50b3\u4e0d\u6703\u91cd\u8907\u8a08\u76f8\u3002':'Source counts can be stale. Cloud verified counts unique photos; repeat upload jobs may reconcile to fewer photos.';}
async function refresh(){const event=current(),request=++generation;const s=await api(`/v1/events/${event.id}/status${currentBefore?'?before='+currentBefore:''}`);if(request!==generation||event.id!==$('events').value)return;nextBefore=s.next_before;$('older').hidden=!nextBefore;const a=el('a',w('openGallery'));a.href='/g/'+event.slug;a.target='_blank';a.rel='noopener';$('gallery').replaceChildren(a);renderStages(s);window.LensDropOperations?.updatePhotographers(s.devices);window.LensDropOperations?.updateCloud(true);$('updated').textContent=time(Date.now());$('testCount').textContent=`${s.test_count} ${w('tests')}`;
$('status').replaceChildren(...s.devices.filter(d=>!d.admin_hidden||$('showArchivedPhotographers').checked).map(d=>{const c=el('article',undefined,'device'),h=el('div',undefined,'sectionhead');c.dataset.deviceId=d.id;h.append(el('strong',d.label),el('span',d.revoked?'Revoked':w(d.heartbeat_fresh?'fresh':'stale'),`badge ${d.revoked?'bad':d.heartbeat_fresh?'good':'neutral'}`));c.append(h,el('p',`Personal QR available: ${number(d.personal_available)} / Unassigned archives: ${number(d.unassigned_archives)} / Guest links pending: ${number(d.pending_assignments)} / Candidates: ${number(d.candidates)} / Skipped: ${number(d.skipped)}`,'muted'),el('p',`${w('source')}: ${number(d.source_received)} / ${w('cloud')}: ${number(d.verified_photos)} / ${w('live')}: ${number(d.live)}`),el('p',`${number(d.queued)} ${w('queued')} / ${number(d.blocked)} ${w('blocked')}`));let hb={};try{hb=JSON.parse(d.heartbeat);}catch{}c.append(el('p',`${w('lastReceived')}: ${time(hb.last_received)} / ${d.tests.verified} ${w('tests')}`,'muted'));if(!d.revoked){const b=el('button',language==='zh'?'\u64a4\u92b7\u6b64\u624b\u6a5f\u6191\u8b49':'Revoke credential','secondary small');b.onclick=async()=>{if(!confirm('Revoke '+d.label+'? Outstanding work will need a valid credential for the same identity.'))return;try{await api(`/v1/devices/${d.id}/revoke`,{});await refresh();}catch(e){tell(e.message,true);}};c.append(b);}const hide=el('button',d.admin_hidden?'Show photographer':'Hide photographer','secondary small');hide.onclick=async()=>{if(!confirm((d.admin_hidden?'Show':'Hide')+' '+d.label+' in Admin? This does not revoke credentials or remove photos.'))return;try{await api('/v1/devices/'+d.id+'/admin-visibility',{hidden:!d.admin_hidden});await refresh();}catch(e){tell(e.message,true);}};c.append(hide);
const rename=el('button','Rename photographer','secondary small');rename.onclick=async()=>{const name=prompt('Photographer display name (does not change history):',d.label);if(!name||name===d.label)return;try{await api('/v1/devices/'+d.id+'/rename',{label:name});await refresh();}catch(e){tell(e.message,true);}};c.append(rename);
if(!d.admin_archived){
 const repair=el('button',d.revoked?'Re-pair & restore (5-minute QR)':'Re-pair this phone (5-minute QR)','secondary small');repair.onclick=async()=>{
   if(!confirm('Issue a NEW, one-time 5-minute QR for '+d.label+'? Admin must verify the code displayed on the phone. On approval, the old credential stops working; existing photo/group history stays assigned to THIS device.'))return;
   repair.disabled=true;
   try{
     const invite=await api('/v1/events/'+event.id+'/pairings',{kind:'phone',label:d.label,origin:location.origin,permissions:{},replace_device_id:d.id,reactivate:!!d.revoked});
     // Show the invitation beside the button that requested it, NOT in Setup or
     // the hidden legacy manual-token form. A 15s Overview refresh recreates
     // cards, so the short-lived in-memory entry is rendered again on refresh.
     if($('events').value!==event.id){tell('Invitation created for a different event; switch back and issue a new QR.',true);return;}
     rememberInlineRepair(event.id,d.id,invite);
     const card=Array.from($('status').querySelectorAll('article.device')).find(x=>x.dataset.deviceId===d.id);
     const panel=card&&renderInlineRepair(card,event.id,d);
     if(!panel){tell('Replacement invitation issued but its card is no longer visible. Refresh Photographer Status to display it.',true);return;}
     panel.scrollIntoView({behavior:'smooth',block:'nearest'});
     tell(panel.querySelector('svg')?'Replacement QR is now inside '+d.label+'’s photographer block. Scan in Android Staff Setup and approve the six-digit code here.':'Replacement link is ready inside '+d.label+'’s block, but QR drawing failed; use Copy invitation or reload the page.',!panel.querySelector('svg'));
   }catch(e){tell(e.message,true);}
   finally{repair.disabled=false;}
 };c.append(repair);
}
if(!d.admin_archived){const archive=el('button','Archive & revoke device','secondary small');archive.onclick=async()=>{if(!confirm('Archive and REVOKE '+d.label+'? Photos and history remain. This phone cannot reconnect without a NEW pairing identity.'))return;try{await api('/v1/devices/'+d.id+'/archive',{confirm:'ARCHIVE_AND_REVOKE'});await refresh();}catch(e){tell(e.message,true);}};c.append(archive);}else {
 c.append(el('p','Archived and revoked; retained for event audit.','muted'));
 const remove=el('button','Permanently delete EMPTY record','secondary small danger');remove.onclick=async()=>{
  if(!confirm('Permanently delete EMPTY archived device '+d.label+'? This cannot remove any device with uploads, photographs, groups, or print history.'))return;
  const id=prompt('Type the FULL device ID to confirm:',d.id);if(id!==d.id)return;
  try{await api('/v1/devices/'+d.id+'/delete-empty',{confirm_id:id,confirm_label:d.label});await refresh();tell('Empty device removed.');}catch(e){tell(e.message+' — Keep this device archived if it has history.',true);}
 };c.append(remove);
}renderInlineRepair(c,event.id,d);return c;}));
const v=s.visibility||{};
const summary=document.getElementById('visibilitySummary');if(summary)summary.textContent=`Cloud stored ${v.cloud_stored??'-'}  |  Review ${v.review??'-'}  |  Public gallery ${v.public_gallery??'-'}  |  Slideshow ${v.projector??'-'}`;
const lifecycle=document.getElementById('eventLifecycle');if(lifecycle){lifecycle.textContent=`Event: ${v.lifecycle||'active'}`;lifecycle.className='badge '+(v.lifecycle==='active'?'good':'neutral');}
const table=el('table');table.className='admin-photo-table';const head=el('tr');for(const text of ['PHOTO / FILE',w('state'),'Projector',w('actions')])head.append(el('th',text));table.append(head);for(const photo of s.photos){const row=el('tr'),first=el('td'),thumbButton=el('button',undefined,'photo-thumb-button'),thumb=el('img');thumb.dataset.photoId=photo.id;thumb.alt='Private thumbnail for '+photo.filename;thumb.loading='lazy';thumb.width=78;thumb.height=78;thumbButton.type='button';thumbButton.setAttribute('aria-label','Enlarge '+photo.filename);thumbButton.title='Enlarge photo';thumbButton.append(thumb);thumbButton.onclick=()=>window.LensDropPresentation?.preview(photo);first.append(thumbButton,el('span',photo.filename,'photo-file-name'));row.append(first,el('td',photo.is_test?'TEST / never public':photo.status));const projector=el('td',photo.is_test?'TEST':photo.projector_visible?'On':'Off');row.append(projector);
const td=el('td');const project=el('button',photo.projector_visible?'Remove from slideshow':'Show on slideshow','secondary small');
project.disabled=!!photo.is_test||photo.status==='hidden';
project.onclick=async()=>{if(!photo.projector_visible&&!confirm('Show this photo on the public projector? This does NOT publish it to the public gallery.'))return;try{await api(`/v1/photos/${photo.id}/projector`,{visible:!photo.projector_visible});await refresh();}catch(e){tell(e.message,true);}};td.append(project);
const star=el('button',photo.starred?'★ Unstar':'☆ Star','secondary small');star.onclick=async()=>{try{await api('/v1/photos/'+photo.id+'/controls',{starred:!photo.starred});await refresh();}catch(e){tell(e.message,true);}};td.append(star);
const preview=el('button',language==='zh'?'\u9810\u89bd':'Preview','secondary small');preview.onclick=()=>window.LensDropPresentation?.preview(photo);td.append(preview);for(const state of ['live','review','hidden']){const b=el('button',state,'secondary small');b.disabled=photo.status===state||!!photo.is_test;b.onclick=async()=>{try{await api(`/v1/photos/${photo.id}/status`,{status:state});await refresh();}catch(e){tell(e.message,true);}};td.append(b);}row.append(td);table.append(row);}$('photos').replaceChildren(table);window.LensDropThumbnails?.mount(table);window.LensDropPresentation?.select(event.id);window.LensDropPresentation?.updateCounts(s.visibility);window.LensDropPersonal?.select(event.id);window.LensDropOnboarding?.select(event.id);window.LensDropStaffDesk?.select(event.id);window.LensDropPhotoControls?.select(event.id);}
bind('connect',async()=>{await loadEvents();tell('Connected. Key stays in this page only.');});
bind('create',async()=>{const e=await api('/v1/events',{name:$('eventName').value,policy:$('policy').value});await loadEvents();$('events').value=e.id;currentBefore=null;await refresh();tell('Event created.');});
bind('pair',async()=>{const e=current(),d=await api(`/v1/events/${e.id}/devices`,{label:$('deviceLabel').value});$('credential').textContent=JSON.stringify({server:location.origin,...d},null,2);$('credential').hidden=false;await refresh();tell('Save the token securely; it cannot be retrieved later.');});
bind('refresh',refresh);bind('newest',async()=>{currentBefore=null;await refresh();});bind('older',async()=>{currentBefore=nextBefore;await refresh();});
bind('deploymentCheck',async()=>{$('deploymentDetails').textContent=JSON.stringify(await api('/v1/admin/deployment'),null,2);$('deploymentDetails').hidden=false;tell('A health check does not prove persistence. Perform the restart and redeploy checks.');});
bind('wrapup',async()=>{const r=await api(`/v1/events/${current().id}/reconciliation`);$('report').hidden=false;$('report').textContent=(r.all_known_files_reconciled?'KNOWN FILE COUNTS MATCH':'NOT YET RECONCILED')+'\n'+JSON.stringify(r.counts,null,2)+'\nKeep originals. Camera-card completeness is not established.';download('LensDrop-wrapup-H5.json',r);});
bind('diagnostics',async()=>download('LensDrop-diagnostics-H5.json',await api(`/v1/events/${current().id}/diagnostics`)));
$('showArchivedPhotographers').onchange=()=>refresh().catch(e=>tell(e.message,true));
$('events').onchange=()=>{clearInlineRepairs();generation++;currentBefore=null;$('credential').hidden=true;$('report').hidden=true;refresh().catch(e=>tell(e.message,true));};
$('key').addEventListener('input',clearInlineRepairs);
$('language').onclick=()=>{language=language==='en'?'zh':'en';document.documentElement.lang=language==='zh'?'zh-Hant':'en';document.querySelectorAll('[data-i]').forEach(e=>e.textContent=(language==='zh'?zh:en)[e.dataset.i]);$('policy').options[0].textContent=language==='zh'?'\u5148\u5be9\u5f8c\u516c\u958b':'Review first';$('policy').options[1].textContent=language==='zh'?'\u9a57\u8b49\u5f8c\u5373\u6642\u516c\u958b':'Publish after verification';if(events.length)refresh().catch(e=>tell(e.message,true));};
$('language').textContent='English / \u7e41\u9ad4\u4e2d\u6587';

window.LensDropAdmin={api,current,tell,loadEvents,refresh,get language(){return language;},asset:async(route,options={})=>fetch(route,{...options,headers:{Authorization:`Bearer ${$('key').value}`,...options.headers}})};

let staffDesk;
window.LensDropStaffDesk={select(eventId){if(staffDesk?.scope===eventId)return;staffDesk?.destroy();staffDesk=window.LuceStaffDesk.mount(document.getElementById('staffDesk'),{scope:eventId,admin:true,api:(path,body)=>window.LensDropAdmin.api('/v1/events/'+eventId+'/staff/'+path,body),asset:path=>window.LensDropAdmin.asset('/v1/events/'+eventId+'/staff/'+path),workflow:body=>window.LensDropAdmin.api('/v1/events/'+eventId+'/workflow',body),permission:(station,body)=>window.LensDropAdmin.api('/v1/events/'+eventId+'/stations/'+station+'/staff-desk',body)});}};

const lifecycleButton=document.getElementById('changeLifecycle');
if(lifecycleButton)lifecycleButton.onclick=async()=>{try{const e=current();const choices={active:'closed',closed:'archived',archived:'closed'};const next=choices[e.lifecycle||'active'];if(!next)return;const name=prompt(`Change ${e.name} from ${e.lifecycle||'active'} to ${next}? Type exact event name to confirm:`);if(name!==e.name)return;await api(`/v1/events/${e.id}/lifecycle`,{lifecycle:next,confirm_name:name});await loadEvents();tell('Event state updated. No media or guest links deleted.');}catch(ex){tell(ex.message,true);}};
const reopenButton=document.getElementById('reopenLifecycle');
if(reopenButton)reopenButton.onclick=async()=>{try{const e=current();if(e.lifecycle!=='closed')throw Error('Only a closed event can be reopened. Archived events must first return to Closed.');const name=prompt('Type event name to reopen:');if(name!==e.name)return;await api(`/v1/events/${e.id}/lifecycle`,{lifecycle:'active',confirm_name:name});await loadEvents();tell('Event reopened.');}catch(ex){tell(ex.message,true);}};

const deleteEmpty=document.getElementById('deleteEmptyEvent');
if(deleteEmpty)deleteEmpty.onclick=async()=>{try{const e=current();if(e.lifecycle!=='archived')throw Error('Archive the empty event first.');const name=prompt('Delete EMPTY archived event only. Type the event name:');if(name!==e.name)return;const id=prompt('Type the full event ID to confirm permanent deletion:');if(id!==e.id)return;await api(`/v1/events/${e.id}/delete-empty`,{confirm_id:id,confirm_name:name});await loadEvents();tell('Empty archived event deleted.');}catch(ex){tell(ex.message,true);}};
