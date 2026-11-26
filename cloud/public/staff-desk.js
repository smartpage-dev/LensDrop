'use strict';
// Shared by authenticated cloud Admin and the authenticated loopback station dashboard.
(()=>{
 const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
 function mount(root,c){let dead=false,epoch=0,collection=null,before=null,configuration=null,preview=null,urls=new Set(),photoFilter='all',lastRows='',thumbObserver=null,thumbQueue=[],thumbActive=0;const pendingKey='lensdrop_staff_order_v1:'+c.scope;
  root.replaceChildren();root.classList.add('staff-desk');root.append(el('div',undefined,'desk-titlebar'));const titlebar=root.firstChild;titlebar.append(el('div',undefined,'desk-title-copy'));titlebar.firstChild.append(el('span','OPERATIONS / STAFF','desk-eyebrow'),el('h2','Staff Photo Desk'),el('p','Review private photos, prepare a print and manage groups. Nothing prints without explicit confirmation.'));titlebar.append(el('span','Private access','desk-pill'));
  const msg=el('p','','desk-message'),configArea=el('div'),tools=el('div',undefined,'desk-actions'),reference=el('input'),heading=el('h3','All event photos'),grid=el('div',undefined,'desk-grid'),orderArea=el('div'),pendingArea=el('div'),groupArea=el('section',undefined,'desk-groups');
  reference.placeholder='Staff: scan / paste guest QR, full collection ID or 8-character REF';reference.setAttribute('aria-label','Guest ticket reference');reference.autocomplete='off';
  const say=t=>{if(!dead)msg.textContent=t;};
  function button(label,fn){const b=el('button',label);b.type='button';b.onclick=async()=>{if(b.disabled)return;b.disabled=true;try{await fn();}catch(e){fail(e);}finally{b.disabled=false;}};return b;}
  function fail(e){say(e.message);if(/staff_desk_not_enabled|station_.*(?:revoked|invalid)|admin_auth_required|local_ui_auth_required/.test(e.message)){epoch++;dropImages();preview?.close();grid.replaceChildren();orderArea.replaceChildren();configuration=null;}}
  const filter=el('select');for(const [v,t] of [['all','All private photos'],['starred','Starred'],['printed','Printed / submitted'],['not_printed','Not printed'],['review','Awaiting review'],['public','Public gallery'],['projector','Slideshow'],['hidden','Globally hidden'],['single','Single QR assigned'],['group','Group QR assigned']])filter.add(new Option(t,v));filter.onchange=()=>{photoFilter=filter.value;before=null;lastRows='';load().catch(fail);};const next=button('Older photos',()=>load(true));next.hidden=true;
  tools.append(button('Refresh setup',configure),button('All event photos',async()=>{groupArea.replaceChildren();collection=null;lastRows='';reference.value='';await load();}),button('Groups / manage',loadGroups),reference,button('Lookup ticket (NO PRINT)',lookup),filter);
  reference.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();lookup().catch(fail);}});
  root.append(msg,configArea,tools,groupArea,pendingArea,heading,grid,next,el('h3','Print orders and collection'),button('Refresh orders',loadOrders),orderArea);
  function dropImages(){thumbObserver?.disconnect();thumbObserver=null;thumbQueue=[];for(const u of urls)URL.revokeObjectURL(u);urls.clear();}
  async function image(id,kind,img,ticket){const r=await c.asset('photo/'+id+'/'+kind);if(!r.ok)throw Error('Preview unavailable; refresh access or photo status.');const blob=await r.blob();if(blob.size>12*1024*1024)throw Error('Preview exceeds size limit');const u=URL.createObjectURL(blob);if(dead||ticket!==epoch){URL.revokeObjectURL(u);return;}urls.add(u);img.src=u;}
  // Only load visible cards, with at most 3 thumbnail requests in flight.
  // This avoids 50 simultaneous JPEG decodes on the Staff Desk.
  function pumpThumbs(ticket){while(!dead&&ticket===epoch&&thumbActive<3&&thumbQueue.length){const job=thumbQueue.shift();if(!job.img.isConnected)continue;thumbActive++;
   image(job.id,'thumb',job.img,ticket).catch(e=>{if(ticket===epoch)job.error.textContent='Thumbnail unavailable: '+e.message;}).finally(()=>{thumbActive--;pumpThumbs(ticket);});}}
  function watchThumb(img,id,error,ticket){
   if(!('IntersectionObserver' in window)){if(thumbQueue.length<12){thumbQueue.push({img,id,error});pumpThumbs(ticket);}return;}
   if(!thumbObserver)thumbObserver=new IntersectionObserver(entries=>{for(const entry of entries){if(!entry.isIntersecting)continue;thumbObserver.unobserve(entry.target);const j=entry.target.__staffThumb;delete entry.target.__staffThumb;if(j){thumbQueue.push(j);pumpThumbs(ticket);}}},{rootMargin:'300px'});
   img.__staffThumb={img,id,error};thumbObserver.observe(img);
  }
  async function configure(){const conf=await c.api('config');if(dead)return;configuration=conf;configArea.replaceChildren();const title=el('p',conf.event.name+' / '+(conf.workflow.print_route==='cloud'?'Method 2: cloud print delivery':'Method 1: phone archive + local-first print'));configArea.append(title,el('p','Phones upload event photos. The Windows station does NOT upload the event photo library. Guest QR access is separate from print permission.'));
   if(c.admin){const mode=el('select');mode.add(new Option('Method 1 - phone cloud archive + local-first print','hybrid'));mode.add(new Option('Method 2 - phone cloud archive + cloud print','cloud'));mode.value=conf.workflow.print_route;configArea.append(mode,button('Save default for newly prepared phones',async()=>{await c.workflow({print_route:mode.value,revision:configuration.workflow.revision});await configure();say('Saved. Phones must refresh prepared station; existing queued requests are unchanged.');}));
    for(const st of conf.stations){const row=el('div',undefined,'desk-actions'),enabled=el('input'),max=el('select');enabled.type='checkbox';enabled.checked=st.staff_enabled;for(let i=1;i<=5;i++)max.add(new Option(String(i),String(i)));max.value=String(st.staff_max_copies);const label=el('label','Allow local staff desk: '+st.label+' ');label.append(enabled);row.append(label,el('span','Maximum sheet copies'),max,button('Save staff permission',async()=>{await c.permission(st.id,{enabled:enabled.checked,max_copies:Number(max.value)});say('Station staff access saved; does not cancel accepted jobs.');await configure();}));configArea.append(row);}
   }
   if(!grid.children.length)await load();showPending();
  }
  async function lookup(){const ref=reference.value.trim();if(!ref)throw Error('Enter a guest ticket reference first.');const data=await c.api('lookup',{reference:ref});if(dead)return;collection=data.collection;lastRows='';render(data);renderOrders(data.orders);say('Found '+collection.label+'. No print request was created.');}
  async function load(older=false){let data;if(collection)data=await c.api('lookup',{reference:collection.id,...(older&&before?{before}: {})});else data=await c.api('photos?filter='+encodeURIComponent(photoFilter)+(older&&before?'&before='+before:''));if(dead)return;const signature=JSON.stringify(data.photos.map(p=>[p.id,p.status,p.starred,p.projector_visible,p.printed,p.delivery?.id,p.delivery?.released,p.collections?.map(x=>x.id+':'+x.revision)]));if(older||signature!==lastRows){lastRows=signature;render(data);}if(!older)await loadOrders();}
  function render(data){const ticket=++epoch;dropImages();heading.textContent=collection?collection.label+' / '+collection.id:'All private event photos';before=data.next_before;next.hidden=!before;grid.replaceChildren();
   if(!data.photos.length)grid.append(el('p','No eligible photos yet. Uploading or guest assignment may still be pending. Hidden/Test photos are not selectable here.'));
   for(const p of data.photos){
    const card=el('article',undefined,'desk-photo-card'),img=el('img');img.alt='Authorized thumbnail of '+p.filename;img.loading='lazy';
    const open=button('View & prepare print',()=>openPreview(p));open.className='desk-primary';
    const imageButton=el('button',undefined,'desk-photo-image');imageButton.type='button';imageButton.title='Enlarge '+p.filename;imageButton.setAttribute('aria-label','Enlarge '+p.filename);imageButton.onclick=()=>openPreview(p).catch(e=>{localError.textContent=e.message;say(e.message);});imageButton.append(img);
    const body=el('div',undefined,'desk-photo-body'),name=el('strong',p.filename,'desk-photo-name'),meta=el('p',p.device_label+' · '+p.status+' · '+(p.printed?'Print submitted':'Not printed'),'desk-photo-meta');
    const tags=el('div',undefined,'desk-tags');for(const col of p.collections||[])tags.append(el('span',col.label+' '+col.kind+(col.released?'':' · pending'),'desk-tag'));if(!p.collections?.length)tags.append(el('span','Unassigned / staff only','desk-tag muted'));
    const localError=el('p','','desk-card-error');localError.setAttribute('role','alert');
    body.append(name,meta,tags,localError);
    const actions=el('div',undefined,'desk-card-actions');actions.append(open);
    if(c.admin||c.scope){for(const [field,title,value] of [['starred',p.starred?'Unstar':'Star',!p.starred],['gallery_visible',p.status==='live'?'Hide from gallery':'Show in gallery',p.status!=='live'],['projector_visible',p.projector_visible?'Remove slideshow':'Add slideshow',!p.projector_visible]]){
      if(p.status==='hidden'&&field!=='starred')continue;const b=button(title+' (NO PRINT)',async()=>{if(field!=='starred'&&!confirm('Change public visibility of '+p.filename+'?'))return;await c.api('photo-control',{photo_id:p.id,field,value});await load();});b.className='desk-secondary';actions.append(b);
    }}
    if(p.status!=='hidden'&&!p.delivery&&collection)actions.append(button('Assign to this guest (NO PRINT)',async()=>{if(!confirm('Assign '+p.filename+' to '+collection.label+'? No printing occurs.'))return;await c.api('assign',{photo_id:p.id,delivery_id:collection.id});await load();}));
    if(p.status!=='hidden'&&!p.delivery)actions.append(button('Assign guest reference',async()=>{const ref=prompt('Paste the intended guest link, collection ID or printed REF. No printing will occur.');if(!ref)return;const found=await c.api('lookup',{reference:ref});if(!confirm('Assign '+p.filename+' to '+found.collection.label+'?'))return;await c.api('assign',{photo_id:p.id,delivery_id:found.collection.id});await load();}));
    card.append(imageButton,body,actions);grid.append(card);
    if(p.status==='hidden'){img.alt='Globally hidden photo; private preview disabled';imageButton.disabled=true;open.disabled=true;}
    else watchThumb(img,p.id,localError,ticket);
   }
  }
  async function loadGroups(){
   const data=await c.api('groups');groupArea.replaceChildren(el('h3','Group collections / staff management'),el('p','Single identity is preserved when Group membership changes. Lookup never prints.'));
   if(!data.groups.length)groupArea.append(el('p','No Groups yet. Start a Group on the photographer phone.'));
   for(const g of data.groups){const row=el('article',undefined,'desk-group'),title=el('strong',g.label+' · '+g.count+' photos · '+g.state);
    row.append(title,button('Open / manage',()=>manageGroup(g)));groupArea.append(row);}
  }
  async function manageGroup(g){const data=await c.api('groups/'+g.id+'/members'),panel=el('div',undefined,'desk-preview');
   panel.append(el('h3',g.label+' — '+data.members.length+' photos'),el('p','Changes affect the photos visible through an already-issued Group QR. Original photos, Single QR and print records are retained.'));
   const target=el('input');target.placeholder='Paste photo UUID from All Photos';target.setAttribute('aria-label','Photo ID to add');
   panel.append(target,button('Add photo to Group (NO PRINT)',async()=>{const id=target.value.trim();if(!/^[0-9a-f-]{36}$/.test(id))throw Error('Paste an exact photo UUID.');if(!confirm('Add this photo to '+g.label+'? Guest access may change.'))return;await c.api('groups/'+g.id+'/members',{action:'add',photo_id:id,delivery_id:g.id,expected_revision:0,request_id:crypto.randomUUID()});dialog.close();await manageGroup(g);await load();}));
   for(const m of data.members){const line=el('div',m.filename+' · '+m.photo_id+' · revision '+m.revision,'desk-actions');
    line.append(button('Remove from Group only',async()=>{if(!confirm('Remove '+m.filename+' from '+g.label+'? Existing paper QR will show a changed collection. The Single QR and original remain.'))return;await c.api('groups/'+g.id+'/members',{action:'remove',photo_id:m.photo_id,delivery_id:g.id,expected_revision:m.revision,request_id:crypto.randomUUID()});dialog.close();await manageGroup(g);await load();}));panel.append(line);}
   const dialog=el('dialog');dialog.append(panel,button('Close',()=>dialog.close()));root.append(dialog);dialog.addEventListener('close',()=>dialog.remove());dialog.showModal();
  }
  function specBody(p,station,profile,copies,reprint,selectedDelivery){return {id:crypto.randomUUID(),photo_id:p.id,station_id:station.id,profile_id:profile.id,profile_revision:profile.revision,copies,delivery_id:selectedDelivery||null,reprint_of:reprint||null};}
  async function openPreview(p){
   if(preview){preview.close();preview.remove();}
   const dialog=el('dialog');dialog.className='desk-preview-dialog';
   const wrap=el('div',undefined,'desk-preview'),header=el('div',undefined,'desk-preview-header'),title=el('h3',p.filename),close=button('Close',()=>dialog.close()),status=el('p','Loading authorized photo...','desk-modal-status');
   close.className='desk-secondary';header.append(title,close);const img=el('img');img.alt='Large authorized preview of '+p.filename;
   const meta=el('p',p.device_label+' · '+p.status+' · Photo ID: '+p.id,'desk-photo-meta');
   const printBox=el('details',undefined,'desk-print-setup'),summary=el('summary','Prepare photo print · review first');printBox.append(summary);
   wrap.append(header,meta,img,status,printBox);dialog.append(wrap);root.append(dialog);preview=dialog;
   dialog.addEventListener('close',()=>{dialog.remove();if(preview===dialog)preview=null;});dialog.showModal();
   // Preview opens BEFORE checking station configuration so an absent/stale print
   // station cannot make the Preview button appear inert.
   const photoTicket=epoch;
   try{await image(p.id,'delivery',img,photoTicket);if(dialog.open&&img.src)status.textContent='Private preview ready. Previewing never publishes or prints.';else status.textContent='Preview changed during refresh; reopen if needed.';}
   catch(e){if(dialog.open)status.textContent='Image unavailable: '+e.message+' (refresh access or check archive)';}
   let conf=configuration;
   if(!conf){try{conf=await c.api('config');if(!dead)configuration=conf;}catch(e){printBox.append(el('p','Printing unavailable: '+e.message,'desk-card-error'));return;}}
   if(!dialog.open||dead)return;
   if(!conf?.stations?.length){printBox.open=true;printBox.append(el('p','No authorized Print Station is configured. Photo preview is still available; connect a station before preparing a print.','desk-card-error'));return;}
   const station=el('select'),profile=el('select'),copies=el('select'),delivery=el('select'),detail=el('p');
   for(const st of conf.stations)station.add(new Option(st.label+(st.paused?' (paused)':''),st.id));
   delivery.add(new Option('Unassigned (only if no collection)',''));for(const col of p.collections||[])delivery.add(new Option(col.label+' / '+col.kind,col.id));if((p.collections||[]).length){delivery.value=p.collections[0].id;delivery.remove(0);}
   for(let i=1;i<=conf.max_copies;i++)copies.add(new Option(String(i),String(i)));
   const selectedStation=()=>conf.stations.find(st=>st.id===station.value),selectedProfile=()=>selectedStation()?.profiles?.find(x=>x.id===profile.value);
   const send=button('Confirm ONE print request',async()=>{
    const pr=selectedProfile();if(!pr)throw Error('Station has no published print profiles.');if(p.bytes>32*1024*1024)throw Error('Photo exceeds the 32 MiB print limit.');if(localStorage.getItem(pendingKey))throw Error('Resolve your saved pending request first.');
    const pair=pr.id.startsWith('cp9550-strip-pair-');if(pair&&Math.abs(Math.max(p.width,p.height)/Math.min(p.width,p.height)-3)>0.04)throw Error('Pair profile requires a 1:3 finished strip JPEG.');
    const request=specBody(p,selectedStation(),pr,Number(copies.value),null,delivery.value);
    if(!confirm('Submit ONE print request for '+p.filename+' / '+copies.value+' sheet(s) to '+selectedStation().label+'?'))return;
    localStorage.setItem(pendingKey,JSON.stringify(request));dialog.close();showPending();await submit(request);
   });send.className='desk-primary';
   function quantity(){const pr=selectedProfile(),pair=pr&&pr.id.startsWith('cp9550-strip-pair-');detail.textContent=(pair?'One strip image duplicated: '+Number(copies.value)*2+' strips.':'Full photo: '+copies.value+' sheet(s).')+' Guest: '+((p.collections||[]).find(x=>x.id===delivery.value)?.label||'unassigned')+'. Output is not proof of physical printing.';img.className=pair?'desk-strip':'';send.disabled=!pr;}
   function profiles(){profile.replaceChildren(...(selectedStation()?.profiles||[]).map(x=>new Option(x.label+' [revision '+x.revision+']',x.id)));quantity();if(!profile.options.length)detail.textContent='Selected station has no published profiles. Configure it in Windows Station first.';}
   station.onchange=profiles;profile.onchange=quantity;copies.onchange=quantity;delivery.onchange=quantity;
   const fields=el('div',undefined,'desk-print-fields');for(const [text,field] of [['Destination station',station],['Validated station profile',profile],['Sheet copies',copies],['Guest collection for this order',delivery]]){const label=el('label',text);label.append(field);fields.append(label);}
   printBox.append(fields,detail,send);profiles();
  }
  async function submit(request){const result=await c.api('orders',request);if(result.job.id!==request.id)throw Error('Order identity mismatch; keep pending record.');localStorage.removeItem(pendingKey);showPending();say('Order '+result.job.id+' is '+result.job.state+'. Same request ID retained on retries. Windows operator must verify paper.');await loadOrders();}
  function showPending(){pendingArea.replaceChildren();const raw=localStorage.getItem(pendingKey);if(!raw)return;let p;try{p=JSON.parse(raw);}catch{say('Saved request record damaged; inspect before clearing.');return;}pendingArea.append(el('strong','Saved request '+p.id+' — outcome not yet reconciled'),button('Check / retry the SAME request',()=>submit(p)),el('p','Do not create another request to work around an uncertain response. Refresh orders first. No auto reprint.'));
   pendingArea.append(button('Cancel only an incomplete preparation',async()=>{if(!confirm('This only cancels an incomplete preparation that the station cannot execute. Ready/accepted jobs must be reconciled on the station. Continue?'))return;await c.api('cancel-prepared',{job_id:p.id});localStorage.removeItem(pendingKey);showPending();await loadOrders();}));
   pendingArea.append(button('Acknowledge pending record after manual reconciliation',async()=>{if(!confirm('I inspected the order list / station. Clearing this browser record does NOT cancel or refund the job. Continue?'))return;localStorage.removeItem(pendingKey);showPending();}));
  }
  async function loadOrders(){const data=collection?await c.api('lookup',{reference:collection.id}):await c.api('orders');if(!dead)renderOrders(data.orders);}
  function renderOrders(rows){orderArea.replaceChildren();for(const r of rows||[]){const item=el('article'),txt=el('p',r.id+' / '+r.state+' / '+r.copies+' sheet copies / '+(r.collected_at?'COLLECTED':'not collected'));item.append(txt);if(r.state==='operator_confirmed'&&!r.collected_at)item.append(button('Confirm handover / collected (NO PRINT)',async()=>{if(!confirm('Confirm the guest physically received this order? No new print will be sent.'))return;await c.api('collect',{job_id:r.id});await loadOrders();}));orderArea.append(item);}if(!rows?.length)orderArea.append(el('p','No linked print orders in this view. Local receipts / cloud archives may still be pending.'));}
  const timer=setInterval(()=>{if(dead||document.hidden||collection||preview||document.activeElement===reference||document.activeElement===filter)return;load().catch(fail);},10000);configure().catch(fail);
  return {scope:c.scope,destroy(){dead=true;clearInterval(timer);epoch++;dropImages();preview?.close();root.replaceChildren();}};
 }
 window.LuceStaffDesk={mount};
})();
