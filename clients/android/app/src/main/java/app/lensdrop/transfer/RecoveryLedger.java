package app.lensdrop.transfer;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Local decisions only. No server cancellation, file deletion, link mutation or guest reassignment. */
final class RecoveryLedger {
    private static void audit(SQLiteDatabase d,String scope,String job,String action,String previous,String detail){
        d.execSQL("INSERT INTO recovery_audit(id,scope,job_id,action,previous_state,at,detail) VALUES(?,?,?,?,?,?,?)",new Object[]{UUID.randomUUID().toString(),scope,job,action,previous,System.currentTimeMillis(),detail});
    }
    static JSONArray uploads(QueueDb q,String scope,int offset)throws Exception{
        JSONArray out=new JSONArray();
        try(Cursor c=q.getReadableDatabase().rawQuery("SELECT j.id,j.uri,j.name,j.size,j.modified,j.state,j.error,j.sha,j.delivery_id,j.source_revision,coalesce(w.disposition,''),j.delivery_error,j.delivery_synced FROM jobs j LEFT JOIN watch_items w ON w.scope=j.scope AND w.uri=j.uri AND w.job_id=j.id WHERE j.scope=? ORDER BY j.created DESC,j.id LIMIT 50 OFFSET ?",new String[]{scope,Integer.toString(offset)})){
            while(c.moveToNext())out.put(new JSONObject().put("id",c.getString(0)).put("uri",c.getString(1)).put("name",c.getString(2)).put("size",c.getLong(3)).put("modified",c.getLong(4)).put("state",c.getString(5)).put("error",SessionRules.safeError(c.getString(6))).put("sha",c.getString(7)).put("delivery_id",c.getString(8)).put("source_revision",c.getLong(9)).put("source_state",c.getString(10)).put("delivery_error",c.getString(11)).put("delivery_synced",c.getInt(12)));
        }return out;
    }
    static void retireUpload(QueueDb q,String scope,String id){
        SQLiteDatabase d=q.getWritableDatabase();d.beginTransaction();try{
            String state;boolean assignmentPending;try(Cursor c=d.rawQuery("SELECT state,archive_private,delivery_id,delivery_synced FROM jobs WHERE scope=? AND id=?",new String[]{scope,id})){if(!c.moveToFirst())throw new IllegalStateException("Work not found");state=c.getString(0);assignmentPending=c.getInt(1)==1&&c.getString(2)!=null&&c.getInt(3)==0;}
            if(state.equals("uploaded")&&assignmentPending){audit(d,scope,id,"stop_assignment_retry",state,"Cloud original and guest identity retained; no remote revocation");d.execSQL("UPDATE jobs SET delivery_synced=-1 WHERE scope=? AND id=?",new Object[]{scope,id});d.setTransactionSuccessful();return;}
            if(state.equals("uploaded")||state.equals("skipped")||state.equals("retired_local"))throw new IllegalStateException("This work is already resolved locally");
            if(state.equals("uploading"))throw new IllegalStateException("Wait for the current upload checkpoint");
            audit(d,scope,id,"stop_local_retry",state,"Original, spool, assignment and remote upload ID retained; remote outcome NOT cancelled");
            d.execSQL("UPDATE jobs SET state='retired_local',next_try=0 WHERE scope=? AND id=?",new Object[]{scope,id});d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    static void retirePrint(PrintQueue q,String scope,String id){
        SQLiteDatabase d=q.getWritableDatabase();d.beginTransaction();try{
            String state,remote;try(Cursor c=d.rawQuery("SELECT state,remote_state FROM jobs WHERE scope=? AND id=?",new String[]{scope,id})){if(!c.moveToFirst())throw new IllegalStateException("Print not found");state=c.getString(0);remote=c.getString(1);}
            if(state.equals("done")||state.equals("cancelled")||state.equals("retired_local"))throw new IllegalStateException("Print already resolved locally");
            audit(d,scope,id,"stop_local_polling",state,"Remote state="+remote+"; station may still print. No remote cancellation or quota refund.");
            d.execSQL("UPDATE jobs SET state='retired_local',next_try=0 WHERE scope=? AND id=?",new Object[]{scope,id});d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    static String replacement(QueueDb q,String scope,String oldId,String name,long size,long modified,String sha,String spool,String newId)throws Exception{
        if(size<4||size>64L*1024*1024||!sha.matches("[0-9a-f]{64}")||spool.isEmpty()||name.contains("/")||name.contains("\\"))throw new IllegalStateException("Invalid replacement snapshot");
        UUID.fromString(newId);SQLiteDatabase d=q.getWritableDatabase();d.beginTransaction();try{
            String uri,state,oldSha;try(Cursor c=d.rawQuery("SELECT uri,state,sha FROM jobs WHERE scope=? AND id=?",new String[]{scope,oldId})){if(!c.moveToFirst())throw new IllegalStateException("Source record unavailable");uri=c.getString(0);state=c.getString(1);oldSha=c.getString(2);}
            if(!state.equals("retired_local")&&!state.equals("uploaded")&&!state.equals("skipped"))throw new IllegalStateException("Retire the old local work first. Its remote result and guest assignment will be preserved.");
            if(sha.equals(oldSha))throw new IllegalStateException("Content is identical to the saved version; no replacement created");
            long revision;try(Cursor c=d.rawQuery("SELECT coalesce(max(source_revision),0)+1 FROM jobs WHERE scope=? AND uri=?",new String[]{scope,uri})){c.moveToFirst();revision=c.getLong(0);}
            try(Cursor c=d.rawQuery("SELECT id FROM jobs WHERE scope=? AND uri=? AND sha=?",new String[]{scope,uri,sha})){if(c.moveToFirst())throw new IllegalStateException("This exact content already has a local record");}
            ContentValues v=new ContentValues();v.put("id",newId);v.put("scope",scope);v.put("uri",uri);v.put("name",name);v.put("size",size);v.put("modified",modified);v.put("sha",sha);v.put("spool",spool);v.put("source_revision",revision);v.put("state",q.archiveAll(scope)?"queued":"candidate");v.put("archive_private",q.archiveAll(scope)?1:0);v.put("created",System.currentTimeMillis());d.insertOrThrow("jobs",null,v);
            d.execSQL("UPDATE watch_items SET disposition='ready',size=?,modified=?,job_id=?,delivery_id=NULL,mode='single' WHERE scope=? AND uri=?",new Object[]{size,modified,newId,scope,uri});
            audit(d,scope,oldId,"new_unassigned_revision",state,"Replacement job "+newId+"; no delivery inherited");d.setTransactionSuccessful();return newId;
        }finally{d.endTransaction();}
    }
    static JSONArray scopes(QueueDb q,PrintQueue p)throws Exception{
        java.util.LinkedHashSet<String> values=new java.util.LinkedHashSet<>();
        try(Cursor c=q.getReadableDatabase().rawQuery("SELECT scope FROM watch_sessions UNION SELECT scope FROM jobs",null)){while(c.moveToNext())values.add(c.getString(0));}
        try(Cursor c=p.getReadableDatabase().rawQuery("SELECT DISTINCT scope FROM jobs",null)){while(c.moveToNext())values.add(c.getString(0));}
        JSONArray out=new JSONArray();for(String value:values)out.put(value);return out;
    }
    static JSONObject auditReport(QueueDb q,PrintQueue p)throws Exception{
        JSONObject out=new JSONObject().put("schema","lensdrop.private-recovery/1").put("generated_at",System.currentTimeMillis()).put("warning","Private identifiers included. No tokens, photo bytes, guest URLs or file paths. Local retirement is not remote cancellation.");
        out.put("uploads",auditRows(q.getReadableDatabase()));out.put("prints",auditRows(p.getReadableDatabase()));return out;
    }
    private static JSONArray auditRows(SQLiteDatabase d)throws Exception{
        JSONArray rows=new JSONArray();try(Cursor c=d.rawQuery("SELECT id,scope,job_id,action,previous_state,at,detail FROM recovery_audit ORDER BY at,id",null)){while(c.moveToNext())rows.put(new JSONObject().put("id",c.getString(0)).put("scope",c.getString(1)).put("job_id",c.getString(2)).put("action",c.getString(3)).put("previous_state",c.getString(4)).put("at",c.getLong(5)).put("detail",c.getString(6)));}return rows;
    }
}
