/* ============================================================
   Pipeline: ingest, AI tagging, retouch again, replace, tracking, posters
   ============================================================ */
const Store = {
  get(k, d){ try{ const v = localStorage.getItem("lensdrop." + k); return v == null ? d : JSON.parse(v); }catch{ return d; } },
  set(k, v){ try{ localStorage.setItem("lensdrop." + k, JSON.stringify(v)); }catch{} }
};

async function uploadAll(items){
  const rs = await Promise.allSettled(items.map(([b,t])=>uploadBlob(b,t)));
  const bad = rs.find(r=>r.status==="rejected");
  if(bad){ for(const r of rs) if(r.status==="fulfilled") deleteAsset(r.value.id); throw bad.reason; }
  return rs.map(r=>r.value);
}

const Ingest = {
  jobs: [], active: 0, CONC: 2, recent: [],
  add(files, albumId, category, mode){
    const list = [...files];
    for(const file of list){
      const kind = /^video\//.test(file.type) ? "video" : "photo";
      const job = { key: rid(), file, name: file.name, kind, albumId, category: category || null, mode: mode || "new", stage: "Waiting", err: false, ms: null, thumb: null, status: null };
      if(mode === "replace"){
        const matches = photoList().filter(x => baseName(x.name).toLowerCase() === baseName(file.name).toLowerCase() && x.kind !== "video");
        if(matches.length !== 1){ job.stage = matches.length ? "Ambiguous filename: select a unique photo ID before returning this edit" : "No frame in this album has this file name"; job.err = true; job.done = true; }
        else job.target = matches[0].id;
      }
      this.jobs.push(job);
    }
    // Only successful, completed history is bounded. Pending/running/error records survive.
    let keptCompleted = 0;
    this.jobs = this.jobs.slice().reverse().filter(j => {
      if(!j.done || j.running || j.err) return true;
      if(keptCompleted++ < 400) return true;
      if(j.thumb) URL.revokeObjectURL(j.thumb);
      return false;
    }).reverse();
    this.render(); this.pump();
  },
  pump(){
    while(this.active < this.CONC){
      const job = this.jobs.find(j => !j.done && !j.running); if(!job) break;
      job.running = true; this.active++;
      const run = job.mode === "replace" ? replaceJob(job) : processJob(job);
      run.catch(e=>{ job.err = true; job.stage = (e && e.msg) || uploadMessage(e); })
         .finally(()=>{ job.done = true; job.running = false; this.active--; this.render(); this.pump(); });
    }
  },
  stage(job, s){ job.stage = s; this.render(); },
  render(){ if(Console.mountedTab === "ingest") Console.tabs.ingest.renderQueue(); },
};

function findDup(albumId, hash, takenAt){
  const pool = [];
  if(S.photosAlbum === albumId) for(const [id,p] of S.photos) if(p.cull && p.cull.hash) pool.push({ id, hash: p.cull.hash, takenAt: p.takenAt });
  for(const r of Ingest.recent) if(r.albumId === albumId && !S.photos.has(r.id)) pool.push(r);
  // a failed upload leaves a recent entry that never became a frame; that’s harmless for matching
  let best = null, bd = 99;
  for(const c of pool.slice(-500)){
    const d = hamming(hash, c.hash);
    if(d < bd && (!takenAt || !c.takenAt || Math.abs(takenAt - c.takenAt) < 90000)){ bd = d; best = c; }
  }
  return bd <= 5 ? best.id : null;
}

