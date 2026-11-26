package app.lensdrop.transfer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;

/** Single-page one-touch photo queue. This sends to the existing authorized
 * Windows station through PrintService; it does NOT claim that paper exited.
 * No photo-gallery navigation, implicit guest reassignment, or photo-print retry ID.
 */
final class SinglePhotoPrint {
    private SinglePhotoPrint() {}

    static JSONObject setup(Config config)throws Exception {
        JSONObject setup=new JSONObject(config.secret("print_setup"));
        if(setup.getLong("expires")<=System.currentTimeMillis())
            throw new IllegalStateException("Print Station grant expired. Refresh station in Print Photo setup first.");
        if(config.get("scope").split("\\|",-1).length!=3)
            throw new IllegalStateException("Pair an event and station first.");
        if(setup.optInt("max_copies",0)<1)
            throw new IllegalStateException("Photo printing is not authorized for this device.");
        if(setup.getJSONObject("station").getJSONArray("profiles").length()==0)
            throw new IllegalStateException("No station print profiles. Configure the Windows station first.");
        return setup;
    }

    static JSONObject profile(JSONObject setup,String id)throws Exception {
        JSONArray all=setup.getJSONObject("station").getJSONArray("profiles");
        for(int i=0;i<all.length();i++){
            JSONObject p=all.getJSONObject(i);
            if(p.getString("id").equals(id))return new JSONObject(p.toString());
        }
        throw new IllegalStateException("Quick print profile changed. Choose a valid profile again.");
    }

    /** Call from an IO executor, after verifying the shown job and chosen profile.
     * Reprint is a new, explicitly approved print job; it is NEVER a retry of the
     * first print ID with an unknown physical outcome. */
    static String enqueue(Context context,Config config,String scope,String sourceJob,
                          JSONObject profile,boolean explicitReprint)throws Exception {
        if(!config.get("scope").equals(scope))throw new IllegalStateException("Event changed. Select the photo again.");
        JSONObject setup=setup(config);
        profile=profile(setup,profile.getString("id"));
        final String originalId=SingleQuickPrintRules.firstPhotoPrintId(scope,sourceJob);
        final String id=explicitReprint?UUID.randomUUID().toString():originalId;
        try(PrintQueue queue=new PrintQueue(context)){
            if(queue.get(scope,originalId)!=null&&!explicitReprint)
                throw new IllegalStateException("This photo already has a quick-print job. Inspect its outcome before another print.");
        }
        Uri uri;
        try(QueueDb q=new QueueDb(context);Cursor c=q.getReadableDatabase().rawQuery(
              "SELECT uri,is_test,state FROM jobs WHERE scope=? AND id=? LIMIT 1",new String[]{scope,sourceJob})){
            if(!c.moveToFirst()||c.getInt(1)!=0||"skipped".equals(c.getString(2))||"retired_local".equals(c.getString(2)))
                throw new IllegalStateException("The selected photo is no longer available for printing.");
            uri=Uri.parse(c.getString(0));
        }
        if(!"content".equals(uri.getScheme()))
            throw new IllegalStateException("Original JPEG not stored on this phone. Use the Cloud Staff Photo Desk.");
        String[] s=scope.split("\\|",-1);
        JSONObject station=setup.getJSONObject("station");
        if(!s[1].equals(station.optString("event_id")))
            throw new IllegalStateException("Station belongs to another event. Refresh Print Station setup first.");
        final String mode="cloud".equals(config.get("event_print_route"))?"cloud":"hybrid";
        // With LAN-first mode a reachable, unpaused station is a precondition
        // for the quick action. Cloud mode is an explicitly authorized queue.
        if(!"cloud".equals(mode)){
            JSONObject info=new LanPrintApi(context,setup).call("/v1/info",null);
            if(info.optBoolean("paused"))throw new IllegalStateException("Windows Print Station is paused. No new print requested.");
        }else{
            // A cloud print job can outlive the user's tap. For this one-tap
            // action require a recently connected station, not just a saved grant.
            try(CloudApi api=new CloudApi(context,config.get("base"),config.token(),config.cellular())){
                JSONObject live=api.call("/v1/device/print-config",null);
                JSONObject perms=live.optJSONObject("permissions"),remote=live.optJSONObject("station");
                if(perms==null||!perms.optBoolean("print")||remote==null||
                   !station.getString("id").equals(remote.optString("id"))||remote.optBoolean("paused")||
                   remote.optBoolean("revoked")||Math.abs(System.currentTimeMillis()-remote.optLong("last_seen",0))>90000)
                    throw new IllegalStateException("Cloud Print Station not connected and ready. No new photo job queued.");
            }
        }
        File dir=new File(context.getFilesDir(),"print-stage");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Print staging folder unavailable.");
        long used=0;File[] staged=dir.listFiles();if(staged!=null)for(File f:staged)if(f.isFile())used+=f.length();
        if(used>224L*1024*1024||dir.getUsableSpace()<1024L*1024*1024)
            throw new IllegalStateException("Phone print storage limit reached. Complete pending prints first.");
        File target=new File(dir,id+".jpg");boolean committed=false;
        try{
            long count=0;MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=context.getContentResolver().openInputStream(uri);
                FileOutputStream out=new FileOutputStream(target)){
                if(in==null)throw new IllegalStateException("Original JPEG is inaccessible.");
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){count+=n;if(count>32L*1024*1024)throw new IllegalStateException("JPEG over 32 MiB. Print from the Staff Desk.");digest.update(buf,0,n);out.write(buf,0,n);}
                out.getFD().sync();
            }
            JpegStructure.validate(target);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(target.getPath(),bounds);
            if(count==0||!"image/jpeg".equals(bounds.outMimeType)||bounds.outWidth<=0||bounds.outHeight<=0
                 ||(long)bounds.outWidth*bounds.outHeight>60L*1024*1024)
                throw new IllegalStateException("Valid JPEG up to 60 megapixels required.");
            PrintProfilePolicy.validateSource(profile.getString("id"),bounds.outWidth,bounds.outHeight);
            JSONObject spec=new JSONObject()
                 .put("id",id).put("event_id",s[1]).put("device_id",s[2])
                 .put("station_id",station.getString("id"))
                 .put("sha256",CloudApi.hex(digest.digest())).put("bytes",count)
                 .put("profile_id",profile.getString("id")).put("profile_revision",profile.getInt("revision"))
                 .put("copies",1);
            // Existing archive workflow retains the original guest association.
            // A quick photo print does not silently assign a new guest.
            long end=System.currentTimeMillis()+20000;
            while(!TransferLane.enter()){
                if(System.currentTimeMillis()>end||!config.get("scope").equals(scope))
                    throw new IllegalStateException("Photo archive busy. No new print queued; retry when transfer finishes.");
                Thread.sleep(100);
            }
            String archiveId;
            try{archiveId=ArchiveStaging.register(context,scope,target,spec,null);}
            finally{TransferLane.leave();}
            if(!config.get("scope").equals(scope))throw new IllegalStateException("Event changed; print not queued.");
            try(PrintQueue q=new PrintQueue(context)){
                if(q.get(scope,id)!=null)throw new IllegalStateException("This print job already exists; inspect the queue.");
                q.add(scope,spec,target.getAbsolutePath(),mode,archiveId);
            }
            committed=true;
            return id;
        }finally{if(!committed)target.delete();}
    }
}
