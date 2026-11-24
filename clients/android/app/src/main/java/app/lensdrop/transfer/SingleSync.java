package app.lensdrop.transfer;

import android.content.ContentValues;
import android.database.Cursor;
import org.json.JSONObject;
/** Reconciles immutable verified cloud receipts to single QR identities, one job per tick.
 * Network failures never alter upload state or source/group assignments. */
final class SingleSync {
    static void one(QueueDb q,String scope,CloudApi api)throws Exception {
        try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id,photo_id,single_attempts FROM jobs WHERE scope=? AND state='uploaded' AND is_test=0 AND photo_id IS NOT NULL AND single_id IS NULL AND single_next<=? ORDER BY created,id LIMIT 1",new String[]{scope,Long.toString(System.currentTimeMillis())})){
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
