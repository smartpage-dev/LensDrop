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
            JSONObject g=new WatchLedger(queue).group(scope,delivery);
            if(g==null||!"open".equals(g.optString("state"))||!label.equals(g.optString("label"))||!url.equals(TicketProtocol.guestUrl(scope.split("\\|",-1)[0],g.getString("guest_path"))))throw new IllegalStateException("Group link changed or became unavailable. Reopen ticket preview.");
            if(parent!=null){JSONObject previous=get(scope,parent);if(previous==null||!previous.getString("delivery_id").equals(delivery)||!previous.getString("guest_url").equals(url)||"sending".equals(previous.getString("state")))throw new IllegalStateException("Invalid reprint source");}
            JSONObject old=get(scope,id);
            if(old!=null){if(!old.getString("delivery_id").equals(delivery)||!old.getString("guest_url").equals(url)||!old.getString("printer").equals(printer)||!old.getString("label").equals(label)||!(parent==null?!old.has("parent_id"):parent.equals(old.optString("parent_id"))))throw new IllegalStateException("Ticket ID conflict");d.setTransactionSuccessful();return;}
            long now=System.currentTimeMillis();
            d.execSQL("INSERT INTO qr_tickets(id,scope,delivery_id,label,guest_url,printer,parent_id,created,last_change) VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{id,scope,delivery,label,url,printer,parent,now,now});
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
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
