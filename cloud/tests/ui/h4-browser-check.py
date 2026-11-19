"""Real local backend + Chromium. Falls back to an explicitly labelled transport bridge."""
from pathlib import Path
import json,os,subprocess,tempfile,time,urllib.request,urllib.error,urllib.parse,sys,re,ast,base64
from playwright.sync_api import sync_playwright
import cv2
ROOT=Path(__file__).resolve().parents[2]
OUT=Path(os.environ.get('H4_UI_OUTPUT','h4-ui-output')).resolve();OUT.mkdir(parents=True,exist_ok=True)
work=tempfile.TemporaryDirectory();server=subprocess.Popen(['node',str(Path(__file__).with_name('h4-seed.cjs'))],env={**os.environ,'H4_UI_WORKDIR':work.name},stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
results=[]
def check(name,condition):
 if not condition:raise AssertionError(name)
 results.append(name);print('PASS',name,flush=True)
try:
 for _ in range(150):
  if Path(work.name,'config.json').exists():break
  if server.poll() is not None:raise RuntimeError(server.stderr.read())
  time.sleep(.1)
 C=json.loads(Path(work.name,'config.json').read_text());BASE=C['base'];opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
 def call(path,body=None):
  req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+C['key'],'Content-Type':'application/json'})
  with opener.open(req,timeout=15) as r:return json.loads(r.read())
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path=os.environ.get('CHROMIUM','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
  context=browser.new_context(viewport={'width':1440,'height':1050})
  mode='DOM fetch/image bridge to real local backend; browser navigation/CSP/HTTPS not verified';BASE_UI=BASE
  print('TRANSPORT',mode,flush=True)
  def bridge(path,options):
   body=base64.b64decode(options['body']) if options.get('body') is not None else None
   req=urllib.request.Request(BASE+path,data=body,method=options.get('method','GET'),headers=options.get('headers',{}))
   try:r=opener.open(req,timeout=15)
   except urllib.error.HTTPError as e:r=e
   return {'status':r.status,'headers':dict(r.headers),'body':base64.b64encode(r.read()).decode()}
  legacy=ast.parse(Path(__file__).with_name('check_bridge.py').read_text())
  BRIDGE_JS=next(ast.literal_eval(n.value) for n in legacy.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='BRIDGE_JS' for t in n.targets))
  errors=[]
  def mount(page,htmlfile,scripts,token=None):
   page.expose_function('__backend',bridge) if not getattr(page,'_h4exposed',False) else None
   page._h4exposed=True
   html=(ROOT/'public'/htmlfile).read_text()
   html=re.sub(r'<script[^>]*src="[^"]+"[^>]*></script>','',html)
   html=re.sub(r'<link rel="stylesheet" href="/([^"]+)">',lambda m:'<style>'+(ROOT/'public'/m[1]).read_text()+'</style>',html)
   page.set_content(html);page.evaluate(BRIDGE_JS)
   for name in scripts:
    code=(ROOT/'public'/name).read_text().replace('location.origin',json.dumps(BASE_UI))
    if token:code=code.replace("location.pathname.split('/').pop()",json.dumps(token))
    page.add_script_tag(content=code)
  admin=context.new_page();admin.on('pageerror',lambda e:errors.append(str(e)))
  mount(admin,'index.html',['qr.js','app.js','presentation-admin.js','personal-admin.js'])
  admin.locator('#key').fill(C['key']);admin.locator('#connect').click();admin.wait_for_function("document.querySelector('#personalDeliveries').children.length===2")
  check('Admin loads both QR delivery modes',admin.locator('#personalDeliveries').inner_text().find('single')>=0)
  check('Personal auto-release defaults OFF',not admin.locator('#personalAuto').is_checked())
  guest=context.new_page();guest.on('pageerror',lambda e:errors.append(str(e)));mount(guest,'personal.html',['personal.js'],C['group']['guest_path'].split('/')[-1]);guest.wait_for_function("document.querySelector('#personalState').textContent.includes('prepared')")
  check('Unapproved personal photos render waiting page',guest.locator('.personal-photo').count()==0)
  check('Shared event branding loads',guest.locator('#personalLogo').is_visible())
  admin.locator('#personalDeliveries .device').filter(has_text='Group 001').locator('button').click();admin.wait_for_selector('#personalDetails .personal-qr svg');admin.locator('#personalDetails').screenshot(path=str(OUT/'H4_Admin_Personal_Delivery.png'))
  admin.locator('#personalDetails .personal-qr svg').screenshot(path=str(OUT/'H4_Personal_QR_Test.png'));image=cv2.imread(str(OUT/'H4_Personal_QR_Test.png'));value,_,_=cv2.QRCodeDetector().detectAndDecode(image)
  check('Independently decoded personal QR matches original stable URL',value==BASE_UI+C['group']['guest_path'])
  admin.locator('#personalDetails button').filter(has_text='Release to this personal QR only').first.click();guest.wait_for_selector('.personal-photo',timeout=8000)
  check('Admin release makes only one chosen photo personally visible',guest.locator('.personal-photo').count()==1)
  check('Personal release does not publish whole-event gallery',len(call('/v1/g/'+C['event']['slug'])['photos'])==0)
  guest.locator('.personal-photo').first.click();check('Selected preview and download appear',guest.locator('#personalPreview').is_visible() and 'download=1' in guest.locator('#personalDownload').get_attribute('href'))
  guest.locator('#closePersonal').click();guest.screenshot(path=str(OUT/'H4_Personal_Gallery_Desktop.png'),full_page=True)
  guest.set_viewport_size({'width':390,'height':844});guest.screenshot(path=str(OUT/'H4_Personal_Gallery_Mobile.png'),full_page=True)
  check('Mobile gallery has no horizontal overflow',guest.evaluate('document.documentElement.scrollWidth<=innerWidth+1'))
  # Release the other photo: URL stays unchanged and the page updates without user reload.
  for pid in C['photos']:call('/v1/photos/'+pid+'/personal-release',{'released':True})
  guest.wait_for_function("document.querySelectorAll('.personal-photo').length===2",timeout=8000)
  check('Same group page receives another approved photograph automatically',guest.locator('#deliveryLabel').inner_text().startswith('Group 001'))
  other=context.new_page();mount(other,'personal.html',['personal.js'],C['single']['guest_path'].split('/')[-1]);other.wait_for_function("document.querySelector('#personalState').textContent.includes('prepared')");check('Unrelated single QR does not expose group photos',other.locator('.personal-photo').count()==0)
  guest.locator('.personal-photo').first.click();visible=guest.evaluate("document.querySelector('#personalImage').getAttribute('src')").split('/')[-2];call('/v1/photos/'+visible+'/status',{'status':'hidden'})
  guest.wait_for_function("document.querySelectorAll('.personal-photo').length===1",timeout=8000)
  check('Hiding a photo clears its open personal preview',not guest.locator('#personalPreview').is_visible())
  call('/v1/deliveries/'+C['group']['id']+'/revoke',{});guest.wait_for_function("document.querySelector('#personalState').textContent.includes('unavailable')",timeout=8000)
  check('Revoked personal link clears photos and event logo',guest.locator('.personal-photo').count()==0 and not guest.locator('#personalLogo').is_visible())
  check('No browser JavaScript errors',not errors)
  browser.close()
 (OUT/'results.json').write_text(json.dumps({'transport':mode,'passed':len(results),'checks':results,'synthetic_photos':True,'android_ui_tested':False},indent=2))
 print('RESULT',len(results),'browser/QR checks passed; transport:',mode)
finally:
 server.terminate()
 try:server.wait(timeout=10)
 except subprocess.TimeoutExpired:server.kill();server.wait()
 work.cleanup()
