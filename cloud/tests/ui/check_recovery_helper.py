from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[2]
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 page=browser.new_page(viewport={'width':1080,'height':1000})
 errors=[];requests=[]
 page.on('pageerror',lambda error:errors.append(str(error)))
 page.on('request',lambda req:requests.append(req.url))
 page.set_content((root/'tools/Hostinger_Setup_Helper.html').read_text())
 page.click('#adminGenerate')
 assert page.locator('#adminValue').evaluate('(e)=>/^[a-f0-9]{64}$/.test(e.value)')
 assert page.locator('#values').input_value()==''
 assert page.locator('#path').input_value()==''
 page.click('#adminCopy')
 assert page.locator('#adminMessage').inner_text()
 # Do not retain the temporary generated test secret in any screenshot/file.
 page.evaluate("document.querySelector('#adminValue').value='';document.querySelector('#adminMessage').textContent=''")
 page.set_viewport_size({'width':390,'height':844})
 assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1')
 assert errors==[],errors
 assert requests==[],requests
 browser.close()
print('PASS offline helper JavaScript in Chromium (set_content)')
print('PASS administrator-only generation yields 64 hex characters')
print('PASS storage fields remain empty and unchanged')
print('PASS copy action provides a result or manual-copy instruction')
print('PASS 390px viewport has no horizontal overflow')
print('PASS no page errors or network requests')
print('LIMIT: helper-only Chromium check using set_content, not native file navigation or live Hostinger')
