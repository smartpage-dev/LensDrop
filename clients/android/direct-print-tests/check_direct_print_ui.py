from pathlib import Path
import re
base=Path(__file__).resolve().parents[1]/'app/src/main/java/app/lensdrop/transfer'
s=(base/'SingleDeliveryActivity.java').read_text()
p=(base/'SinglePhotoPrint.java').read_text()
t=(base/'TicketLedger.java').read_text()
q=(base/'PrintQueue.java').read_text()
begin=p.index('JSONObject spec=new JSONObject()')
end=p.index(';',begin)
spec_fields=set(re.findall(r'\.put\("([a-z0-9_]+)"',p[begin:end]))
expected_spec={'id','event_id','device_id','station_id','sha256','bytes','profile_id','profile_revision','copies'}
checks={
 'Photo spec is EXACT 9 allowed fields': spec_fields==expected_spec,
 'Full QR button removed': 'button(r2,"Full QR"' not in s and 'void fullQr(' not in s,
 'Auto/pause toggle in Single dock': 'button(r2,autoTicket?"Pause Print QR":"Auto Print QR"' in s,
 'Print QR invokes local dispatch': 'button(r2,"Print QR",this::printQrHere)' in s and 'op.send(packet)' in s,
 'Print photo invokes local quick queue': 'button(r2,"Print Photo",this::printPhotoHere)' in s and 'SinglePhotoPrint.enqueue(' in s,
 'No ticket/photo navigation for normal buttons': 'startActivity(new Intent(this,TicketActivity.class))' not in s[s.index('LinearLayout r2='):s.index('private Button button(')] and 'startActivity(new Intent(this,PrintActivity.class))' not in s[s.index('LinearLayout r2='):s.index('private Button button(')],
 'Auto opt in and scoped': 'single_auto_ticket_scope' in s and 'single_auto_ticket_enabled",false' in s,
 'Auto cancelled upon pause': 'TicketTransport op=ticketOperation;if(op!=null)op.cancel()' in s,
 'Auto one attempt per guest and photo': 'latestForGuestUrl(scope,url)' in s and 'autoTicketId(scope,e.id)' in s,
 'Manual repeat requires explicit confirmation': 'Print this QR again?' in s and 'Print another ticket' in s,
 'Photo repeat requires explicit confirmation': 'Photo already requested' in s and 'Queue an additional copy' in s,
 'Photo original file validated and durable': 'JpegStructure.validate(target)' in p and 'getFD().sync()' in p,
 'Station authorization and routing retained': 'print_setup' in p and '/v1/device/print-config' in p and '/v1/info' in p,
 'Strict Windows photo print protocol preserved': '.put("source_job_id",' not in p and '.put("copies",1)' in p,
 'Private archive service started after photo queue': 'new Intent(this,EventService.class)' in s and 'new Intent(this,PrintService.class)' in s,
 'Photo print spool unchanged': 'ArchiveStaging.register(context,scope,target,spec,null)' in p and 'q.add(scope,spec,target.getAbsolutePath(),mode,archiveId)' in p,
 'Ticket ledger tracks guest URL': 'latestForGuestUrl' in t,
 'Photo queue idempotent lookup': 'JSONObject get(String scope,String id)' in q,
 'No automatic print when paused': '(automatic&&(!autoTicket||hold))' in s,
 'Hold pauses auto QR': 'Auto QR paused while a guest scans the held QR.' in s and 'if(!autoTicket||!foreground||hold)return;' in s,
 'Default one sheet only': '.put("copies",1)' in p,
}
for name,ok in checks.items():
    print(('PASS' if ok else 'FAIL'),name)
assert all(checks.values()),'Direct print source contract failed'
print(f'Direct print source contracts: {len(checks)}/{len(checks)} PASS (not native Android build)')