async function processJob(job){
  const t0 = performance.now();
  const album = S.albums.get(job.albumId); if(!album) throw { msg: "This album no longer exists." };
  const c = cfg(album);
  if(job.kind === "video") return processVideo(job, c, t0);
  Ingest.stage(job, "Reading");
  const exif = await readExif(job.file);
  let dec; try{ dec = await decodeFile(job.file); }catch{ throw { msg: "This file can’t be opened in this browser. Export it as JPEG and add it again." }; }
  try{
    Ingest.stage(job, "Culling");
    await sleep(0);
    const a = analyze(dec.img); const ex = exposureFlags(a);
    const blurry = c.cullOn && a.sharp < c.blur, dark = c.cullOn && ex.dark, bright = c.cullOn && ex.bright;
    const takenAt = exif.takenAt || job.file.lastModified || null;
    const dupOf = c.cullOn ? findDup(job.albumId, a.hash, exif.takenAt || null) : null;
    const flagged = blurry || dark || bright || !!dupOf;
    const pid = rid();
    Ingest.recent.push({ albumId: job.albumId, id: pid, hash: a.hash, takenAt: exif.takenAt || null });
    if(Ingest.recent.length > 600) Ingest.recent.splice(0, 100);
    const retouch = PRESETS[c.retouch] ? c.retouch : null;
    Ingest.stage(job, retouch ? "Retouching" : "Preparing");
    await sleep(0);
    let sourceBlob = null;
    if(retouch) sourceBlob = await toBlob(renderDelivery(dec.img, a, "off", c.maxEdge), "image/jpeg", .9);
    const del = renderDelivery(dec.img, a, retouch || "off", c.maxEdge);
    const delBlob = await toBlob(del, "image/jpeg", .9);
    const tf = fit(del.width, del.height, 640); const thumbBlob = await toBlob(drawScaled(del, tf.w, tf.h), "image/jpeg", .82);
    let aiBlob = null;
    if(c.aiTag && S.sample && S.aiLimits && !AI.disabled){ const af = fit(del.width, del.height, 1280); aiBlob = await toBlob(drawScaled(del, af.w, af.h), "image/jpeg", .85); }
    job.thumb = URL.createObjectURL(thumbBlob);
    Ingest.stage(job, "Uploading");
    const items = [[delBlob,"image/jpeg"],[thumbBlob,"image/jpeg"]]; if(sourceBlob) items.push([sourceBlob,"image/jpeg"]);
    const [up, th, src] = await uploadAll(items);
    const status = (flagged && c.hold) ? "review" : (c.publish === "live" ? "live" : "review");
    const ingestMs = Math.round(performance.now() - t0);
    const doc = {
      kind: "photo", name: job.file.name, assetId: up.id, thumbId: th.id, sourceId: src ? src.id : null,
      w: del.width, h: del.height, bytes: up.sizeBytes || delBlob.size, origBytes: job.file.size,
      takenAt, uploadedAt: Date.now(), uploader: S.me.id || null, category: job.category, status, pick: false,
      retouch, camera: exif.camera || null, ingestMs,
      cull: { checked: c.cullOn, sharp: a.sharp, mean: Math.round(a.mean), hash: a.hash, blurry, dark, bright, dup: !!dupOf, dupOf: dupOf || null },
      ai: null,
    };
    try{ await photoRef(job.albumId, pid).set(doc); }
    catch(e){ for(const r of [up,th,src]) if(r) deleteAsset(r.id); throw { msg: (e && e.code === "quota_exceeded") ? "This album has reached its photo limit. Delete frames you don’t need." : "Saving the frame didn’t work. Add the file again." }; }
    job.pid = pid; job.ms = ingestMs; job.status = status;
    const fl = flagsOf(doc);
    Ingest.stage(job, status === "live" ? (fl.length ? "Live, flagged: " + fl.join(", ").toLowerCase() : "Live in the gallery") : (fl.length && c.hold ? "Held for review: " + fl.join(", ").toLowerCase() : "Waiting for review"));
    if(aiBlob) AI.enqueue([{ albumId: job.albumId, pid, blob: aiBlob }]);
  }finally{ URL.revokeObjectURL(dec.url); }
}

async function processVideo(job, c, t0){
  if(!/^video\/(mp4|webm)$/.test(job.file.type)) throw { msg: "Convert this clip to MP4 or WebM to add it." };
  if(job.file.size > 20*1024*1024) throw { msg: "Clips can be up to 20 MB here. Trim or compress it, then add it again." };
  Ingest.stage(job, "Reading");
  const url = URL.createObjectURL(job.file);
  try{
    const v = document.createElement("video"); v.muted = true; v.playsInline = true; v.preload = "auto"; v.src = url;
    await new Promise((res, rej)=>{ v.onloadeddata = res; v.onerror = ()=>rej({ msg: "This clip can’t be played in this browser." }); setTimeout(()=>rej({ msg: "This clip took too long to open." }), 15000); });
    await new Promise(res=>{ v.onseeked = res; try{ v.currentTime = Math.min(.6, (v.duration || 1)/3); }catch{ res(); } setTimeout(res, 3000); });
    const vw = v.videoWidth || 1280, vh = v.videoHeight || 720; const tf = fit(vw, vh, 640);
    const pc = mkCanvas(tf.w, tf.h); pc.getContext("2d").drawImage(v, 0, 0, tf.w, tf.h);
    const thumbBlob = await toBlob(pc, "image/jpeg", .82); job.thumb = URL.createObjectURL(thumbBlob);
    Ingest.stage(job, "Uploading");
    const [up, th] = await uploadAll([[job.file, job.file.type], [thumbBlob, "image/jpeg"]]);
    const pid = rid(); const status = c.publish === "live" ? "live" : "review"; const ingestMs = Math.round(performance.now() - t0);
    await photoRef(job.albumId, pid).set({
      kind: "video", name: job.file.name, assetId: up.id, thumbId: th.id, sourceId: null, w: vw, h: vh, duration: Math.round((v.duration||0)*10)/10,
      bytes: up.sizeBytes || job.file.size, origBytes: job.file.size, takenAt: job.file.lastModified || null, uploadedAt: Date.now(),
      uploader: S.me.id || null, category: job.category, status, pick: false, retouch: null, camera: null, ingestMs, cull: null, ai: null,
    });
    job.pid = pid; job.ms = ingestMs; job.status = status;
    Ingest.stage(job, status === "live" ? "Live in the gallery" : "Waiting for review");
  }finally{ URL.revokeObjectURL(url); }
}

