'use strict';
(()=>{
  const $=id=>document.getElementById(id);let eventId=null,offset=0,next=null,selected=null,epoch=0;
  const api=(p,b)=>window.LensDropAdmin.api(p,b);
  const say=t=>$('personalAdminMessage').textContent=t;
  async function load(){
    if(!eventId)return;const id=eventId,ticket=++epoch;try{const [prefs,data]=await Promise.all([api('/v1/events/'+id+'/personal-settings'),api('/v1/events/'+id+'/deliveries?offset='+offset)]);if(ticket!==epoch||id!==eventId)return;
      $('personalAuto').checked=prefs.auto_release;next=data.next_offset;$('personalOlder').hidden=next===null;
      const nodes=data.deliveries.map(d=>{const row=document.createElement('div');row.className='device';const title=document.createElement('strong');title.textContent=d.label+' / '+d.kind+' / '+d.state+' / '+d.device_label;const details=document.createElement('p');details.textContent=d.verified+' cloud verified / '+d.available+' personally available';const button=document.createElement('button');button.className='secondary';button.textContent='QR / review personal delivery';button.onclick=()=>detail(d.id);row.append(title,details,button);return row;});$('personalDeliveries').replaceChildren(...nodes);say(data.deliveries.length?'Personal QR history loaded.':'No personal deliveries yet. Create groups from the H4 Android source or the supplied API smoke tool.');
    }catch(e){say(e.message);}
  }
  async function detail(id){const eid=eventId;try{const d=await api('/v1/deliveries/'+id);if(eid!==eventId)return;selected=d.id;const area=$('personalDetails');area.replaceChildren();const h=document.createElement('h3');h.textContent=d.label+' / '+d.state;const link=document.createElement('a');link.href=d.guest_path;link.textContent='Open this personal QR page';link.target='_blank';link.rel='noopener noreferrer';const qr=document.createElement('div');qr.className='personal-qr';qr.innerHTML=window.LensDropQR.toSvg(location.origin+d.guest_path);area.append(h,link,qr);
      const revoke=document.createElement('button');revoke.textContent='Revoke this personal link';revoke.className='secondary';revoke.disabled=d.state==='cancelled';revoke.onclick=async()=>{if(!confirm('Revoke this personal link? It cannot be re-used.'))return;try{await api('/v1/deliveries/'+id+'/revoke',{});await detail(id);await load();}catch(e){say(e.message);}};area.append(revoke);
      for(const p of d.photos){const row=document.createElement('div');row.className='device';const name=document.createElement('p');name.textContent=p.filename+' / gallery: '+p.status+' / personal release: '+(p.released?'yes':'no');const preview=document.createElement('button');preview.textContent='Preview';preview.className='secondary';preview.onclick=()=>window.LensDropPresentation?.preview(p);const release=document.createElement('button');release.textContent=p.released?'Withdraw personal delivery':'Release to this personal QR only';release.disabled=d.state==='cancelled'||p.status==='hidden';release.onclick=async()=>{try{await api('/v1/photos/'+p.id+'/personal-release',{released:!p.released});await detail(id);await load();}catch(e){say(e.message);}};row.append(name,preview,release);area.append(row);}
      if(d.photos.length===200){const note=document.createElement('p');note.textContent='First 200 photos shown here. Use normal photo pagination for larger groups.';area.append(note);}
    }catch(e){say(e.message);}}
  $('personalSave').onclick=async()=>{if(!eventId)return;try{await api('/v1/events/'+eventId+'/personal-settings',{auto_release:$('personalAuto').checked});say('Saved for future personal uploads. Existing photos are unchanged; public gallery/slideshow publication is separate.');}catch(e){say(e.message);}};
  $('personalReload').onclick=()=>{offset=0;load();};$('personalOlder').onclick=()=>{offset=next;load();};
  window.LensDropPersonal={select(id){if(id!==eventId){eventId=id;offset=0;selected=null;$('personalDetails').replaceChildren();}load();}};
})();
