from pathlib import Path
import re,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
J=ROOT/'app/src/main/java/app/lensdrop/transfer'
read=lambda n:(J/n).read_text()
g=read('GalleryActivity.java'); l=read('GalleryLedger.java');d=read('DeliveryActivity.java');u=read('LuceUi.java');p=read('PrintActivity.java');m=read('MainActivity.java');q=read('QueueDb.java')
checks={
 'partial row preserves three equal columns': 'rows.length()%3!=0' in g and '3-rows.length()%3' in g and 'row.addView(blank' in g,
 'cloud star sync uses bounded fetch': '/v1/device/photo-controls' in g and 'cloudFlags' in g and 'cloudRefreshAt' in g,
 'cloud visibility requires confirmation': 'gallery_visible' in g and 'projector_visible' in g and 'Cloud update not saved' in g,
 '3-column layout': 'i%3==0' in g and 'LinearLayout.LayoutParams(0,-2,1)' in g,
 'five-second foreground refresh': 'ui.postDelayed(this,5000)' in g and 'onPause(){active=false' in g,
 'refresh does not rebuild unchanged grid': 'next.equals(lastSignature)' in g,
 'bounded page and memory cache': 'Math.min(90,limit)' in l and 'LruCache<String,Bitmap>(12*1024*1024)' in g,
 'off-UI thumbnail loading': 'Executors.newFixedThreadPool(2)' in g and 'getContentResolver().loadThumbnail' in g,
 'archive thumb fallback': 'api.thumbnail' in g and 'getCanonicalFile().getParentFile().equals(parent)' in g,
 'photo enlargement and details': 'loadThumb(item,image,scope,generation,900)' in g and 'Photo information' in g,
 'star and reversible hide': 'gallery_marks' in l and 'Restore hidden tile' in g and 'Hide from this gallery (NO DELETE)' in g,
 'no hard delete': 'DELETE FROM jobs' not in g and 'DELETE FROM jobs' not in l and 'DELETE FROM guest_groups' not in g,
 'local gallery event isolation': 'j.scope=? AND j.is_test=0' in l and 'scope=? AND id=? AND is_test=0' in l,
 'gallery entry': 'Open photo gallery' in m and 'GalleryActivity.class' in m,
 'photo print reuses existing confirmation': 'selectProfile(photo,route.getSelectedItemPosition())' in p and 'gallery_job_id' in p,
 'QR ticket never auto printed by gallery': 'TicketActivity.class' not in g and 'gallery_ticket_hint' in g,
 'fixed footer': 'LuceUi.dockedScreen' in d and 'dockButton(row1' in d and 'dockButton(row2' in d,
 'QR preview above toolbar': 'display.addView(qr' in d and 'qrScroll.smoothScrollTo(0,0)' in d,
 'one-tap online group preparation': 'DeliveryCoordinator.prepare(q,scope,api)' in d and 'w.nextGroup(scope)' in d,
 'group display never changes receiver': 'w.show(config.get("scope"),target.getString("id"))' in d and 'Receiving group is unchanged' in d,
 'mode remains deliberate': 'Group QR / multiple photographs' in d and 'Single selected photo QR' in d,
 'safe guest confirmation retained': 'x.select(config.get("scope"),photo.getString("id"))' in d,
 'schema7 additive metadata': 'if(oldVersion<7)createH58(db)' in q and 'CREATE TABLE IF NOT EXISTS gallery_marks' in q,
 'no new external component': '<activity android:name=".GalleryActivity" android:exported="false"/>' in (ROOT/'app/src/main/AndroidManifest.xml').read_text(),
 'no silent automatic print': 'Queue Print' in p and 'Print ONE ticket' in read('TicketActivity.java'),
 'safe resume': 'onResume(){super.onResume();active=true' in g and 'onPause(){active=false' in g,
}
for name,ok in checks.items():
 if not ok:raise AssertionError(name)
 print('PASS',name)
print('PASSED',len(checks),'gallery/QR UI source contracts (not Android runtime or lint)')
