import json, urllib.request, urllib.error, pathlib, hashlib, uuid, re, base64, os
from playwright.sync_api import sync_playwright
source=pathlib.Path(os.environ.get('LENSDROP_SOURCE_ROOT', pathlib.Path(__file__).resolve().parents[2]));root=pathlib.Path(os.environ.get('LENSDROP_UI_WORKDIR',source/'.h5-ui-tests'));(root/'logs').mkdir(parents=True,exist_ok=True)
info=json.loads((root/'ui-info.json').read_text());out=source/'docs/h5/screenshots';out.mkdir(parents=True,exist_ok=True)
checks=[];errors=[]
def check(value,label):
    assert value,label
    checks.append(label);print('PASS',label)
def request(url,body=None,auth=None):
    h={'Content-Type':'application/json'}
    if auth:h['Authorization']='Bearer '+auth
    with urllib.request.urlopen(urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers=h),timeout=10) as r:return json.load(r)
def setup(page,target,folder,location):
    def backend(route,options):
        assert route.startswith('/') and not route.startswith('//')
        opts=options or {};body=opts.get('body');h=opts.get('headers',{});data=body.encode() if isinstance(body,str) else None
        req=urllib.request.Request(target+route,data=data,headers=h,method=opts.get('method','GET'))
        try:r=urllib.request.urlopen(req,timeout=12)
        except urllib.error.HTTPError as e:r=e
        return {'status':r.status,'text':r.read().decode(),'headers':dict(r.headers)}
    page.expose_function('testBackend',backend)
    page.add_init_script('window.__testMarker=true')
    html=(folder/'index.html').read_text()
    scripts=re.findall(r'<script[^>]+src="([^"]+)"',html)
    html=re.sub(r'<script\b[^>]*>.*?</script>','',html,flags=re.S)
    html=re.sub(r'<link[^>]+rel="stylesheet"[^>]*>','',html)
    page.set_content(html)
    page.add_style_tag(content=(folder/'style.css').read_text())
    page.evaluate('''() => {window.fetch=async(p,o)=>{const x=await window.testBackend(String(p),o||{});return new Response(x.text,{status:x.status,headers:x.headers});};window.testStorage={v:{},getItem(k){return this.v[k]||null},setItem(k,v){this.v[k]=v}};}''')
    for script in scripts:
        js=(folder/pathlib.Path(script).name).read_text()
        page.evaluate('(args)=>{const fn=new Function("location","history","sessionStorage",args.source);fn(args.location,{replaceState(){}},window.testStorage);}',{'source':js,'location':location})
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
    context=browser.new_context(viewport={'width':1440,'height':1000})
    page=context.new_page();page.on('pageerror',lambda e:errors.append(str(e)))
    setup(page,info['base'],source/'cloud/public',{'origin':'https://lensdrop.test','hash':'','pathname':'/admin'})
    page.fill('#key',info['adminKey']);page.click('#connect');page.wait_for_function("document.querySelector('#h5-stations').textContent.includes('Reception')")
    check(page.locator('#h5-panel').is_visible(),'Admin shows H5 pairing and station controls')
    check('Paused' in page.locator('#h5-stations').inner_text(),'Shared Admin shows the station pause status')
    page.fill('#h5-label','Photographer 02 - rehearsal');page.select_option('#h5-kind','phone');page.click('#h5-create');page.wait_for_function("document.querySelector('#h5-url').value.includes('/pair#')")
    invitation=page.input_value('#h5-url');check(page.locator('#h5-qr svg').count()==1,'Staff invitation renders a local QR SVG')
    check('STAFF' in page.locator('#h5-panel').inner_text().upper(),'Staff QR is clearly labelled separately from guest delivery')
    ident,secret=invitation.split('#')[1].split('.');credential=uuid.uuid4().hex+uuid.uuid4().hex;claim=uuid.uuid4().hex+uuid.uuid4().hex
    claimed=request(info['base']+'/v1/pairings/claim',{'id':ident,'secret':secret,'claim_secret':claim,'credential_hash':hashlib.sha256(credential.encode()).hexdigest(),'client_id':str(uuid.uuid4())})
    page.click('#h5-refresh');page.wait_for_function("document.querySelector('#h5-pairings').textContent.includes('claimed')")
    page.once('dialog',lambda d:d.accept(claimed['code']));page.get_by_role('button',name='Approve matching device').click()
    page.wait_for_function("document.querySelector('#h5-pairings').textContent.includes('approved')")
    status=request(info['base']+'/v1/pairings/'+ident+'/status',auth=claim)
    check(status['state']=='approved','UI code confirmation approves the exact cloud identity')
    page.locator('#h5-panel').screenshot(path=str(out/'H5_Cloud_Staff_Pairing.png'))
    page.set_viewport_size({'width':390,'height':844});page.wait_for_timeout(200)
    check(page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'Admin layout fits a 390px viewport')
    page.locator('#h5-panel').screenshot(path=str(out/'H5_Cloud_Staff_Pairing_Mobile.png'))
    station=context.new_page();station.on('pageerror',lambda e:errors.append(str(e)))
    setup(station,info['stationUI'].split('/#')[0],source/'clients/print-station/public',{'origin':'http://127.0.0.1','hash':'#'+info['uiSecret'],'pathname':'/'})
    station.wait_for_function("document.querySelector('#state').textContent.includes('SIMULATION')")
    check(station.locator('#state').inner_text()=='SIMULATION / PAUSED','Station shows simulation and pause explicitly')
    check(station.locator('#cloudAdmin').is_visible(),'Station provides the shared cloud Admin link')
    check(station.input_value('#ip')=='192.168.10.20','Station loads its published local address')
    station.screenshot(path=str(out/'H5_Print_Station_Dashboard.png'),full_page=True)
    station.set_viewport_size({'width':390,'height':844});station.wait_for_timeout(200)
    check(station.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),'Station layout fits a narrow viewport')
    check(not errors,'No script errors in either UI harness')
    browser.close()
(root/'logs/ui-checks.json').write_text(json.dumps({'checks':checks,'passed':len(checks),'browser':'Chromium','harness':'Exact HTML/CSS/JS; injected location/history/sessionStorage and fetch bridge to real local backend. Navigation blocked by environment. Not native Windows or HTTPS browser test.','synthetic_data':True},indent=2))
print('RESULT',len(checks),'UI harness checks passed. No native platform or real browser-network validation claimed.')
