import json,time,urllib.request,urllib.error,sys,re,base64,uuid,os
from pathlib import Path
from playwright.sync_api import sync_playwright
source=Path(os.environ.get('LENSDROP_SOURCE_ROOT',Path(__file__).resolve().parents[2]));w=Path(os.environ.get('LENSDROP_UI_WORKDIR',source/'.h55-ui-tests'));i=json.loads((w/'ui-info.json').read_text());results=[];errors=[]
def check(name,ok):
 assert ok,name
 results.append(name);print('PASS',name,flush=True)
def control(action):
 req=urllib.request.Request(i['control']+'/'+action,headers={'Authorization':'Bearer '+i['controlSecret']});return json.load(urllib.request.urlopen(req))
def forward(route):
 req=route.request
 if not (req.url.startswith(i['base']+'/') or req.url.startswith(i['stationUI'].split('/#')[0]+'/')):
  route.abort();return
 headers={k:v for k,v in req.headers.items() if k.lower() not in ['host','content-length','accept-encoding','connection']}
 r=urllib.request.Request(req.url,data=req.post_data_buffer,headers=headers,method=req.method)
 try:
  with urllib.request.urlopen(r,timeout=10) as response: status=response.status;data=response.read();h=dict(response.headers)
 except urllib.error.HTTPError as response:status=response.code;data=response.read();h=dict(response.headers)
 h={k:v for k,v in h.items() if k.lower() not in ['transfer-encoding','content-length','content-encoding','connection']};route.fulfill(status=status,body=data,headers=h)
