'use strict';
(()=>{
  const token=location.pathname.split('/').pop(),base='/v1/p/'+token,$=id=>document.getElementById(id);
  let before=null,next=null,busy=false,lastOk=0,signature='',selected=null,available=new Set();
  function clear(){ $('personalPhotos').replaceChildren();$('personalLogo').hidden=true;$('personalLogo').removeAttribute('src');$('personalPreview').close();$('personalImage').removeAttribute('src');$('personalDownload').removeAttribute('href');available.clear();signature=''; }
  async function refresh(){
    if(busy)return;busy=true;
    try{
      const r=await fetch(base+(before?'?before='+before:''),{cache:'no-store'});if(!r.ok)throw Error(r.status===404?'Preparing your link, or the link is no longer available. This page will automatically retry.':'Connection interrupted.');const d=await r.json();lastOk=Date.now();
      $('eventName').textContent=d.name;$('deliveryLabel').textContent=d.label+' / '+(d.kind==='single'?'Single selected photograph':'Your group');
      $('personalState').textContent=d.delivery_status==='needs_staff_attention'?'Please contact the photographer: this photo needs identity review.':d.photos.length?'Your photographs are ready.':d.delivery_status==='awaiting_approval'?'Photo uploaded. Awaiting staff release; this page will update automatically.':'Preparing your photographs. No need to scan again; this page checks automatically.';
      if(d.branding?.logo_path){if($('personalLogo').getAttribute('src')!==d.branding.logo_path)$('personalLogo').src=d.branding.logo_path;$('personalLogo').hidden=false;}else $('personalLogo').hidden=true;
      available=new Set(d.photos.map(p=>p.id));if(selected&&!available.has(selected)){$('personalPreview').close();$('personalImage').removeAttribute('src');selected=null;}
      next=d.next_before;$('olderPage').hidden=!next;
      const value=JSON.stringify(d.photos);if(value!==signature){signature=value;const fragment=document.createDocumentFragment();for(const p of d.photos){const button=document.createElement('button');button.className='personal-photo secondary';button.setAttribute('aria-label','Open photograph');const img=document.createElement('img');img.loading='lazy';img.src=base+'/photos/'+p.id+'/thumb';img.alt='Delivered photograph';button.append(img);button.onclick=()=>{selected=p.id;$('personalImage').src=base+'/photos/'+p.id+'/delivery';$('personalDownload').href=base+'/photos/'+p.id+'/delivery?download=1';$('personalPreview').showModal();};fragment.append(button);}$('personalPhotos').replaceChildren(fragment);}
    }catch(e){$('personalState').textContent=e.message;if(!lastOk||Date.now()-lastOk>30000||e.message.includes('unavailable'))clear();}
    finally{busy=false;}
  }
  const guestActions=document.createElement('div');guestActions.className='actions';for(const [channel,label] of [['gallery_visible','Hide this photo from public event gallery'],['projector_visible','Hide this photo from public slideshow']]){const b=document.createElement('button');b.textContent=label;b.className='secondary';b.onclick=async()=>{if(!selected||!confirm(label+'? Your private guest photo remains available.'))return;b.disabled=true;try{const r=await fetch(base+'/photos/'+selected+'/visibility',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({channel,visible:false})});if(!r.ok)throw Error('Could not change public visibility. Contact staff.');$('personalState').textContent='Public visibility updated. Your private photo remains available.';}catch(e){$('personalState').textContent=e.message;}finally{b.disabled=false;}};guestActions.append(b);}document.querySelector('#personalPreview').append(guestActions);
  $('closePersonal').onclick=()=>{$('personalPreview').close();$('personalImage').removeAttribute('src');selected=null;};
  $('latestPage').onclick=()=>{before=null;refresh();};$('olderPage').onclick=()=>{before=next;refresh();};
  document.addEventListener('visibilitychange',()=>{if(!document.hidden){clear();lastOk=0;refresh();}});
  refresh();setInterval(()=>{if(!document.hidden)refresh();if(lastOk&&Date.now()-lastOk>30000)clear();},3000);
})();