/* a retoucher returns an edited file: it replaces the frame with the same file name */
async function replaceJob(job){
  const t0 = performance.now();
  const p = S.photos.get(job.target); if(!p) throw { msg: "That frame was deleted." };
  const album = S.albums.get(job.albumId); const c = cfg(album);
  Ingest.stage(job, "Reading");
  let dec; try{ dec = await decodeFile(job.file); }catch{ throw { msg: "This file can’t be opened in this browser. Export it as JPEG." }; }
  try{
    const a = analyze(dec.img);
    const del = renderDelivery(dec.img, a, "off", c.maxEdge);
    const delBlob = await toBlob(del, "image/jpeg", .92);
    const tf = fit(del.width, del.height, 640); const thumbBlob = await toBlob(drawScaled(del, tf.w, tf.h), "image/jpeg", .82);
    job.thumb = URL.createObjectURL(thumbBlob);
    Ingest.stage(job, "Uploading");
    const [up, th] = await uploadAll([[delBlob,"image/jpeg"],[thumbBlob,"image/jpeg"]]);
    const keepSource = p.sourceId || p.assetId;
    const ex = exposureFlags(a);
    const nextStatus = (p.status === "editing" || p.status === "review") ? (c.publish === "live" ? "live" : "review") : p.status;
    await photoRef(job.albumId, job.target).update({
      assetId: up.id, thumbId: th.id, sourceId: keepSource, w: del.width, h: del.height, bytes: up.sizeBytes || delBlob.size,
      retouch: "manual", editedAt: Date.now(), editor: S.me.id || null, status: nextStatus,
      cull: { sharp: a.sharp, mean: Math.round(a.mean), blurry: false, dark: ex.dark && c.cullOn, bright: ex.bright && c.cullOn, dup: false, dupOf: null },
    });
    if(p.assetId && p.assetId !== keepSource) deleteAsset(p.assetId);
    if(p.thumbId) deleteAsset(p.thumbId);
    job.pid = job.target; job.ms = Math.round(performance.now() - t0); job.status = nextStatus;
    Ingest.stage(job, nextStatus === "live" ? "Edited version is live" : "Edited version saved");
  }finally{ URL.revokeObjectURL(dec.url); }
}

/* run a different retouch preset from the untouched source */
async function retouchAgain(albumId, pid, preset){
  const p = S.photos.get(pid); if(!p || p.kind === "video") return;
  const album = S.albums.get(albumId); const c = cfg(album);
  const base = p.sourceId || p.assetId;
  const raw = await fetchBlob(base); const u = URL.createObjectURL(raw);
  try{
    const img = await loadImg(u); const a = analyze(img);
    let newAsset, del;
    if(!PRESETS[preset]){ del = drawScaled(img, img.naturalWidth, img.naturalHeight); newAsset = { id: base }; }
    else{ del = renderDelivery(img, a, preset, c.maxEdge); newAsset = await uploadBlob(await toBlob(del, "image/jpeg", .9), "image/jpeg"); }
    const tf = fit(del.width, del.height, 640); const th = await uploadBlob(await toBlob(drawScaled(del, tf.w, tf.h), "image/jpeg", .82), "image/jpeg");
    await photoRef(albumId, pid).update({ assetId: newAsset.id, thumbId: th.id, sourceId: base, retouch: PRESETS[preset] ? preset : null, w: del.width, h: del.height });
    if(p.assetId && p.assetId !== base && p.assetId !== newAsset.id) deleteAsset(p.assetId);
    if(p.thumbId) deleteAsset(p.thumbId);
  }finally{ URL.revokeObjectURL(u); }
}

