"use strict";
(()=>{const $=id=>document.getElementById(id);let eventId=null,revision=0;
async function api(path,body){const r=await fetch(path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+$('key').value,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined,cache:'no-store'});const j=await r.json();if(!r.ok)throw Error(j.error||r.status);return j;}
function message(t){$('h510PolicyMessage').textContent=t;}
async function select(id){eventId=id;try{const p=await api('/v1/events/'+id+'/photo-policy');if(eventId!==id)return;revision=p.revision;$('h510GalleryAuto').checked=p.gallery_auto;$('h510ProjectorAuto').checked=p.projector_auto;$('h510GuestHide').checked=p.guest_hide_enabled;message(p.configured?'Saved event policy loaded.':'Legacy policy active. Save to opt into explicit per-channel policy.');}catch(e){message(e.message);}}
$('h510SavePolicy').onclick=async()=>{if(!eventId)return;try{const p=await api('/v1/events/'+eventId+'/photo-policy',{gallery_auto:$('h510GalleryAuto').checked,projector_auto:$('h510ProjectorAuto').checked,guest_hide_enabled:$('h510GuestHide').checked,revision});revision=p.revision;message('Saved. Future eligible photos use these settings. No old photos changed.');}catch(e){message(e.message);}};
$('h510ReloadPolicy').onclick=()=>eventId&&select(eventId);
$('h510ExportLogs').onclick=async()=>{if(!eventId)return;try{const d=await api('/v1/events/'+eventId+'/diagnostic-export');const url=URL.createObjectURL(new Blob([JSON.stringify(d,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='LensDrop-event-diagnostics-'+eventId.slice(0,8)+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),3000);message('Private diagnostic export created. Check contents before sharing.');}catch(e){message(e.message);}};
window.LensDropPhotoControls={select,api};
})();
