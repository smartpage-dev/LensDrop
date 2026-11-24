#!/usr/bin/env python3
"""Source contract only; NOT Android SDK compilation or camera/printer validation."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/app/lensdrop/transfer/SlideshowActivity.java').read_text()
p=(r/'app/src/main/java/app/lensdrop/transfer/SlideshowTicketProtocol.java').read_text()
q=(r/'app/src/main/java/app/lensdrop/transfer/QueueDb.java').read_text()
m=(r/'app/src/main/AndroidManifest.xml').read_text()
main=(r/'app/src/main/java/app/lensdrop/transfer/MainActivity.java').read_text()
checks={
 'support entry':'Show / open / print event slideshow QR' in main,
 'activity registered':'android:name=".SlideshowActivity"' in m,
 'own paired event API':'/v1/device/slideshow' in s,
 'refresh when resumed':'foreground=true;load();' in s,
 'no hidden Admin key':'LENSDROP_ADMIN_KEY' not in s,
 'separate public projector QR':'/s/[a-f0-9]{32}' in p and 'NOT A GUEST ALBUM' in p,
 'no photo print job':'PrintQueue' not in s and 'print-jobs' not in s,
 'link recheck before prompt':'Rechecking the slideshow link before printing' in s,
 'link recheck before Bluetooth':'Revalidate immediately before the immutable ticket snapshot' in s,
 'no auto print':'Print ONE event slideshow ticket' in s and 'autoPrint' not in s,
 'no automatic ticket retry':'No auto retry' in s and 'op.send(bytes)' in s,
 'shared Bluetooth mutual exclusion':'TicketTransport.BUSY.compareAndSet(false,true)' in s,
 'persistent ticket attempts':'CREATE TABLE IF NOT EXISTS slideshow_tickets' in q,
 'immutable ticket snapshots':'immutable_slideshow_ticket' in q,
 'crash recovery no replay':"state='outcome_unknown'" in s and "state='cancelled_before_send'" in s,
 'history observation':'Record actual paper observation' in s,
 'no live QR if disabled':'!r.optBoolean("enabled")' in s,
}
for k,v in checks.items():
 if not v:raise SystemExit('FAIL '+k)
 print('PASS '+k)
print('PASS',len(checks),'slideshow UI source contracts (not native Android runtime)')