async function deletePhoto(albumId, pid){
  const p = S.photos.get(pid); if(!p) return;
  await photoRef(albumId, pid).delete();
  for(const id of [p.assetId, p.thumbId, p.sourceId]) if(id) await deleteAsset(id);
}

/* ---------- Claude tagging: captions, search words, numbers, outfits, quality issues ---------- */
const AI_ISSUES = ["eyes closed","motion blur","out of focus","cut-off subject","obstructed","too dark","too bright"];
function aiPrompt(n){
  return `You are tagging ${n} event photo${n>1?"s":""} for a searchable live gallery. The images are attached in order.
For each image return one object with these fields:
"caption": one short sentence describing the scene and action. Never name or identify any person.
"tags": 5 to 12 lowercase search words for the scene, activity, objects, setting and mood.
"numbers": every clearly readable bib, race, jersey or table number as strings, or [].
"outfits": up to 6 short descriptions of visible clothing, like "red dress" or "navy suit, white shirt", or [].
"issues": those of ${JSON.stringify(AI_ISSUES)} that clearly apply to the main subject, or [].
Return only a JSON array of ${n} objects in image order.`;
}
function normAI(r){
  const arr = (v, max, len) => (Array.isArray(v) ? v : []).map(x=>String(x).trim()).filter(Boolean).slice(0, max).map(s=>s.slice(0, len));
  return {
    caption: String((r && r.caption) || "").slice(0, 220),
    tags: arr(r && r.tags, 15, 40).map(s=>s.toLowerCase()),
    numbers: arr(r && r.numbers, 12, 12).map(s=>s.replace(/[^0-9A-Za-z-]/g,"")).filter(Boolean),
    outfits: arr(r && r.outfits, 8, 60).map(s=>s.toLowerCase()),
    issues: arr(r && r.issues, 7, 30).map(s=>s.toLowerCase()).filter(s=>AI_ISSUES.includes(s)),
    at: Date.now(),
  };
}
const AI = {
  q: [], busy: false, paused: false, disabled: false, note: "", done: 0, failed: 0,
  enqueue(jobs){
    if(this.disabled || !S.sample || !S.aiLimits){ toast("Claude tagging isn’t available in this view."); return; }
    this.q.push(...jobs); this.render(); this.pump();
  },
  resume(){ this.paused = false; this.note = ""; this.render(); this.pump(); },
  async pump(){
    if(this.busy || this.paused || !this.q.length) return;
    this.busy = true; this.render();
    try{
      while(this.q.length && !this.paused && !this.disabled){
        const max = Math.max(1, Math.min((S.aiLimits && S.aiLimits.maxCount) || 1, 4));
        await this.batch(this.q.splice(0, max));
        this.render();
      }
    }finally{ this.busy = false; this.render(); }
  },
  async blobFor(job){
    if(job.blob) return job.blob;
    const p = S.photos.get(job.pid); if(!p) throw new Error("gone");
    const raw = await fetchBlob(p.assetId); const u = URL.createObjectURL(raw);
    try{ const img = await loadImg(u); const f = fit(img.naturalWidth, img.naturalHeight, 1280); return await toBlob(drawScaled(img, f.w, f.h), "image/jpeg", .85); }
    finally{ URL.revokeObjectURL(u); }
  },
  async batch(jobs){
    const ok = [], blobs = [];
    for(const j of jobs){ try{ blobs.push(await this.blobFor(j)); ok.push(j); }catch{ this.failed++; } }
    if(!ok.length) return;
    try{
      const opts = { images: blobs }; if(S.aiTier) opts.modelTier = S.aiTier;
      const out = await S.sample.json(aiPrompt(ok.length), opts);
      const arr = Array.isArray(out) ? out : (out && Array.isArray(out.photos) ? out.photos : [out]);
      for(let i=0;i<ok.length;i++){
        const r = arr[i]; if(!r || typeof r !== "object"){ this.failed++; continue; }
        try{ await photoRef(ok[i].albumId, ok[i].pid).update({ ai: normAI(r) }); this.done++; }catch{ this.failed++; }
      }
    }catch(e){
      const c = e && e.code;
      if(["not_granted","sampling_disabled","not_declared","capability_disabled","capability_removed","images_unavailable"].includes(c)){
        this.disabled = true; this.q = []; this.note = c === "not_granted" ? "Claude tagging is off because permission wasn’t given." : "Claude tagging isn’t available in this view.";
      }else if(c === "rate_limited" || c === "session_expired" || c === "upstream_error"){
        this.paused = true; this.q.unshift(...ok);
        this.note = c === "rate_limited" ? "Claude’s usage limit was reached. Resume tagging later." : c === "session_expired" ? "Your session expired. Reload, then resume tagging." : "Tagging stopped after a connection problem.";
      }else{ this.failed += ok.length; }
    }
  },
  render(){ if(Console.mountedTab === "ingest") Console.tabs.ingest.renderAI(); }
};

