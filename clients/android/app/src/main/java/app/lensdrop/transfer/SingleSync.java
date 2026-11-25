package app.lensdrop.transfer;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;
/** Reconciles immutable verified cloud receipts to single QR identities, one job per tick.
 * Network failures never alter upload state or source/group assignments. */
final class SingleSync {
    static void one(QueueDb q,String scope,CloudApi api)throws Exception {
        // Register the immutable local token before uploading any JPEG bytes. A rejected
        // registration is retried independently and never changes the token or queue state.
        try(Cursor pre=q.getReadableDatabase().rawQuery("SELECT id,name,size,early_token,early_attempts FROM jobs WHERE scope=? AND early_token IS NOT NULL AND early_registered=0 AND is_test=0 AND early_next<=? AND state NOT IN ('skipped','retired_local') ORDER BY created,id LIMIT 1",new String[]{scope,Long.toString(System.currentTimeMillis())})){
            if(pre.moveToFirst()){
                String id=pre.getString(0),name=pre.getString(1),token=pre.getString(3);int attempts=pre.getInt(4);
                try{
                    JSONObject b=new JSONObject().put("client_id",id).put("filename",name).put("size",pre.getLong(2)).put("token",token);
                    JSONObject r=api.call("/v1/device/single-preissue",b);
                    if(!"single".equals(r.getString("kind"))||!InstantQr.path(token).equals(r.getString("guest_path")))throw new IllegalStateException("Early QR cloud identity mismatch");
                    ContentValues values=new ContentValues();values.put("single_id",r.getString("id"));values.put("single_label",r.getString("label"));values.put("single_path",r.getString("guest_path"));values.put("early_registered",1);values.put("early_next",0);q.update(id,values);
                }catch(Exception e){
                    boolean retry=!(e instanceof CloudApi.Failure)||((CloudApi.Failure)e).retry;
                    ContentValues values=new ContentValues();values.put("early_attempts",attempts+1);
                    values.put("early_next",retry?System.currentTimeMillis()+Math.min(60000L,2000L*(1L<<Math.min(5,attempts))):Long.MAX_VALUE);q.update(id,values);
                }
                return;
            }
        }
        try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id,photo_id,single_attempts FROM jobs WHERE scope=? AND state='uploaded' AND is_test=0 AND photo_id IS NOT NULL AND single_id IS NULL AND early_token IS NULL AND single_next<=? ORDER BY created,id LIMIT 1",new String[]{scope,Long.toString(System.currentTimeMillis())})){
            if(!c.moveToFirst())return;
            String job=c.getString(0),photo=c.getString(1);int attempts=c.getInt(2);
            try{
                JSONObject result=api.call("/v1/device/photos/"+photo+"/single",new JSONObject());
                if(!result.getString("photo_id").equals(photo)||!result.getString("guest_path").matches("/p/[a-f0-9]{32}")||!result.getString("kind").equals("single"))throw new IllegalStateException("Invalid single QR response");
                ContentValues update=new ContentValues();update.put("single_id",result.getString("id"));update.put("single_label",result.getString("label"));update.put("single_path",result.getString("guest_path"));update.put("single_next",0);q.update(job,update);
            }catch(Exception e){
                ContentValues update=new ContentValues();update.put("single_attempts",attempts+1);
                update.put("single_next",System.currentTimeMillis()+Math.min(60000L,2000L*(1L<<Math.min(5,attempts))));q.update(job,update);
            }
        }
    }
    private SingleSync(){}
}
