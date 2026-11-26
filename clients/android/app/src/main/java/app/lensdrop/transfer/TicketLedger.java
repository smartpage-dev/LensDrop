package app.lensdrop.transfer;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separate immutable ticket attempts in the existing fully-synchronous local queue DB.
 * No scheduled/automatic send or retry. A saved ticket protects its guest link from Undo. */
final class TicketLedger {
    private final QueueDb queue;
    TicketLedger(QueueDb queue){this.queue=queue;}
    private SQLiteDatabase db(){return queue.getWritableDatabase();}
    void create(String scope,String id,String delivery,String label,String url,String printer,String parent)throws Exception{
        TicketProtocol.packet(scope,id,delivery,label,url);TicketProtocol.printer(printer);if(parent!=null)TicketProtocol.uuid(parent);
        SQLiteDatabase d=db();d.beginTransaction();try{
            JSONObject previous=parent==null?null:get(scope,parent);
            if(parent!=null&&(previous==null||!previous.getString("delivery_id").equals(delivery)||!previous.getString("guest_url").equals(url)||"sending".equals(previous.getString("state"))))throw new IllegalStateException("Invalid reprint source");
            String origin=scope.split("\\|",-1)[0];
            JSONObject g=new WatchLedger(queue).group(scope,delivery);
            if(g!=null){
                if(!"open".equals(g.optString("state"))||!url.equals(TicketProtocol.guestUrl(origin,g.getString("guest_path")))||!(label.equals(g.optString("label"))||(previous!=null&&label.equals(previous.optString("label")))))throw new IllegalStateException("Group link changed or became unavailable. Reopen ticket preview.");
            }else{
                // Single print identity may be the local job UUID (before preissue) or
                // the immutable Cloud Single ID (after preissue). Both resolve to one job.
                try(Cursor c=d.rawQuery("SELECT id,single_label,single_path,early_token FROM jobs WHERE scope=? AND (id=? OR single_id=?) AND is_test=0 AND state NOT IN ('skipped','retired_local') LIMIT 2",new String[]{scope,delivery,delivery})){
                    if(!c.moveToFirst())throw new IllegalStateException("Single link unavailable. Reopen Single QR preview.");
                    String job=c.getString(0),official=c.getString(1),path=c.getString(2),token=c.getString(3);
                    if(c.moveToNext())throw new IllegalStateException("Ambiguous Single ticket identity");
                    String expected=path!=null?path:token!=null?InstantQr.path(token):null;
                    if(expected==null||!url.equals(TicketProtocol.guestUrl(origin,expected)))throw new IllegalStateException("Single QR changed or became unavailable. Reopen ticket preview.");
                    String current=official==null?TicketProtocol.earlySingleLabel(job):official;
                    if(!label.equals(current)&&!(previous!=null&&label.equals(previous.optString("label"))))throw new IllegalStateException("Single label changed. Reopen ticket preview.");
                }
            }
            JSONObject old=get(scope,id);
            if(old!=null){if(!old.getString("delivery_id").equals(delivery)||!old.getString("guest_url").equals(url)||!old.getString("printer").equals(printer)||!old.getString("label").equals(label)||!(parent==null?!old.has("parent_id"):parent.equals(old.optString("parent_id"))))throw new IllegalStateException("Ticket ID conflict");d.setTransactionSuccessful();return;}
            long now=System.currentTimeMillis();
            d.execSQL("INSERT INTO qr_tickets(id,scope,delivery_id,label,guest_url,printer,parent_id,created,last_change) VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{id,scope,delivery,label,url,printer,parent,now,now});
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    /** Stable guest URL may change from a local job ID to a Cloud Single ID.
     * Detect an earlier ticket without treating that as a new printable guest. */
    JSONObject latestForGuestUrl(String scope,String guestUrl)throws Exception{
        try(Cursor c=db().rawQuery("SELECT id FROM qr_tickets WHERE scope=? AND guest_url=? ORDER BY created DESC,id DESC LIMIT 1",new String[]{scope,guestUrl})){
            return c.moveToFirst()?get(scope,c.getString(0)):null;
        }
    }
    /** Latest attempt for the exact personal delivery. Never infer completion from sending. */
    JSONObject latestForDelivery(String scope,String delivery)throws Exception{
        try(Cursor c=db().rawQuery("SELECT id FROM qr_tickets WHERE scope=? AND delivery_id=? ORDER BY created DESC,id DESC LIMIT 1",new String[]{scope,delivery})){
            return c.moveToFirst()?get(scope,c.getString(0)):null;
        }
    }
    JSONObject get(String scope,String id)throws Exception{
        try(Cursor c=db().rawQuery("SELECT id,scope,delivery_id,label,guest_url,printer,parent_id,created,state,observation,error,last_change FROM qr_tickets WHERE scope=? AND id=?",new String[]{scope,id})){
            if(!c.moveToFirst())return null;
            JSONObject o=new JSONObject();String[] keys={"id","scope","delivery_id","label","guest_url","printer","parent_id","created","state","observation","error","last_change"};
            for(int i=0;i<keys.length;i++){if(i==7||i==11)o.put(keys[i],c.getLong(i));else o.put(keys[i],c.getString(i));}return o;
        }
    }
    boolean claim(String scope,String id){
        SQLiteDatabase d=db();d.beginTransaction();try{
            d.execSQL("UPDATE qr_tickets SET state='sending',last_change=? WHERE scope=? AND id=? AND state='prepared'",new Object[]{System.currentTimeMillis(),scope,id});
            boolean ok;try(Cursor c=d.rawQuery("SELECT changes()",null)){c.moveToFirst();ok=c.getInt(0)==1;}d.setTransactionSuccessful();return ok;
        }finally{d.endTransaction();}
    }
    void end(String scope,String id,String state,String error){
        if(!TicketProtocol.finished(state))throw new IllegalArgumentException("Invalid ticket outcome");
        if(error!=null&&!error.matches("[a-z_]{1,40}"))throw new IllegalArgumentException("Safe error code required");
        db().execSQL("UPDATE qr_tickets SET state=?,error=?,last_change=? WHERE scope=? AND id=? AND state='sending'",new Object[]{state,error,System.currentTimeMillis(),scope,id});
    }
    void recover(){
        db().execSQL("UPDATE qr_tickets SET state='outcome_unknown',error='interrupted_process',last_change=? WHERE state='sending'",new Object[]{System.currentTimeMillis()});
        db().execSQL("UPDATE qr_tickets SET state='cancelled_before_send',error='not_dispatched',last_change=? WHERE state='prepared'",new Object[]{System.currentTimeMillis()});
    }
    void observe(String scope,String id,String result){
        if(!TicketProtocol.observation(result))throw new IllegalArgumentException("Invalid observation");
        db().execSQL("UPDATE qr_tickets SET observation=?,last_change=? WHERE scope=? AND id=? AND state NOT IN ('prepared','sending')",new Object[]{result,System.currentTimeMillis(),scope,id});
    }
    JSONArray history(String scope,int offset)throws Exception{
        if(offset<0)throw new IllegalArgumentException("History offset");JSONArray a=new JSONArray();
        try(Cursor c=db().rawQuery("SELECT id FROM qr_tickets WHERE scope=? ORDER BY created DESC,id DESC LIMIT 50 OFFSET ?",new String[]{scope,Integer.toString(offset)})){while(c.moveToNext())a.put(get(scope,c.getString(0)));}return a;
    }
    JSONObject diagnostics(String scope)throws Exception{
        JSONObject result=new JSONObject().put("schema","lensdrop.ticket-diagnostic/1").put("profile",TicketProtocol.PROFILE).put("generated_at",System.currentTimeMillis()).put("automatic_retry",false).put("physical_print_verified",false).put("scope","selected-event-device-only");
        JSONArray rows=new JSONArray();try(Cursor c=db().rawQuery("SELECT state,observation,count(*) FROM qr_tickets WHERE scope=? GROUP BY state,observation",new String[]{scope})){while(c.moveToNext())rows.put(new JSONObject().put("state",c.getString(0)).put("observation",c.getString(1)).put("count",c.getLong(2)));}
        result.put("counts",rows).put("omitted","guest URLs/tokens, printer address/name, group/event/device IDs, photos, raw errors");return result;
    }
}
