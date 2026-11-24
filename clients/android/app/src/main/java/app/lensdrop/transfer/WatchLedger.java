package app.lensdrop.transfer;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Durable baseline, observed identities, immutable job destination, and QR history. */
final class WatchLedger {
    final QueueDb queue;
    WatchLedger(QueueDb q){queue=q;}
    private SQLiteDatabase db(){return queue.getWritableDatabase();}
    private String value(String scope,String column){
        try(Cursor c=db().rawQuery("SELECT "+column+" FROM watch_sessions WHERE scope=?",new String[]{scope})){return c.moveToFirst()?c.getString(0):null;}
    }
    void ensure(String scope,String folder){db().execSQL("INSERT OR IGNORE INTO watch_sessions(scope,folder,provider_version) VALUES(?,?,'')",new Object[]{scope,folder});}
    boolean ready(String scope){return "1".equals(value(scope,"baseline_ready"));}
    String version(String scope){return value(scope,"provider_version");}
    long lastScan(String scope){String v=value(scope,"last_scan");return v==null?0:Long.parseLong(v);}
    String mode(String scope){String v=value(scope,"mode");return v==null?"group":v;}
    String active(String scope){return value(scope,"active_group");}
    String shown(String scope){return value(scope,"shown_group");}
    boolean automatic(String scope){return "1".equals(value(scope,"auto_assign"));}
    void auto(String scope,boolean enabled){db().execSQL("UPDATE watch_sessions SET auto_assign=? WHERE scope=?",new Object[]{enabled?1:0,scope});}
    void mode(String scope,String mode){
        if(mode.equals(mode(scope)))return;
        if(!mode.equals("group")&&!mode.equals("single"))throw new IllegalArgumentException("Invalid mode");
        db().execSQL("UPDATE watch_sessions SET mode=?,auto_assign=0 WHERE scope=?",new Object[]{mode,scope});
    }
    void begin(String scope,String folder,String version){
        ensure(scope,folder);
        if(ready(scope))throw new IllegalStateException("Existing baseline must not be reset");
        db().execSQL("DELETE FROM watch_items WHERE scope=?",new Object[]{scope});
        db().execSQL("UPDATE watch_sessions SET provider_version=?,folder=?,baseline_count=0 WHERE scope=?",new Object[]{version,folder,scope});
    }
    void exclude(String scope,String uri,long size,long modified){
        db().execSQL("INSERT OR IGNORE INTO watch_items(scope,uri,disposition,size,modified,stable_since) VALUES(?,?,'excluded',?,?,0)",new Object[]{scope,uri,size,modified});
    }
    void finishBaseline(String scope,long count){db().execSQL("UPDATE watch_sessions SET baseline_ready=1,baseline_count=?,last_scan=? WHERE scope=?",new Object[]{count,System.currentTimeMillis(),scope});}
    void scanned(String scope){db().execSQL("UPDATE watch_sessions SET last_scan=? WHERE scope=?",new Object[]{System.currentTimeMillis(),scope});}
    void arrival(String scope,String uri,String name,long size,long modified,boolean pending,boolean recovered){
        SQLiteDatabase d=db();d.beginTransaction();
        try{
            long now=System.currentTimeMillis(),since=now;String state=null,delivery=null,intakeMode=mode(scope),job=null;
            try(Cursor c=d.rawQuery("SELECT disposition,size,modified,stable_since,delivery_id,mode,job_id FROM watch_items WHERE scope=? AND uri=?",new String[]{scope,uri})){
                if(c.moveToFirst()){
                    state=c.getString(0);if(WatchRules.excluded(state)){d.setTransactionSuccessful();return;}
                    if(!state.equals("pending")){
                        if(c.getLong(1)!=size||c.getLong(2)!=modified)d.execSQL("UPDATE watch_items SET disposition='changed' WHERE scope=? AND uri=?",new Object[]{scope,uri});
                        d.setTransactionSuccessful();return;
                    }
                    delivery=c.getString(4);intakeMode=c.getString(5);job=c.getString(6);
                    if(c.getLong(1)==size&&c.getLong(2)==modified)since=c.getLong(3);
                }
            }
            if(state==null){
                delivery=WatchRules.autoAssign(intakeMode,automatic(scope),recovered,active(scope))?active(scope):null;
                d.execSQL("INSERT INTO watch_items(scope,uri,disposition,size,modified,stable_since,delivery_id,mode) VALUES(?,?,'pending',?,?,?,?,?)",new Object[]{scope,uri,size,modified,now,delivery,intakeMode});
            }else d.execSQL("UPDATE watch_items SET size=?,modified=?,stable_since=? WHERE scope=? AND uri=?",new Object[]{size,modified,since,scope,uri});
            if(WatchRules.stable(now,since,size,pending)){
                if(!name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.jpe?g$")||name.contains("/")||name.contains("\\")){
                    d.execSQL("UPDATE watch_items SET disposition='invalid' WHERE scope=? AND uri=?",new Object[]{scope,uri});d.setTransactionSuccessful();return;
                }
                job=UUID.randomUUID().toString();ContentValues v=new ContentValues();v.put("id",job);v.put("scope",scope);v.put("uri",uri);v.put("name",name);v.put("size",size);v.put("modified",modified);v.put("created",now);v.put("delivery_id",delivery);v.put("state",queue.archiveAll(scope)||delivery!=null?"queued":"candidate");v.put("archive_private",queue.archiveAll(scope)?1:0);
                long inserted=d.insertWithOnConflict("jobs",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                if(inserted<0){try(Cursor c=d.rawQuery("SELECT id FROM jobs WHERE scope=? AND uri=? AND size=? AND modified=?",new String[]{scope,uri,Long.toString(size),Long.toString(modified)})){if(c.moveToFirst())job=c.getString(0);}}
                d.execSQL("UPDATE watch_items SET disposition='ready',job_id=? WHERE scope=? AND uri=?",new Object[]{job,scope,uri});
            }
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    JSONArray pending(String scope)throws Exception{
        JSONArray a=new JSONArray();try(Cursor c=db().rawQuery("SELECT uri FROM watch_items WHERE scope=? AND disposition='pending' ORDER BY stable_since LIMIT 64",new String[]{scope})){while(c.moveToNext())a.put(c.getString(0));}return a;
    }
    JSONObject summary(String scope)throws Exception{
        JSONObject o=new JSONObject();o.put("ready",ready(scope));o.put("mode",mode(scope));o.put("active",active(scope));o.put("shown",shown(scope));o.put("auto_assign",automatic(scope));
        o.put("excluded",Long.parseLong(value(scope,"baseline_count")==null?"0":value(scope,"baseline_count")));
        try(Cursor c=db().rawQuery("SELECT state,count(*) FROM jobs WHERE scope=? GROUP BY state",new String[]{scope})){while(c.moveToNext())o.put(c.getString(0),c.getLong(1));}
        try(Cursor c=db().rawQuery("SELECT count(*) FROM jobs WHERE scope=? AND archive_private=1 AND selection_hidden=0 AND delivery_id IS NULL AND state NOT IN ('skipped','retired_local')",new String[]{scope})){if(c.moveToFirst())o.put("unassigned_archives",c.getLong(0));}
        try(Cursor c=db().rawQuery("SELECT count(*) FROM watch_items WHERE scope=? AND disposition='changed'",new String[]{scope})){if(c.moveToFirst())o.put("changed_sources",c.getLong(0));}return o;
    }
    JSONObject group(String scope,String id)throws Exception{
        if(id==null)return null;
        try(Cursor c=db().rawQuery("SELECT id,kind,label,guest_path,state,created FROM guest_groups WHERE scope=? AND id=?",new String[]{scope,id})){
            if(!c.moveToFirst())return null;JSONObject o=new JSONObject();o.put("id",c.getString(0));o.put("kind",c.getString(1));o.put("label",c.getString(2));o.put("guest_path",c.getString(3));o.put("state",c.getString(4));o.put("created",c.getLong(5));
            try(Cursor jobs=db().rawQuery("SELECT state,count(*) FROM jobs WHERE scope=? AND delivery_id=? GROUP BY state",new String[]{scope,id})){while(jobs.moveToNext())o.put(jobs.getString(0),jobs.getLong(1));}return o;
        }
    }
    JSONArray history(String scope,int offset)throws Exception{
        JSONArray list=new JSONArray();try(Cursor c=db().rawQuery("SELECT id FROM guest_groups WHERE scope=? AND state<>'reserved' ORDER BY created DESC,id DESC LIMIT 50 OFFSET ?",new String[]{scope,Integer.toString(offset)})){while(c.moveToNext())list.put(group(scope,c.getString(0)));}return list;
    }
    void show(String scope,String id)throws Exception{
        JSONObject g=group(scope,id);if(g==null||!g.getString("state").equals("open"))throw new IllegalStateException("This group is unavailable");
        // Display-only: NEVER update active_group here.
        db().execSQL("UPDATE watch_sessions SET shown_group=? WHERE scope=?",new Object[]{id,scope});
    }
    void resume(String scope,String id)throws Exception{
        JSONObject g=group(scope,id);if(g==null||!g.getString("state").equals("open")||!g.getString("kind").equals("group"))throw new IllegalStateException("Only an open group can receive additional photographs");
        db().execSQL("UPDATE watch_sessions SET active_group=?,shown_group=?,previous_group=NULL,undo_until=0,mode='group',auto_assign=0 WHERE scope=?",new Object[]{id,id,scope});
    }
    private String takeReserved(String scope,String kind){
        try(Cursor c=db().rawQuery("SELECT id FROM guest_groups WHERE scope=? AND kind=? AND state='reserved' AND guest_path IS NOT NULL ORDER BY created,id LIMIT 1",new String[]{scope,kind})){
            if(!c.moveToFirst())throw new IllegalStateException("No prepared QR remains. Connect to internet and tap Prepare QR links. Existing queue is safe.");
            String id=c.getString(0);db().execSQL("UPDATE guest_groups SET state='open' WHERE id=?",new Object[]{id});return id;
        }
    }
    String nextGroup(String scope){
        SQLiteDatabase d=db();d.beginTransaction();try{
            String previous=active(scope),id=takeReserved(scope,"group");
            d.execSQL("UPDATE watch_sessions SET previous_group=?,active_group=?,shown_group=?,mode='group',auto_assign=0,undo_until=? WHERE scope=?",new Object[]{previous,id,id,System.currentTimeMillis()+120000,scope});d.setTransactionSuccessful();return id;
        }finally{d.endTransaction();}
    }
    long assigned(String scope,String id){
        try(Cursor c=db().rawQuery("SELECT (SELECT count(*) FROM jobs WHERE scope=? AND delivery_id=?)+(SELECT count(*) FROM watch_items WHERE scope=? AND delivery_id=? AND disposition='pending')",new String[]{scope,id,scope,id})){c.moveToFirst();return c.getLong(0);}
    }
    String undo(String scope){
        SQLiteDatabase d=db();d.beginTransaction();try{
            String old=value(scope,"previous_group"),current=active(scope);if(old==null||current==null)throw new IllegalStateException("No Next Group action to undo. Use History to resume a previous group.");
            long deadline=Long.parseLong(value(scope,"undo_until"));if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Quick Undo expired. Use History then Resume to intentionally change the receiver.");
            long n=assigned(scope,current);boolean ticketIssued;try(Cursor c=d.rawQuery("SELECT 1 FROM qr_tickets WHERE scope=? AND delivery_id=? LIMIT 1",new String[]{scope,current})){ticketIssued=c.moveToFirst();}if(n>0||ticketIssued)throw new IllegalStateException("This group has assigned work or a ticket. Use History then Resume; nothing was moved.");if(WatchRules.cancelOnUndo(n)&&!ticketIssued)d.execSQL("UPDATE guest_groups SET state='cancel_pending' WHERE id=?",new Object[]{current});
            d.execSQL("UPDATE watch_sessions SET active_group=?,shown_group=?,previous_group=NULL,undo_until=0,auto_assign=0 WHERE scope=?",new Object[]{old,old,scope});d.setTransactionSuccessful();
            return n==0&&!ticketIssued?"Previous group restored. Empty accidental QR is retired and will be cancelled online.":"Previous group restored. Ticket-issued QR and assigned photos remain in the other group; nothing was moved.";
        }finally{d.endTransaction();}
    }
    JSONArray candidates(String scope,int offset)throws Exception{
        JSONArray a=new JSONArray();try(Cursor c=db().rawQuery("SELECT id,uri,name,size,modified,spool,sha,state,photo_id FROM jobs WHERE scope=? AND (state='candidate' OR (archive_private=1 AND selection_hidden=0 AND state NOT IN ('skipped','retired_local'))) AND delivery_id IS NULL ORDER BY created DESC,id LIMIT 50 OFFSET ?",new String[]{scope,Integer.toString(offset)})){
            while(c.moveToNext()){JSONObject o=new JSONObject();o.put("id",c.getString(0));o.put("uri",c.getString(1));o.put("name",c.getString(2));o.put("size",c.getLong(3));o.put("modified",c.getLong(4));o.put("spool",c.getString(5));o.put("sha",c.getString(6));o.put("state",c.getString(7));o.put("photo_id",c.getString(8));a.put(o);}
        }return a;
    }
    void skip(String scope,String job){
        // Hiding from guest selection never cancels an archive upload.
        if(job==null)db().execSQL("UPDATE jobs SET selection_hidden=1 WHERE scope=? AND archive_private=1 AND delivery_id IS NULL",new Object[]{scope});
        else db().execSQL("UPDATE jobs SET selection_hidden=1 WHERE scope=? AND id=? AND archive_private=1 AND delivery_id IS NULL",new Object[]{scope,job});
        if(job==null)db().execSQL("UPDATE jobs SET state='skipped' WHERE scope=? AND state='candidate' AND archive_private=0 AND delivery_id IS NULL",new Object[]{scope});
        else db().execSQL("UPDATE jobs SET state='skipped' WHERE scope=? AND id=? AND state='candidate' AND archive_private=0 AND delivery_id IS NULL",new Object[]{scope,job});
    }
    String select(String scope,String job)throws Exception{
        SQLiteDatabase d=db();d.beginTransaction();try{
            String state,already;boolean archived;try(Cursor c=d.rawQuery("SELECT state,delivery_id,archive_private,selection_hidden FROM jobs WHERE scope=? AND id=? AND is_test=0",new String[]{scope,job})){if(!c.moveToFirst())throw new IllegalStateException("Candidate no longer available");state=c.getString(0);already=c.getString(1);archived=c.getInt(2)==1;if(c.getInt(3)==1)throw new IllegalStateException("Candidate was hidden from selection");}
            if(!(archived&&already==null&&!state.equals("retired_local")&&!state.equals("skipped"))&&!WatchRules.canAssign(state,already))throw new IllegalStateException("Photo already selected; its QR destination cannot be overwritten");
            String id=mode(scope).equals("single")?takeReserved(scope,"single"):active(scope);
            if(id==null)throw new IllegalStateException("Start or resume a group first");
            JSONObject g=group(scope,id);if(g==null||!g.getString("state").equals("open"))throw new IllegalStateException("Group is unavailable");
            d.execSQL("UPDATE jobs SET state=CASE WHEN archive_private=1 THEN state ELSE 'queued' END,delivery_id=? WHERE scope=? AND id=?",new Object[]{id,scope,job});
            d.execSQL("UPDATE watch_items SET delivery_id=? WHERE scope=? AND job_id=?",new Object[]{id,scope,job});
            if(mode(scope).equals("single")||shown(scope)==null)d.execSQL("UPDATE watch_sessions SET shown_group=? WHERE scope=?",new Object[]{id,scope});d.setTransactionSuccessful();return id;
        }finally{d.endTransaction();}
    }
    void reserve(String scope,String kind){
        SQLiteDatabase d=db();d.beginTransaction();try{
            long n,total;try(Cursor c=d.rawQuery("SELECT count(*) FROM guest_groups WHERE scope=? AND kind=? AND state='reserved'",new String[]{scope,kind})){c.moveToFirst();n=c.getLong(0);}
            try(Cursor c=d.rawQuery("SELECT count(*) FROM guest_groups WHERE scope=? AND kind=?",new String[]{scope,kind})){c.moveToFirst();total=c.getLong(0);}
            while(n++<3){total++;d.execSQL("INSERT INTO guest_groups(id,scope,kind,label,created) VALUES(?,?,?,?,?)",new Object[]{UUID.randomUUID().toString(),scope,kind,(kind.equals("group")?"Group ":"Photo ")+String.format(java.util.Locale.ROOT,"%04d",total),System.currentTimeMillis()});}
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    JSONArray toSync(String scope)throws Exception{
        JSONArray list=new JSONArray();try(Cursor c=db().rawQuery("SELECT id FROM guest_groups WHERE scope=? AND (guest_path IS NULL OR state='cancel_pending') ORDER BY created LIMIT 12",new String[]{scope})){while(c.moveToNext())list.put(group(scope,c.getString(0)));}return list;
    }
    // Legacy test/client compatibility: preserves the local label when no cloud label is provided.
    void cloudGroup(String id,String path){if(!path.matches("/p/[a-f0-9]{32}"))throw new IllegalStateException("Invalid QR response");db().execSQL("UPDATE guest_groups SET guest_path=? WHERE id=?",new Object[]{path,id});}
    void cloudGroup(String id,String path,String label){if(!path.matches("/p/[a-f0-9]{32}")||label==null||!label.matches("[SG][0-9]{4,8}"))throw new IllegalStateException("Invalid QR response");db().execSQL("UPDATE guest_groups SET guest_path=?,label=? WHERE id=?",new Object[]{path,label,id});}
    void cancelled(String id){db().execSQL("UPDATE guest_groups SET state='cancelled' WHERE id=? AND state='cancel_pending'",new Object[]{id});}
}
