package app.lensdrop.transfer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONObject;
import java.util.UUID;

final class QueueDb extends SQLiteOpenHelper {
    private final Context context;
    QueueDb(Context context) { super(context,"lensdrop-queue.sqlite",null,10);this.context=context.getApplicationContext();setWriteAheadLoggingEnabled(true); }
    boolean instant(){return new Config(context).instantQr();}
    @Override public void onConfigure(SQLiteDatabase db) { db.execSQL("PRAGMA synchronous=FULL"); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE observed(scope TEXT,uri TEXT,size INTEGER,modified INTEGER,stable INTEGER,PRIMARY KEY(scope,uri))");
        db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,scope TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'queued',spool TEXT,sha TEXT,upload TEXT,offset INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0,next_try INTEGER NOT NULL DEFAULT 0,error TEXT,receipt TEXT,created INTEGER NOT NULL,is_test INTEGER NOT NULL DEFAULT 0,last_progress INTEGER NOT NULL DEFAULT 0,UNIQUE(scope,uri,size,modified))");
        db.execSQL("CREATE TABLE sources(folder TEXT PRIMARY KEY,scope TEXT UNIQUE NOT NULL)");
        db.execSQL("CREATE INDEX due ON jobs(scope,state,next_try,created)");
        createH4(db);
        createH53(db);
        createH54(db);
        createH55(db);
        createH58(db);
        createH59(db);
        createH511(db);
        createH512(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<1||newVersion!=10)throw new IllegalStateException("Unsupported queue version. Preserve app data.");
        if(oldVersion<2){
            db.execSQL("ALTER TABLE jobs ADD COLUMN is_test INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE jobs ADD COLUMN last_progress INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE sources(folder TEXT PRIMARY KEY,scope TEXT UNIQUE NOT NULL)");
        }
        if(oldVersion<3)createH4(db);
        if(oldVersion<4)createH53(db);
        if(oldVersion<5)createH54(db);
        if(oldVersion<6)createH55(db);
        if(oldVersion<7)createH58(db);
        if(oldVersion<8)createH59(db);
        if(oldVersion<9)createH511(db);
        if(oldVersion<10)createH512(db);
    }
    private static void createH512(SQLiteDatabase db){
        db.execSQL("ALTER TABLE jobs ADD COLUMN early_token TEXT");
        db.execSQL("ALTER TABLE jobs ADD COLUMN early_registered INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN early_attempts INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN early_next INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE guest_groups ADD COLUMN local_token TEXT");
        db.execSQL("ALTER TABLE guest_groups ADD COLUMN cloud_registered INTEGER NOT NULL DEFAULT 0");
        db.execSQL("UPDATE guest_groups SET cloud_registered=1 WHERE guest_path IS NOT NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS early_single_due ON jobs(scope,early_registered,early_next)");
    }
    void primeInstant(String scope){
        if(!instant())return;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM jobs WHERE scope=? AND early_token IS NULL AND single_id IS NULL AND is_test=0 AND (archive_private=1 OR delivery_id IS NOT NULL) AND state IN ('queued','retry','uploading') ORDER BY created LIMIT 5",new String[]{scope})){
            while(c.moveToNext())getWritableDatabase().execSQL("UPDATE jobs SET early_token=? WHERE scope=? AND id=? AND early_token IS NULL",new Object[]{InstantQr.token(),scope,c.getString(0)});
        }
    }
    private static void createH511(SQLiteDatabase db){
        db.execSQL("ALTER TABLE jobs ADD COLUMN single_id TEXT");
        db.execSQL("ALTER TABLE jobs ADD COLUMN single_label TEXT");
        db.execSQL("ALTER TABLE jobs ADD COLUMN single_path TEXT");
        db.execSQL("ALTER TABLE jobs ADD COLUMN single_next INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN single_attempts INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE INDEX IF NOT EXISTS single_reconcile ON jobs(scope,state,single_next)");
    }
    private static void createH59(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS slideshow_tickets(id TEXT PRIMARY KEY,scope TEXT NOT NULL,event_id TEXT NOT NULL,url TEXT NOT NULL,printer TEXT NOT NULL,created INTEGER NOT NULL,state TEXT NOT NULL,observation TEXT NOT NULL DEFAULT 'not_checked',error TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS slideshow_tickets_scope ON slideshow_tickets(scope,created)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS slideshow_ticket_immutable BEFORE UPDATE OF id,scope,event_id,url,printer,created ON slideshow_tickets BEGIN SELECT RAISE(ABORT,'immutable_slideshow_ticket'); END");
    }
    private static void createH58(SQLiteDatabase db){
        // UI-only local metadata. Never delete cloud photos, print jobs, or guest assignments.
        db.execSQL("CREATE TABLE IF NOT EXISTS gallery_marks(scope TEXT NOT NULL,job_id TEXT NOT NULL,starred INTEGER NOT NULL DEFAULT 0,hidden INTEGER NOT NULL DEFAULT 0,changed INTEGER NOT NULL,PRIMARY KEY(scope,job_id))");
    }
    private static void createH55(SQLiteDatabase db){
        db.execSQL("ALTER TABLE watch_sessions ADD COLUMN archive_all INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN archive_private INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN selection_hidden INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN photo_id TEXT");
        db.execSQL("ALTER TABLE jobs ADD COLUMN delivery_synced INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN delivery_error TEXT");
        db.execSQL("ALTER TABLE jobs ADD COLUMN delivery_next INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN delivery_attempts INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE INDEX assignment_due ON jobs(scope,archive_private,state,delivery_synced,delivery_next)");
    }
    boolean archiveAll(String scope){try(Cursor c=getReadableDatabase().rawQuery("SELECT archive_all FROM watch_sessions WHERE scope=?",new String[]{scope})){return c.moveToFirst()&&c.getInt(0)==1;}}
    void archivePolicy(String scope,boolean all){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            d.execSQL("UPDATE watch_sessions SET archive_all=? WHERE scope=?",new Object[]{all?1:0,scope});
            // Explicitly enabling archive-all promotes known, unselected NEW arrivals, not excluded/skipped files.
            if(all)d.execSQL("UPDATE jobs SET state='queued',archive_private=1 WHERE scope=? AND state='candidate' AND is_test=0 AND delivery_id IS NULL",new Object[]{scope});
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    boolean assignmentRetryPending(String scope){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM jobs WHERE scope=? AND archive_private=1 AND state='uploaded' AND delivery_id IS NOT NULL AND photo_id IS NOT NULL AND delivery_synced=0 AND delivery_next<? LIMIT 1",new String[]{scope,Long.toString(Long.MAX_VALUE)})){return c.moveToFirst();}}
    void retryAssignments(String scope){getWritableDatabase().execSQL("UPDATE jobs SET delivery_error=NULL,delivery_next=0,delivery_attempts=0 WHERE scope=? AND archive_private=1 AND state='uploaded' AND delivery_synced=0",new Object[]{scope});}
    /** Re-pair/retry never changes a guest's already displayed early token. */
    void retryEarlySingles(String scope){getWritableDatabase().execSQL("UPDATE jobs SET early_next=0,early_attempts=0 WHERE scope=? AND early_token IS NOT NULL AND early_registered=0 AND is_test=0 AND state NOT IN ('skipped','retired_local')",new Object[]{scope});}
    /** Idempotent recovery for rows parked by the old H5.12.1 client. No schema bump. */
    void recoverParkedLinks(String scope){
        getWritableDatabase().execSQL("UPDATE jobs SET delivery_next=0,delivery_attempts=0 WHERE scope=? AND delivery_synced=0 AND delivery_error='delivery_not_found' AND delivery_next=?",new Object[]{scope,Long.MAX_VALUE});
        getWritableDatabase().execSQL("UPDATE jobs SET early_next=0,early_attempts=0 WHERE scope=? AND early_registered=0 AND early_token IS NOT NULL AND early_next=?",new Object[]{scope,Long.MAX_VALUE});
    }
    boolean needsGroupRegistration(String scope){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM guest_groups g JOIN jobs j ON j.scope=g.scope AND j.delivery_id=g.id WHERE g.scope=? AND g.kind='group' AND g.state='open' AND g.cloud_registered=0 AND j.state NOT IN ('skipped','retired_local') LIMIT 1",new String[]{scope})){return c.moveToFirst();}}
    boolean waitingGroupRegistration(String scope,String delivery){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM guest_groups WHERE scope=? AND id=? AND state='open' AND cloud_registered=0",new String[]{scope,delivery})){return c.moveToFirst();}}
    private static void createH54(SQLiteDatabase db){
        // Preserve every old ID/receipt/assignment while permitting explicitly selected revisions.
        db.execSQL("ALTER TABLE jobs RENAME TO jobs_h53");
        db.execSQL("CREATE TABLE jobs(id TEXT PRIMARY KEY,scope TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,state TEXT NOT NULL DEFAULT 'queued',spool TEXT,sha TEXT,upload TEXT,offset INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL DEFAULT 0,next_try INTEGER NOT NULL DEFAULT 0,error TEXT,receipt TEXT,created INTEGER NOT NULL,is_test INTEGER NOT NULL DEFAULT 0,last_progress INTEGER NOT NULL DEFAULT 0,delivery_id TEXT,source_revision INTEGER NOT NULL DEFAULT 0,file_failures INTEGER NOT NULL DEFAULT 0,UNIQUE(scope,uri,size,modified,source_revision))");
        db.execSQL("INSERT INTO jobs(id,scope,uri,name,size,modified,state,spool,sha,upload,offset,attempts,next_try,error,receipt,created,is_test,last_progress,delivery_id) SELECT id,scope,uri,name,size,modified,state,spool,sha,upload,offset,attempts,next_try,error,receipt,created,is_test,last_progress,delivery_id FROM jobs_h53");
        db.execSQL("DROP TABLE jobs_h53");
        db.execSQL("CREATE INDEX due ON jobs(scope,state,next_try,created)");
        db.execSQL("CREATE INDEX job_delivery ON jobs(delivery_id)");
        db.execSQL("ALTER TABLE watch_sessions ADD COLUMN undo_until INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE recovery_audit(id TEXT PRIMARY KEY,scope TEXT NOT NULL,job_id TEXT NOT NULL,action TEXT NOT NULL,previous_state TEXT NOT NULL,at INTEGER NOT NULL,detail TEXT NOT NULL)");
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
    void retryBlocked(String scope) {
        getWritableDatabase().execSQL("UPDATE jobs SET state='retry',next_try=0,file_failures=0 WHERE state='blocked' AND scope=?",new Object[]{scope});
        retryEarlySingles(scope);
    }
    boolean hasOtherPending(String scope) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM jobs WHERE scope<>? AND (state NOT IN ('uploaded','skipped','retired_local') OR (state='uploaded' AND archive_private=1 AND delivery_id IS NOT NULL AND delivery_synced=0)) LIMIT 1",new String[]{scope})) {return c.moveToFirst();}
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
        getWritableDatabase().execSQL("UPDATE jobs SET state='queued',next_try=0,error=NULL WHERE scope=? AND uri=? AND size=? AND modified=? AND state='blocked' AND error='source_missing'",new Object[]{scope,uri,size,modified});
    }
    Job due(String scope) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM jobs WHERE scope=? AND state IN ('queued','retry') AND next_try<=? ORDER BY (spool IS NULL),next_try,created,id LIMIT 1",new String[]{scope,Long.toString(System.currentTimeMillis())})) {
            return c.moveToFirst()?new Job(c):null;
        }
    }
    void update(String id,ContentValues values) { getWritableDatabase().update("jobs",values,"id=? AND state<>'retired_local'",new String[]{id}); }
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
        JSONObject result=new JSONObject();result.put("version","0.2.0-alpha.9-h5.12.3");result.put("generated_at",System.currentTimeMillis());result.put("scope","files-observed-by-this-client-only");result.put("camera_card_complete",false);result.put("source_originals_may_be_deleted",false);
        for(int flag=0;flag<=1;flag++){
            JSONObject group=new JSONObject();long received=0,uploaded=0,queued=0,blocked=0,candidates=0,skipped=0,retired=0;
            try(Cursor c=getReadableDatabase().rawQuery("SELECT state,count(*) FROM jobs WHERE scope=? AND is_test=? GROUP BY state",new String[]{scope,Integer.toString(flag)})){
                while(c.moveToNext()){long n=c.getLong(1);received+=n;String state=c.getString(0);if(state.equals("uploaded"))uploaded+=n;else if(state.equals("blocked"))blocked+=n;else if(state.equals("candidate"))candidates+=n;else if(state.equals("skipped"))skipped+=n;else if(state.equals("retired_local"))retired+=n;else queued+=n;}
            }
            try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM jobs WHERE scope=? AND is_test=? AND archive_private=1 AND delivery_id IS NULL AND selection_hidden=0 AND state NOT IN ('skipped','retired_local')",new String[]{scope,Integer.toString(flag)})){c.moveToFirst();group.put("unassigned_archives",c.getLong(0));}
            try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM jobs WHERE scope=? AND is_test=? AND archive_private=1 AND delivery_id IS NOT NULL AND delivery_synced=0 AND state<>'retired_local'",new String[]{scope,Integer.toString(flag)})){c.moveToFirst();group.put("pending_assignments",c.getLong(0));}
            group.put("received",received);group.put("uploaded",uploaded);group.put("queued",queued);group.put("blocked",blocked);group.put("candidates",candidates);group.put("skipped",skipped);group.put("retired",retired);
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
        final String id,scope,uri,name,spool,sha,upload,delivery;final long size,modified;final int attempts,fileFailures;final boolean isTest,archivePrivate;
        Job(Cursor c) {
            isTest=c.getInt(c.getColumnIndexOrThrow("is_test"))==1;archivePrivate=c.getInt(c.getColumnIndexOrThrow("archive_private"))==1;
            delivery=s(c,"delivery_id");id=s(c,"id");scope=s(c,"scope");uri=s(c,"uri");name=s(c,"name");spool=s(c,"spool");sha=s(c,"sha");upload=s(c,"upload");
            size=c.getLong(c.getColumnIndexOrThrow("size"));modified=c.getLong(c.getColumnIndexOrThrow("modified"));attempts=c.getInt(c.getColumnIndexOrThrow("attempts"));fileFailures=c.getInt(c.getColumnIndexOrThrow("file_failures"));
        }
        private static String s(Cursor c,String key) { return c.getString(c.getColumnIndexOrThrow(key)); }
    }
}
