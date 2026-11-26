package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import android.database.Cursor;
import android.view.View;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Independent Single QR display. Uploaded receipts are reconciled by UploadEngine.
 * Viewing history never changes a group receiver. */
public final class SingleDeliveryActivity extends Activity {
 private Config config;private TextView summary,notice;private WebView qr;private ImageView preview;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ArrayList<Entry> items=new ArrayList<>();private int selected=0;private boolean hold=false;private String shown="";
 private final ExecutorService ticketWorker=Executors.newSingleThreadExecutor();
 private final ExecutorService photoWorker=Executors.newSingleThreadExecutor();
 private final AtomicBoolean photoBusy=new AtomicBoolean(false);
 private final Set<String> autoScheduled=new HashSet<>();
 private volatile TicketTransport ticketOperation;private volatile boolean foreground;
 private volatile boolean autoTicket;private long armedAfter;private Button autoButton;
 private String printMessage="";
 private final Runnable refresh=new Runnable(){public void run(){load();handler.postDelayed(this,5000);}};
 private static final class Entry {
  final String id,singleId,photo,label,path,name,uri;final boolean registered,needsAction;final long created;
  Entry(String id,String singleId,String photo,String label,String path,String name,String uri,boolean registered,boolean needsAction,long created){
   this.id=id;this.singleId=singleId;this.photo=photo;this.label=label;this.path=path;this.name=name;this.uri=uri;this.registered=registered;this.needsAction=needsAction;this.created=created;
  }
  String delivery(){return singleId==null||singleId.isEmpty()?id:singleId;}
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);config=new Config(this);LinearLayout[] dock=new LinearLayout[1];LinearLayout page=LuceUi.dockedScreen(this,"Single Photo QR","Stable private QR on arrival; photo becomes visible after Cloud upload and release.",dock);
  LinearLayout box=LuceUi.card(this,page,"Latest Single Photo QR","New photos get a durable local QR immediately. Guest page waits for upload; Cloud registration runs in the background.");
  summary=LuceUi.label(this,box,"Waiting for verified photos",17,true);
  qr=new WebView(this);qr.getSettings().setJavaScriptEnabled(true);qr.getSettings().setBlockNetworkLoads(true);qr.getSettings().setAllowFileAccess(false);qr.getSettings().setAllowContentAccess(false);qr.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,String url){return true;}});
  int w=getResources().getDisplayMetrics().widthPixels;int dp=(int)(w/getResources().getDisplayMetrics().density);int heightDp=Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);int size=Math.min(Math.max(160,heightDp-315),Math.min(350,Math.max(160,dp-64)));box.addView(qr,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,size)));
  notice=LuceUi.notice(this,page,"Auto latest display ON. Auto ticket printing is OFF by default.");
  autoTicket=config.prefs.getBoolean("single_auto_ticket_enabled",false)&&config.get("scope").equals(config.get("single_auto_ticket_scope"));
  ticketWorker.execute(()->{if(TicketTransport.BUSY.compareAndSet(false,true)){
   try(QueueDb q=new QueueDb(this)){new TicketLedger(q).recover();}
   catch(Exception ignored){}finally{TicketTransport.BUSY.set(false);}
  }});
  LinearLayout thumb=LuceUi.card(this,page,"Photo preview","Below the fixed QR controls. Tap the image for a larger preview.");preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);thumb.addView(preview,new LinearLayout.LayoutParams(-1,-2));preview.setOnClickListener(v->largePhoto());
  LinearLayout r1=LuceUi.column(this);r1.setOrientation(LinearLayout.HORIZONTAL);dock[0].addView(r1);button(r1,"Previous",()->{hold=true;if(autoTicket)pauseAuto("Auto QR paused while showing an earlier guest QR.");selected=Math.min(selected+1,Math.max(0,items.size()-1));show();});button(r1,"Latest",()->{hold=false;selected=0;show();});button(r1,"Hold / Resume",()->{hold=!hold;if(hold&&autoTicket)pauseAuto("Auto QR paused while a guest scans the held QR.");if(!hold)selected=0;show();});
  LinearLayout r2=LuceUi.column(this);r2.setOrientation(LinearLayout.HORIZONTAL);dock[0].addView(r2);
  autoButton=button(r2,autoTicket?"Pause Print QR":"Auto Print QR",this::toggleAuto);
  button(r2,"Print QR",this::printQrHere);
  button(r2,"Print Photo",this::printPhotoHere);
 }
 private Button button(LinearLayout row,String title,Runnable task){LinearLayout slot=LuceUi.column(this);row.addView(slot,new LinearLayout.LayoutParams(0,-2,1));Button b=LuceUi.action(this,slot,title,false,task);b.setTextSize(12);b.setMinHeight(LuceUi.dp(this,46));return b;}
 private void load(){if(worker.isShutdown()||config.get("scope").isEmpty())return;worker.execute(()->{
  ArrayList<Entry> rows=new ArrayList<>();long pending=0;
  try(QueueDb q=new QueueDb(this);Cursor c=q.getReadableDatabase().rawQuery("SELECT id,single_id,photo_id,COALESCE(single_label,'Preparing QR'),COALESCE(single_path,'/p/'||early_token),name,uri,early_registered,early_next,created FROM jobs WHERE scope=? AND is_test=0 AND (single_id IS NOT NULL OR early_token IS NOT NULL) AND state NOT IN ('skipped','retired_local') ORDER BY created DESC,id DESC LIMIT 100",new String[]{config.get("scope")})){
   while(c.moveToNext())rows.add(new Entry(c.getString(0),c.getString(1),c.getString(2),
    "Preparing QR".equals(c.getString(3))?TicketProtocol.earlySingleLabel(c.getString(0)):c.getString(3),
    c.getString(4),c.getString(5),c.getString(6),c.getInt(7)==1||c.getString(1)!=null,c.getInt(7)==0&&c.getString(1)==null&&c.getLong(8)==Long.MAX_VALUE,c.getLong(9)));
   try(Cursor p=q.getReadableDatabase().rawQuery("SELECT count(*) FROM jobs WHERE scope=? AND is_test=0 AND early_token IS NOT NULL AND early_registered=0",new String[]{config.get("scope")})){if(p.moveToFirst())pending=p.getLong(0);}
  }catch(Exception e){return;}
  long wait=pending;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;String previous=selected<items.size()?items.get(selected).id:null;items.clear();items.addAll(rows);if(hold&&previous!=null){for(int i=0;i<items.size();i++)if(items.get(i).id.equals(previous)){selected=i;break;}}else selected=0;notice.setText((hold?"HOLD current QR":"Auto latest ON")+" · "+wait+" pending Cloud registration(s) · Ticket auto "+(autoTicket?"ON":"PAUSED")+(printMessage.isEmpty()?"":"\n"+printMessage));show();scheduleAuto(rows);});
 });}
 private String url(){return items.isEmpty()?"":config.get("base")+items.get(Math.min(selected,items.size()-1)).path;}
 private String html(String link){try(java.io.InputStream in=getAssets().open("qr.js")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)out.write(block,0,n);byte[] js=out.toByteArray();return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;display:grid;place-items:center;min-height:100vh;background:white}svg{width:min(90vw,90vh);height:auto}</style><div id='q'></div><script>"+new String(js,StandardCharsets.UTF_8)+"</script><script>document.getElementById('q').innerHTML=LensDropQR.toSvg("+JSONObject.quote(link)+");</script>";}catch(Exception e){return "<p>QR unavailable. Check installation.</p>";}}
 private void show(){if(items.isEmpty()){summary.setText("No Single QR yet. New eligible photos will generate a local QR without waiting for upload.");return;}Entry e=items.get(Math.min(selected,items.size()-1));summary.setText(e.label+" · "+e.name+" · "+(e.registered?"Cloud registered":e.needsAction?"QR registration blocked - use Retry blocked uploads / links":"Local QR / Preparing")+" · "+(selected+1)+" / "+items.size());String link=url();if(!link.equals(shown)){shown=link;qr.loadDataWithBaseURL("https://lensdrop.invalid/",html(link),"text/html","UTF-8",null);loadPhoto(e);}}
 private void loadPhoto(Entry e){worker.execute(()->{
  try{
   Bitmap b;
   if(e.photo!=null&&!e.photo.isEmpty()){
    try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){byte[] bytes=api.thumbnail(e.photo);BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);}
   }else{b=getContentResolver().loadThumbnail(android.net.Uri.parse(e.uri),new android.util.Size(480,480),null);}
   runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&shown.equals(config.get("base")+e.path))preview.setImageBitmap(b);else if(b!=null)b.recycle();});
  }catch(Exception ignored){}
 });}
 private Entry current(){return items.isEmpty()?null:items.get(Math.min(selected,items.size()-1));}
 private String guestUrl(Entry e){return TicketProtocol.guestUrl(config.get("base"),e.path);}
 private void status(String text){printMessage=text;runOnUiThread(()->{
  if(!isFinishing()&&!isDestroyed())notice.setText((hold?"HOLD current QR":"Auto latest ON")+" · Ticket auto "+(autoTicket?"ON":"PAUSED")+"\n"+text);
 });}
 private boolean ticketPermission(){return Build.VERSION.SDK_INT<31||
   (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&
    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED);}
 private boolean ticketReady(){
  if(config.get("scope").isEmpty()){status("Pair an event first.");return false;}
  try{TicketProtocol.printer(config.get("ticket_printer"));}catch(Exception ex){
   new AlertDialog.Builder(this).setTitle("QR printer not selected")
    .setMessage("Select and test your dedicated 58B3 printer once in QR Ticket setup. No ticket was sent.")
    .setPositiveButton("Open QR printer setup",(d,n)->startActivity(new Intent(this,TicketActivity.class)))
    .setNegativeButton("Stay here",null).show();return false;
  }
  if(!ticketPermission()){
   if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},65);
   status("Grant nearby-device permission; then press Print QR again.");return false;
  }
  BluetoothManager manager=getSystemService(BluetoothManager.class);
  BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
  if(adapter==null||!adapter.isEnabled()){status("Enable Bluetooth and connect the dedicated ticket printer first.");return false;}
  return true;
 }
 private void toggleAuto(){
  if(autoTicket){pauseAuto("Auto QR printing paused. The on-screen QR still updates.");return;}
  if(hold){status("Tap Latest or Resume first. Auto QR must never print while an old QR is held.");return;}
  if(!ticketReady())return;
  autoTicket=true;armedAfter=System.currentTimeMillis();autoScheduled.clear();
  config.prefs.edit().putString("single_auto_ticket_scope",config.get("scope")).putBoolean("single_auto_ticket_enabled",true).commit();
  autoButton.setText("Pause Print QR");status("Auto QR armed for NEW photos registered with Cloud while this screen is open. No old QR tickets will print.");
 }
 private void pauseAuto(String message){
  autoTicket=false;armedAfter=0;
  config.prefs.edit().putBoolean("single_auto_ticket_enabled",false).commit();
  if(autoButton!=null)autoButton.setText("Auto Print QR");status(message);
 }
 private void scheduleAuto(ArrayList<Entry> rows){
  if(!autoTicket||!foreground||hold)return;
  for(int i=rows.size()-1;i>=0;i--){Entry e=rows.get(i);
   if(!SingleQuickPrintRules.eligible(autoTicket,foreground,e.registered,e.created,armedAfter))continue;
   if(autoScheduled.add(e.id))ticketWorker.execute(()->dispatchTicket(e,true,false,false));
  }
 }
 private void printQrHere(){
  Entry e=current();if(e==null){status("No Single QR is selected.");return;}
  if(!ticketReady())return;
  if(!e.registered){
   new AlertDialog.Builder(this).setTitle("Cloud QR not yet registered")
    .setMessage("The QR is durably prepared on this phone, but Cloud has NOT accepted its guest link yet. Printing early may give the guest a Preparing/unavailable page until sync succeeds. Auto printing waits for registration.")
    .setPositiveButton("Print local QR anyway",(d,n)->printQrChecked(e,true))
    .setNegativeButton("Wait for Cloud",null).show();
   return;
  }
  printQrChecked(e,false);
 }
 private void printQrChecked(Entry e,boolean allowUnregistered){
  final String scope=config.get("scope");
  ticketWorker.execute(()->{
   try{
    JSONObject last;
    try(QueueDb q=new QueueDb(this)){
     last=new TicketLedger(q).latestForGuestUrl(scope,TicketProtocol.guestUrl(scope.split("\\|",-1)[0],e.path));
    }
    if(last!=null){
     runOnUiThread(()->{
      if(!foreground||isFinishing()||isDestroyed())return;
      new AlertDialog.Builder(this).setTitle("Print this QR again?")
       .setMessage("A QR ticket for this private link was already attempted ("+last.optString("state")+"). Inspect paper and the ticket history before intentionally printing another copy. Unknown outcomes must never auto-reprint.")
       .setPositiveButton("Print another ticket",(d,n)->ticketWorker.execute(()->dispatchTicket(e,false,true,allowUnregistered)))
       .setNegativeButton("Cancel",null).show();
     });
    }else dispatchTicket(e,false,false,allowUnregistered);
   }catch(Exception ex){status("QR not sent: "+safe(ex));}
  });
 }
 private String safe(Exception e){return e instanceof IllegalStateException||e instanceof IllegalArgumentException?e.getMessage():"Printer unavailable. Inspect Bluetooth/paper; no automatic retry.";}
 private void dispatchTicket(Entry e,boolean automatic,boolean explicitReprint,boolean allowUnregistered){
  String scope=config.get("scope"),printer=config.get("ticket_printer");
  if(!foreground||scope.isEmpty()||!ticketPermission()||(!e.registered&&!allowUnregistered)||(automatic&&(!autoTicket||hold))){
   if(!automatic)status("QR not ready or printer permission missing. No ticket sent.");return;
  }
  if(!TicketTransport.BUSY.compareAndSet(false,true)){
   if(!automatic)status("Another ticket is sending. Wait before pressing Print QR.");return;
  }
  TicketTransport op=new TicketTransport();ticketOperation=op;boolean claimed=false;
  String id=automatic?SingleQuickPrintRules.autoTicketId(scope,e.id):UUID.randomUUID().toString();
  try(QueueDb q=new QueueDb(this)){
   TicketLedger ledger=new TicketLedger(q);if(automatic&&ledger.get(scope,id)!=null)return;
   if(!foreground||!config.get("scope").equals(scope)||!config.get("ticket_printer").equals(printer)||(automatic&&(!autoTicket||hold)))return;
   String url=TicketProtocol.guestUrl(scope.split("\\|",-1)[0],e.path);
   if(automatic&&ledger.latestForGuestUrl(scope,url)!=null)return;
   String delivery=e.delivery();byte[] packet=TicketProtocol.packet(scope,id,delivery,e.label,url);
   ledger.create(scope,id,delivery,e.label,url,printer,null);claimed=ledger.claim(scope,id);
   if(!claimed)return;
   status((automatic?"Auto":"Manual")+" QR: connecting to selected 58B3 printer ...");
   BluetoothManager manager=getSystemService(BluetoothManager.class);
   op.connect(manager==null?null:manager.getAdapter(),printer);
   if(!foreground)op.cancel();
   op.send(packet);
   ledger.end(scope,id,"sent_unconfirmed",null);
   status((automatic?"Auto":"Manual")+" QR "+e.label+" sent (physical output UNCONFIRMED). Inspect/scan paper; no automatic retry.");
  }catch(Exception ex){
   if(claimed)try(QueueDb q=new QueueDb(this)){
    new TicketLedger(q).end(scope,id,op.writeAttempted()?"outcome_unknown":"failed_before_send",op.writeAttempted()?"connection_interrupted":"connect_or_permission");
   }catch(Exception ignored){}
   if(automatic)runOnUiThread(()->pauseAuto("Auto QR paused after a printer error. Check paper/connection before rearming; no retry."));
   status(op.writeAttempted()?"QR outcome UNKNOWN. Inspect paper before explicit reprint; never auto-retry.":"QR not sent. Check paired printer/permission; no automatic retry.");
  }finally{op.close();if(ticketOperation==op)ticketOperation=null;TicketTransport.BUSY.set(false);}
 }
 private void printPhotoHere(){
  Entry e=current();if(e==null){status("Choose a Single photo first.");return;}
  String scope=config.get("scope");if(scope.isEmpty()){status("Pair an event and prepare Windows Print Station first.");return;}
  try{
   JSONObject setup=SinglePhotoPrint.setup(config);
   org.json.JSONArray profiles=setup.getJSONObject("station").getJSONArray("profiles");
   String saved=config.get("single_quick_photo_profile");
   if(!saved.isEmpty()){
    try{enqueuePhoto(e,SinglePhotoPrint.profile(setup,saved),false);return;}
    catch(IllegalStateException ignored){config.prefs.edit().remove("single_quick_photo_profile").commit();}
   }
   if(profiles.length()==1){JSONObject p=profiles.getJSONObject(0);config.prefs.edit().putString("single_quick_photo_profile",p.getString("id")).commit();enqueuePhoto(e,p,false);return;}
   String[] names=new String[profiles.length()];for(int i=0;i<names.length;i++)names[i]=profiles.getJSONObject(i).getString("label");
   new AlertDialog.Builder(this).setTitle("Choose default photo profile (once)")
    .setItems(names,(d,n)->{try{JSONObject p=profiles.getJSONObject(n);config.prefs.edit().putString("single_quick_photo_profile",p.getString("id")).commit();enqueuePhoto(e,p,false);}catch(Exception ex){status("Invalid station profile. No photo printed.");}})
    .setNegativeButton("Cancel",null).show();
  }catch(Exception ex){
   new AlertDialog.Builder(this).setTitle("Photo printer not prepared")
    .setMessage(safe(ex)+"\nConfigure your station and photo profiles once; later Print Photo taps stay on this page.")
    .setPositiveButton("Open Print Station setup",(d,n)->startActivity(new Intent(this,PrintActivity.class)))
    .setNegativeButton("Stay here",null).show();
  }
 }
 private void enqueuePhoto(Entry e,JSONObject profile,boolean reprint){
  if(!photoBusy.compareAndSet(false,true)){status("Previous photo request is still being prepared.");return;}
  final String scope=config.get("scope");photoWorker.execute(()->{
   try{
    String first=SingleQuickPrintRules.firstPhotoPrintId(scope,e.id);
    if(!reprint){try(PrintQueue q=new PrintQueue(this)){
     JSONObject old=q.get(scope,first);
     if(old!=null){runOnUiThread(()->{photoBusy.set(false);if(!foreground)return;
      new AlertDialog.Builder(this).setTitle("Photo already requested")
       .setMessage("Existing photo print job "+first.substring(0,8)+" is "+old.optString("state")+". Check the Windows station and paper before creating another job. A new request is not a retry of an uncertain print.")
       .setPositiveButton("Queue an additional copy",(d,n)->enqueuePhoto(e,profile,true))
       .setNegativeButton("Cancel",null).show();});return;
     }
    }}
    String id=SinglePhotoPrint.enqueue(this,config,scope,e.id,profile,reprint);
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     config.prefs.edit().putString("test_only","")
      .putBoolean("drain",config.get("folder").isEmpty())
      .putBoolean("print_active",true)
      .putInt("print_mode","cloud".equals(config.get("event_print_route"))?1:0).commit();
     // Match the established PrintActivity path: a confirmed print keeps its
     // private archive task, independently from the Windows paper outcome.
     boolean alreadyWatching=config.active();config.active(true);
     boolean archiveStarted=true;
     try{startForegroundService(new Intent(this,EventService.class));}
     catch(RuntimeException ex){archiveStarted=false;if(!alreadyWatching)config.active(false);}
     try{startForegroundService(new Intent(this,PrintService.class));
      status("Photo "+e.label+" queued to Windows Print Station ("+id.substring(0,8)+"). Paper output unconfirmed. "+(archiveStarted?"Archive transfer started.":"Archive transfer paused; Resume archive from Event screen."));
     }catch(RuntimeException ex){status("Photo queued, but Android could not start print transfer. Open Print Queue and Resume. No new print job needed.");}
    });
   }catch(Exception ex){status("Photo not queued: "+safe(ex));}
   finally{photoBusy.set(false);}
  });
 }

 private void largePhoto(){if(preview.getDrawable()==null)return;ImageView i=new ImageView(this);i.setImageDrawable(preview.getDrawable());i.setAdjustViewBounds(true);new AlertDialog.Builder(this).setView(i).setPositiveButton("Close",null).show();}
 @Override protected void onResume(){super.onResume();foreground=true;
  if(autoTicket){armedAfter=System.currentTimeMillis();autoScheduled.clear();status("Auto QR armed for new Cloud-registered links while this screen is open. Previous photos will not print.");}
  handler.post(refresh);
 }
 @Override protected void onPause(){foreground=false;handler.removeCallbacks(refresh);
  TicketTransport op=ticketOperation;if(op!=null)op.cancel();
  super.onPause();
 }
 @Override protected void onDestroy(){handler.removeCallbacks(refresh);worker.shutdown();ticketWorker.shutdown();photoWorker.shutdown();
  TicketTransport op=ticketOperation;if(op!=null)op.cancel();qr.stopLoading();qr.destroy();super.onDestroy();
 }
}
