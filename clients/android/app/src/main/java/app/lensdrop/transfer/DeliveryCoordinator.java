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
