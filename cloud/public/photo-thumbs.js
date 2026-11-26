"use strict";
// Authorized admin thumbnails. No public guest URL, no token in src, maximum
// 3 concurrent requests and only when a thumbnail approaches the viewport.
(()=>{
 let generation=0,observer=null,queue=[],active=0,urls=new Set();
 function reset(){generation++;observer?.disconnect();observer=null;queue=[];for(const u of urls)URL.revokeObjectURL(u);urls.clear();}
 function mount(table){reset();const serial=generation;const imgs=[...table.querySelectorAll('img[data-photo-id]')];
  const queueOne=img=>{if(img.dataset.queued)return;img.dataset.queued='1';queue.push(img);pump(serial);};
  function pump(ticket){while(ticket===generation&&active<3&&queue.length){const img=queue.shift();if(!img.isConnected)continue;active++;
    (async()=>{let url=null;try{const res=await window.LensDropAdmin.asset('/v1/photos/'+img.dataset.photoId+'/thumb',{cache:'no-store'});if(!res.ok)throw Error('HTTP '+res.status);const b=await res.blob();if(!b.type.startsWith('image/')||b.size>4*1024*1024)throw Error('Invalid thumbnail');url=URL.createObjectURL(b);
      if(ticket===generation&&img.isConnected){urls.add(url);img.src=url;img.dataset.loaded='1';url=null;}
    }catch(_){if(ticket===generation&&img.isConnected){img.alt='Preview unavailable';img.classList.add('thumb-missing');}}
    finally{if(url)URL.revokeObjectURL(url);active--;pump(ticket);}})();}}
  if('IntersectionObserver' in window){observer=new IntersectionObserver(entries=>{for(const e of entries)if(e.isIntersecting){observer.unobserve(e.target);queueOne(e.target);}},{rootMargin:'320px'});imgs.forEach(img=>observer.observe(img));}
  else imgs.slice(0,40).forEach(queueOne);
 }
 window.LensDropThumbnails={mount,clear:reset};
})();
