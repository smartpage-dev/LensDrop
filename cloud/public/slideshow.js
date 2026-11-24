'use strict';
(() => {
  const $=id=>document.getElementById(id), token=location.pathname.split('/').at(-1), endpoint='/v1/s/'+token;
  const playlist=new window.LensDropPlaylist();const layers=[$('layerA'),$('layerB')];
  let config={style:'fade',fit:'contain',background:'black',order:'newest',interval_seconds:8,poll_seconds:10}, feed=null;
  let currentId=null,active=-1,paused=false,busy=false,showing=false,renderTicket=0,lastVerified=0,started=false;
  let slideTimer=null,pollTimer=null,controlTimer=null,wakeLock=null,qrBlob=null,qrPath=null,history=[],noticeTimer=null,cancelImage=null;
  const LEASE=30000;const fresh=()=>lastVerified>0&&performance.now()-lastVerified<LEASE;
  function notice(text,temporary=false){clearTimeout(noticeTimer);$('notice').textContent=text;$('notice').hidden=!text;if(temporary)noticeTimer=setTimeout(()=>$('notice').hidden=true,5000);}
  function reveal(){document.body.classList.remove('controls-hidden');clearTimeout(controlTimer);controlTimer=setTimeout(()=>{if($('details').hidden&&!$('controls').querySelector(':focus-visible'))document.body.classList.add('controls-hidden');},4500);}
  function empty(text,title){$('emptyState').hidden=false;$('emptyText').textContent=text;$('emptyTitle').textContent=title||feed?.name||'LensDrop / Live slideshow';}
  function clearPhotos(text,clearPlaylist=false){renderTicket++;if(cancelImage)cancelImage();showing=false;clearTimeout(slideTimer);slideTimer=null;for(const layer of layers){layer.classList.remove('on');layer.removeAttribute('data-photo');const img=layer.querySelector('img');img.onload=img.onerror=null;img.removeAttribute('src');}active=-1;currentId=null;if(clearPlaylist){playlist.clear();history=[];}empty(text);}
  function concealBrand(){for(const id of ['brand','audience'])$(id).hidden=true;$('brandLogo').removeAttribute('src');}
  function setStyle(){const local=$('localStyle').value;for(const name of ['style-fade','style-zoom','style-slide','fit-contain','fit-cover','bg-black','bg-charcoal','bg-white'])document.body.classList.remove(name);document.body.classList.add('style-'+(local==='server'?config.style:local),'fit-'+config.fit,'bg-'+config.background);document.documentElement.style.setProperty('--slide-duration',config.interval_seconds+'s');}
  function applyBrand(data){const b=data.branding;const mode=b.mode;$('brand').className='brand '+b.position;$('brand').hidden=mode==='none';const logo=$('brandLogo');logo.className='logo-bg-'+b.logo_background;const showLogo=!!b.logo_path&&(mode==='both'||mode==='logo');logo.hidden=!showLogo;if(showLogo&&logo.getAttribute('src')!==b.logo_path)logo.src=b.logo_path;if(!showLogo)logo.removeAttribute('src');$('brandName').textContent=data.name;$('brandName').hidden=mode==='logo'&&showLogo;logo.onerror=()=>{logo.hidden=true;$('brandName').hidden=false;};
    const showQr=data.settings.show_gallery_qr&&data.gallery_path;$('audience').hidden=!showQr;
    $('audience').className='audience '+b.position.replace(/left|right/g,v=>v==='left'?'right':'left');
    if(showQr&&qrPath!==data.gallery_path){qrPath=data.gallery_path;if(qrBlob)URL.revokeObjectURL(qrBlob);qrBlob=URL.createObjectURL(new Blob([window.LensDropQR.toSvg(new URL(qrPath,location.origin).href)],{type:'image/svg+xml'}));$('audienceQr').src=qrBlob;}
    if(!showQr){if(qrBlob)URL.revokeObjectURL(qrBlob);qrBlob=null;qrPath=null;$('audienceQr').removeAttribute('src');}
  }
  function schedule(){clearTimeout(slideTimer);if(!paused&&!document.hidden&&fresh()&&playlist.photos.size)slideTimer=setTimeout(()=>{slideTimer=null;advance();},config.interval_seconds*1000);}
  async function display(photo,remember=true){if(!photo||!fresh()||!playlist.photos.has(photo.id)||document.hidden)return false;
    if(currentId===photo.id){schedule();return true;}
    const ticket=++renderTicket;showing=true;const target=active===0?1:0,layer=layers[target],img=layer.querySelector('img');layer.classList.remove('on');
    try{
      await new Promise((resolve,reject)=>{let timeout;const cancel=()=>finish(Error('image_cancelled'));const finish=error=>{clearTimeout(timeout);img.onload=img.onerror=null;if(cancelImage===cancel)cancelImage=null;error?reject(error):resolve();};cancelImage=cancel;timeout=setTimeout(()=>finish(Error('image_timeout')),8000);img.onload=()=>finish();img.onerror=()=>finish(Error('image_unavailable'));img.src=endpoint+'/photos/'+photo.id+'/delivery';});
      if(img.decode)await img.decode();
      if(ticket!==renderTicket||!fresh()||!playlist.photos.has(photo.id)||document.hidden){if(ticket===renderTicket)img.removeAttribute('src');return false;}
      if(remember&&currentId&&playlist.photos.has(currentId)){history.push(currentId);if(history.length>200)history.shift();}
      const previous=active;currentId=photo.id;active=target;layer.dataset.photo=photo.id;
      // Two image layers only. No full-album preload or original-file decoding.
      void layer.offsetWidth;layer.classList.add('on');if(previous!==-1)layers[previous].classList.remove('on');$('emptyState').hidden=true;
      setTimeout(()=>{if(active!==previous&&previous!==-1&&!layers[previous].classList.contains('on')){layers[previous].querySelector('img').removeAttribute('src');layers[previous].removeAttribute('data-photo');}},800);
      schedule();return true;
    }catch{if(ticket===renderTicket){img.removeAttribute('src');notice('Photo could not load. Retrying another projector-approved photo.',true);}return false;}
    finally{if(ticket===renderTicket)showing=false;}
  }
  async function advance(){if(showing||!fresh()||document.hidden)return;clearTimeout(slideTimer);for(let attempt=0;attempt<Math.min(3,playlist.photos.size);attempt++){const candidate=playlist.next(currentId);if(await display(candidate))return;}schedule();}
  async function previous(){if(showing||!fresh())return;clearTimeout(slideTimer);while(history.length){const candidate=playlist.photos.get(history.pop());if(candidate&&candidate.id!==currentId){await display(candidate,false);return;}}schedule();}
  async function poll(){if(busy||document.hidden)return;busy=true;clearTimeout(pollTimer);const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),8000);
    try{
      const r=await fetch(endpoint,{cache:'no-store',signal:controller.signal});
      if(!r.ok){if(r.status===404){lastVerified=0;feed=null;clearPhotos('Slideshow is disabled or this link has been replaced. Ask the event operator for an active projector link.',true);concealBrand();notice('Link inactive / \u9023\u7d50\u672a\u958b\u555f');}throw Error('feed_unavailable');}
      const data=await r.json();lastVerified=performance.now();feed=data;const previousInterval=config.interval_seconds;config=data.settings;playlist.update(data.photos,config.order);
      setStyle();applyBrand(data);document.title=data.name+' | LensDrop Slideshow';$('emptyTitle').textContent=data.name;
      $('detailName').textContent=data.name;$('detailCounts').textContent=`${data.window_count} photos in playback window / ${data.total} projector-approved photos. New approvals join at the next slide boundary. Order is upload order, not camera capture time.`;
      $('detailConnection').textContent='Feed verified: '+new Date().toLocaleTimeString()+' / checking every '+config.poll_seconds+'s';
      notice('');
      if(currentId&&!playlist.photos.has(currentId))clearPhotos('Updating projector-approved photographs...');
      history=history.filter(id=>playlist.photos.has(id));
      if(!playlist.photos.size)clearPhotos('Waiting for the photographer to publish photographs. / \u7b49\u5f85\u651d\u5f71\u5e2b\u516c\u958b\u7167\u7247');
      else if(!currentId&&!paused&&!showing)advance();
      else if(!slideTimer||previousInterval!==config.interval_seconds)schedule();
    }catch{
      $('detailConnection').textContent='Connection unavailable. Automatic retry is active.';
      if(!fresh()){clearPhotos('Connection unavailable. Photos are paused until their visibility can be verified. / \u7b49\u5f85\u91cd\u9023');concealBrand();}
      else notice('Connection interrupted. Retrying automatically. / \u81ea\u52d5\u91cd\u9023\u4e2d');
    }finally{clearTimeout(timeout);busy=false;pollTimer=setTimeout(poll,config.poll_seconds*1000);}
  }
  function togglePause(){paused=!paused;document.body.classList.toggle('paused',paused);$('pause').textContent=paused?'\u7e7c\u7e8c / Play':'\u66ab\u505c / Pause';$('pausedBadge').hidden=!paused;if(paused){clearTimeout(slideTimer);slideTimer=null;}else if(currentId)schedule();else advance();reveal();}
  async function awake(){if(!started||document.hidden||wakeLock)return;if(!('wakeLock'in navigator)){$('detailWake').textContent='Screen wake lock unavailable. Disable OS sleep manually.';return;}try{wakeLock=await navigator.wakeLock.request('screen');$('detailWake').textContent='Screen wake lock active (OS may still release it).';wakeLock.addEventListener('release',()=>{wakeLock=null;$('detailWake').textContent='Wake lock released. Check screen-sleep settings.';});}catch{$('detailWake').textContent='Wake lock not granted. Disable OS sleep manually.';}}
  $('fullscreen').onclick=()=>{started=true;if(document.fullscreenElement){document.exitFullscreen().catch(()=>{});}else if(document.documentElement.requestFullscreen){document.documentElement.requestFullscreen().catch(()=>notice('Fullscreen was not granted. Use the browser fullscreen command.',true));}else notice('Use the browser fullscreen command.',true);awake();reveal();};
  document.addEventListener('fullscreenchange',()=>{$('fullscreen').textContent=document.fullscreenElement?'\u96e2\u958b\u5168\u87a2\u5e55 / Exit':'\u5168\u87a2\u5e55 / Fullscreen';});
  $('pause').onclick=togglePause;$('next').onclick=()=>{advance();reveal();};$('previous').onclick=()=>{previous();reveal();};$('refresh').onclick=()=>{poll();reveal();};$('localStyle').onchange=()=>{setStyle();reveal();};$('infoToggle').onclick=()=>{$('details').hidden=!$('details').hidden;reveal();};
  document.addEventListener('mousemove',reveal,{passive:true});document.addEventListener('pointerdown',reveal,{passive:true});
  document.addEventListener('keydown',e=>{if(['INPUT','SELECT','TEXTAREA'].includes(e.target.tagName))return;if(e.key===' '){e.preventDefault();togglePause();}else if(e.key==='ArrowRight'){e.preventDefault();advance();}else if(e.key==='ArrowLeft'){e.preventDefault();previous();}else if(e.key.toLowerCase()==='f')$('fullscreen').click();reveal();});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){clearTimeout(slideTimer);clearTimeout(pollTimer);if(wakeLock)wakeLock.release().catch(()=>{});}else{if(!fresh())clearPhotos('Checking visibility before resuming...');poll();awake();}});
  window.addEventListener('online',poll);window.addEventListener('pagehide',()=>{if(wakeLock)wakeLock.release().catch(()=>{});if(qrBlob)URL.revokeObjectURL(qrBlob);});
  setInterval(()=>{if(lastVerified&&!fresh()){lastVerified=0;clearPhotos('Connection verification expired. Waiting to reconnect. / \u7b49\u5f85\u91cd\u9023');concealBrand();}},1000);
  window.LensDropShow={poll,advance,status:()=>({current:currentId,pool:playlist.photos.size,paused,fresh:fresh(),showing})};
  reveal();poll();
})();
