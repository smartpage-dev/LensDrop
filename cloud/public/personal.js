'use strict';
(()=>{
  const token=location.pathname.split('/').pop(),base='/v1/p/'+token,$=id=>document.getElementById(id);
  let before=null,next=null,busy=false,lastOk=0,signature='',selected=null,available=new Set();
  function clear(){ $('personalPhotos').replaceChildren();$('personalLogo').hidden=true;$('personalLogo').removeAttribute('src');$('personalPreview').close();$('personalImage').removeAttribute('src');$('personalDownload').removeAttribute('href');available.clear();signature=''; }
  async function refresh(){
    if(busy)return;busy=true;
    try{
      const r=await fetch(base+(before?'?before='+before:''),{cache:'no-store'});if(!r.ok)throw Error(r.status===404?'This delivery is unavailable.':'Connection interrupted.');const d=await r.json();lastOk=Date.now();
      $('eventName').textContent=d.name;$('deliveryLabel').textContent=d.label+' / '+(d.kind==='single'?'Single selected photograph':'Your group');
      $('personalState').textContent=d.photos.length?'Your photographs are ready.':'Your photographs are being prepared. Keep this page; it will update after upload and approval.';
      if(d.branding?.logo_path){if($('personalLogo').getAttribute('src')!==d.branding.logo_path)$('personalLogo').src=d.branding.logo_path;$('personalLogo').hidden=false;}else $('personalLogo').hidden=true;
      available=new Set(d.photos.map(p=>p.id));if(selected&&!available.has(selected)){$('personalPreview').close();$('personalImage').removeAttribute('src');selected=null;}
      next=d.next_before;$('olderPage').hidden=!next;
      const value=JSON.stringify(d.photos);if(value!==signature){signature=value;const fragment=document.createDocumentFragment();for(const p of d.photos){const button=document.createElement('button');button.className='personal-photo secondary';button.setAttribute('aria-label','Open photograph');const img=document.createElement('img');img.loading='lazy';img.src=base+'/photos/'+p.id+'/thumb';img.alt='Delivered photograph';button.append(img);button.onclick=()=>{selected=p.id;$('personalImage').src=base+'/photos/'+p.id+'/delivery';$('personalDownload').href=base+'/photos/'+p.id+'/delivery?download=1';$('personalPreview').showModal();};fragment.append(button);}$('personalPhotos').replaceChildren(fragment);}
    }catch(e){$('personalState').textContent=e.message;if(!lastOk||Date.now()-lastOk>30000||e.message.includes('unavailable'))clear();}
    finally{busy=false;}
  }
  $('closePersonal').onclick=()=>{$('personalPreview').close();$('personalImage').removeAttribute('src');selected=null;};
  $('latestPage').onclick=()=>{before=null;refresh();};$('olderPage').onclick=()=>{before=next;refresh();};
  document.addEventListener('visibilitychange',()=>{if(!document.hidden){clear();lastOk=0;refresh();}});
  refresh();setInterval(()=>{if(!document.hidden)refresh();if(lastOk&&Date.now()-lastOk>30000)clear();},5000);
})();
