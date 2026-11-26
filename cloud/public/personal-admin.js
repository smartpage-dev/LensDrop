"use strict";
(() => {
  const $=id=>document.getElementById(id);
  const api=(p,b)=>window.LensDropAdmin.api(p,b);
  const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
  let eventId=null,offset=0,next=null,selected=null,epoch=0;
  const say=t=>{$('personalAdminMessage').textContent=t;};
  function detailSlot(row,id){const slot=el('div',undefined,'personal-inline-detail');slot.dataset.deliveryId=id;slot.hidden=true;row.append(slot);return slot;}
  async function load(){
    if(!eventId)return;const id=eventId,ticket=++epoch;
    try{
      const [prefs,data]=await Promise.all([api('/v1/events/'+id+'/personal-settings'),api('/v1/events/'+id+'/deliveries?offset='+offset)]);
      if(ticket!==epoch||id!==eventId)return;
      $('personalAuto').checked=!!prefs.auto_release;next=data.next_offset;$('personalOlder').hidden=next===null;
      const nodes=data.deliveries.map(d=>{
        const row=el('article',undefined,'personal-delivery-card');row.dataset.deliveryId=d.id;
        const top=el('div',undefined,'personal-delivery-top'),labels=el('div',undefined,'personal-delivery-labels');
        labels.append(el('strong',d.label+' · '+d.kind),el('small',(d.device_label||'Photographer')+' · '+d.state));
        const badge=el('span',d.available+' / '+d.verified+' available','personal-count');top.append(labels,badge);
        const b=el('button',selected===d.id?'Close QR & details':'View QR & photos','secondary personal-view');b.type='button';b.setAttribute('aria-expanded',String(selected===d.id));
        const slot=detailSlot(row,d.id);
        b.onclick=()=>{if(selected===d.id){selected=null;slot.hidden=true;b.textContent='View QR & photos';b.setAttribute('aria-expanded','false');}
          else{selected=d.id;const all=$('personalDeliveries');for(const x of all.querySelectorAll('.personal-inline-detail')){x.hidden=true;x.replaceChildren();}for(const x of all.querySelectorAll('.personal-view')){x.textContent='View QR & photos';x.setAttribute('aria-expanded','false');}
          slot.hidden=false;b.textContent='Close QR & details';b.setAttribute('aria-expanded','true');detail(d.id,slot);}};
        row.prepend(top,el('p',d.verified+' verified Cloud photos · '+d.available+' personally released','personal-mini'),b);
        return row;
      });
      $('personalDeliveries').replaceChildren(...nodes);$('personalDetails').replaceChildren();
      if(selected){const row=nodes.find(n=>n.dataset.deliveryId===selected);if(row){const slot=row.querySelector('.personal-inline-detail');slot.hidden=false;row.querySelector('.personal-view').textContent='Close QR & details';row.querySelector('.personal-view').setAttribute('aria-expanded','true');detail(selected,slot);}else selected=null;}
      say(data.deliveries.length?'Select a delivery to review its private QR and photos in the SAME card.':'No personal deliveries yet.');
    }catch(e){say(e.message);}
  }
  async function detail(id,slot){const expectedEvent=eventId;
    slot.hidden=false;slot.replaceChildren(el('p','Loading private delivery...','muted'));
    try{
      const d=await api('/v1/deliveries/'+id);if(eventId!==expectedEvent||selected!==id||!slot.isConnected)return;
      const url=new URL(d.guest_path,location.origin);
      if(url.origin!==location.origin||!/^\/p\/[0-9a-f]{32}$/.test(url.pathname)||url.search||url.hash)throw Error('Invalid private guest URL; cannot show QR safely.');
      slot.replaceChildren();const heading=el('div',undefined,'personal-inline-heading');heading.append(el('h3',d.label+' · '+d.state));
      const qr=el('div',undefined,'personal-qr');qr.innerHTML=window.LensDropQR.toSvg(url.href);
      const open=el('a','Open private guest page','button-link');open.href=url.href;open.target='_blank';open.rel='noopener noreferrer';
      const copy=el('button','Copy private link','secondary');copy.onclick=async()=>{try{await navigator.clipboard.writeText(url.href);say('Private link copied. Share only with intended guest.');}catch(e){say('Clipboard unavailable; open private page to copy link.');}};
      const actions=el('div',undefined,'personal-inline-actions');actions.append(open,copy);
      const summary=el('div',undefined,'personal-inline-summary');summary.append(qr,actions);
      const revoke=el('button','Revoke this personal link','secondary danger');revoke.disabled=d.state==='cancelled';revoke.onclick=async()=>{if(!confirm('Revoke this personal link? Existing printed QR will stop working.'))return;try{await api('/v1/deliveries/'+id+'/revoke',{});selected=null;await load();say('Personal link revoked.');}catch(e){say(e.message);}};
      const list=el('div',undefined,'personal-inline-photos');list.append(el('h4','Photos in this delivery'));
      for(const p of d.photos||[]){const item=el('div',undefined,'personal-photo-row'),txt=el('div',undefined,'personal-photo-name');txt.append(el('strong',p.filename),el('small','Gallery: '+p.status+' · Personal release: '+(p.released?'YES':'NO')));
        const preview=el('button','Enlarge','secondary small');preview.onclick=()=>window.LensDropPresentation?.preview(p);
        const release=el('button',p.released?'Withdraw personal access':'Release to THIS QR','secondary small');release.disabled=d.state==='cancelled'||p.status==='hidden';release.onclick=async()=>{try{await api('/v1/photos/'+p.id+'/personal-release',{released:!p.released});await detail(id,slot);}catch(e){say(e.message);}};
        item.append(txt,preview,release);list.append(item);}
      if(d.photos?.length===200)list.append(el('p','First 200 photos shown; use normal pagination for larger groups.','muted'));
      slot.append(heading,summary,list,revoke);
    }catch(e){if(eventId===expectedEvent&&slot.isConnected)slot.replaceChildren(el('p','Cannot load QR: '+e.message,'error'));}
  }
  $('personalSave').onclick=async()=>{if(!eventId)return;try{await api('/v1/events/'+eventId+'/personal-settings',{auto_release:$('personalAuto').checked});say('Saved for FUTURE personal uploads. Existing photos and public gallery settings remain unchanged.');}catch(e){say(e.message);}};
  $('personalReload').onclick=()=>{offset=0;load();};$('personalOlder').onclick=()=>{offset=next;selected=null;load();};
  window.LensDropPersonal={select(id){if(id!==eventId){eventId=id;offset=0;selected=null;$('personalDetails').replaceChildren();}load();}};
})();
