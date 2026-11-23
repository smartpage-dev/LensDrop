package app.lensdrop.transfer;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.os.StatFs;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

final class UploadEngine implements AutoCloseable {
    static final AtomicBoolean IN_FLIGHT=new AtomicBoolean(false);
    static final int CHUNK=1024*1024;
    final Config config;final QueueDb queue;
    private final String boundScope;private final Context context;private final MediaScanner scanner;private final CloudApi api;
    private long lastScan,lastHeartbeat,lastDeliverySync;volatile boolean stopped,dirty=true;
    UploadEngine(Context context)throws Exception{
        this.context=context.getApplicationContext();config=new Config(context);queue=new QueueDb(context);scanner=new MediaScanner(context);
        boundScope=config.get("scope");
        api=new CloudApi(context,config.get("base"),config.token(),config.cellular());
    }
    void tick(){
        if(stopped||!config.active()||!boundScope.equals(config.get("scope"))||!TransferLane.enter())return;
        if(!IN_FLIGHT.compareAndSet(false,true)){TransferLane.leave();return;}
        try{
            queue.recover();
            try(android.database.Cursor old=queue.getReadableDatabase().rawQuery("SELECT id,spool FROM jobs WHERE state='uploaded' AND spool IS NOT NULL",null)){
                while(old.moveToNext()){
                    File f=new File(old.getString(1));File owner=new File(context.getFilesDir(),"spool");
                    if(f.getCanonicalFile().getParentFile().equals(owner.getCanonicalFile())&&(!f.exists()||f.delete())){
                        ContentValues cleared=new ContentValues();cleared.putNull("spool");queue.update(old.getString(0),cleared);
                    }
                }
            }
            if(!config.get("folder").isEmpty()&&!config.prefs.getBoolean("drain",false)&&config.get("test_only").isEmpty()&&((dirty&&System.currentTimeMillis()-lastScan>=5000)||System.currentTimeMillis()-lastScan>30000)){scanner.scan(config,queue);lastScan=System.currentTimeMillis();dirty=false;}
            if(!config.get("folder").isEmpty()&&config.get("test_only").isEmpty())scanner.settle(config,queue);
            if(System.currentTimeMillis()<config.prefs.getLong("cooldown",0)){config.status("Waiting before a shared network/server retry; queue retained.");return;}
            String gate=gate();if(gate!=null){config.status(gate);return;}
            if(System.currentTimeMillis()-lastDeliverySync>30000){lastDeliverySync=System.currentTimeMillis();try{DeliveryCoordinator.sync(queue,boundScope,api);}catch(Exception ignored){}}
            if(config.get("test_only").isEmpty())DeliveryCoordinator.syncAssignments(queue,boundScope,api);
            String testId=config.get("test_only");
            QueueDb.Job job=testId.isEmpty()?queue.due(boundScope):queue.testDue(boundScope,testId);
            if(!testId.isEmpty()&&queue.jobState(boundScope,testId).equals("uploaded")){
                JSONObject check=api.call("/v1/device/preflight?client_id="+testId,null);
                config.prefs.edit().putString("preflight_report",check.toString()).commit();reportHeartbeat(true);config.active(false);config.status("Preflight cloud receipt verified. Test stays private. Camera-card completeness is not tested.");return;
            }
            if(!testId.isEmpty()&&queue.jobState(boundScope,testId).equals("blocked")){reportHeartbeat(true);config.active(false);config.status("Preflight needs attention. Export diagnostics; the test and queue are retained.");return;}
            if(config.prefs.getBoolean("drain",false)&&job==null){
                JSONObject totals=queue.snapshot(boundScope).getJSONObject("production"),tests=queue.snapshot(boundScope).getJSONObject("preflight");
                if(totals.optLong("queued")+tests.optLong("queued")==0&&!queue.assignmentRetryPending(boundScope)){reportHeartbeat(true);config.active(false);config.status("Archive queue drained or blocked. Review guest-link status, unassigned selections and failures separately; keep originals.");return;}
            }
            if(job==null)config.status("Watching selected folder. No upload due.");else transfer(job);
            reportHeartbeat(!config.active());
        }catch(IllegalStateException e){config.active(false);config.status(e.getMessage()+" Queue retained.");}
        catch(SecurityException e){config.status("Photo permission required. Open LensDrop and grant access.");}
        catch(Exception e){config.status("Queue retained. Reopen app to inspect permissions or storage.");}
        finally{IN_FLIGHT.set(false);TransferLane.leave();}
    }
    private void reportHeartbeat(boolean force)throws Exception{
        if(!force&&System.currentTimeMillis()-lastHeartbeat<=30000)return;
        JSONObject snapshot=queue.snapshot(boundScope),h=snapshot.getJSONObject("production"),tests=snapshot.getJSONObject("preflight");
        h.put("last_scan",lastScan);h.put("scan_paused",config.prefs.getBoolean("drain",false)||!config.get("test_only").isEmpty()?1:0);
        h.put("test_received",tests.optLong("received"));h.put("test_uploaded",tests.optLong("uploaded"));h.put("test_pending",tests.optLong("queued")+tests.optLong("blocked"));
        try{api.call("/v1/device/heartbeat",h);JSONObject remote=api.call("/v1/device/summary",null);JSONObject safe=new JSONObject();for(String k:new String[]{"verified_photos","verified_uploads","live","review","hidden","personal_available","incomplete","reconciled","selection_accounted_for","heartbeat_fresh"})safe.put(k,remote.opt(k));safe.put("fetched_at",System.currentTimeMillis());config.prefs.edit().putString("cloud_snapshot",safe.toString()).apply();}catch(Exception ignored){}
        lastHeartbeat=System.currentTimeMillis();
    }
    private String gate(){
        if(stopped||!config.active())return "Paused by user";
        PowerManager power=context.getSystemService(PowerManager.class);
        if(power.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)return "Paused: phone is too warm. Queue retained.";
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null){
            int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,100),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            boolean charging=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
            if(!charging&&scale>0&&level*100/scale<=15)return "Paused: battery 15% or below.";
            if(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)>=430)return "Paused: battery temperature at least 43 C.";
        }
        return null;
    }
    private static final class Staged {
        final File file; final String sha;
        Staged(File file,String sha){this.file=file;this.sha=sha;}
    }
    private Staged stage(QueueDb.Job j)throws Exception{
        if(j.spool!=null&&j.sha!=null){File f=new File(j.spool);if(f.isFile()&&f.length()==j.size)return new Staged(f,j.sha);}
        if(!scanner.unchanged(j))throw new CloudApi.Failure("source_missing_or_changed",false,0);
        File dir=new File(context.getFilesDir(),"spool");if(!dir.exists()&&!dir.mkdirs())throw new CloudApi.Failure("spool_unavailable",true,60000);
        File target=new File(dir,j.id+".jpg"),temp=new File(dir,j.id+".part");
        if(temp.exists()&&!temp.delete())throw new CloudApi.Failure("staging_cleanup_failed",true,60000);
        if(target.exists()&&!target.delete())throw new CloudApi.Failure("staging_cleanup_failed",true,60000);
        long used=0;File[] stagedFiles=dir.listFiles();if(stagedFiles!=null)for(File f:stagedFiles)if(f.isFile()&&!f.equals(target))used+=f.length();
        if(used+j.size>256L*1024*1024)throw new CloudApi.Failure("staging_limit_reached",true,60000);
        if(new StatFs(dir.getAbsolutePath()).getAvailableBytes()<1024L*1024*1024+j.size)throw new CloudApi.Failure("phone_storage_low",true,60000);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
        try{
            try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(j.uri));FileOutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new CloudApi.Failure("source_missing",false,0);
                byte[] block=new byte[64*1024];int n;
                while((n=in.read(block))!=-1){if(stopped||!config.active())throw new CloudApi.Failure("paused",true,1000);size+=n;if(size>j.size)throw new CloudApi.Failure("source_changed",false,0);digest.update(block,0,n);out.write(block,0,n);}
                out.flush();out.getFD().sync();
            }
            if(size!=j.size||!scanner.unchanged(j))throw new CloudApi.Failure("source_changed",false,0);
            try{JpegStructure.validate(temp);}catch(JpegStructure.Invalid e){throw new CloudApi.Failure(e.code,e.incomplete,5000);}
            Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            DirectorySync.sync(dir);
            String sha=CloudApi.hex(digest.digest());ContentValues v=new ContentValues();v.put("spool",target.getAbsolutePath());v.put("sha",sha);queue.update(j.id,v);return new Staged(target,sha);
        }catch(java.io.IOException e){throw new CloudApi.Failure("source_read_retry",true,5000);}finally{if(temp.exists())temp.delete();}
    }
    private void transfer(QueueDb.Job j){
        try{
            if(!j.scope.equals(boundScope))throw new CloudApi.Failure("queue_scope_mismatch",false,0);
            if(j.delivery!=null&&!j.archivePrivate&&j.upload==null){JSONObject ident=api.call("/v1/device",null);if(ident.optJSONObject("capabilities")==null||!ident.getJSONObject("capabilities").optBoolean("personal_delivery_v1"))throw new CloudApi.Failure("personal_delivery_requires_h4",false,0);}
            if(j.isTest&&j.upload==null){JSONObject identity=api.call("/v1/device",null);if(identity.optJSONObject("capabilities")==null||!identity.getJSONObject("capabilities").optBoolean("isolated_preflight"))throw new CloudApi.Failure("preflight_requires_h2_server",false,0);}
            if(j.archivePrivate&&j.upload==null){JSONObject identity=api.call("/v1/device",null);if(identity.optJSONObject("capabilities")==null||!identity.getJSONObject("capabilities").optBoolean("archive_delivery_v1"))throw new CloudApi.Failure("private_archive_requires_h55",false,0);}
            config.status("Transferring one photograph; other files remain queued.");
            Staged staged=stage(j);File file=staged.file;String sha=staged.sha;
            queue.state(j.id,"uploading",null,j.attempts,0);
            JSONObject r;
            if(j.upload==null){JSONObject body=new JSONObject();body.put("client_id",j.id);body.put("filename",j.name);body.put("size",j.size);body.put("sha256",sha);body.put("is_test",j.isTest);if(j.archivePrivate)body.put("archive_private",true);else if(j.delivery!=null)body.put("delivery_id",j.delivery);r=api.call("/v1/uploads",body);}
            else r=api.call("/v1/uploads/"+j.upload,null);
            if(j.archivePrivate&&!r.optBoolean("archive_private"))throw new CloudApi.Failure("private_archive_not_acknowledged",false,0);
            if(!j.archivePrivate&&j.delivery!=null&&!j.delivery.equals(r.optString("delivery_id")))throw new CloudApi.Failure("delivery_identity_mismatch",false,0);
            if(j.isTest&&!r.optBoolean("is_test"))throw new CloudApi.Failure("preflight_requires_h2_server",false,0);
            if(r.getLong("size")!=j.size||!r.getString("sha256").equals(sha))throw new CloudApi.Failure("server_identity_mismatch",false,0);
            String uid=r.getString("upload_id");ContentValues update=new ContentValues();update.put("upload",uid);update.put("offset",r.getLong("offset"));update.put("last_progress",System.currentTimeMillis());queue.update(j.id,update);
            try(RandomAccessFile in=new RandomAccessFile(file,"r")){
                while(!r.getString("state").equals("complete")&&r.getLong("offset")<j.size){
                    String guard=gate();if(guard!=null)throw new CloudApi.Failure("resource_or_user_pause",true,15000);
                    long offset=r.getLong("offset");if(offset<0||offset%CHUNK!=0)throw new CloudApi.Failure("invalid_server_offset",false,0);
                    byte[] bytes=new byte[(int)Math.min(CHUNK,j.size-offset)];in.seek(offset);in.readFully(bytes);
                    r=api.chunk("/v1/uploads/"+uid+"/chunks?offset="+offset,bytes);
                    if(r.getLong("offset")<=offset)throw new CloudApi.Failure("server_no_progress",true,5000);
                    update.clear();update.put("offset",r.getLong("offset"));update.put("last_progress",System.currentTimeMillis());queue.update(j.id,update);
                }
            }
            if(!r.getString("state").equals("complete"))r=api.call("/v1/uploads/"+uid+"/complete",new JSONObject());
            JSONObject receipt=r.getJSONObject("receipt");
            if(!receipt.getString("sha256").equals(sha)||receipt.getLong("bytes")!=j.size||receipt.optString("photo_id").isEmpty())throw new CloudApi.Failure("receipt_mismatch",false,0);
            update.clear();update.put("state","uploaded");update.put("offset",j.size);update.putNull("error");update.put("receipt",receipt.toString());update.put("photo_id",receipt.getString("photo_id"));update.put("last_progress",System.currentTimeMillis());queue.update(j.id,update);
            // Remove only this app's duplicate spool after the durable cloud receipt.
            // The vendor-app/MediaStore original is NEVER deleted.
            if(file.delete()){ContentValues cleared=new ContentValues();cleared.putNull("spool");queue.update(j.id,cleared);}config.status("Cloud receipt verified. Original retained on phone.");
        }catch(CloudApi.Failure e){
            int attempts=j.attempts+1;long cap=Math.min(60000,1000L*(1L<<Math.min(attempts,6)));
            long delay=Math.max(e.retryAfter,ThreadLocalRandom.current().nextLong(1000,Math.max(1001,cap+1)));
            boolean retry=UploadRetryPolicy.retry(e.code,e.retry,j.fileFailures);
            if(UploadRetryPolicy.fileLocal(e.code)){ContentValues local=new ContentValues();local.put("file_failures",j.fileFailures+1);queue.update(j.id,local);}
            if(UploadRetryPolicy.sharedCooldown(e.code,retry))config.prefs.edit().putLong("cooldown",System.currentTimeMillis()+Math.min(60000,Math.max(2000,delay))).commit();
            if(e.code.equals("device_token_invalid_or_revoked")||e.code.equals("device_revoked"))config.active(false);
            queue.state(j.id,retry?"retry":"blocked",e.code,attempts,System.currentTimeMillis()+delay);config.status(retry?"Retry queued: "+e.code:"Action needed: "+e.code);
        }catch(SecurityException e){queue.state(j.id,"blocked","photo_permission_required",j.attempts,System.currentTimeMillis());}
        catch(Exception e){config.prefs.edit().putLong("cooldown",System.currentTimeMillis()+30000).commit();queue.state(j.id,"retry","network_or_io_error",j.attempts+1,System.currentTimeMillis()+30000);config.status("Connection or storage interrupted; queue retained.");}
    }
    @Override public void close(){stopped=true;api.close();queue.close();}
}
