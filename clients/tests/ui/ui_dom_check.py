from pathlib import Path
import json
from playwright.sync_api import sync_playwright
import os
ROOT=Path(os.environ.get('LENSDROP_PUBLIC_DIR', str(Path(__file__).resolve().parents[3]/'cloud/public')));OUT=Path(os.environ.get('LENSDROP_UI_OUTPUT','ui-output'));OUT.mkdir(parents=True,exist_ok=True)
results=[]
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 page=browser.new_page(viewport={'width':1440,'height':1100});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 # No navigation or network forwarding. Local source render with explicitly synthetic responses only.
 html=(ROOT/'index.html').read_text().replace('<link rel="stylesheet" href="/style.css">','<style>'+(ROOT/'style.css').read_text()+'</style>').replace('<script src="/app.js"></script>','')
 html=html.replace('<body>','<body><div style="padding:10px 28px;background:#fff5dc;font:13px system-ui">LOCAL UI RENDER / SYNTHETIC TEST DATA / NOT A HOSTINGER OR PHONE SCREENSHOT</div>')
 page.set_content(html)
 page.evaluate('''() => {window.fetch=async(path,options)=>{const event={id:'event-test',name:'Studio rehearsal / H2',policy:'live',slug:'a'.repeat(32)};const device={id:'device-test',label:'Photographer A / test phone',revoked:false,heartbeat_fresh:true,source_counts_known:true,source_received:286,queued:5,blocked:0,verified_photos:281,live:260,review:21,hidden:0,tests:{verified:1},heartbeat:JSON.stringify({last_received:Date.now()})};const status={devices:[device],counts:[{status:'live',count:260},{status:'review',count:21}],test_count:1,next_before:null,photos:[{id:'photo-1',filename:'IMG_0041.JPG',status:'live',is_test:0},{id:'photo-2',filename:'IMG_0042.JPG',status:'review',is_test:0},{id:'photo-test',filename:'TEST_001.JPG',status:'hidden',is_test:1}]};let data=path==='/v1/events'?[event]:path.endsWith('/status')?status:{ok:true};return {ok:true,json:async()=>data};};}''')
 page.add_script_tag(content=(ROOT/'app.js').read_text())
 page.fill('#key','SYNTHETIC-DEMO-KEY');page.click('#connect');page.wait_for_timeout(300);page.wait_for_function("document.querySelectorAll('#stages strong').length===3",timeout=5000)
 assert page.locator('#stages strong').all_text_contents()==['286','281','260'];results.append('PASS rendered H2 source shows three distinct synthetic counters')
 testrow=page.get_by_text('TEST / never public',exact=True).locator('..');assert testrow.locator('button:disabled').count()==3;results.append('PASS isolated test buttons are disabled in the rendered console')
 page.click('#language');assert page.locator('html').get_attribute('lang')=='zh-Hant';results.append('PASS Traditional Chinese core UI toggle')
 page.screenshot(path=str(OUT/'LensDrop_H2_Admin_UI_Snapshot.png'),full_page=True)
 page.set_viewport_size({'width':390,'height':844});page.screenshot(path=str(OUT/'LensDrop_H2_Admin_Mobile_UI_Snapshot.png'),full_page=True)
 assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+1'),page.evaluate('({scroll:document.documentElement.scrollWidth,width:innerWidth})');results.append('PASS 390px rendered admin has no horizontal page overflow')
 assert not errors,errors;results.append('PASS no JavaScript errors in exercised static/mocked admin controls')
 # Guest DOM test: all API responses are synthetic; live backend navigation remains blocked.
 guest=browser.new_page(viewport={'width':900,'height':700});guest.on('pageerror',lambda e:errors.append(str(e)))
 html=(ROOT/'gallery.html').read_text().replace('<link rel="stylesheet" href="/style.css">','<style>'+(ROOT/'style.css').read_text()+'</style>').replace('<script src="/gallery.js"></script>','')
 guest.set_content(html)
 guest.evaluate('''() => {window.fixture={n:20,withdraw:[]};window.fetch=async(path,opts)=>{const photos=Array.from({length:fixture.n},(_,i)=>({id:'photo-'+i,filename:'Fixture '+i+'.jpg'})).reverse();const body=opts?.body?JSON.parse(opts.body):null;return {ok:true,json:async()=>path.endsWith('/visible')?{visible:body.ids.filter(x=>!fixture.withdraw.includes(x))}:{name:'Synthetic gallery',photos,total:fixture.n,next_before:null}};};}''')
 guest.add_script_tag(content=(ROOT/'gallery.js').read_text());guest.wait_for_function("document.querySelectorAll('#grid article').length===20")
 guest.evaluate('window.scrollTo(0,600)');before=guest.evaluate('scrollY');guest.evaluate('fixture.n=21;refresh()');assert guest.locator('#newPhotos').is_visible();assert guest.locator('#grid article').count()==20
 assert abs(guest.evaluate('scrollY')-before)<2,(before,guest.evaluate('scrollY'));results.append('PASS mocked guest new-photo refresh preserves current cards and scroll position')
 guest.click('#newPhotos');guest.wait_for_function("document.querySelectorAll('#grid article').length===21");results.append('PASS explicit mocked guest action renders new cards')
 guest.evaluate("fixture.withdraw=['photo-20'];refresh()");assert guest.locator('.withdrawn').count()==1;results.append('PASS mocked visibility update withdraws a photo')
 assert not errors,errors
 browser.close()
(OUT/'ui-dom-h2-checks.log').write_text('\n'.join(results)+'\nLIMIT: set_content with mocked fetch; NOT live browser/server, HTTPS, Hostinger, Android or camera validation.\n')
print('\n'.join(results))
