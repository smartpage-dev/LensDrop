"use strict";
(()=>{
 const $=id=>document.getElementById(id);const el=(tag,text,cl)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cl)n.className=cl;return n;};
 let key='',events=[],current=null,invites=new Map(),allHistory=false,timeout=null;
 const note=(t,error=false)=>{$('notice').textContent=t;$('notice').className=error?'error':'';};
 async function api(path,body){if(!key)throw Error('Admin key required');const r=await fetch(path,{method:body===undefined?'GET':'POST',cache:'no-store',headers:{Authorization:'Bearer '+key,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
  const data=await r.json();if(!r.ok)throw Error(data.error||data.err||'HTTP '+r.status);return data;}
 function fail(e){note(String(e?.message||e),true);}
 function action(title,fn,cl='secondary'){const b=el('button',title,cl);b.type='button';b.onclick=async()=>{b.disabled=true;try{await fn();}catch(e){fail(e);}finally{b.disabled=false;}};return b;}
 function clearInvites(){invites.clear();$('newInvitation').replaceChildren();}
 function inviteCard(inv,target,kind){target.replaceChildren();if(!inv||inv.expires<=Date.now())return;
  const block=el('div',undefined,'invite');block.append(el('h4',(kind==='replacement'?'Replacement':'New')+' pairing QR'),el('small','Scan in LensDrop Photographer App → Staff Setup. Valid until '+new Date(inv.expires).toLocaleTimeString()+'.'));
  const qr=el('div',undefined,'qr');try{qr.innerHTML=window.LensDropQR.toSvg(inv.pairing_url);}catch{qr.textContent='QR graphic unavailable. Use URL below.';}block.append(qr);
  const link=el('input');link.readOnly=true;link.value=inv.pairing_url;link.setAttribute('aria-label','Private pairing invitation');block.append(link);
  const actions=el('div',undefined,'actions');actions.append(action('Copy invitation',async()=>{await navigator.clipboard.writeText(inv.pairing_url);note('Invitation copied — share with intended photographer only.');}),action('Approve 6-digit code',async()=>{const code=prompt('Type six digits shown in the photographer app.');if(code===null)return;if(!/^[0-9]{6}$/.test(code))throw Error('Enter exactly 6 digits.');await api('/v1/pairings/'+inv.id+'/approve',{code});invites.delete(inv.key);block.remove();note('Approved. Existing device/group history remains assigned.');await refresh();},'primary'));
  block.append(actions);target.append(block);
 }
 function drawActiveInvites(){for(const [k,inv] of invites){if(Date.now()>=inv.expires){invites.delete(k);(k==='new'?$('newInvitation'):document.querySelector('[data-invite-key="'+CSS.escape(k)+'"]'))?.replaceChildren(el('p','QR expired. Create a new invitation.'));}else{const target=document.querySelector('[data-invite-key="'+CSS.escape(k)+'"]');if(target&&!target.children.length)inviteCard(inv,target,inv.kind);}}}
 async function connect(){const supplied=$('adminKey').value.trim();if(!supplied)throw Error('Enter Admin Key first.');key=supplied;$('adminKey').value='';try{events=await api('/v1/events');$('event').replaceChildren(...events.map(e=>{const opt=new Option(e.name+' · '+(e.lifecycle||'active'),e.id);return opt;}));$('loginPanel').hidden=true;$('workspace').hidden=false;if(events.length)await refresh();note('Connected. Key is kept in session memory only.');}catch(e){key='';throw e;}}
 async function refresh(){const id=$('event').value;current=events.find(e=>e.id===id);if(!current)return;const [s,inv]=await Promise.all([api('/v1/events/'+id+'/status'),api('/v1/events/'+id+'/pairings')]);if($('event').value!==id)return;
  $('summary').textContent=current.name+' · '+s.devices.length+' devices · '+(s.visibility?.cloud_stored??'-')+' photos';
  const active=s.devices.filter(d=>!d.admin_archived);$('devices').replaceChildren(...active.map(d=>{
   const card=el('article',undefined,'device');card.append(el('h3',d.label+(d.revoked?' · REVOKED':'')),el('p',(d.verified_photos||0)+' verified photos · '+(d.unassigned_archives||0)+' unassigned · '+(d.heartbeat_fresh?'Online recently':'Offline/stale')));
   const actions=el('div',undefined,'actions'),qrSlot=el('div');qrSlot.dataset.inviteKey='re:'+d.id;
   actions.append(action(d.revoked?'Re-pair & restore':'Re-pair this phone',async()=>{
     if(!confirm('Replace the credential for '+d.label+'? Preserve its existing Device ID and photo history?'))return;
     const response=await api('/v1/events/'+id+'/pairings',{kind:'phone',label:d.label,origin:location.origin,permissions:{},replace_device_id:d.id,reactivate:!!d.revoked});
     if(!response.pairing_url?.startsWith(location.origin+'/pair#'))throw Error('Invalid invitation origin');const entry={...response,kind:'replacement',key:'re:'+d.id};invites.set(entry.key,entry);inviteCard(entry,qrSlot,'replacement');
   },'primary'));
   if(!d.revoked)actions.append(action('Revoke',async()=>{if(!confirm('Revoke '+d.label+'? Offline access may persist temporarily.'))return;await api('/v1/devices/'+d.id+'/revoke',{});await refresh();},'quiet danger'));
   card.append(actions,qrSlot);return card;}));
  drawActiveInvites();
  const currentTime=Date.now(),sorted=[...inv].sort((a,b)=>(b.created||b.expires||0)-(a.created||a.expires||0));
  const needs=i=>i.state==='claimed'&&i.expires>currentTime&&(i.approval_failures||0)<5;
  const visible=sorted.filter((i,idx)=>allHistory||needs(i)||idx<4);
  $('pending').replaceChildren(...visible.map(i=>{const row=el('div',undefined,'pairRow');row.append(el('strong',i.label+' · '+i.state),el('p',needs(i)?'Waiting for six-digit Admin approval':'Historical/expired invitation'));
   if(needs(i))row.append(action('Approve matching code',async()=>{const code=prompt('Six-digit code from intended phone:');if(code===null)return;if(!/^[0-9]{6}$/.test(code))throw Error('Exactly 6 digits required');await api('/v1/pairings/'+i.id+'/approve',{code});await refresh();}));return row;}));
 }
 $('connect').onclick=()=>connect().catch(fail);$('refresh').onclick=()=>refresh().catch(fail);$('event').onchange=()=>{clearInvites();refresh().catch(fail);};
 $('logout').onclick=()=>{key='';current=null;events=[];clearInvites();$('devices').replaceChildren();$('pending').replaceChildren();$('workspace').hidden=true;$('loginPanel').hidden=false;note('Logged out. Admin key cleared from page memory.');};
 $('newPair').onclick=async()=>{const b=$('newPair');b.disabled=true;try{if(!current)throw Error('Select event');const label=$('newLabel').value.trim();if(!label)throw Error('Photographer name required');const inv=await api('/v1/events/'+current.id+'/pairings',{kind:'phone',label,origin:location.origin,permissions:{}});if(!inv.pairing_url?.startsWith(location.origin+'/pair#'))throw Error('Invalid invitation origin');inv.kind='new';inv.key='new';invites.set('new',inv);inviteCard(inv,$('newInvitation'),'new');}catch(e){fail(e);}finally{b.disabled=false;}};
 $('allHistory').onclick=()=>{allHistory=!allHistory;$('allHistory').textContent=allHistory?'Show recent':'Show all';refresh().catch(fail);};
 // Do not persist credentials or pairing capabilities in localStorage / IndexedDB.
 timeout=setInterval(()=>{if(!document.hidden&&key){drawActiveInvites();}},1000);
})();
