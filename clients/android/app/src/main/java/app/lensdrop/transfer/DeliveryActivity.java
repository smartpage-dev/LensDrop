package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
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
public final class DeliveryActivity extends Activity {
    private Config config;private TextView summary,message;private WebView qr;private String qrSignature=null;private CheckBox automatic;private Spinner modes;
    private boolean binding;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean preparing=new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,5000);}};
    private interface Task{void run(WatchLedger w)throws Exception;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);config=new Config(this);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
        TextView title=new TextView(this);title.setText("LensDrop / Guest QR");title.setTextSize(24);body.addView(title);
        summary=new TextView(this);summary.setTextSize(17);body.addView(summary);
        qr=new WebView(this);qr.getSettings().setJavaScriptEnabled(true);qr.getSettings().setAllowFileAccess(false);qr.getSettings().setAllowContentAccess(false);qr.getSettings().setBlockNetworkLoads(true);qr.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,String url){return true;}});
        body.addView(qr,new LinearLayout.LayoutParams(-1,(int)(320*getResources().getDisplayMetrics().density)));
        message=new TextView(this);message.setTextSize(14);body.addView(message);
        modes=new Spinner(this);modes.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Group QR / multiple photographs","Single selected photo QR"}));body.addView(modes);
        try(QueueDb initial=new QueueDb(this)){modes.setSelection(new WatchLedger(initial).mode(config.get("scope")).equals("single")?1:0);}catch(Exception ignored){}
        modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int n,long id){if(binding)return;run(w->{w.mode(config.get("scope"),n==0?"group":"single");say("Mode changed for future selections only. Existing assignments are retained.");});}});
        button(body,"Prepare 3 group + 3 single QR links (online)",v->prepare());
        button(body,"Next Group",v->run(w->{w.nextGroup(config.get("scope"));say("New group ready. Automatic assignment is OFF; review new candidates.");}));
        button(body,"Undo Next Group",v->new AlertDialog.Builder(this).setTitle("Restore previous receiving group?").setMessage("Existing assigned photos will never move. An empty accidental QR is retired only if no ticket snapshot exists. Ticket-issued and nonempty groups remain in history.").setPositiveButton("Restore",(d,n)->run(w->say(w.undo(config.get("scope"))))).setNegativeButton("Cancel",null).show());
        button(body,"Candidates / choose best photo",v->candidates(0));
        button(body,"Skip all remaining candidates (keep originals)",v->new AlertDialog.Builder(this).setMessage("These unselected candidates will not upload. Source photos remain untouched. Existing selected/queued uploads are not changed.").setPositiveButton("Skip candidates",(d,n)->run(w->w.skip(config.get("scope"),null))).setNegativeButton("Cancel",null).show());
        button(body,"History / show a previous QR",v->history(0));
        button(body,"Show current receiving group",v->run(w->{String id=w.active(config.get("scope"));if(id==null)throw new IllegalStateException("Start a group first");w.show(config.get("scope"),id);}));
        automatic=new CheckBox(this);automatic.setText("Auto-assign new arrivals to current group (TRUSTED ORDER ONLY)");body.addView(automatic);
        automatic.setOnCheckedChangeListener((b,checked)->{if(binding)return;if(!checked){run(w->w.auto(config.get("scope"),false));return;}
            binding=true;automatic.setChecked(false);binding=false;
            new AlertDialog.Builder(this).setTitle("Only for a known ordered source").setMessage("A folder watcher cannot identify a delayed camera or editor result. Keep this OFF for external editors or uncertain arrival order. It resets at Next Group / Resume / mode changes. Recovered arrivals still require confirmation.").setPositiveButton("Enable for this group",(d,n)->run(w->{if(w.active(config.get("scope"))==null||!w.mode(config.get("scope")).equals("group"))throw new IllegalStateException("Select Group mode and start a group first");w.auto(config.get("scope"),true);})).setNegativeButton("Keep safe confirmation",null).show();});
        TextView note=new TextView(this);note.setText("Showing history does not change the receiving group. Guest links need cloud approval unless the administrator explicitly enables personal auto-release. Print selected JPEGs through the separate H5 Print screen. Direct camera control is not implemented.");body.addView(note);
        button(body,"Print a selected photo",v->startActivity(new android.content.Intent(this,PrintActivity.class)));
        button(body,"Print SHOWN guest QR ticket (Bluetooth)",v->startActivity(new android.content.Intent(this,TicketActivity.class)));
        button(body,"Back to transfer controls",v->finish());
        if(config.get("scope").isEmpty()){say("Pair the phone in Transfer controls first.");return;}
        run(w->w.ensure(config.get("scope"),config.get("folder")));
    }
    private void button(LinearLayout p,String text,View.OnClickListener action){Button b=new Button(this);b.setText(text);b.setOnClickListener(action);p.addView(b);}
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
                String text="Receiving: "+(s.getString("mode").equals("single")?"single-photo candidates":receiving==null?"no group":receiving.getString("label"))+"\nShowing QR: "+(shown==null?"none":shown.getString("label"))+"\nExcluded at start: "+s.optLong("excluded")+" / Candidates: "+s.optLong("candidate")+" / Verified uploads: "+s.optLong("uploaded")+"\nChanged source files needing attention: "+s.optLong("changed_sources");
                String link=shown!=null&&shown.optString("state").equals("open")&&!shown.optString("guest_path").isEmpty()?config.get("base")+shown.getString("guest_path"):"";
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;summary.setText(text);binding=true;int pos=s.optString("mode").equals("single")?1:0;if(modes.getSelectedItemPosition()!=pos)modes.setSelection(pos);automatic.setChecked(s.optBoolean("auto_assign"));binding=false;renderQr(link);});
            }catch(Exception ignored){}
        });
    }
    private void renderQr(String link){
        if(java.util.Objects.equals(link,qrSignature))return;qrSignature=link;
        try{
            String js;try(java.io.InputStream in=getAssets().open("qr.js")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)bytes.write(block,0,n);js=new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
            String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;display:grid;place-items:center;min-height:95vh;font:16px sans-serif}svg{width:min(85vw,300px);height:auto}</style><div id='q'></div><script>"+js+"</script><script>document.getElementById('q')."+(link.isEmpty()?"textContent='Prepare QR links, then choose a group or photograph.'":"innerHTML=LensDropQR.toSvg("+JSONObject.quote(link)+")")+";</script>";
            qr.loadDataWithBaseURL("https://lensdrop.invalid/",html,"text/html","UTF-8",null);
        }catch(Exception e){say("QR renderer unavailable. Use the saved guest link in Admin; do not change group identities.");}
    }
    private void history(int offset){
        run(w->{JSONArray rows=w.history(config.get("scope"),offset);String[] choices=new String[rows.length()+1];for(int i=0;i<rows.length();i++){JSONObject g=rows.getJSONObject(i);choices[i]=g.getString("label")+" / "+g.getString("kind")+" / "+g.getString("state");}choices[rows.length()]="Older history...";
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("History: show is display-only").setItems(choices,(dialog,n)->{if(n==rows.length()){history(offset+50);return;}try{JSONObject g=rows.getJSONObject(n);String id=g.getString("id");String[] actions=g.getString("kind").equals("group")?new String[]{"Show QR only","Resume this group for new photos"}:new String[]{"Show QR only"};new AlertDialog.Builder(this).setTitle(g.getString("label")).setItems(actions,(d,action)->{if(action==0)run(x->x.show(config.get("scope"),id));else new AlertDialog.Builder(this).setMessage("Change receiving group? Existing photo assignments stay unchanged.").setPositiveButton("Resume group",(dd,nn)->run(x->x.resume(config.get("scope"),id))).setNegativeButton("Cancel",null).show();}).show();}catch(Exception e){say("Could not read history entry.");}}).setNegativeButton("Close",null).show());
        });
    }
    private void candidates(int offset){
        run(w->{JSONArray rows=w.candidates(config.get("scope"),offset);String[] labels=new String[rows.length()+1];for(int i=0;i<rows.length();i++)labels[i]=rows.getJSONObject(i).getString("name");labels[rows.length()]="Older candidates...";
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Select a received photo (not uploaded yet)").setItems(labels,(dialog,n)->{if(n==rows.length()){candidates(offset+50);return;}try{preview(rows.getJSONObject(n));}catch(Exception e){say("Candidate unavailable.");}}).setNegativeButton("Close",null).show());
        });
    }
    private void preview(JSONObject photo){
        run(w->{String uri=photo.getString("uri");
            try(android.database.Cursor check=getContentResolver().query(Uri.parse(uri),new String[]{"_size","date_modified","is_pending"},null,null,null)){
                if(check==null||!check.moveToFirst()||check.getLong(0)!=photo.getLong("size")||check.getLong(1)!=photo.getLong("modified")||check.getInt(2)!=0)throw new IllegalStateException("Source changed or is still exporting. Selection was not made.");
            }
            Bitmap bitmap=getContentResolver().loadThumbnail(Uri.parse(uri),new Size(640,640),null);
            final String mode=w.mode(config.get("scope"));final String target=w.active(config.get("scope"));
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()){bitmap.recycle();return;}ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap(bitmap);
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle(mode.equals("single")?"Create QR for this photo only":"Assign to receiving group").setView(image).setPositiveButton("Confirm",(d,n)->run(x->{if(!x.mode(config.get("scope")).equals(mode)||!java.util.Objects.equals(x.active(config.get("scope")),target))throw new IllegalStateException("Receiving selection changed. Reopen preview.");x.select(config.get("scope"),photo.getString("id"));say("Photo destination locked. QR remains the same while upload continues. Resume transfer if paused.");})).setNeutralButton("Skip (keep original)",(d,n)->run(x->x.skip(config.get("scope"),photo.getString("id")))).setNegativeButton("Cancel",null).create();
                dialog.setOnDismissListener(d->{image.setImageDrawable(null);bitmap.recycle();});dialog.show();
            });
        });
    }
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);io.shutdown();network.shutdown();qr.stopLoading();qr.destroy();super.onDestroy();}
}
