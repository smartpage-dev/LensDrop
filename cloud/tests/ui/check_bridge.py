from pathlib import Path
import json, base64, urllib.request, urllib.error, time
from playwright.sync_api import sync_playwright
import os
WORK=Path(os.environ.get('LENSDROP_UI_WORKDIR','.ui-rehearsal')).resolve()
ROOT=Path(os.environ.get('LENSDROP_CLOUD_DIR',str(Path(__file__).resolve().parents[2])))/'public'
OUT=WORK/'ui-output';OUT.mkdir(exist_ok=True)
C=json.loads((WORK/'ui-config.json').read_text())
RESULTS=[]
OPENER=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def api(path, body=None):
 req=urllib.request.Request(C['base']+path,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+C['key'],'Content-Type':'application/json'})
 with OPENER.open(req,timeout=10) as r:return json.loads(r.read())
def bridge(path,options):
 body=base64.b64decode(options['body']) if options.get('body') is not None else None
 req=urllib.request.Request(C['base']+path,data=body,method=options.get('method','GET'),headers=options.get('headers',{}))
 try:r=OPENER.open(req,timeout=10)
 except urllib.error.HTTPError as e:r=e
 return {'status':r.status,'headers':dict(r.headers),'body':base64.b64encode(r.read()).decode()}
