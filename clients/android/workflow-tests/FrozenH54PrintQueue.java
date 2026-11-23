// Exact H5.4 source, class/constructor renamed for migration testing only.
package app.lensdrop.transfer;

import android.content.*;import android.database.Cursor;import android.database.sqlite.*;import org.json.*;

final class FrozenH54PrintQueue extends SQLiteOpenHelper {
    FrozenH54PrintQueue(Context context){super(context,"lensdrop-prints.sqlite",null,2);setWriteAheadLoggingEnabled(true);}
    public void onConfigure(SQLiteDatabase db){db.execSQL("PRAGMA synchronous=FULL");}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,scope TEXT NOT NULL,spec TEXT NOT NULL,path TEXT NOT NULL,mode TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'queued',remote_state TEXT NOT NULL DEFAULT '',error TEXT NOT NULL DEFAULT '',next_try INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL)");createRecovery(db);}
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion==1&&newVersion==2)createRecovery(db);else throw new IllegalStateException("Unsupported print queue migration");}
    private static void createRecovery(SQLiteDatabase db){db.execSQL("CREATE TABLE recovery_audit(id TEXT PRIMARY KEY,scope TEXT NOT NULL,job_id TEXT NOT NULL,action TEXT NOT NULL,previous_state TEXT NOT NULL,at INTEGER NOT NULL,detail TEXT NOT NULL)");}
    void add(String scope,JSONObject spec,String path,String mode)throws Exception{ContentValues v=new ContentValues();v.put("id",spec.getString("id"));v.put("scope",scope);v.put("spec",spec.toString());v.put("path",path);v.put("mode",mode);v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("jobs",null,v);}
    JSONObject row(Cursor c)throws Exception{JSONObject x=new JSONObject();for(String k:new String[]{"id","scope","spec","path","mode","state","remote_state","error"})x.put(k,c.getString(c.getColumnIndexOrThrow(k)));x.put("next_try",c.getLong(c.getColumnIndexOrThrow("next_try")));x.put("attempts",c.getInt(c.getColumnIndexOrThrow("attempts")));return x;}
    JSONObject next(String scope)throws Exception{try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs WHERE scope=? AND state IN ('queued','accepted') AND next_try<=? ORDER BY created LIMIT 1",new String[]{scope,Long.toString(System.currentTimeMillis())})){return c.moveToFirst()?row(c):null;}}
    boolean hasPending(){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM jobs WHERE state NOT IN ('done','cancelled','retired_local') LIMIT 1",null)){return c.moveToFirst();}}
    JSONArray list()throws Exception{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs ORDER BY created DESC LIMIT 100",null)){while(c.moveToNext())a.put(row(c));}return a;}
    JSONArray history(String scope,int offset)throws Exception{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs WHERE scope=? ORDER BY created DESC,id LIMIT 50 OFFSET ?",new String[]{scope,Integer.toString(offset)})){while(c.moveToNext())a.put(row(c));}return a;}
    void update(String id,String state,String remote,String error,long delay,boolean retry){getWritableDatabase().execSQL("UPDATE jobs SET state=?,remote_state=?,error=?,next_try=?,attempts=attempts+? WHERE id=? AND state<>'retired_local'",new Object[]{state,remote,error,System.currentTimeMillis()+delay,retry?1:0,id});}
    void resumeBlocked(){getWritableDatabase().execSQL("UPDATE jobs SET state='queued',next_try=0,error='' WHERE state='blocked' AND error IN ('print_grant_expired_or_invalid','printing_not_authorized','device_revoked','station_token_invalid_or_revoked')");}
}