/* ---------- guest analytics: de-identified, one record per browser, no account ids ---------- */
function hourKey(ts){ const d = new Date(ts || Date.now()); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}T${String(d.getHours()).padStart(2,"0")}`; }
function sessionId(){ let v = Store.get("sid", null); if(!v || !/^[a-z0-9]{8,40}$/.test(v)){ v = (rid() + rid()).slice(0, 24); Store.set("sid", v); } return v; }
const Track = {
  ref: null, data: null, albumId: null, dirty: false, timer: null, busy: false, off: false,
  async start(albumId){
    if(this.albumId === albumId) return;
    const prev = this.flush(); this.albumId = albumId; this.data = null; this.ref = null; await prev;
    if(S.me.canEdit || this.off || !S.db) return;
    const ref = S.db.doc(`stats/${albumId}/sessions/${sessionId()}`);
    let data;
    try{ const s = await ref.get(); data = s.exists ? JSON.parse(JSON.stringify(s.data())) : { opens: 0, pv: {}, dl: {}, posters: 0, hours: {} }; }
    catch{ this.off = true; return; }
    if(this.albumId !== albumId) return;
    this.ref = ref; this.data = data;
    this.bump("open");
  },
  bump(kind, pid){
    if(!this.data || this.off) return;
    const a = this.data;
    if(kind === "open"){ a.opens = (a.opens||0)+1; a.hours = a.hours || {}; const h = hourKey(); a.hours[h] = (a.hours[h]||0)+1; }
    else if(kind === "view"){ a.pv = a.pv || {}; a.pv[pid] = (a.pv[pid]||0)+1; }
    else if(kind === "dl"){ a.dl = a.dl || {}; a.dl[pid] = (a.dl[pid]||0)+1; }
    else if(kind === "poster"){ a.posters = (a.posters||0)+1; }
    this.dirty = true; this.schedule();
  },
  schedule(){ if(this.timer) return; this.timer = setTimeout(()=>{ this.timer = null; this.flush(); }, 4000); },
  async flush(){
    if(!this.dirty || !this.ref || this.off) return;
    if(this.busy){ this.schedule(); return; }
    this.busy = true; this.dirty = false;
    try{ await this.ref.set(this.data); }
    catch(e){ if(e && (e.code === "invalid_argument" || e.code === "revoked")) this.off = true; else this.dirty = true; }
    finally{ this.busy = false; }
  }
};
document.addEventListener("visibilitychange", ()=>{ if(document.visibilityState === "hidden") Track.flush(); });

/* ---------- posters, carousel slides, table cards ---------- */
function wrapLines(x, text, maxW, maxLines){
  const words = String(text||"").split(/\s+/).filter(Boolean); const lines = []; let line = "";
  for(const w of words){
    const t = line ? line + " " + w : w;
    if(x.measureText(t).width <= maxW || !line){ line = t; }
    else{ lines.push(line); line = w; if(lines.length === maxLines){ line = ""; break; } }
  }
  if(line && lines.length < maxLines) lines.push(line);
  if(lines.length === maxLines && words.join(" ") !== lines.join(" ")){
    let last = lines[maxLines-1]; while(last && x.measureText(last + "…").width > maxW) last = last.slice(0,-1); lines[maxLines-1] = last.trimEnd() + "…";
  }
  return lines;
}
function setWide(x){ try{ if("fontStretch" in x) x.fontStretch = "expanded"; }catch{} }
function setNormal(x){ try{ if("fontStretch" in x) x.fontStretch = "normal"; }catch{} }
async function renderPoster(album, p, fmt, idx, total){
  await fontsReady(); try{ await document.fonts.load('800 expanded 60px "Archivo"'); }catch{}
  const B = brandOf(album), bg = B.color, fg = contrastInk(bg);
  const [W,H] = ({ post:[1080,1350], story:[1080,1920], card:[1200,1800], slide:[1080,1350] })[fmt];
  const c = mkCanvas(W,H), x = c.getContext("2d");
  x.fillStyle = bg; x.fillRect(0,0,W,H);
  let logo = null; if(B.logoId){ try{ logo = await loadImg(blobUrl(B.logoId)); }catch{} }
  let img = null; if(p && p.kind !== "video"){ try{ img = await loadImg(blobUrl(p.assetId)); }catch{} } else if(p){ try{ img = await loadImg(blobUrl(p.thumbId)); }catch{} }
  const link = galleryLink(album);
  const meta = [fmtDate(album.date), album.venue].filter(Boolean).join(", ");
  const drawLogo = (lx, ly, maxH, maxW)=>{ if(!logo) return 0; const s = Math.min(maxH/logo.naturalHeight, maxW/logo.naturalWidth); const w = logo.naturalWidth*s, h = logo.naturalHeight*s; x.drawImage(logo, lx, ly + (maxH-h)/2, w, h); return w; };
  x.fillStyle = fg; x.textBaseline = "alphabetic";
  if(fmt === "slide"){
    const band = 150;
    if(img){ drawCover(x, img, 0, 0, W, H-band); await drawWatermark(x, 0, 0, W, H-band, album); }
    x.fillStyle = bg; x.fillRect(0, H-band, W, band); x.fillStyle = fg;
    const lw = drawLogo(56, H-band+35, 80, 220);
    setWide(x); x.font = "800 40px Archivo, Arial, sans-serif";
    const tl = wrapLines(x, album.title, W - 112 - (lw ? lw+28 : 0) - 120, 1);
    x.fillText(tl[0] || "", 56 + (lw ? lw+28 : 0), H - band/2 + 14);
    setNormal(x); x.font = "600 30px Archivo, Arial, sans-serif"; x.textAlign = "right"; x.fillText(`${idx}/${total}`, W-56, H - band/2 + 12); x.textAlign = "left";
  }else if(fmt === "card"){
    const m = 90; let y = 170;
    const lw = logo ? drawLogo(m, 70, 110, 420) : 0;
    if(lw) y = 270;
    setWide(x); x.font = "850 86px Archivo, Arial, sans-serif";
    for(const l of wrapLines(x, album.title, W-2*m, 3)){ x.fillText(l, m, y); y += 96; }
    setNormal(x); x.font = "400 38px Archivo, Arial, sans-serif"; if(meta){ x.globalAlpha=.85; x.fillText(meta, m, y+6); x.globalAlpha=1; y += 70; }
    const qs = 640, qx = (W-qs)/2, qy = Math.max(y + 40, 760);
    if(link && drawQR(x, link, qx, qy, qs)){
      x.fillStyle = fg; x.font = "700 46px Archivo, Arial, sans-serif"; x.textAlign = "center";
      x.fillText("Scan to see the photos live", W/2, qy + qs + 86); x.textAlign = "left";
    }else{ x.font = "600 40px Archivo, Arial, sans-serif"; x.fillText("Ask the photographer for the gallery link.", m, qy + 60); }
  }else{
    const m = 64, ph = fmt === "story" ? 1240 : 870;
    if(img){ drawCover(x, img, m, m, W-2*m, ph); if(p.kind !== "video") await drawWatermark(x, m, m, W-2*m, ph, album); }
    const qs = fmt === "story" ? 240 : 200, hasQR = !!(link && qrMatrix(link));
    const textW = W - 2*m - (hasQR ? qs + 36 : 0);
    let y = m + ph + (fmt === "story" ? 110 : 92);
    setWide(x); x.font = `850 ${fmt === "story" ? 64 : 56}px Archivo, Arial, sans-serif`; x.fillStyle = fg;
    for(const l of wrapLines(x, album.title, textW, 2)){ x.fillText(l, m, y); y += fmt === "story" ? 74 : 64; }
    setNormal(x); x.font = "400 30px Archivo, Arial, sans-serif";
    if(meta){ x.globalAlpha = .85; x.fillText(meta, m, y + 4); x.globalAlpha = 1; }
    if(logo) drawLogo(m, H - m - 90, 90, 360);
    if(hasQR){ const qy = fmt === "story" ? H - m - qs - 40 : m + ph + 40; drawQR(x, link, W - m - qs, qy, qs); x.font = "500 22px Archivo, Arial, sans-serif"; x.fillStyle = fg; x.textAlign = "center"; x.fillText("All photos", W - m - qs/2, qy + qs + 32); x.textAlign = "left"; }
  }
  return toBlob(c, fmt === "card" ? "image/png" : "image/jpeg", .92);
}