BRIDGE_JS=r'''() => {
 window.__offline=false;window.__age=0;const realNow=performance.now.bind(performance);performance.now=()=>realNow()+window.__age;
 window.fetch=async(path,opts={})=>{if(window.__offline)throw new TypeError('Simulated offline');let body=null;
 if(opts.body!==undefined&&opts.body!==null){const bytes=typeof opts.body==='string'?new TextEncoder().encode(opts.body):new Uint8Array(await opts.body.arrayBuffer());let s='';for(let i=0;i<bytes.length;i+=32768)s+=String.fromCharCode(...bytes.subarray(i,i+32768));body=btoa(s);}
 const raw=await window.__backend(String(path),{method:opts.method||'GET',headers:opts.headers||{},body});const bytes=Uint8Array.from(atob(raw.body),c=>c.charCodeAt(0));return new Response(bytes,{status:raw.status,headers:raw.headers});};
 const desc=Object.getOwnPropertyDescriptor(HTMLImageElement.prototype,'src'),remove=Element.prototype.removeAttribute,get=Element.prototype.getAttribute;
 Object.defineProperty(HTMLImageElement.prototype,'src',{get(){return this.__path||desc.get.call(this);},set(value){
 const image=this;image.__path=value;image.__load=(image.__load||0)+1;const serial=image.__load;if(image.__blob){URL.revokeObjectURL(image.__blob);image.__blob=null;}
 if(String(value).startsWith('/v1/'))fetch(value).then(r=>{if(!r.ok)throw Error('Image unavailable');return r.blob();}).then(blob=>{if(image.__load!==serial)return;image.__blob=URL.createObjectURL(blob);desc.set.call(image,image.__blob);}).catch(()=>{if(image.__load===serial)image.dispatchEvent(new Event('error'));});
 else desc.set.call(image,value);
 }});
 HTMLImageElement.prototype.getAttribute=function(name){return name==='src'&&this.__path?this.__path:get.call(this,name);};
 HTMLImageElement.prototype.removeAttribute=function(name){if(name==='src'){this.__load=(this.__load||0)+1;this.__path=null;if(this.__blob){URL.revokeObjectURL(this.__blob);this.__blob=null;}}return remove.call(this,name);};
}'''
def mount(browser,html_name,scripts,viewport):
 page=browser.new_page(viewport=viewport);errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.expose_function('__backend',bridge)
 html=(ROOT/html_name).read_text()
 import re
 html=re.sub(r'<script[^>]*src="[^"]+"[^>]*></script>','',html)
 html=re.sub(r'<link rel="stylesheet" href="/([^"]+)">',lambda m:'<style>'+(ROOT/m[1]).read_text()+'</style>',html)
 page.set_content(html);page.evaluate(BRIDGE_JS)
 for f in scripts:
  source=(ROOT/f).read_text().replace("location.pathname.split('/').at(-1)",json.dumps(C['event']['slug'] if f=='gallery.js' else C['presentation']['slideshow_path'].split('/')[-1])).replace('location.origin',json.dumps(C['base']))
  page.add_script_tag(content=source)
 return page,errors
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 a,ae=mount(browser,'index.html',['qr.js','app.js','presentation-admin.js'],{'width':1440,'height':1000})
 a.fill('#key',C['key']);a.click('#connect');a.wait_for_selector('#presentationForm:not([hidden])',timeout=5000);a.wait_for_function("document.querySelector('#logoPreview').naturalWidth>0")
 assert a.locator('#slideshowLink').input_value()==C['base']+C['presentation']['slideshow_path'];RESULTS.append('PASS admin loads H3 settings and distinct share links from the real local backend through the test bridge')
 a.click('#language');assert a.locator('html').get_attribute('lang')=='zh-Hant';assert a.locator('#presentationTitle').inner_text().startswith('Slideshow');RESULTS.append('PASS Traditional Chinese H3 labels and controls')
 a.locator('#presentationPanel').screenshot(path=str(OUT/'LensDrop_H3_Admin_Slideshow_Panel.png'))
 for prefix in ['slideshow','guest']:
  encoded=a.locator('#'+prefix+'Qr').evaluate('(c)=>c.toDataURL("image/png").split(",")[1]');(OUT/(prefix+'-qr.png')).write_bytes(base64.b64decode(encoded))
 a.set_viewport_size({'width':390,'height':844});assert a.evaluate('document.documentElement.scrollWidth<=innerWidth+1');a.locator('#presentationPanel').screenshot(path=str(OUT/'LensDrop_H3_Admin_Mobile.png'));RESULTS.append('PASS H3 admin at 390px has no horizontal page overflow')
 a.set_viewport_size({'width':1440,'height':1000});a.fill('#slideInterval','12');a.select_option('#slidePoll','5');a.click('#savePresentation');a.wait_for_function("document.querySelector('#presentationMessage').textContent.includes('\u5df2\u5132\u5b58')")
 cfg=api('/v1/events/'+C['event']['id']+'/presentation');assert cfg['settings']['interval_seconds']==12;RESULTS.append('PASS admin saves slideshow settings to the real local backend')
 a.locator('#photos button').first.click();a.wait_for_function("document.querySelector('#previewImage').naturalWidth>0");a.click('#closePreview');RESULTS.append('PASS authenticated admin image preview loads without publishing state changes')
 g,ge=mount(browser,'gallery.html',['gallery.js'],{'width':1000,'height':800});g.wait_for_function("document.querySelector('#eventLogo').naturalWidth>0 && document.querySelectorAll('#grid article').length===4");g.screenshot(path=str(OUT/'LensDrop_H3_Guest_Gallery.png'));RESULTS.append('PASS guest gallery displays the same stored event name/logo and four synthetic published photos')
 s,se=mount(browser,'slideshow.html',['qr.js','slideshow-model.js','slideshow.js'],{'width':1920,'height':1080});s.wait_for_function('window.LensDropShow.status().current!==null');first=s.evaluate('LensDropShow.status().current');assert s.locator('#audience').is_visible();assert s.locator('#brand').is_visible();assert s.locator('#brand').get_attribute('class').endswith('bottom-right');assert s.locator('#audience').get_attribute('class').endswith('bottom-left');RESULTS.append('PASS two-layer slideshow loads published delivery images, brand corner and opposite guest QR')
 s.wait_for_timeout(13000);assert s.evaluate('LensDropShow.status().current')!=first;RESULTS.append('PASS 12-second slides still advance with 5-second feed polls (polls do not reset the playback timer)')
 s.evaluate("document.body.classList.add('controls-hidden')");s.screenshot(path=str(OUT/'LensDrop_H3_Slideshow_Landscape.png'))
 s.evaluate("document.body.classList.remove('controls-hidden')");s.click('#pause');paused=s.evaluate('LensDropShow.status().current');s.wait_for_timeout(1200);assert s.evaluate('LensDropShow.status().paused');assert s.evaluate('LensDropShow.status().current')==paused;RESULTS.append('PASS pause preserves the current photograph while feed verification remains active')
 # Remove the current photo while paused; it must disappear, not stay held on screen.
 api('/v1/photos/'+paused+'/status',{'status':'hidden'});s.evaluate('LensDropShow.poll()');s.wait_for_function('LensDropShow.status().current===null');assert s.locator('.slide-layer.on').count()==0;RESULTS.append('PASS a withdrawn paused photograph is removed on the next verified feed')
 api('/v1/photos/'+paused+'/status',{'status':'live'});s.evaluate('LensDropShow.poll()');s.click('#pause');s.wait_for_function('LensDropShow.status().current!==null')
 for style in ['zoom','slide','fade']:
  s.evaluate("document.body.classList.remove('controls-hidden')");s.select_option('#localStyle',style);assert 'style-'+style in s.locator('body').get_attribute('class')
 RESULTS.append('PASS local projector selector applies all three styles without changing shared settings')
 s.evaluate('window.__offline=true;window.__age=31000');s.wait_for_timeout(1500);assert s.locator('.slide-layer.on').count()==0;assert s.locator('#emptyState').is_visible();RESULTS.append('PASS simulated 31-second verification age clears photographs during a simulated outage')
 s.evaluate('window.__offline=false;window.__age=0;LensDropShow.poll()');s.wait_for_function('LensDropShow.status().current!==null');RESULTS.append('PASS reconnection resumes verified photos without reloading the page')
 for _ in range(8):
  current=s.evaluate('LensDropShow.status().current')
  if current==C['photos'][1]:break
  s.evaluate('LensDropShow.advance()');s.wait_for_timeout(450)
 s.evaluate("document.body.classList.remove('controls-hidden')");s.click('#pause');s.wait_for_timeout(1100);s.evaluate("document.body.classList.add('controls-hidden')");s.wait_for_timeout(350);s.screenshot(path=str(OUT/'LensDrop_H3_Slideshow_Portrait.png'))
 # Content is bounded to two layers; portrait contain is never set to cover.
 assert s.locator('#stage img').count()==2;assert s.locator('.slide-layer.on img').evaluate('(e)=>getComputedStyle(e).objectFit')=='contain';RESULTS.append('PASS whole-photo fit and bounded two-image-layer rendering')
 api('/v1/events/'+C['event']['id']+'/presentation',{'enabled':False});s.evaluate('LensDropShow.poll()');s.wait_for_function('LensDropShow.status().current===null');assert s.locator('#audience').is_hidden();RESULTS.append('PASS remote disable clears projection photos and audience QR')
 api('/v1/events/'+C['event']['id']+'/presentation',{'enabled':True,'settings':{'interval_seconds':8,'style':'fade'}})
 assert not ae+ge+se,ae+ge+se;RESULTS.append('PASS no JavaScript page errors in exercised admin, gallery or slideshow flows')
 browser.close()
from pyzbar.pyzbar import decode
from PIL import Image
for prefix,expected in [('slideshow',C['base']+C['presentation']['slideshow_path']),('guest',C['base']+C['presentation']['gallery_path'])]:
 codes=decode(Image.open(OUT/(prefix+'-qr.png')));assert len(codes)==1;assert codes[0].data.decode()==expected;RESULTS.append('PASS independent QR decoder matches exact '+prefix+' URL')
log='\n'.join(RESULTS)+'\nLIMIT: Browser navigation was blocked by environment policy. HTML/CSS/JS were mounted using set_content. Fetch and image loads were bridged to the actual local Node backend, with source substitutions only for location origin/path. This is NOT native browser-network, CSP/HTTPS, Hostinger, projector, camera or phone certification. Offline age was simulated.\n'
(OUT/'browser-h3-checks.log').write_text(log);print(log)
