package app.lensdrop.transfer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Staff-only, explicit guest-ticket printing. No print-on-arrival or Next Group hooks.
 * Photo transfer/Windows printing services are never stopped by this activity. */
public final class TicketActivity extends Activity {
    private Config config;
    private TextView status,details;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private volatile TicketTransport operation;
    private volatile boolean foreground;
    private String exportScope;
    private interface Action{void run()throws Exception;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);config=new Config(this);
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int p=(int)(16*getResources().getDisplayMetrics().density);body.setPadding(p,p,p,p);scroll.addView(body);setContentView(scroll);
        body.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(p,p+i.getSystemWindowInsetTop(),p,p+i.getSystemWindowInsetBottom());return i;});
        text(body,"LensDrop / QR ticket pilot",24);
        text(body,"58B3 secure Bluetooth SPP + native QR only. One dedicated paired printer per photographer. Manual foreground send; no automatic retry. Guest URL printing does not authorize or submit a photo print.",15);
        details=text(body,"Loading saved ticket settings...",16);status=text(body,"",15);
        button(body,"Grant nearby-device permissions",this::grant);
        button(body,"Choose / confirm MY paired QR printer",this::choosePrinter);
        button(body,"Forget saved QR printer (no deletion of tickets)",()->{if(TicketTransport.BUSY.get())throw new IllegalStateException("Wait for the current operation");config.prefs.edit().remove("ticket_printer").remove("ticket_printer_name").commit();refresh();});
        button(body,"Test secure SPP connection (NO PRINT)",this::testConnection);
        button(body,"Preview / print the SHOWN guest QR",()->preview(null));
        button(body,"Ticket history / observations / explicit reprint",()->history(0));
        button(body,"STOP this ticket connection",()->{TicketTransport op=operation;if(op!=null)op.cancel();say("Stop requested. Already transmitted bytes cannot be recalled. Check paper; no automatic resend.");});
        button(body,"Export redacted ticket counts",this::export);
        button(body,"Back to Guest QR / groups",()->{startActivity(new Intent(this,DeliveryActivity.class));finish();});
        text(body,"First validate an ACTUAL full-length LensDrop guest link with another phone. The supplied evidence used a 32-byte test URL; it did not certify long links, sleep/reconnect or camera Wi-Fi coexistence. Keep ticket output private. No Chinese/logo/raster/cutter support in this pilot.",14);
        work(()->{if(TicketTransport.BUSY.compareAndSet(false,true)){try(QueueDb q=new QueueDb(this)){new TicketLedger(q).recover();}finally{TicketTransport.BUSY.set(false);}}refreshNow();});
    }
    private TextView text(LinearLayout parent,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,10,0,10);parent.addView(t);return t;}
    private void button(LinearLayout parent,String value,Action action){Button b=new Button(this);b.setText(value);b.setOnClickListener(v->{try{action.run();}catch(Exception e){say(safeMessage(e));}});parent.addView(b);}
    private void say(String value){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(value);});}
    private static String safeMessage(Exception e){return e instanceof IllegalStateException||e instanceof IllegalArgumentException?e.getMessage():"Ticket action not completed. Queue retained; check Bluetooth, permission and paper. No automatic retry.";}
    private void work(Action action){if(io.isShutdown())return;io.execute(()->{try{action.run();}catch(Exception e){say(safeMessage(e));}});}
    private boolean allowed(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED);}
    private void grant(){if(Build.VERSION.SDK_INT>=31&&!allowed())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},63);else say("Permission ready. Choose your system-paired printer; no discovery is started.");}
    private BluetoothAdapter adapter(){BluetoothManager m=getSystemService(BluetoothManager.class);return m==null?null:m.getAdapter();}
    private void requireReady(){if(!allowed())throw new IllegalStateException("Grant nearby-device permissions, then try again");TicketProtocol.printer(config.get("ticket_printer"));if(TicketTransport.BUSY.get())throw new IllegalStateException("One ticket operation is already active");}
    @SuppressLint("MissingPermission") private void choosePrinter(){
        if(!allowed()){grant();return;}if(TicketTransport.BUSY.get())throw new IllegalStateException("Stop and wait before changing printer");
        BluetoothAdapter a=adapter();if(a==null||!a.isEnabled())throw new IllegalStateException("Enable Bluetooth and pair the 58B3 in Android Settings first");
        List<BluetoothDevice> devices=new ArrayList<>(a.getBondedDevices());devices.sort(Comparator.comparing(BluetoothDevice::getAddress));
        if(devices.isEmpty())throw new IllegalStateException("No paired device. Pair the printer in Android Settings first.");
        String[] names=new String[devices.size()];for(int i=0;i<names.length;i++){BluetoothDevice d=devices.get(i);names[i]=(d.getName()==null?"Unnamed device":d.getName())+" / "+d.getAddress();}
        new AlertDialog.Builder(this).setTitle("Paired devices only - select YOUR printer").setItems(names,(dialog,index)->{
            BluetoothDevice d=devices.get(index);String addr=d.getAddress(),name=d.getName()==null?"QR printer":d.getName();
            new AlertDialog.Builder(this).setTitle("Confirm dedicated ticket printer").setMessage(name+"\n"+addr+"\nNo automatic switching to a similarly named device. Test paper before use.").setPositiveButton("Save this printer",(x,n)->{if(TicketTransport.BUSY.get()){say("Operation active; selection not changed.");return;}config.prefs.edit().putString("ticket_printer",addr).putString("ticket_printer_name",name).commit();refresh();}).setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Cancel",null).show();
    }
    private void refresh(){work(this::refreshNow);}
    private void refreshNow()throws Exception{
        String scope=config.get("scope"),printer=config.get("ticket_printer_name");String body="Printer: "+(printer.isEmpty()?"not selected":printer)+"\nNo background ticket sending. Leave this screen to stop its connection.";
        if(!scope.isEmpty())try(QueueDb q=new QueueDb(this)){WatchLedger w=new WatchLedger(q);JSONObject shown=w.group(scope,w.shown(scope)),active=w.group(scope,w.active(scope));body+="\nShowing QR: "+(shown==null?"none":shown.getString("label"))+"\nReceiving: "+(w.mode(scope).equals("single")?"single-photo candidates":active==null?"none":active.getString("label"));}
        final String message=body;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())details.setText(message);});
    }
    private void testConnection(){requireReady();final String addr=config.get("ticket_printer");if(!TicketTransport.BUSY.compareAndSet(false,true))return;TicketTransport op=new TicketTransport();operation=op;say("Connecting to your selected printer; no print command will be sent.");
        work(()->{try{if(!foreground)op.cancel();op.connect(adapter(),addr);say("Secure SPP connection established. This is not proof of paper output. Connection now closes.");}catch(Exception e){say("Connection not established. Check Bluetooth pairing / permissions / printer power. Nothing was sent.");}finally{op.close();if(operation==op)operation=null;TicketTransport.BUSY.set(false);}});
    }
    private void preview(JSONObject prior){
        requireReady();final String scope=config.get("scope"),printer=config.get("ticket_printer"),printerName=config.get("ticket_printer_name");
        if(scope.isEmpty())throw new IllegalStateException("Pair the event and prepare a guest QR first");
        work(()->{try(QueueDb q=new QueueDb(this)){
            WatchLedger w=new WatchLedger(q);String delivery=prior==null?w.shown(scope):prior.getString("delivery_id");JSONObject group=w.group(scope,delivery);
            if(group==null||!"open".equals(group.optString("state")))throw new IllegalStateException("Show a prepared open guest QR first; cancelled/reserved groups cannot print");
            final String url=TicketProtocol.guestUrl(scope.split("\\|",-1)[0],group.getString("guest_path"));
            if(prior!=null&&!url.equals(prior.getString("guest_url")))throw new IllegalStateException("Saved link changed. Reprint blocked.");
            final String label=group.getString("label"),id=UUID.randomUUID().toString(),parent=prior==null?null:prior.getString("id");
            TicketProtocol.packet(scope,id,delivery,label,url);
            String note=(prior==null?"PRINT SHOWN QR - NOT AUTOMATICALLY THE RECEIVING GROUP":"EXPLICIT REPRINT - SAME ORIGINAL GUEST LINK")+"\n\nPrinter: "+printerName+"\n"+label+"\nGuest URL (private):\n"+url+"\n\n1 ticket. Native Model 2 / ECC M / 4-dot modules. ASCII only. No photo image. Photos may still be uploading or pending approval.\n\nLong-link scannability must be checked on paper before handing it to a guest.\n\nIf a previous ticket may already have printed, inspect it before confirming. This is NOT a 4x6 photo request.";
            runOnUiThread(()->{if(!foreground||isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle(parent==null?"Confirm ONE QR ticket":"Confirm intentional ticket reprint").setMessage(note).setPositiveButton("Print ONE ticket",(d,n)->send(scope,id,delivery,label,url,printer,parent)).setNegativeButton("Cancel",null).show();});
        }});
    }
    private void send(String scope,String id,String delivery,String label,String url,String printer,String parent){
        if(!foreground||!allowed()){say("Ticket not sent: foreground screen and Bluetooth permission required.");return;}
        if(!config.get("scope").equals(scope)||!config.get("ticket_printer").equals(printer)){say("Event/printer changed. Reopen preview.");return;}
        if(!TicketTransport.BUSY.compareAndSet(false,true)){say("Another ticket operation is active; nothing added.");return;}
        TicketTransport op=new TicketTransport();operation=op;say("Saving immutable ticket, then connecting. Do not press again. Check paper after sending.");
        work(()->{
            boolean claimed=false;
            try(QueueDb q=new QueueDb(this)){
                TicketLedger ledger=new TicketLedger(q);
                if(!foreground||!config.get("scope").equals(scope)||!config.get("ticket_printer").equals(printer))throw new IllegalStateException("Event, printer or foreground changed before dispatch");
                ledger.create(scope,id,delivery,label,url,printer,parent);claimed=ledger.claim(scope,id);
                if(!claimed){say("This saved ticket has already been attempted. No automatic resend.");return;}
                byte[] packet=TicketProtocol.packet(scope,id,delivery,label,url);
                if(!foreground)op.cancel();op.connect(adapter(),printer);op.send(packet);
                ledger.end(scope,id,"sent_unconfirmed",null);say("Ticket "+id.substring(0,8)+" sent. Physical outcome UNCONFIRMED. Inspect paper, scan QR with another phone and record its exact-link result in Ticket history.");
            }catch(Exception e){
                if(claimed)try(QueueDb q=new QueueDb(this)){new TicketLedger(q).end(scope,id,op.writeAttempted()?"outcome_unknown":"failed_before_send",op.writeAttempted()?"connection_interrupted":"connect_or_permission");}catch(Exception ignored){}
                say(op.writeAttempted()?"Outcome UNKNOWN: some/all bytes may have reached the printer. Check paper before any explicit reprint. Nothing will retry automatically.":"Not sent to printer. Check connection/permission or changed selection. Open history before making a new explicit attempt.");
            }finally{op.close();if(operation==op)operation=null;TicketTransport.BUSY.set(false);refresh();}
        });
    }
    private void history(int offset){
        final String scope=config.get("scope");if(scope.isEmpty()){say("Pair an event first.");return;}
        work(()->{try(QueueDb q=new QueueDb(this)){
            JSONArray rows=new TicketLedger(q).history(scope,offset);String[] labels=new String[rows.length()+1];
            for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);labels[i]=r.getString("label")+" / "+r.getString("id").substring(0,8)+"\n"+r.getString("state")+" / "+r.getString("observation");}labels[rows.length()]="Older tickets...";
            runOnUiThread(()->{if(!foreground||isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("Tickets: current event/device only").setItems(labels,(d,n)->{if(n==rows.length()){history(offset+50);return;}try{JSONObject r=rows.getJSONObject(n);String[] actions={"Record physical observation","Reprint SAME guest link (new explicit attempt)"};new AlertDialog.Builder(this).setTitle(r.getString("label")).setItems(actions,(x,a)->{if(!config.get("scope").equals(scope)){say("Event changed; reopen history.");return;}try{if(a==0)observe(scope,r);else {if("sending".equals(r.getString("state")))throw new IllegalStateException("Wait until sending ends");preview(r);}}catch(Exception e){say(safeMessage(e));}}).setNegativeButton("Close",null).show();}catch(Exception e){say("Ticket history unavailable; no data was deleted.");}}).setNegativeButton("Close",null).show();});
        }});
    }
    private void observe(String scope,JSONObject row)throws Exception{
        final String id=row.getString("id");String[] labels={"Not checked","Paper output seen (not scanned)","QR scanned AND complete URL matches preview","No output seen - check printer before reprint","Garbled / truncated","QR unreadable","QR scanned but WRONG content"};String[] values={"not_checked","printed_seen","qr_matches","no_output","garbled","qr_unreadable","qr_mismatch"};
        new AlertDialog.Builder(this).setTitle("Observation for "+id.substring(0,8)).setItems(labels,(d,n)->work(()->{if(TicketTransport.BUSY.get())throw new IllegalStateException("Wait for sending to finish");try(QueueDb q=new QueueDb(this)){new TicketLedger(q).observe(scope,id,values[n]);}say("Operator observation saved. It is not printer telemetry and does not authorize photo printing.");})).setNegativeButton("Cancel",null).show();
    }
    private void export(){if(TicketTransport.BUSY.get())throw new IllegalStateException("Finish the ticket operation before exporting");exportScope=config.get("scope");if(exportScope.isEmpty())throw new IllegalStateException("Pair an event first");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"LensDrop-ticket-counts.json");startActivityForResult(i,64);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=64||result!=RESULT_OK||data==null||data.getData()==null||exportScope==null)return;final String scope=exportScope;final android.net.Uri dest=data.getData();work(()->{try(QueueDb q=new QueueDb(this);OutputStream out=getContentResolver().openOutputStream(dest)){if(out==null)throw new IllegalStateException("Export destination unavailable");out.write(new TicketLedger(q).diagnostics(scope).toString(2).getBytes(StandardCharsets.UTF_8));say("Redacted count snapshot exported. No guest URL, token, address or photograph included.");}});}
    @Override protected void onStart(){super.onStart();foreground=true;}
    @Override protected void onResume(){super.onResume();foreground=true;}
    @Override protected void onPause(){foreground=false;TicketTransport op=operation;if(op!=null)op.cancel();super.onPause();}
    @Override protected void onStop(){foreground=false;TicketTransport op=operation;if(op!=null)op.cancel();super.onStop();}
    @Override protected void onDestroy(){TicketTransport op=operation;if(op!=null)op.cancel();io.shutdown();super.onDestroy();}
}
