package app.lensdrop.transfer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.UUID;

final class FrozenH53QueueDb extends SQLiteOpenHelper {
    FrozenH53QueueDb(Context context) { super(context,"lensdrop-queue.sqlite",null,4);setWriteAheadLoggingEnabled(true); }
    @Override public void onConfigure(SQLiteDatabase db) { db.execSQL("PRAGMA synchronous=FULL"); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE observed(scope TEXT,uri TEXT,size INTEGER,modified INTEGER,stable INTEGER,PRIMARY KEY(scope,uri))");
        db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,scope TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'queued',spool TEXT,sha TEXT,upload TEXT,offset INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0,next_try INTEGER NOT NULL DEFAULT 0,error TEXT,receipt TEXT,created INTEGER NOT NULL,is_test INTEGER NOT NULL DEFAULT 0,last_progress INTEGER NOT NULL DEFAULT 0,UNIQUE(scope,uri,size,modified))");
        db.execSQL("CREATE TABLE sources(folder TEXT PRIMARY KEY,scope TEXT UNIQUE NOT NULL)");
        db.execSQL("CREATE INDEX due ON jobs(scope,state,next_try,created)");
        createH4(db);
        createH53(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<1||newVersion!=4)throw new IllegalStateException("Unsupported queue version. Preserve app data.");
        if(oldVersion<2){
            db.execSQL("ALTER TABLE jobs ADD COLUMN is_test INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE jobs ADD COLUMN last_progress INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE sources(folder TEXT PRIMARY KEY,scope TEXT UNIQUE NOT NULL)");
        }
        if(oldVersion<3)createH4(db);
        if(oldVersion<4)createH53(db);
    }
    private static void createH53(SQLiteDatabase db){
        db.execSQL("CREATE TABLE qr_tickets(id TEXT PRIMARY KEY,scope TEXT NOT NULL,delivery_id TEXT NOT NULL,label TEXT NOT NULL,guest_url TEXT NOT NULL,printer TEXT NOT NULL,parent_id TEXT,created INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'prepared',observation TEXT NOT NULL DEFAULT 'not_checked',error TEXT,last_change INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX qr_ticket_scope ON qr_tickets(scope,created,id)");
        db.execSQL("CREATE INDEX qr_ticket_delivery ON qr_tickets(scope,delivery_id)");
        db.execSQL("CREATE TRIGGER qr_ticket_immutable BEFORE UPDATE OF id,scope,delivery_id,label,guest_url,printer,parent_id,created ON qr_tickets BEGIN SELECT RAISE(ABORT,'immutable_ticket_snapshot'); END");
    }
    private static void createH4(SQLiteDatabase db){
        db.execSQL("ALTER TABLE jobs ADD COLUMN delivery_id TEXT");
        db.execSQL("CREATE TABLE watch_sessions(scope TEXT PRIMARY KEY,folder TEXT NOT NULL,provider_version TEXT NOT NULL,baseline_ready INTEGER NOT NULL DEFAULT 0,baseline_count INTEGER NOT NULL DEFAULT 0,last_scan INTEGER NOT NULL DEFAULT 0,mode TEXT NOT NULL DEFAULT 'group',active_group TEXT,previous_group TEXT,shown_group TEXT,auto_assign INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE watch_items(scope TEXT NOT NULL,uri TEXT NOT NULL,disposition TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,stable_since INTEGER NOT NULL,delivery_id TEXT,mode TEXT NOT NULL DEFAULT 'group',job_id TEXT,PRIMARY KEY(scope,uri))");
        db.execSQL("CREATE TABLE guest_groups(id TEXT PRIMARY KEY,scope TEXT NOT NULL,kind TEXT NOT NULL,label TEXT NOT NULL,guest_path TEXT,state TEXT NOT NULL DEFAULT 'reserved',created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX group_scope ON guest_groups(scope,kind,state,created)");
        db.execSQL("CREATE INDEX job_delivery ON jobs(delivery_id)");
    }
    void recover() { getWritableDatabase().execSQL("UPDATE jobs SET state='retry',next_try=0 WHERE state='uploading'"); }
    void retryBlocked(String scope) { getWritableDatabase().execSQL("UPDATE jobs SET state='retry',next_try=0 WHERE state='blocked' AND scope=?",new Object[]{scope}); }
    boolean hasOtherPending(String scope) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM jobs WHERE scope<>? AND state NOT IN ('uploaded','skipped') LIMIT 1",new String[]{scope})) {return c.moveToFirst();}
    }
    boolean observe(String scope,String uri,long size,long modified,long now) {
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT size,modified,stable FROM observed WHERE scope=? AND uri=?",new String[]{scope,uri})) {
            if(c.moveToFirst()&&c.getLong(0)==size&&c.getLong(1)==modified) return now-c.getLong(2)>=2000;
        }
        ContentValues v=new ContentValues();v.put("scope",scope);v.put("uri",uri);v.put("size",size);v.put("modified",modified);v.put("stable",now);
        db.insertWithOnConflict("observed",null,v,SQLiteDatabase.CONFLICT_REPLACE);return false;
    }
    void add(String scope,String uri,String name,long size,long modified) {
        ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("scope",scope);v.put("uri",uri);v.put("name",name);v.put("size",size);v.put("modified",modified);v.put("created",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("jobs",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        getWritableDatabase().execSQL("UPDATE jobs SET state='queued',next_try=0,error=NULL WHERE scope=? AND uri=? AND size=? AND modified=? AND error='source_missing'",new Object[]{scope,uri,size,modified});
    }
    Job due(String scope) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs WHERE scope=? AND state IN ('queued','retry') AND next_try<=? ORDER BY (spool IS NULL),next_try,created,id LIMIT 1",new String[]{scope,Long.toString(System.currentTimeMillis())})) {
            return c.moveToFirst()?new Job(c):null;
        }
    }
    void update(String id,ContentValues values) { getWritableDatabase().update("jobs",values,"id=?",new String[]{id}); }
    void state(String id,String state,String error,int attempts,long next) {
        ContentValues v=new ContentValues();v.put("state",state);v.put("error",error);v.put("attempts",attempts);v.put("next_try",next);update(id,v);
    }
    JSONObject counts() { return counts(null); }
    JSONObject counts(String scope) {
        JSONObject out=new JSONObject();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT state,count(*) FROM jobs"+(scope==null?"":" WHERE scope=?")+" GROUP BY state",scope==null?null:new String[]{scope})) {
            while(c.moveToNext())out.put(c.getString(0),c.getLong(1));
        } catch(Exception ignored) { }
        return out;
    }
    void claimSource(String scope,String folder){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            try(Cursor c=db.rawQuery("SELECT scope FROM sources WHERE folder=?",new String[]{folder})){
                if(c.moveToFirst()&&!c.getString(0).equals(scope))throw new IllegalStateException("This folder belongs to another event/device. Select a different shared folder; old files must not be reassigned.");
            }
            try(Cursor c=db.rawQuery("SELECT folder FROM sources WHERE scope=?",new String[]{scope})){
                if(c.moveToFirst()&&!c.getString(0).equals(folder))throw new IllegalStateException("This session is bound to another folder. Preserve it and create a separate event source.");
            }
            ContentValues v=new ContentValues();v.put("scope",scope);v.put("folder",folder);db.insertWithOnConflict("sources",null,v,SQLiteDatabase.CONFLICT_IGNORE);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    String addTest(String scope,String uri,String name,long size,long modified){
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT id,is_test FROM jobs WHERE scope=? AND uri=? AND size=? AND modified=?",new String[]{scope,uri,Long.toString(size),Long.toString(modified)})){
            if(c.moveToFirst()){if(c.getInt(1)!=1)throw new IllegalStateException("Already a production photo. Select a separate test shot.");return c.getString(0);}
        }
        String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",id);v.put("scope",scope);v.put("uri",uri);v.put("name",name);v.put("size",size);v.put("modified",modified);v.put("created",System.currentTimeMillis());v.put("is_test",1);db.insertOrThrow("jobs",null,v);return id;
    }
    Job testDue(String scope,String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs WHERE scope=? AND id=? AND is_test=1 AND state IN ('queued','retry') AND next_try<=?",new String[]{scope,id,Long.toString(System.currentTimeMillis())})){return c.moveToFirst()?new Job(c):null;}
    }
    String jobState(String scope,String id){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT state FROM jobs WHERE scope=? AND id=?",new String[]{scope,id})){return c.moveToFirst()?c.getString(0):"missing";}
    }
    JSONObject snapshot(String scope)throws Exception{
        JSONObject result=new JSONObject();result.put("version","0.2.0-alpha.5-h4");result.put("generated_at",System.currentTimeMillis());result.put("scope","files-observed-by-this-client-only");result.put("camera_card_complete",false);result.put("source_originals_may_be_deleted",false);
        for(int flag=0;flag<=1;flag++){
            JSONObject group=new JSONObject();long received=0,uploaded=0,queued=0,blocked=0,candidates=0,skipped=0;
            try(Cursor c=getReadableDatabase().rawQuery("SELECT state,count(*) FROM jobs WHERE scope=? AND is_test=? GROUP BY state",new String[]{scope,Integer.toString(flag)})){
                while(c.moveToNext()){long n=c.getLong(1);received+=n;String state=c.getString(0);if(state.equals("uploaded"))uploaded+=n;else if(state.equals("blocked"))blocked+=n;else if(state.equals("candidate"))candidates+=n;else if(state.equals("skipped"))skipped+=n;else queued+=n;}
            }
            group.put("received",received);group.put("uploaded",uploaded);group.put("queued",queued);group.put("blocked",blocked);group.put("candidates",candidates);group.put("skipped",skipped);
            try(Cursor c=getReadableDatabase().rawQuery("SELECT coalesce(max(created),0),coalesce(max(last_progress),0),coalesce(sum(CASE WHEN state IN ('queued','retry','uploading','blocked') THEN size-offset ELSE 0 END),0) FROM jobs WHERE scope=? AND is_test=?",new String[]{scope,Integer.toString(flag)})){
                if(c.moveToFirst()){group.put("last_received",c.getLong(0));group.put("last_progress",c.getLong(1));group.put("queue_bytes",c.getLong(2));}
            }
            result.put(flag==0?"production":"preflight",group);
        }
        return result;
    }
    JSONObject diagnostics(String scope)throws Exception{
        JSONObject result=snapshot(scope),errors=new JSONObject();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT error,count(*) FROM jobs WHERE scope=? AND error IS NOT NULL GROUP BY error",new String[]{scope})){
            while(c.moveToNext()){String key=SessionRules.safeError(c.getString(0));errors.put(key,errors.optLong(key)+c.getLong(1));}
        }
        result.put("error_counts",errors);result.put("omitted","tokens, server URLs, event names, source URIs, filenames, photos, raw errors");return result;
    }
    static final class Job {
        final String id,scope,uri,name,spool,sha,upload,delivery;final long size,modified;final int attempts;final boolean isTest;
        Job(Cursor c) {
            isTest=c.getInt(c.getColumnIndexOrThrow("is_test"))==1;
            delivery=s(c,"delivery_id");id=s(c,"id");scope=s(c,"scope");uri=s(c,"uri");name=s(c,"name");spool=s(c,"spool");sha=s(c,"sha");upload=s(c,"upload");
            size=c.getLong(c.getColumnIndexOrThrow("size"));modified=c.getLong(c.getColumnIndexOrThrow("modified"));attempts=c.getInt(c.getColumnIndexOrThrow("attempts"));
        }
        private static String s(Cursor c,String key) { return c.getString(c.getColumnIndexOrThrow(key)); }
    }
}
