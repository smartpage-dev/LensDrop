package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit staff recovery. Every mutation requires paused workers and the shared transfer lane. */
public final class RecoveryActivity extends Activity {
    private Config config;private String scope;private TextView status;private final ExecutorService io=Executors.newSingleThreadExecutor();
    private interface Task {void run()throws Exception;}
    @Override public void onCreate(Bundle b){super.onCreate(b);config=new Config(this);scope=config.get("scope");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,32,24,32);ScrollView view=new ScrollView(this);view.addView(box);setContentView(view);
        TextView heading=new TextView(this);heading.setText("Recovery / previous events");heading.setTextSize(24);box.addView(heading);
        TextView note=new TextView(this);note.setText("Retirement stops LOCAL retries/polling only. It does not cancel a cloud upload or a print accepted by a station. Originals, staged bytes, job IDs and guest assignments remain. Inspect the station before resolving uncertain paper output.");box.addView(note);
        add(box,"Pause photo and print workers",()->{config.active(false);config.prefs.edit().putBoolean("print_active",false).commit();stopService(new Intent(this,EventService.class));stopService(new Intent(this,PrintService.class));RecoveryJob.cancel(this);say("Paused. Wait for the current checkpoint before taking a recovery action.");});
        add(box,"Choose current / previous event record",this::scopes);
        add(box,"Review uploads / changed sources",()->uploads(0));
        add(box,"Review print requests (local status)",()->prints(0));
        add(box,"View preserved guest history (read-only)",()->groups(0));
        add(box,"Export PRIVATE recovery audit",()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"LensDrop-private-recovery-"+System.currentTimeMillis()+".json");startActivityForResult(i,40);});
        status=new TextView(this);status.setTextSize(16);box.addView(status);say("Inspecting event/device scope: "+scope);
    }
    private void add(LinearLayout box,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->action.run());box.addView(b);}
    private void say(String s){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(s);});}
    private void work(Task t){if(io.isShutdown())return;io.execute(()->{try{t.run();}catch(Exception e){say(e instanceof IllegalStateException?e.getMessage():"Action failed. Work and originals retained; check permissions/storage.");}});}
    private void guarded(Task t)throws Exception{
        if(config.active()||config.prefs.getBoolean("print_active",false)||UploadEngine.IN_FLIGHT.get()||PrintEngine.IN_FLIGHT.get())throw new IllegalStateException("Pause both workers and wait for the checkpoint first.");
        if(!TransferLane.enter())throw new IllegalStateException("Transfer checkpoint still in progress; try again after it stops.");
        try{if(config.active()||config.prefs.getBoolean("print_active",false))throw new IllegalStateException("Workers were resumed. Pause again.");t.run();}finally{TransferLane.leave();}
    }
    private void scopes(){work(()->{try(QueueDb q=new QueueDb(this);PrintQueue p=new PrintQueue(this)){JSONArray rows=RecoveryLedger.scopes(q,p);String[] items=new String[rows.length()];for(int n=0;n<items.length;n++)items[n]=rows.getString(n);runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Read-only event selection; does NOT change pairing").setItems(items,(d,n)->{scope=items[n];say("Inspecting: "+scope);}).setNegativeButton("Close",null).show());}});}
    private void uploads(int offset){final String selected=scope;work(()->{try(QueueDb q=new QueueDb(this)){JSONArray rows=RecoveryLedger.uploads(q,selected,offset);String[] items=new String[rows.length()+1];for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);items[n]=row.getString("name")+" / "+row.getString("state")+" / "+row.optString("source_state")+" / "+row.optString("error")+" / guest link: "+row.optString("delivery_error");}items[rows.length()]="Older uploads...";
        runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Original files are never deleted").setItems(items,(d,n)->{if(n==rows.length()){uploads(offset+50);return;}try{uploadActions(selected,rows.getJSONObject(n));}catch(Exception e){say("Record unavailable");}}).setNegativeButton("Close",null).show());}});}
    private void uploadActions(String selected,JSONObject row)throws Exception{
        final String id=row.getString("id");new AlertDialog.Builder(this).setTitle(row.getString("name")+" / "+row.getString("state")).setItems(new String[]{"Stop LOCAL retries (retain remote uncertainty)","Review changed source as NEW unassigned candidate"},(d,n)->{
            String text=n==0?"Stop local retries only? The cloud may already have received this job. No original or staged file is deleted. Existing QR assignment remains.":"Create a NEW candidate from the current file? First retire the old work unless it is already uploaded/skipped. Content is staged and checked twice. The new candidate inherits NO guest and must be previewed/selected again.";
            new AlertDialog.Builder(this).setMessage(text).setPositiveButton(n==0?"Retire locally":"Stage new candidate",(a,b)->work(()->guarded(()->{try(QueueDb q=new QueueDb(this)){if(n==0){RecoveryLedger.retireUpload(q,selected,id);say("Local retry stopped. Remote upload NOT cancelled. Export the audit before event turnover.");}else replace(q,selected,row);}}))).setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Close",null).show();
    }
    private void replace(QueueDb q,String selected,JSONObject old)throws Exception{
        if(!selected.equals(config.get("scope")))throw new IllegalStateException("Changed-source recovery requires the original active event. Previous-event records remain read-only.");
        WatchLedger w=new WatchLedger(q);MediaScanner scanner=new MediaScanner(this);
        if(!w.ready(selected)||!w.version(selected).equals(scanner.currentVersion()))throw new IllegalStateException("Photo provider identity changed. Do not rebind by filename; preserve the source and audit for reconciliation.");
        Uri uri=Uri.parse(old.getString("uri"));String name;long bytes,modified;
        try(Cursor c=getContentResolver().query(uri,new String[]{"_display_name","_size","date_modified","is_pending","relative_path"},null,null,null)){
            if(c==null||!c.moveToFirst()||c.getInt(3)!=0||!config.get("folder").equals(c.getString(4)))throw new IllegalStateException("Completed source is not in the original folder");name=c.getString(0);bytes=c.getLong(1);modified=c.getLong(2);
        }
        if(name==null||!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.jpe?g$")||bytes<4||bytes>64L*1024*1024)throw new IllegalStateException("Choose a completed JPEG up to 64 MiB");
        File dir=new File(getFilesDir(),"spool");if(!dir.exists()&&!dir.mkdirs())throw new IOException();long used=0;File[] all=dir.listFiles();if(all!=null)for(File f:all)if(f.isFile())used+=f.length();
        if(used+bytes>256L*1024*1024||new StatFs(dir.getAbsolutePath()).getAvailableBytes()<1024L*1024*1024+bytes)throw new IllegalStateException("Not enough staging space; old files were retained");
        String newId=UUID.randomUUID().toString();File tmp=new File(dir,newId+".part"),dest=new File(dir,newId+".jpg");boolean saved=false;
        try{
            String sha=copyHash(uri,tmp,bytes);JpegStructure.validate(tmp);
            if(!sha.equals(copyHash(uri,null,bytes)))throw new IllegalStateException("Source changed while reading; no candidate created");
            try(Cursor c=getContentResolver().query(uri,new String[]{"_size","date_modified","is_pending"},null,null,null)){if(c==null||!c.moveToFirst()||c.getLong(0)!=bytes||c.getLong(1)!=modified||c.getInt(2)!=0)throw new IllegalStateException("Source changed; no candidate created");}
            Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE);DirectorySync.sync(dir);
            if(!selected.equals(config.get("scope"))||config.active()||config.prefs.getBoolean("print_active",false)||!w.version(selected).equals(scanner.currentVersion()))throw new IllegalStateException("Setup or provider changed during recovery. No candidate created; pause and review again.");
            RecoveryLedger.replacement(q,selected,old.getString("id"),name,bytes,modified,sha,dest.getAbsolutePath(),newId);saved=true;say("New UNASSIGNED candidate saved. Review its preview and explicitly select the intended guest. Old work remains unchanged.");
        }finally{tmp.delete();if(!saved)dest.delete();}
    }
    private String copyHash(Uri uri,File output,long expected)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
        try(InputStream input=getContentResolver().openInputStream(uri);FileOutputStream out=output==null?null:new FileOutputStream(output)){
            if(input==null)throw new IOException();byte[] buf=new byte[65536];int n;while((n=input.read(buf))!=-1){total+=n;if(total>expected)throw new IllegalStateException("Source grew while reading");digest.update(buf,0,n);if(out!=null)out.write(buf,0,n);}if(out!=null){out.flush();out.getFD().sync();}
        }if(total!=expected)throw new IllegalStateException("Source size changed");return CloudApi.hex(digest.digest());
    }
    private void prints(int offset){final String selected=scope;work(()->{try(PrintQueue q=new PrintQueue(this)){JSONArray rows=q.history(selected,offset);String[] items=new String[rows.length()+1];for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);items[n]=row.getString("id")+" / "+row.getString("state")+" / remote: "+row.getString("remote_state");}
        items[rows.length()]="Older print records...";runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Print history; station may still print").setItems(items,(d,n)->{if(n==rows.length()){prints(offset+50);return;}try{final String id=rows.getJSONObject(n).getString("id");new AlertDialog.Builder(this).setMessage("STOP LOCAL POLLING ONLY. This is NOT a cancellation. Check the cloud/station and physical paper. The staged file, job ID and remote outcome remain in history.").setPositiveButton("Retire locally",(a,b)->work(()->guarded(()->{try(PrintQueue p=new PrintQueue(this)){RecoveryLedger.retirePrint(p,selected,id);say("Local polling stopped; remote print NOT cancelled. Export audit and review at the station.");}}))).setNegativeButton("Cancel",null).show();}catch(Exception e){say("Record unavailable");}}).setNegativeButton("Close",null).show());}});}
    private void groups(int offset){final String selected=scope;work(()->{try(QueueDb q=new QueueDb(this)){JSONArray rows=new WatchLedger(q).history(selected,offset);String[] items=new String[rows.length()+1];for(int n=0;n<rows.length();n++){JSONObject g=rows.getJSONObject(n);items[n]=g.optString("label")+" / "+g.optString("kind")+" / "+g.optString("state");}items[rows.length()]="Older history...";runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Preserved guest identities - no receiver change").setItems(items,(d,n)->{if(n==rows.length()){groups(offset+50);return;}try{JSONObject g=rows.getJSONObject(n);say(g.toString(2));}catch(Exception e){say("Group unavailable");}}).setNegativeButton("Close",null).show());}});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=40||result!=RESULT_OK||data==null||data.getData()==null)return;final Uri uri=data.getData();work(()->{try(QueueDb q=new QueueDb(this);PrintQueue p=new PrintQueue(this);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(RecoveryLedger.auditReport(q,p).toString(2).getBytes(StandardCharsets.UTF_8));say("PRIVATE audit exported. Keep it with the event backup; do not publish it.");}});}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
