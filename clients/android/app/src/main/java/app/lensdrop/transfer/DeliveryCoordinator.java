package app.lensdrop.transfer;
import org.json.JSONArray;
import org.json.JSONObject;
final class DeliveryCoordinator {
    static void prepare(QueueDb q,String scope,CloudApi api)throws Exception{
        JSONObject identity=api.call("/v1/device",null);
        if(identity.optJSONObject("capabilities")==null||!identity.getJSONObject("capabilities").optBoolean("personal_delivery_v1"))throw new IllegalStateException("Upgrade cloud to H4 before using personal QR delivery");
        String expected=api.origin()+"|"+identity.getJSONObject("event").getString("id")+"|"+identity.getString("device_id");
        if(!scope.equals(expected))throw new IllegalStateException("Pairing changed; previous QR history was not moved");
        WatchLedger w=new WatchLedger(q);w.reserve(scope,"group");w.reserve(scope,"single");sync(q,scope,api);
    }
    static void syncAssignments(QueueDb q,String scope,CloudApi api)throws Exception{
        // One small batch; a bad link cannot starve other assignments or photo uploads.
        try(android.database.Cursor rows=q.getReadableDatabase().rawQuery("SELECT id,photo_id,delivery_id,delivery_attempts FROM jobs WHERE scope=? AND archive_private=1 AND state='uploaded' AND delivery_id IS NOT NULL AND photo_id IS NOT NULL AND delivery_synced=0 AND delivery_next<=? ORDER BY delivery_next,created LIMIT 3",new String[]{scope,Long.toString(System.currentTimeMillis())})){
            while(rows.moveToNext()){
                String job=rows.getString(0),photo=rows.getString(1),delivery=rows.getString(2);int attempts=rows.getInt(3);
                try{JSONObject r=api.call("/v1/device/photos/"+photo+"/delivery",new JSONObject().put("delivery_id",delivery));
                    if(!photo.equals(r.getString("photo_id"))||!delivery.equals(r.getString("delivery_id")))throw new IllegalStateException("assignment_receipt_mismatch");
                    q.getWritableDatabase().execSQL("UPDATE jobs SET delivery_synced=1,delivery_error=NULL WHERE id=? AND delivery_synced=0",new Object[]{job});
                }catch(Exception e){boolean retry=e instanceof CloudApi.Failure&&((CloudApi.Failure)e).retry;String code=e instanceof CloudApi.Failure?((CloudApi.Failure)e).code:"assignment_connection_or_receipt_error";
                    if(!(e instanceof CloudApi.Failure)&&!(e instanceof IllegalStateException))retry=true;
                    q.getWritableDatabase().execSQL("UPDATE jobs SET delivery_error=?,delivery_attempts=delivery_attempts+1,delivery_next=? WHERE id=? AND delivery_synced=0",new Object[]{SessionRules.safeError(code),retry?System.currentTimeMillis()+Math.min(60000,3000L*(1L<<Math.min(4,attempts))):Long.MAX_VALUE,job});
                }
            }
        }
    }
    static void sync(QueueDb q,String scope,CloudApi api)throws Exception{
        WatchLedger w=new WatchLedger(q);JSONArray records=w.toSync(scope);
        for(int i=0;i<records.length();i++){
            JSONObject g=records.getJSONObject(i);String id=g.getString("id");
            if(g.optString("guest_path").isEmpty()){
                JSONObject b=new JSONObject();b.put("id",id);b.put("kind",g.getString("kind"));b.put("label",g.getString("label"));
                JSONObject saved=api.call("/v1/device/deliveries",b);
                if(!saved.getString("id").equals(id)||!saved.getString("kind").equals(g.getString("kind")))throw new IllegalStateException("QR identity mismatch");
                w.cloudGroup(id,saved.getString("guest_path"));
            }
            if(g.getString("state").equals("cancel_pending")){api.call("/v1/device/deliveries/"+id+"/cancel",new JSONObject());w.cancelled(id);}
        }
    }
}
