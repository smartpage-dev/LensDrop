package app.lensdrop.transfer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private Config config;private EditText base,token,folder;private CheckBox mobile;
    private String exportMode="diagnostics";
    private TextView status;private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Runnable refresh=new Runnable(){public void run(){
        if(io.isShutdown())return;
        io.execute(()->{
            try(QueueDb q=new QueueDb(MainActivity.this)){
                JSONObject snapshot=q.snapshot(config.get("scope")),p=snapshot.getJSONObject("production"),tests=snapshot.getJSONObject("preflight");
                StringBuilder text=new StringBuilder("Event: "+config.get("event")+"\n"+config.get("status"));
                text.append("\n\n01 SOURCE RECEIVED: ").append(p.optLong("received"));
                text.append("\nUnselected candidates: ").append(p.optLong("candidates")).append(" / Explicitly skipped: ").append(p.optLong("skipped"));
                text.append("\nPending: ").append(p.optLong("queued")).append(" / Action needed: ").append(p.optLong("blocked"));
                text.append("\n02 VERIFIED RECEIPTS (local): ").append(p.optLong("uploaded"));
                String cached=config.get("cloud_snapshot");
                if(!cached.isEmpty()){
                    JSONObject cloud=new JSONObject(cached);long age=Math.max(0,(System.currentTimeMillis()-cloud.optLong("fetched_at"))/1000);
                    text.append("\n03 PUBLIC GALLERY-VISIBLE (last cloud check): ").append(cloud.optLong("live"));
                    text.append("\nPersonally available via QR: ").append(cloud.optLong("personal_available"));
                    text.append("\nReview: ").append(cloud.optLong("review")).append(" / Hidden: ").append(cloud.optLong("hidden"));
                    text.append("\nCloud snapshot age: ").append(age).append("s").append(age>90?" (STALE)":"");
                }else text.append("\n03 GUEST-VISIBLE: unknown until cloud check");
                text.append("\nIsolated tests: ").append(tests.optLong("uploaded")).append(" verified / ").append(tests.optLong("queued")+tests.optLong("blocked")).append(" outstanding");
                text.append("\n\nCounts cover received accessible files, not the camera card.");
                runOnUiThread(()->status.setText(text.toString()));
            }catch(Exception e){runOnUiThread(()->status.setText("Queue retained. Status is temporarily unavailable."));}
        });
        handler.postDelayed(this,5000);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);config=new Config(this);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);int pad=24;content.setPadding(pad,pad,pad,pad);scroll.addView(content);setContentView(scroll);content.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(pad,pad+insets.getSystemWindowInsetTop(),pad,pad+insets.getSystemWindowInsetBottom());return insets;});
        label(content,"LensDrop Transfer / H5 ALPHA",25);
        status=label(content,"Not paired",16);
        button(content,"Staff Setup / Scan pairing QR",v->startActivity(new Intent(this,StaffSetupActivity.class)));
        button(content,"Print Station / Preview / Print",v->startActivity(new Intent(this,PrintActivity.class)));
        label(content,"JPEG watch-folder source. Hardware and background behaviour are not yet field-certified. Keep the official camera app connected; LensDrop uploads shared photos only.",15);
        base=field(content,"HTTPS server origin",config.get("base"));
        token=field(content,"Device token (not administrator key)","");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        folder=field(content,"Exact shared folder (relative_path)",config.get("folder"));
        button(content,"Find shared photo folders",v->findFolders());
        label(content,"Take and save one test photograph with the official camera app first. App-private galleries cannot be watched. Only this selected folder is scanned. LensDrop records existing files at Start Watching and excludes them. Reopening resumes the same baseline. Primary shared JPEG folders only.",14);
        mobile=new CheckBox(this);mobile.setText("Allow mobile data for cloud uploads (carrier charges may apply)");mobile.setChecked(config.cellular());content.addView(mobile);
        button(content,"Pair event / verify destination",v->start());
        button(content,"Guest QR / Groups / Select one photo",v->startActivity(new Intent(this,DeliveryActivity.class)));
        button(content,"Start watching / resume NEW PHOTOS ONLY",v->{config.prefs.edit().putBoolean("drain",false).putString("test_only","").commit();resume();});
        button(content,"Pre-shoot test: choose ONE separate JPEG",v->chooseTest());
        button(content,"Finish observed queue / stop discovering new files",v->{config.prefs.edit().putBoolean("drain",true).putString("test_only","").commit();resume();});
        button(content,"Export wrap-up counts",v->export("wrapup"));
        button(content,"Pause transfer",v->{config.active(false);stopService(new Intent(this,EventService.class));RecoveryJob.cancel(this);config.status("Paused. Pending queue retained.");});
        button(content,"Retry blocked files",v->{if(config.active()||UploadEngine.IN_FLIGHT.get()){say("Pause and wait for the current checkpoint first.");return;}try(QueueDb q=new QueueDb(this)){q.retryBlocked(config.get("scope"));}config.status("Marked current-event blocked work for retry. Fix the cause, then resume.");});
        button(content,"Export redacted diagnostics",v->export("diagnostics"));
        label(content,"Personal delivery: new arrivals are candidates by default. Select and confirm their group in the QR screen. Optional trusted-order auto assignment resets at each group change. Existing H2 jobs keep their original event-only destination.",14);
        label(content,"Android may stop background work, and Force stop requires reopening. This alpha uses a visible dataSync service and a deferrable 15-minute recovery job; it does not guarantee uninterrupted all-day operation.",14);
        try(QueueDb q=new QueueDb(this)){if(!config.get("scope").isEmpty()&&!config.get("folder").isEmpty())q.claimSource(config.get("scope"),config.get("folder"));}catch(Exception e){say("Source binding needs attention. Do not clear the queue.");}
        permissions();
    }
    private TextView label(LinearLayout parent,String value,int size){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setPadding(0,12,0,12);parent.addView(text);return text;}
    private EditText field(LinearLayout p,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine();p.addView(e);return e;}
    private void button(LinearLayout p,String text,View.OnClickListener action){Button b=new Button(this);b.setText(text);b.setOnClickListener(action);p.addView(b);}
    private void say(String value){config.status(value);runOnUiThread(()->Toast.makeText(this,value,Toast.LENGTH_LONG).show());}
    private boolean permission(){String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private void permissions(){
        ArrayList<String> list=new ArrayList<>();String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)list.add(p);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)list.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!list.isEmpty())requestPermissions(list.toArray(new String[0]),1);
    }
    private void findFolders(){
        if(!permission()){permissions();say("Full shared-image access is needed for automatic new-photo discovery; selected photos alone are insufficient.");return;}
        io.execute(()->{
            TreeSet<String> paths=new TreeSet<>();
            try(Cursor c=getContentResolver().query(MediaScanner.COLLECTION,new String[]{"relative_path"},"mime_type='image/jpeg'",null,null)){
                if(c!=null)while(c.moveToNext()){String path=c.getString(0);if(path!=null&&!path.isEmpty())paths.add(path);}
                if(paths.isEmpty()){say("No shared JPEG folders found. Save a camera test photo to the phone library first.");return;}
                String[] choices=paths.toArray(new String[0]);runOnUiThread(()->new android.app.AlertDialog.Builder(this).setTitle("Choose the camera app's shared folder").setItems(choices,(d,n)->folder.setText(choices[n])).show());
            }catch(Exception e){say("Photo access is unavailable. Check permissions and the official app's save destination.");}
        });
    }
    private void start(){
        if(config.active()||UploadEngine.IN_FLIGHT.get()){say("Pause the existing session before changing pairing or folder.");return;}
        if(!permission()){permissions();return;}
        String b=base.getText().toString().trim().replaceAll("/+$",""),t=token.getText().toString().trim(),f=folder.getText().toString().trim();boolean allow=mobile.isChecked();
        if(f.isEmpty()||!f.endsWith("/")||f.startsWith("/")||f.contains("..")){say("Select an exact shared relative folder, ending in /.");return;}
        io.execute(()->{
            try(CloudApi api=new CloudApi(this,b,t.isEmpty()?config.token():t,allow);QueueDb q=new QueueDb(this);PrintQueue prints=new PrintQueue(this)){
                JSONObject identity=api.call("/v1/device",null);String scope=b+"|"+identity.getJSONObject("event").getString("id")+"|"+identity.getString("device_id");
                if(!scope.equals(config.get("scope"))&&prints.hasPending()){say("Finish or resolve print jobs before changing event pairing.");return;}
                String safety=SessionRules.switchError(config.get("scope"),scope,config.get("folder"),f,q.hasOtherPending(scope));
                if(safety!=null){say(safety);return;}
                q.claimSource(scope,f);
                boolean changed=!scope.equals(config.get("scope"));
                config.save(b,t.isEmpty()?config.token():t,scope,identity.getJSONObject("event").getString("name"),f,allow);
                if(changed)config.prefs.edit().remove("cloud_snapshot").remove("preflight_report").putString("test_only","").putBoolean("drain",false).remove("cooldown").commit();
                runOnUiThread(()->{token.setText("");say("Paired. Run a separate preflight test before resuming event folder watch.");});
            }catch(Exception e){say("Pairing failed. Check HTTPS address, device token and internet connection.");}
        });
    }
    private void resume(){
        if(!permission()){permissions();return;}
        if(config.get("scope").isEmpty()||config.get("folder").isEmpty()){say("Pair this phone and select its shared photo folder first.");return;}
        if(config.get("test_only").isEmpty()&&!config.prefs.getBoolean("drain",false)){
            try(QueueDb check=new QueueDb(this)){
                if(!new WatchLedger(check).ready(config.get("scope"))){
                    io.execute(()->{try(QueueDb q=new QueueDb(this)){new MediaScanner(this).baseline(config,q);say("Existing accessible files excluded. Watch session saved; new arrivals will be candidates.");runOnUiThread(this::resume);}catch(Exception e){say("Watch baseline not created. Check full photo permission, folder and storage.");}});return;
                }
            }
        }
        config.active(true);config.status("Starting event session; saved watch baseline retained.");
        try{startForegroundService(new Intent(this,EventService.class));}catch(RuntimeException e){config.active(false);say("Android could not start foreground transfer. Queue retained; reopen and retry.");}
    }
    private void export(String kind){
        exportMode=kind;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"LensDrop-"+kind+"-H5.json");startActivityForResult(i,9);
    }
    private void chooseTest(){
        if(config.get("scope").isEmpty()){say("Pair an event first.");return;}
        if(config.active()||UploadEngine.IN_FLIGHT.get()){say("Pause and wait for the transfer checkpoint before selecting a test.");return;}
        if(!permission()){permissions();return;}
        Intent i=new Intent(Intent.ACTION_PICK);i.setDataAndType(MediaScanner.COLLECTION,"image/jpeg");startActivityForResult(i,10);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==10){
            io.execute(()->{
                if(config.active()||UploadEngine.IN_FLIGHT.get()){say("Pause first. No test was added.");return;}
                try(QueueDb q=new QueueDb(this);Cursor c=getContentResolver().query(data.getData(),new String[]{"_display_name","_size","date_modified","is_pending"},null,null,null)){
                    if(c==null||!c.moveToFirst()||c.getInt(3)!=0||c.getLong(1)<=0||c.getLong(1)>64L*1024*1024)throw new IllegalStateException("Choose a fully saved shared JPEG up to 64 MiB.");
                    String name=c.getString(0);if(name==null||!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.jpe?g$"))throw new IllegalStateException("JPEG required.");
                    String id=q.addTest(config.get("scope"),data.getData().toString(),name,c.getLong(1),c.getLong(2));
                    config.prefs.edit().putString("test_only",id).putBoolean("drain",true).remove("preflight_report").commit();
                    runOnUiThread(this::resume);
                }catch(IllegalStateException e){say(e.getMessage());}catch(Exception e){say("Could not read that shared JPEG. Keep the original and check full photo permission.");}
            });return;
        }
        if(request!=9)return;final String mode=exportMode;
        io.execute(()->{
            try(QueueDb q=new QueueDb(this);OutputStream out=getContentResolver().openOutputStream(data.getData())){
                JSONObject report=mode.equals("diagnostics")?q.diagnostics(config.get("scope")):q.snapshot(config.get("scope"));
                report.put("android_api",Build.VERSION.SDK_INT);
                String cached=config.get("cloud_snapshot");
                if(!cached.isEmpty()){JSONObject cloud=new JSONObject(cached);report.put("cloud_cached_snapshot",cloud);report.put("cloud_snapshot_stale",System.currentTimeMillis()-cloud.optLong("fetched_at")>90000);}
                else report.put("cloud_snapshot_unavailable",true);
                report.put("limits","Snapshot only. Not a camera-card audit, historical crash log, or permission to delete originals.");
                if(out==null)throw new java.io.IOException("Destination unavailable");out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));say("Report exported without tokens, filenames, source paths or photographs.");
            }catch(Exception e){say("Could not export report. Queue and originals were not changed.");}
        });
    }
    @Override protected void onResume(){super.onResume();base.setText(config.get("base"));folder.setText(config.get("folder"));handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
