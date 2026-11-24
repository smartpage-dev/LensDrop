package app.lensdrop.transfer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Three lightweight native workspaces around the existing H5.5 queues and source logic. */
public final class MainActivity extends Activity {
    private Config config;private EditText base,token,folder;private CheckBox mobile,archive;
    private String exportMode="diagnostics",loadedScope="";private int selectedPane;
    private TextView status,headline,counts,nextHint,joinSummary;private Button primary;
    private LinearLayout[] panes;private Button[] tabs;private volatile boolean visible;private long startGeneration;
    private final AtomicBoolean refreshBusy=new AtomicBoolean(false),baselineBusy=new AtomicBoolean(false),saveBusy=new AtomicBoolean(false);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Runnable refresh=new Runnable(){public void run(){
        if(!visible||io.isShutdown())return;
        if(refreshBusy.compareAndSet(false,true))io.execute(()->{
            String scope=config.get("scope");
            try(QueueDb q=new QueueDb(MainActivity.this)){
                JSONObject snapshot=q.snapshot(scope),p=snapshot.getJSONObject("production"),tests=snapshot.getJSONObject("preflight");
                String brief="Received  "+p.optLong("received")+"     Verified  "+p.optLong("uploaded")+"\nWaiting  "+p.optLong("queued")+"     Need attention  "+p.optLong("blocked");
                StringBuilder detail=new StringBuilder("Event: "+config.get("event")+"\n"+config.get("status"));
                detail.append("\nUnassigned selections: ").append(p.optLong("candidates")+p.optLong("unassigned_archives"));
                detail.append("\nGuest links waiting: ").append(p.optLong("pending_assignments"));
                detail.append("\nExplicit skips: ").append(p.optLong("skipped")).append(" / Retired locally: ").append(p.optLong("retired"));
                String cached=config.get("cloud_snapshot");
                if(!cached.isEmpty()){JSONObject c=new JSONObject(cached);long age=Math.max(0,(System.currentTimeMillis()-c.optLong("fetched_at"))/1000);detail.append("\nPublic gallery: ").append(c.optLong("live")).append(" / Personal QR available: ").append(c.optLong("personal_available")).append("\nCloud snapshot: ").append(age).append("s old").append(age>90?" (STALE)":"");}
                else detail.append("\nGuest visibility: not checked with cloud yet");
                detail.append("\nIsolated test receipts: ").append(tests.optLong("uploaded")).append("\nThese are accessible received-file counts, not a camera-card audit.");
                final String full=detail.toString();
                runOnUiThread(()->{if(!visible||isDestroyed()||!scope.equals(config.get("scope")))return;counts.setText(brief);status.setText(full);updateReadiness();});
            }catch(Exception e){runOnUiThread(()->{if(visible&&!isDestroyed())nextHint.setText("Status unavailable. Saved work is retained; check Support.");});}
            finally{refreshBusy.set(false);}
        });
        handler.postDelayed(this,5000);
    }};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);config=new Config(this);loadedScope=config.get("scope");
        LinearLayout body=LuceUi.screen(this,"LensDrop","Photographer workspace  |  "+LuceUi.version(this)+"\nEngineering pilot - keep original photos",false);
        LinearLayout bar=new LinearLayout(this);body.addView(bar);tabs=new Button[3];panes=new LinearLayout[3];
        String[] names={"Event","Setup","Support"};
        for(int n=0;n<3;n++){final int at=n;LinearLayout slot=LuceUi.column(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(n>0)lp.leftMargin=LuceUi.dp(this,7);bar.addView(slot,lp);tabs[n]=LuceUi.action(this,slot,names[n],false,()->showPane(at));}
        for(int n=0;n<3;n++){panes[n]=LuceUi.column(this);body.addView(panes[n]);}
        LinearLayout event=LuceUi.card(this,panes[0],"Your event",null);
        headline=LuceUi.label(this,event,"Not connected yet",22,true);nextHint=LuceUi.notice(this,event,"Join an event, then choose a photo folder.");
        primary=LuceUi.action(this,event,"Join your event",true,this::primaryAction);
        LinearLayout progress=LuceUi.card(this,panes[0],"Photo transfer", "Cloud storage, guest delivery and printing have separate statuses.");
        counts=LuceUi.label(this,progress,"Received  0     Verified  0\nWaiting  0     Need attention  0",19,true);
        LinearLayout guest=LuceUi.card(this,panes[0],"Guest delivery","Choose the intended guest before confirming a photo. A displayed history QR does not change the receiver.");
        LuceUi.action(this,guest,"Guest QR & photo selection",true,()->open(DeliveryActivity.class));
        LuceUi.action(this,guest,"Print a photo",false,()->open(PrintActivity.class));
        LuceUi.action(this,guest,"Print a QR ticket",false,()->open(TicketActivity.class));
        LinearLayout join=LuceUi.card(this,panes[1],"1  Join event","Use a fresh staff QR. Manual credentials are under Advanced, not required after QR pairing.");
        joinSummary=LuceUi.label(this,join,"",15,true);LuceUi.action(this,join,"Join / manage staff pairing",true,()->open(StaffSetupActivity.class));
        LinearLayout source=LuceUi.card(this,panes[1],"2  Photo source","Save one camera test JPEG into shared phone storage before choosing its folder.");
        folder=LuceUi.field(this,source,"Choose a shared photo folder",config.get("folder"),false);folder.setFocusable(false);folder.setOnClickListener(v->findFolders());
        LuceUi.action(this,source,"Choose photo folder",true,this::findFolders);
        mobile=LuceUi.check(this,source,"Use cellular fallback if Wi-Fi has no internet (optional)",config.cellular());
        LuceUi.label(this,source,"4G/5G and metered Wi-Fi work by default for pairing, photo uploads and cloud printing. This option only requests an alternative cellular network if the active connection is unusable. Data charges may apply.",14,false);
        archive=LuceUi.check(this,source,"Archive all new photos privately",archiveEnabled());
        LuceUi.label(this,source,"Archive first; assign/release to guests separately. Existing excluded, skipped and retired files stay untouched. Pause transfers before changing these settings.",14,false);
        LuceUi.action(this,source,"Save photo source",true,this::start);
        LuceUi.action(this,source,"Photo / notification permissions",false,this::permissions);
        LuceUi.notice(this,source,"First start: pause capture/export while existing files are excluded. Begin shooting only after Watching is ready.");
        LinearLayout printing=LuceUi.card(this,panes[1],"3  Printing (optional)","Photo printing needs Admin permission and a prepared Windows station. QR tickets use your own Bluetooth printer.");
        LuceUi.action(this,printing,"Prepare photo Print Station",false,()->open(PrintActivity.class));
        LuceUi.action(this,printing,"Set up my QR-ticket printer",false,()->open(TicketActivity.class));
        LinearLayout advanced=LuceUi.disclosure(this,panes[1],"Advanced manual setup");
        LuceUi.label(this,advanced,"Do not change these to repair an active queue. A device token is NOT the master Admin Key.",14,false);
        base=LuceUi.field(this,advanced,"HTTPS server origin",config.get("base"),false);
        token=LuceUi.field(this,advanced,"Device token (leave blank after QR pairing)","",true);
        LuceUi.action(this,advanced,"Edit folder path manually",false,this::editFolder);
        LinearLayout diagnostics=LuceUi.card(this,panes[2],"Status & recovery","Recovery never clears your originals or silently reprints uncertain work.");
        status=LuceUi.label(this,diagnostics,"Waiting for status...",14,false);
        LuceUi.action(this,diagnostics,"Recovery / previous events",true,()->open(RecoveryActivity.class));
        LuceUi.action(this,diagnostics,"Pause photo uploads",false,this::pauseUploads);
        LuceUi.action(this,diagnostics,"Retry blocked uploads / links",false,()->{if(config.active()||UploadEngine.IN_FLIGHT.get()){say("Pause and wait for the current checkpoint first.");return;}try(QueueDb q=new QueueDb(this)){q.retryBlocked(config.get("scope"));q.retryAssignments(config.get("scope"));}say("Marked for retry. Fix the cause, then resume.");});
        LuceUi.action(this,diagnostics,"Export redacted diagnostics",false,()->export("diagnostics"));
        LinearLayout rehearsal=LuceUi.disclosure(this,panes[2],"Rehearsal & end of event");
        LuceUi.action(this,rehearsal,"Isolated pre-shoot test JPEG",false,this::chooseTest);
        LuceUi.action(this,rehearsal,"Finish received queue (stop discovering)",false,()->{config.prefs.edit().putBoolean("drain",true).putString("test_only","").commit();resume();});
        LuceUi.action(this,rehearsal,"Export wrap-up counts",false,()->export("wrapup"));
        LuceUi.label(this,rehearsal,"Keep originals. An isolated test is never a guest photo. Android background work, camera export and real printers still require field testing.",14,false);
        if(saved!=null&&!saved.getString("draft_scope","").isEmpty()&&saved.getString("draft_scope","").equals(loadedScope)){
            folder.setText(saved.getString("draft_folder",config.get("folder")));archive.setChecked(saved.getBoolean("draft_archive",archive.isChecked()));mobile.setChecked(saved.getBoolean("draft_mobile",mobile.isChecked()));
        }
        showPane(saved!=null?saved.getInt("pane",config.get("scope").isEmpty()?1:0):config.get("scope").isEmpty()?1:0);updateReadiness();
        try(QueueDb q=new QueueDb(this)){if(!config.get("scope").isEmpty()&&!config.get("folder").isEmpty())q.claimSource(config.get("scope"),config.get("folder"));}catch(Exception e){say("Source binding needs attention. Do not clear the queue.");}
    }
    private void open(Class<?> screen){startActivity(new Intent(this,screen));}
    private boolean archiveEnabled(){try(QueueDb q=new QueueDb(this);Cursor c=q.getReadableDatabase().rawQuery("SELECT archive_all FROM watch_sessions WHERE scope=?",new String[]{config.get("scope")})){return !c.moveToFirst()||c.getInt(0)==1;}catch(Exception e){return false;}}
    private void showPane(int n){selectedPane=Math.max(0,Math.min(2,n));for(int i=0;i<3;i++){panes[i].setVisibility(i==selectedPane?View.VISIBLE:View.GONE);tabs[i].setTextColor(i==selectedPane?android.graphics.Color.WHITE:LuceUi.ACCENT);tabs[i].setBackground(LuceUi.shape(this,i==selectedPane?LuceUi.ACCENT:LuceUi.SOFT,0));} }
    private void updateReadiness(){if(config==null)return;boolean paired=!config.get("scope").isEmpty();SetupFlow.Step next=SetupFlow.next(paired,!config.get("folder").isEmpty(),permission());
        headline.setText(paired?config.get("event"):"Join your event");joinSummary.setText(paired?config.get("event")+"\n"+config.get("base"):"No event paired yet");
        primary.setText(SetupFlow.action(next,config.active()));
        nextHint.setText(config.active()?"Transfer requested. "+config.get("status"):next==SetupFlow.Step.READY?"Source saved. Start/resume is explicit; no queued work was deleted.":SetupFlow.title(next)+" in Setup before starting. Printing is optional.");
    }
    private void primaryAction(){SetupFlow.Step next=SetupFlow.next(!config.get("scope").isEmpty(),!config.get("folder").isEmpty(),permission());if(next==SetupFlow.Step.JOIN){showPane(1);open(StaffSetupActivity.class);return;}if(next!=SetupFlow.Step.READY){showPane(1);if(next==SetupFlow.Step.PERMISSION)permissions();return;}if(config.active()){pauseUploads();return;}
        if(!folder.getText().toString().trim().equals(config.get("folder"))||archive.isChecked()!=archiveEnabled()||mobile.isChecked()!=config.cellular()){showPane(1);say("Save your changed photo-source settings first, or restore the saved values.");return;}
        config.prefs.edit().putBoolean("drain",false).putString("test_only","").commit();resume();updateReadiness();
    }
    private void pauseUploads(){startGeneration++;config.active(false);stopService(new Intent(this,EventService.class));RecoveryJob.cancel(this);say("Uploads paused. Saved queue retained. Accepted station jobs are NOT cancelled.");updateReadiness();}
    private void editFolder(){EditText e=new EditText(this);e.setSingleLine(true);e.setText(folder.getText());new AlertDialog.Builder(this).setTitle("Advanced: exact shared relative folder").setMessage("Ends in /. This only changes the draft; save it while paused. Existing bindings and queues are retained.").setView(e).setPositiveButton("Use draft",(d,n)->folder.setText(e.getText())).setNegativeButton("Cancel",null).show();}
    private void say(String value){config.status(value);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){nextHint.setText(value);Toast.makeText(this,value,Toast.LENGTH_LONG).show();}});}
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
                String[] choices=paths.toArray(new String[0]);runOnUiThread(()->{if(!visible||isFinishing()||isDestroyed())return;new android.app.AlertDialog.Builder(this).setTitle("Choose the camera app's shared folder").setItems(choices,(d,n)->folder.setText(choices[n])).show();});
            }catch(Exception e){say("Photo access is unavailable. Check permissions and the official app's save destination.");}
        });
    }
    private void start(){
        if(config.active()||UploadEngine.IN_FLIGHT.get()){say("Pause the existing session before changing pairing or folder.");return;}
        if(!permission()){permissions();return;}
        String b=base.getText().toString().trim().replaceAll("/+$",""),t=token.getText().toString().trim(),f=folder.getText().toString().trim();boolean allow=mobile.isChecked(),archiveAll=archive.isChecked();
        if(f.isEmpty()||!f.endsWith("/")||f.startsWith("/")||f.contains("..")){say("Select an exact shared relative folder, ending in /.");return;}
        if(!saveBusy.compareAndSet(false,true)){say("Saving source settings. Please finish this request first.");return;}
        io.execute(()->{
            try(CloudApi api=new CloudApi(this,b,t.isEmpty()?config.token():t,allow);QueueDb q=new QueueDb(this);PrintQueue prints=new PrintQueue(this)){
                JSONObject identity=api.call("/v1/device",null);String scope=b+"|"+identity.getJSONObject("event").getString("id")+"|"+identity.getString("device_id");
                if(!scope.equals(config.get("scope"))&&prints.hasPending()){say("Finish or resolve print jobs before changing event pairing.");return;}
                String safety=SessionRules.switchError(config.get("scope"),scope,config.get("folder"),f,q.hasOtherPending(scope));
                if(safety!=null){say(safety);return;}
                if(archiveAll&&(identity.optJSONObject("capabilities")==null||!identity.getJSONObject("capabilities").optBoolean("archive_delivery_v1")))throw new IllegalStateException("Upgrade cloud to H5.5 before private archiving");
                q.claimSource(scope,f);new WatchLedger(q).ensure(scope,f);q.archivePolicy(scope,archiveAll);
                boolean changed=!scope.equals(config.get("scope"));
                config.save(b,t.isEmpty()?config.token():t,scope,identity.getJSONObject("event").getString("name"),f,allow);
                if(changed)config.prefs.edit().remove("cloud_snapshot").remove("preflight_report").putString("test_only","").putBoolean("drain",false).remove("cooldown").commit();
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;token.setText("");loadedScope=config.get("scope");showPane(0);say("Photo source saved. Start uploads when capture/export is paused for the first baseline.");});
            }catch(Exception e){say("Source settings were not saved. Check event connection and internet; retained queues were not cleared.");}finally{saveBusy.set(false);}
        });
    }
    private void resume(){
        if(!permission()){permissions();return;}
        if(config.get("scope").isEmpty()||config.get("folder").isEmpty()){say("Pair this phone and select its shared photo folder first.");return;}
        if(config.get("test_only").isEmpty()&&!config.prefs.getBoolean("drain",false)){
            try(QueueDb check=new QueueDb(this)){
                if(!new WatchLedger(check).ready(config.get("scope"))){
                    if(!baselineBusy.compareAndSet(false,true)){say("Recording the existing-file baseline. Keep capture paused.");return;}
                    final long generation=startGeneration;final String baselineScope=config.get("scope");
                    io.execute(()->{try(QueueDb q=new QueueDb(this)){new MediaScanner(this).baseline(config,q);say("Existing accessible files excluded. Watching is ready. Archive policy controls uploads; guest assignment stays separate.");runOnUiThread(()->{if(visible&&!isFinishing()&&!isDestroyed()&&generation==startGeneration&&baselineScope.equals(config.get("scope")))resume();});}catch(Exception e){say("Watch baseline not created. Check full photo permission, folder and storage.");}finally{baselineBusy.set(false);}});return;
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
                report.put("installed_build",LuceUi.version(this));report.put("staff_scan_result",config.get("staff_scan_result"));report.put("staff_scan_at",config.prefs.getLong("staff_scan_at",0));report.put("staff_scan_failure",config.get("staff_scan_failure"));report.put("staff_scan_failure_type",config.get("staff_scan_failure_type"));report.put("staff_scan_failure_at",config.prefs.getLong("staff_scan_failure_at",0));
                String cached=config.get("cloud_snapshot");
                if(!cached.isEmpty()){JSONObject cloud=new JSONObject(cached);report.put("cloud_cached_snapshot",cloud);report.put("cloud_snapshot_stale",System.currentTimeMillis()-cloud.optLong("fetched_at")>90000);}
                else report.put("cloud_snapshot_unavailable",true);
                report.put("limits","Snapshot only. Not a camera-card audit, historical crash log, or permission to delete originals.");
                if(out==null)throw new java.io.IOException("Destination unavailable");out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));say("Report exported without tokens, filenames, source paths or photographs.");
            }catch(Exception e){say("Could not export report. Queue and originals were not changed.");}
        });
    }
    @Override protected void onResume(){super.onResume();visible=true;boolean changed=!loadedScope.equals(config.get("scope"));if(changed){loadedScope=config.get("scope");base.setText(config.get("base"));folder.setText(config.get("folder"));token.setText("");archive.setChecked(archiveEnabled());showPane(1);}else if(base.getText().toString().trim().isEmpty())base.setText(config.get("base"));updateReadiness();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("pane",selectedPane);state.putString("draft_scope",config.get("scope"));state.putString("draft_folder",folder.getText().toString());state.putBoolean("draft_archive",archive.isChecked());state.putBoolean("draft_mobile",mobile.isChecked());super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){visible=false;handler.removeCallbacks(refresh);io.shutdown();super.onDestroy();}
}