def setup(page,target,folder,location):
 folder=Path(folder)
 def backend(route,options):
  assert route.startswith('/') and not route.startswith('//')
  opts=options or {};body=opts.get('body');headers=opts.get('headers',{});data=body.encode() if isinstance(body,str) else None
  req=urllib.request.Request(target+route,data=data,headers=headers,method=opts.get('method','GET'))
  try:r=urllib.request.urlopen(req,timeout=12)
  except urllib.error.HTTPError as e:r=e
  return {'status':r.status,'b64':base64.b64encode(r.read()).decode(),'headers':dict(r.headers)}
 page.expose_function('testBackend',backend)
 html=(folder/'index.html').read_text();scripts=re.findall(r'<script[^>]+src="([^"]+)"',html);css=re.findall(r'<link[^>]+href="([^"]+\.css)"',html)
 html=re.sub(r'<script\b[^>]*>.*?</script>','',html,flags=re.S);html=re.sub(r'<link[^>]+rel="stylesheet"[^>]*>','',html);page.set_content(html)
 for name in css:page.add_style_tag(content=(folder/Path(name).name).read_text())
 page.evaluate("""() => {window.fetch=async(p,o)=>{const x=await window.testBackend(String(p),o||{});return new Response(Uint8Array.from(atob(x.b64),c=>c.charCodeAt(0)),{status:x.status,headers:x.headers});};window.testStorage={v:{},getItem(k){return this.v[k]||null},setItem(k,v){this.v[k]=String(v)},removeItem(k){delete this.v[k]}};window.testCrypto={randomUUID(){return '10000000-1000-4000-8000-'+String(++window.testCounter).padStart(12,'0')}};window.testCounter=0;}""")
 for name in scripts:
  js=(folder/Path(name).name).read_text();page.evaluate('(a)=>{new Function("location","history","sessionStorage","localStorage","crypto",a.source)(a.location,{replaceState(){}},window.testStorage,window.testStorage,window.testCrypto);}',{'source':js,'location':location})
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);c=b.new_context(viewport={'width':1440,'height':1050});c.route('**/*',lambda r:r.abort())
 a=c.new_page();a.on('pageerror',lambda e:errors.append(str(e)));a.on('dialog',lambda d:d.accept());setup(a,i['base'],source/'cloud/public',{'origin':'https://lensdrop.test','hash':'','pathname':'/admin'});a.locator('#key').fill(i['adminKey']);a.locator('#connect').click();a.locator('#staffDesk .desk-grid article').nth(2).wait_for();check('Admin loads three private archive photographs',a.locator('#staffDesk .desk-grid article').count()==3)
 a.wait_for_function("Array.from(document.querySelectorAll('#staffDesk .desk-grid img')).every(x=>x.complete&&x.naturalWidth>0)")
 check('Protected thumbnails render as local blob URLs',a.locator('#staffDesk .desk-grid img').first.get_attribute('src').startswith('blob:'))
 check('Archive and browsing have not created print orders',control('counts')['orders']==0)
 desk=a.locator('#staffDesk');desk.locator('select').first.select_option('cloud');desk.get_by_role('button',name='Save default for newly prepared phones',exact=True).click();a.wait_for_timeout(250);check('Method 2 setting saved and shown', 'Method 2: cloud print delivery' in desk.inner_text())
 desk.locator('input[type=checkbox]').check();desk.get_by_role('button',name='Save staff permission',exact=True).click();a.wait_for_timeout(200)
 s=c.new_page();s.on('pageerror',lambda e:errors.append(str(e)));s.on('dialog',lambda d:d.accept());setup(s,i['stationUI'].split('/#')[0],source/'clients/print-station/public',{'origin':'http://127.0.0.1','hash':'#'+i['uiSecret'],'pathname':'/'});sd=s.locator('#staffDesk');sd.locator('.desk-grid article').nth(2).wait_for();check('Paired Windows dashboard receives scoped staff library',sd.locator('.desk-grid article').count()==3)
 sd.get_by_label('Guest ticket reference').fill('REF '+i['group']['id'].replace('-','')[:8].upper());sd.get_by_role('button',name='Lookup ticket (NO PRINT)',exact=True).click();s.wait_for_timeout(200);check('Printed REF lookup shows only its one assigned photo',sd.locator('.desk-grid article').count()==1);check('Lookup alone creates no print job',control('counts')['orders']==0)
 sd.get_by_role('button',name='Preview / prepare print',exact=True).click();s.locator('#staffDesk dialog').wait_for();s.wait_for_function("document.querySelector('#staffDesk dialog img').naturalWidth>0");sd.get_by_role('button',name='Cancel preview',exact=True).click();check('Cancelling preview does not print',control('counts')['orders']==0)
 sd.get_by_role('button',name='Preview / prepare print',exact=True).click();sd.get_by_role('button',name='Confirm ONE print request',exact=True).click();s.wait_for_function("document.querySelector('#staffDesk .desk-message').textContent.includes('is ready')");check('Explicit confirmation creates exactly one ready order',control('counts')['orders']==1);check('Successful acknowledgement clears browser pending identity',s.evaluate("!window.testStorage.getItem('lensdrop_staff_order_v1:local-station')"))
 check('Physical collection is not offered for ready work',sd.get_by_role('button',name='Confirm handover / collected (NO PRINT)',exact=True).count()==0)
 outcome=control('simulate');check('Staff order goes through existing simulated station worker once',len(outcome['states'])==1 and outcome['states'][0]['state']=='simulated');sd.get_by_role('button',name='Refresh orders',exact=True).click();s.wait_for_timeout(200);check('Simulation does not unlock physical collection',sd.get_by_role('button',name='Confirm handover / collected (NO PRINT)',exact=True).count()==0)
 control('seed-confirmed');sd.get_by_role('button',name='Refresh orders',exact=True).click();sd.get_by_role('button',name='Confirm handover / collected (NO PRINT)',exact=True).wait_for();before=control('counts')['orders'];sd.get_by_role('button',name='Confirm handover / collected (NO PRINT)',exact=True).click();s.wait_for_timeout(200);check('Collection records handover without creating print',control('counts')['collections']==1 and control('counts')['orders']==before);sd.get_by_role('button',name='Refresh orders',exact=True).click();s.wait_for_timeout(100);check('Collected order has no repeat handover action',sd.get_by_role('button',name='Confirm handover / collected (NO PRINT)',exact=True).count()==0)
 sd.screenshot(path=str(w/'H55_Windows_Staff_Desk.png'));desk.screenshot(path=str(w/'H55_Cloud_Admin_Staff_Desk.png'))
 a.set_viewport_size({'width':390,'height':844});check('Staff panel fits mobile width',a.evaluate("document.querySelector('#staffDesk').scrollWidth<=document.querySelector('#staffDesk').clientWidth+1"));desk.screenshot(path=str(w/'H55_Staff_Desk_Mobile.png'))
 check('No JavaScript runtime errors',not errors);b.close()
(w/'ui-results.json').write_text(json.dumps({'scope':'Actual HTML/CSS/JS with injected location/history/storage/crypto and explicit HTTP bridge to actual local cloud/station, synthetic photos and driver; not native Windows/Android/HTTPS/device test','checks':results,'errors':errors},indent=2));print('PASSED',len(results),'UI checks')
