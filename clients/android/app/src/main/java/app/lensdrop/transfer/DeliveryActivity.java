package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.util.Size;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Staff-operated QR screen. No Javascript bridge, network-loaded QR, or full-size decode. */
public class DeliveryActivity extends Activity {
    private Config config;private TextView summary,message;private WebView qr;private String qrSignature=null;private CheckBox automatic;private Spinner modes;
    private boolean binding;private boolean groupOnly;private LinearLayout groupThumbs;private String lastThumbKey="";private long remoteThumbAt=0;private String remoteGroup="";private final ExecutorService thumbnails=Executors.newFixedThreadPool(2);
    private ScrollView qrScroll;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean preparing=new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,5000);}};
    private interface Task{void run(WatchLedger w)throws Exception;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);config=new Config(this);groupOnly=getIntent().getBooleanExtra("group_only",false);
        LinearLayout[] dock=new LinearLayout[1];
        LinearLayout page=LuceUi.dockedScreen(this,"Guest delivery","QR stays with the guest; receiving group stays independent.",dock);
        qrScroll=(ScrollView)page.getParent();
        LinearLayout display=LuceUi.card(this,page,"Guest QR","Showing history does not change the receiving group.");
        summary=LuceUi.label(this,display,"Prepare a group or select one photo.",16,true);
        qr=new WebView(this);qr.getSettings().setJavaScriptEnabled(true);qr.getSettings().setAllowFileAccess(false);qr.getSettings().setAllowContentAccess(false);qr.getSettings().setBlockNetworkLoads(true);qr.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,String url){return true;}});
        int widthDp=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);int heightDp=Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);display.addView(qr,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,Math.min(Math.max(160,heightDp-315),Math.min(350,Math.max(160,widthDp-64))))));
        message=LuceUi.notice(this,page,"Guest approval is separate from private cloud archiving.");
        groupThumbs=LuceUi.card(this,page,"Group photographs","Three fixed columns. Tap a thumbnail to enlarge; this section can scroll below the QR.");
        LinearLayout actions=LuceUi.card(this,page,"Choose the delivery","Group: several photos share one QR. Single: one selected photo has one fixed QR.");
        modes=new Spinner(this);modes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Group QR / multiple photographs","Single selected photo QR"}));actions.addView(modes);if(groupOnly){actions.setVisibility(View.GONE);modes.setEnabled(false);}
        try(QueueDb initial=new QueueDb(this)){modes.setSelection(new WatchLedger(initial).mode(config.get("scope")).equals("single")?1:0);}catch(Exception ignored){}
        modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int n,long id){if(binding)return;run(w->{w.mode(config.get("scope"),n==0?"group":"single");say("Mode changed for future selections only. Existing assignments are retained.");});}});

        LuceUi.label(this,actions,"Daily controls stay in the fixed toolbar below. Select Group or Single here; changing mode never moves existing photos.",13,false);
        LinearLayout links=LuceUi.card(this,page,"Prepare & deliver","Prepare links online before use. Printing a QR ticket does not create a photo-print request.");
        button(links,"Prepare guest links (3 group + 3 single)",v->prepare());
        LuceUi.label(this,links,"First QR: use Next Group (Group mode) or Select Photo (Single mode). The app prepares links online if needed. Watching a folder does not assign a guest automatically.",13,false);
        LinearLayout advanced=LuceUi.disclosure(this,page,"Advanced group controls");
        button(advanced,"History / choose any previous QR",v->history(0));
        button(advanced,"Undo unused Next Group",v->new AlertDialog.Builder(this).setTitle("Restore previous receiving group?").setMessage("Quick Undo is available for 120 seconds, only while the new group has no assigned work or ticket. Otherwise use History then Resume. Existing photos never move.").setPositiveButton("Restore",(d,n)->run(w->say(w.undo(config.get("scope"))))).setNegativeButton("Cancel",null).show());
        automatic=LuceUi.check(this,advanced,"Auto-assign new arrivals (TRUSTED ORDER ONLY)",false);
        automatic.setOnCheckedChangeListener((b,checked)->{if(binding)return;if(!checked){run(w->w.auto(config.get("scope"),false));return;}
            binding=true;automatic.setChecked(false);binding=false;
            new AlertDialog.Builder(this).setTitle("Only for a known ordered source").setMessage("A folder watcher cannot identify a delayed camera or editor result. Keep this OFF for external editors or uncertain arrival order. It resets at Next Group / Resume / mode changes. Recovered arrivals still require confirmation.").setPositiveButton("Enable for this group",(d,n)->run(w->{if(w.active(config.get("scope"))==null||!w.mode(config.get("scope")).equals("group"))throw new IllegalStateException("Select Group mode and start a group first");w.auto(config.get("scope"),true);})).setNegativeButton("Keep safe confirmation",null).show();});

        button(advanced,"Hide all unselected / skip legacy candidates",v->new AlertDialog.Builder(this).setMessage("Archive-all photos continue uploading privately and are only hidden from selection. Legacy selected-only candidates are skipped without upload. Source photos and guest assignments are kept.").setPositiveButton("Confirm hide / legacy skip",(d,n)->run(w->w.skip(config.get("scope"),null))).setNegativeButton("Cancel",null).show());
        button(advanced,"Recovery / changed sources / previous events",v->startActivity(new Intent(this,RecoveryActivity.class)));
        LuceUi.label(this,advanced,"Do not enable trusted order for delayed editor output or uncertain camera transfer. It resets on group or mode changes. Private guest release is controlled in Admin.",14,false);
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);dock[0].addView(row1);
        dockButton(row1,"Prev QR",this::previousQr);
        dockButton(row1,"Current QR",()->run(w->{String id=w.active(config.get("scope"));if(id==null)throw new IllegalStateException("Start a group first");w.show(config.get("scope"),id);showQrTop();}));
        dockButton(row1,"Next Group",this::nextGroupGuided);
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);dock[0].addView(row2);
        dockButton(row2,"Select Photo",this::chooseGuided);
        dockButton(row2,"Print Photo",()->startActivity(new Intent(this,PrintActivity.class)));
        dockButton(row2,"Print QR",()->startActivity(new Intent(this,TicketActivity.class)));
        if(config.get("scope").isEmpty()){say("Join an event in Setup first.");return;}
        run(w->{w.ensure(config.get("scope"),config.get("folder"));if(groupOnly)w.mode(config.get("scope"),"group");});
        String showId=getIntent().getStringExtra("gallery_show_delivery");
        String galleryId=getIntent().getStringExtra("gallery_job_id");
        if(showId!=null&&!showId.isEmpty())run(w->{w.show(config.get("scope"),showId);showQrTop();say(getIntent().getBooleanExtra("gallery_ticket_hint",false)?"Check the shown guest QR, then tap Print QR below. No ticket has printed yet.":"Showing the selected guest QR. Receiving group was not changed.");});
        else if(galleryId!=null&&!galleryId.isEmpty())uiGalleryCandidate(galleryId);
    }
    private void dockButton(LinearLayout row,String title,Runnable action){
        LinearLayout slot=LuceUi.column(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(LuceUi.dp(this,2),0,LuceUi.dp(this,2),0);row.addView(slot,lp);
        Button b=LuceUi.action(this,slot,title,false,action);b.setTextSize(13);b.setMinHeight(LuceUi.dp(this,46));b.setPadding(2,4,2,4);
    }
    private void showQrTop(){runOnUiThread(()->{if(qrScroll!=null)qrScroll.smoothScrollTo(0,0);});}
    private void previousQr(){
        run(w->{JSONArray rows=w.history(config.get("scope"),0);String current=w.shown(config.get("scope"));
            int at=-1;for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(current)){at=i;break;}
            int next=at+1;
            if(next>=rows.length())throw new IllegalStateException("No earlier QR in recent history. Use Advanced History for older groups.");
            JSONObject target=rows.getJSONObject(next);
            w.show(config.get("scope"),target.getString("id"));
            say("Showing "+target.getString("label")+". Receiving group is unchanged.");showQrTop();
        });
    }
    private void nextGroupGuided(){
        if(config.get("scope").isEmpty()){say("Pair an event first.");return;}
        if(!preparing.compareAndSet(false,true)){say("QR preparation already running.");return;}
        final String scope=config.get("scope");
        network.execute(()->{
            try(QueueDb q=new QueueDb(this)){
                WatchLedger w=new WatchLedger(q);w.ensure(scope,config.get("folder"));
                if(!"group".equals(w.mode(scope)))throw new IllegalStateException("Switch to Group mode before Next Group.");
                try{w.nextGroup(scope);}catch(IllegalStateException missing){
                    // No prepared QR: reserve and verify guest links ONLINE, then take one.
                    try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){DeliveryCoordinator.prepare(q,scope,api);}
                    if(!scope.equals(config.get("scope")))throw new IllegalStateException("Event changed while preparing QR");
                    w.nextGroup(scope);
                }
                say("New group QR ready. Photos must still be assigned to this guest.");showQrTop();
            }catch(Exception e){say(e instanceof IllegalStateException?e.getMessage():"Could not prepare a group QR. Check internet and staff pairing; previous groups are safe.");}
            finally{preparing.set(false);refresh();}
        });
    }
    private void chooseGuided(){
        if(config.get("scope").isEmpty()){say("Pair an event first.");return;}
        final String scope=config.get("scope");
        // Reserve Single links if needed. For Group mode, existing active QR is reused.
        if(!preparing.compareAndSet(false,true))return;
        network.execute(()->{
            try(QueueDb q=new QueueDb(this)){
                WatchLedger w=new WatchLedger(q);
                if("group".equals(w.mode(scope))&&w.active(scope)==null)throw new IllegalStateException("Tap Next Group first, then select photos for that guest.");
                if("single".equals(w.mode(scope))){
                    boolean ready;
                    try(android.database.Cursor c=q.getReadableDatabase().rawQuery(
                        "SELECT 1 FROM guest_groups WHERE scope=? AND kind='single' AND state='reserved' AND guest_path IS NOT NULL LIMIT 1",new String[]{scope})){
                        ready=c.moveToFirst();
                    }
                    if(!ready)try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){DeliveryCoordinator.prepare(q,scope,api);}
                }
                if(scope.equals(config.get("scope")))runOnUiThread(()->candidates(0));
            }catch(Exception e){say(e instanceof IllegalStateException?e.getMessage():"Could not prepare photo QR. Existing archive and queue are safe; check internet.");}
            finally{preparing.set(false);refresh();}
        });
    }
    private void uiGalleryCandidate(String id){
        final String scope=config.get("scope");
        run(w->{try(android.database.Cursor c=w.queue.getReadableDatabase().rawQuery(
                "SELECT id,uri,name,size,modified,spool,sha,state,photo_id FROM jobs WHERE scope=? AND id=? AND is_test=0 AND delivery_id IS NULL AND selection_hidden=0",new String[]{scope,id})){
            if(!c.moveToFirst()){say("This photo is already assigned or unavailable. Select it from Gallery to view its guest QR.");return;}
            JSONObject p=new JSONObject();String[] fields={"id","uri","name","size","modified","spool","sha","state","photo_id"};
            for(int n=0;n<fields.length;n++){if(n==3||n==4)p.put(fields[n],c.getLong(n));else p.put(fields[n],c.getString(n));}
            if("group".equals(w.mode(scope))&&w.active(scope)==null){say("Tap Next Group to create a guest QR, then choose this photo again.");return;}
            if("single".equals(w.mode(scope))){
                boolean ready;
                try(android.database.Cursor pool=w.queue.getReadableDatabase().rawQuery(
                        "SELECT 1 FROM guest_groups WHERE scope=? AND kind='single' AND state='reserved' AND guest_path IS NOT NULL LIMIT 1",new String[]{scope})){
                    ready=pool.moveToFirst();
                }
                if(!ready){
                    say("Preparing one-photo QR links. Your selected photo is not assigned yet.");
                    network.execute(()->{
                        try(QueueDb q=new QueueDb(this);CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){
                            DeliveryCoordinator.prepare(q,scope,api);
                            if(scope.equals(config.get("scope")))preview(p);
                        }catch(Exception e){say("Could not prepare single-photo QR. Photo remains unassigned; check internet and try again.");}
                    });
                    return;
                }
            }
            say("Selected photo opened. Confirm its guest destination in the preview.");preview(p);
        }});
    }
    private void button(LinearLayout p,String text,View.OnClickListener action){LuceUi.action(this,p,text,false,()->action.onClick(p));}
    private void say(String text){runOnUiThread(()->message.setText(text));}
    private void run(Task task){
        if(config.get("scope").isEmpty()){say("Pair an event first.");return;}
        if(io.isShutdown())return;
        io.execute(()->{try(QueueDb q=new QueueDb(this)){WatchLedger w=new WatchLedger(q);w.ensure(config.get("scope"),config.get("folder"));task.run(w);}catch(Exception e){say(e instanceof IllegalStateException?e.getMessage():"Action not completed. Queue retained; check pairing and connectivity.");}refresh();});
    }
    private void prepare(){
        if(config.get("scope").isEmpty()){say("Pair an event first.");return;}
        if(!preparing.compareAndSet(false,true)){say("QR preparation already in progress. Local history remains available.");return;}
        final String scope=config.get("scope"),origin=config.get("base");final boolean cellular=config.cellular();
        network.execute(()->{try(QueueDb q=new QueueDb(this);CloudApi api=new CloudApi(this,origin,config.token(),cellular)){
            DeliveryCoordinator.prepare(q,scope,api);say("Prepared QR links saved locally. Existing codes work during an upload interruption.");
        }catch(Exception e){say("QR preparation could not complete. Existing codes and queue are retained; check internet and event pairing, then retry.");}finally{preparing.set(false);refresh();}});
    }
    private void refresh(){
        if(io.isShutdown()||config==null||config.get("scope").isEmpty())return;
        io.execute(()->{
            try(QueueDb q=new QueueDb(this)){
                WatchLedger w=new WatchLedger(q);JSONObject s=w.summary(config.get("scope")),shown=w.group(config.get("scope"),w.shown(config.get("scope"))),receiving=w.group(config.get("scope"),w.active(config.get("scope")));
                String text="Receiving: "+(s.getString("mode").equals("single")?"single-photo candidates":receiving==null?"no group":receiving.getString("label"))+"\nShowing QR: "+(shown==null?"none":shown.getString("label"))+"\nExcluded at start: "+s.optLong("excluded")+" / Candidates: "+(s.optLong("candidate")+s.optLong("unassigned_archives"))+" / Verified uploads: "+s.optLong("uploaded")+"\nChanged source files needing attention: "+s.optLong("changed_sources")+(shown==null?"\nNo QR yet: tap Next Group or select a photo. New arrivals alone do not create guest links.":"");
                String link=shown!=null&&shown.optString("state").equals("open")&&!shown.optString("guest_path").isEmpty()?config.get("base")+shown.getString("guest_path"):"";
                final String shownId=shown==null?"":shown.optString("id");
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;summary.setText(text);if(groupOnly){showGroupThumbs(shownId);refreshRemoteThumbs(shownId);}binding=true;int pos=s.optString("mode").equals("single")?1:0;if(!groupOnly&&modes.getSelectedItemPosition()!=pos)modes.setSelection(pos);automatic.setChecked(s.optBoolean("auto_assign"));binding=false;renderQr(link);});
            }catch(Exception ignored){}
        });
    }
    private void refreshRemoteThumbs(String groupId){
        if(groupId.isEmpty()||network.isShutdown())return;
        if(groupId.equals(remoteGroup)&&System.currentTimeMillis()-remoteThumbAt<15000)return;
        remoteGroup=groupId;remoteThumbAt=System.currentTimeMillis();
        network.execute(()->{try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){
            JSONObject result=api.call("/v1/device/deliveries/"+groupId+"/members",null);
            org.json.JSONArray photos=result.getJSONArray("photos");
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&groupId.equals(remoteGroup))showRemoteGroupThumbs(groupId,photos);});
        }catch(Exception ignored){/* offline: keep local thumbnails and last valid QR */}});
    }
    private void showRemoteGroupThumbs(String groupId,org.json.JSONArray photos){
        StringBuilder key=new StringBuilder("cloud:").append(groupId);for(int i=0;i<photos.length();i++)key.append(':').append(photos.optJSONObject(i).optString("id"));
        if(key.toString().equals(lastThumbKey))return;lastThumbKey=key.toString();groupThumbs.removeAllViews();LinearLayout row=null;
        int count=Math.min(24,photos.length());for(int n=0;n<count;n++){
            if(n%3==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);groupThumbs.addView(row);}
            JSONObject p=photos.optJSONObject(n);if(p==null)continue;String photo=p.optString("id");
            LinearLayout tile=LuceUi.column(this);row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);tile.addView(image,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,110)));
            TextView label=LuceUi.label(this,tile,p.optString("filename"),11,false);label.setMaxLines(1);
            image.setOnClickListener(v->{if(image.getDrawable()==null)return;ImageView big=new ImageView(this);big.setImageDrawable(image.getDrawable());big.setAdjustViewBounds(true);new AlertDialog.Builder(this).setView(big).setPositiveButton("Close",null).show();});
            thumbnails.execute(()->{try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){byte[] b=api.groupThumbnail(groupId,photo);Bitmap bmp=android.graphics.BitmapFactory.decodeByteArray(b,0,b.length);runOnUiThread(()->{if(!isDestroyed()&&lastThumbKey.equals(key.toString()))image.setImageBitmap(bmp);else if(bmp!=null)bmp.recycle();});}catch(Exception ignored){}});
        }
        if(count%3!=0&&row!=null)for(int i=count%3;i<3;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    private void showGroupThumbs(String groupId){
        if(groupId.equals(remoteGroup)&&lastThumbKey.startsWith("cloud:"))return;
        if(groupId.isEmpty()){groupThumbs.removeAllViews();lastThumbKey="";return;}
        final java.util.ArrayList<String[]> rows=new java.util.ArrayList<>();
        try(QueueDb q=new QueueDb(this);android.database.Cursor c=q.getReadableDatabase().rawQuery("SELECT id,name,photo_id FROM jobs WHERE scope=? AND delivery_id=? AND is_test=0 ORDER BY created DESC LIMIT 24",new String[]{config.get("scope"),groupId})){
            while(c.moveToNext())rows.add(new String[]{c.getString(0),c.getString(1),c.getString(2)});
        }catch(Exception ignored){return;}
        StringBuilder key=new StringBuilder(groupId);for(String[] x:rows)key.append(':').append(x[0]).append(':').append(x[2]);if(key.toString().equals(lastThumbKey))return;lastThumbKey=key.toString();
        groupThumbs.removeAllViews();LinearLayout row=null;
        for(int n=0;n<rows.size();n++){
            if(n%3==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);groupThumbs.addView(row);}
            String[] p=rows.get(n);LinearLayout tile=LuceUi.column(this);LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);row.addView(tile,cell);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);tile.addView(image,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,110)));
            TextView title=LuceUi.label(this,tile,p[1],11,false);title.setMaxLines(1);image.setOnClickListener(v->{if(image.getDrawable()==null)return;ImageView big=new ImageView(this);big.setImageDrawable(image.getDrawable());big.setAdjustViewBounds(true);new AlertDialog.Builder(this).setView(big).setPositiveButton("Close",null).show();});
            if(p[2]!=null&&!p[2].isEmpty())thumbnails.execute(()->{try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){byte[] bytes=api.thumbnail(p[2]);android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=2;Bitmap bmp=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);runOnUiThread(()->{if(!isDestroyed()&&lastThumbKey.equals(key.toString()))image.setImageBitmap(bmp);else if(bmp!=null)bmp.recycle();});}catch(Exception ignored){}});
        }
        if(rows.size()%3!=0&&row!=null)for(int i=rows.size()%3;i<3;i++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    private void renderQr(String link){
        if(java.util.Objects.equals(link,qrSignature))return;qrSignature=link;
        try{
            String js;try(java.io.InputStream in=getAssets().open("qr.js")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);js=new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
            String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;display:grid;place-items:center;min-height:95vh;font:16px sans-serif}svg{width:min(88vw,90vh);height:auto}</style><div id='q'></div><script>"+js+"</script><script>document.getElementById('q')."+(link.isEmpty()?"textContent='Prepare QR links, then choose a group or photograph.'":"innerHTML=LensDropQR.toSvg("+JSONObject.quote(link)+")")+";</script>";
            qr.loadDataWithBaseURL("https://lensdrop.invalid/",html,"text/html","UTF-8",null);
        }catch(Exception e){say("QR renderer unavailable. Use the saved guest link in Admin; do not change group identities.");}
    }
    private void history(int offset){
        run(w->{JSONArray rows=w.history(config.get("scope"),offset);String[] choices=new String[rows.length()+1];for(int i=0;i<rows.length();i++){JSONObject g=rows.getJSONObject(i);choices[i]=g.getString("label")+" / "+g.getString("kind")+" / "+g.getString("state");}choices[rows.length()]="Older history...";
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("History: show is display-only").setItems(choices,(dialog,n)->{if(n==rows.length()){history(offset+50);return;}try{JSONObject g=rows.getJSONObject(n);String id=g.getString("id");String[] actions=g.getString("kind").equals("group")?new String[]{"Show QR only","Resume this group for new photos"}:new String[]{"Show QR only"};new AlertDialog.Builder(this).setTitle(g.getString("label")).setItems(actions,(d,action)->{if(action==0)run(x->x.show(config.get("scope"),id));else new AlertDialog.Builder(this).setMessage("Resume "+g.optString("label")+" for FUTURE photographs? Existing assignments stay unchanged; automatic assignment stays OFF.").setPositiveButton("Resume group",(dd,nn)->run(x->x.resume(config.get("scope"),id))).setNegativeButton("Cancel",null).show();}).show();}catch(Exception e){say("Could not read history entry.");}}).setNegativeButton("Close",null).show());
        });
    }
    private void candidates(int offset){
        run(w->{JSONArray rows=w.candidates(config.get("scope"),offset);String[] labels=new String[rows.length()+1];for(int i=0;i<rows.length();i++)labels[i]=rows.getJSONObject(i).getString("name");labels[rows.length()]="Older candidates...";
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Choose guest delivery (archive status is separate)").setItems(labels,(dialog,n)->{if(n==rows.length()){candidates(offset+50);return;}try{preview(rows.getJSONObject(n));}catch(Exception e){say("Candidate unavailable.");}}).setNegativeButton("Close",null).show());
        });
    }
    private void preview(JSONObject photo){
        run(w->{String uri=photo.getString("uri");
            Bitmap bitmap;
            String staged=photo.optString("spool");
            if(!photo.optString("photo_id").isEmpty()){
                try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){byte[] bytes=api.thumbnail(photo.getString("photo_id"));bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)throw new IllegalStateException("Archived thumbnail unavailable");}
            }else if(!staged.isEmpty()){
                java.io.File f=new java.io.File(staged);if(!f.getCanonicalFile().getParentFile().equals(new java.io.File(getFilesDir(),"spool").getCanonicalFile())||!f.isFile()||f.length()!=photo.getLong("size"))throw new IllegalStateException("Staged candidate unavailable; do not select another file silently.");
                android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(staged,opts);if(opts.outWidth<=0||opts.outHeight<=0)throw new IllegalStateException("Staged JPEG preview unavailable");int sample=1;while(Math.max(opts.outWidth,opts.outHeight)/sample>640)sample*=2;opts.inSampleSize=sample;opts.inJustDecodeBounds=false;bitmap=android.graphics.BitmapFactory.decodeFile(staged,opts);if(bitmap==null)throw new IllegalStateException("Staged JPEG preview unavailable");
            }else{
                try(android.database.Cursor check=getContentResolver().query(Uri.parse(uri),new String[]{"_size","date_modified","is_pending"},null,null,null)){
                    if(check==null||!check.moveToFirst()||check.getLong(0)!=photo.getLong("size")||check.getLong(1)!=photo.getLong("modified")||check.getInt(2)!=0)throw new IllegalStateException("Source changed or is still exporting. Selection was not made.");
                }
                bitmap=getContentResolver().loadThumbnail(Uri.parse(uri),new Size(640,640),null);
            }
            final String mode=w.mode(config.get("scope"));final String target=w.active(config.get("scope"));final JSONObject targetGroup=w.group(config.get("scope"),target);final String targetLabel=targetGroup==null?"No receiving group":targetGroup.optString("label");final boolean showingOther=!java.util.Objects.equals(w.shown(config.get("scope")),target);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()){bitmap.recycle();return;}ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle(mode.equals("single")?"Create QR for this photo only":"Assign to "+targetLabel).setMessage(mode.equals("single")?"A NEW single-photo link will be shown after confirmation.":"Receive in: "+targetLabel+(showingOther?". You are showing another guest QR; it will stay on screen.":". Existing assignments stay unchanged.")).setView(image).setPositiveButton("Confirm",(d,n)->run(x->{if(!x.mode(config.get("scope")).equals(mode)||!java.util.Objects.equals(x.active(config.get("scope")),target))throw new IllegalStateException("Receiving selection changed. Reopen preview.");x.select(config.get("scope"),photo.getString("id"));say("Photo destination locked. QR remains the same while upload continues. Resume transfer if paused.");})).setNeutralButton("Hide from selection (keep archive)",(d,n)->run(x->x.skip(config.get("scope"),photo.getString("id")))).setNegativeButton("Cancel",null).create();
                dialog.setOnDismissListener(d->{image.setImageDrawable(null);bitmap.recycle();});dialog.show();
            });
        });
    }
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);io.shutdown();network.shutdown();thumbnails.shutdown();qr.stopLoading();qr.destroy();super.onDestroy();}
}
