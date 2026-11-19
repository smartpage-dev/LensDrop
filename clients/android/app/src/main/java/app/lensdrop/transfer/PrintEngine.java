package app.lensdrop.transfer;

import android.content.Context;import org.json.*;import java.io.*;import java.util.Arrays;import java.util.concurrent.atomic.AtomicBoolean;import javax.net.ssl.SSLException;

/** One immutable job ID for both routes. No new ID on retry or route fallback. */
final class PrintEngine {
    static final AtomicBoolean IN_FLIGHT=new AtomicBoolean(false);
    static boolean terminal(String state){return PrintRules.terminal(state);}
    private final Context context;private final Config config;
    PrintEngine(Context c){context=c;config=new Config(c);}
    void tick(){if(!config.prefs.getBoolean("print_active",false))return;
        android.os.PowerManager power=context.getSystemService(android.os.PowerManager.class);
        android.content.Intent battery=context.registerReceiver(null,new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        if(power.getCurrentThermalStatus()>=android.os.PowerManager.THERMAL_STATUS_SEVERE||(battery!=null&&battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,0)>=430)){config.prefs.edit().putString("print_status","Phone too warm; print queue retained.").apply();return;}
        if(battery!=null&&battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0)==0&&battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,100)*100/Math.max(1,battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100))<=15){config.prefs.edit().putString("print_status","Battery at or below 15%; print queue retained.").apply();return;}
        if(!TransferLane.enter())return;if(!IN_FLIGHT.compareAndSet(false,true)){TransferLane.leave();return;}
        try(PrintQueue q=new PrintQueue(context)){JSONObject row=q.next(config.get("scope"));if(row==null)return;String id=row.getString("id"),mode=row.getString("mode");JSONObject spec=new JSONObject(row.getString("spec"));JSONObject result=null;String route="";
            try{
                if(!mode.equals("cloud")){
                    try{JSONObject setup=new JSONObject(config.secret("print_setup"));if(!setup.getJSONObject("station").getString("id").equals(spec.getString("station_id")))throw new IllegalStateException("station_identity_changed");LanPrintApi api=new LanPrintApi(context,setup);result=local(api,spec,new File(row.getString("path")));route="LAN";
                    }catch(SSLException e){throw new IllegalStateException("station_certificate_changed");}
                    catch(LanPrintApi.Failure e){if(mode.equals("local")||e.status<500&&e.status!=408&&e.status!=429)throw e;}
                    catch(IOException e){if(mode.equals("local"))throw e;}
                }
                if(result==null){try(CloudApi api=new CloudApi(context,config.get("base"),config.token(),config.cellular())){result=cloud(api,spec,new File(row.getString("path")));route="Cloud";}}
                String state=result.getString("state");if(terminal(state)){q.update(id,"done",state,"",0,false);new File(row.getString("path")).delete();}else if(state.equals("outcome_unknown")||state.equals("submitted_unconfirmed")||state.equals("submitting")){q.update(id,"accepted",state,"",10000,false);}else q.update(id,"accepted",state,"",5000,false);
                config.prefs.edit().putString("print_status",route+" / "+id.substring(0,8)+" / "+state).apply();
            }catch(Exception e){String code=e instanceof LanPrintApi.Failure?((LanPrintApi.Failure)e).code:e instanceof CloudApi.Failure?((CloudApi.Failure)e).code:e instanceof IllegalStateException?e.getMessage():"network_or_io_error";
                boolean block=e instanceof IllegalStateException||e instanceof LanPrintApi.Failure&&((LanPrintApi.Failure)e).status>=400&&((LanPrintApi.Failure)e).status<500&&((LanPrintApi.Failure)e).status!=408&&((LanPrintApi.Failure)e).status!=429||e instanceof CloudApi.Failure&&!((CloudApi.Failure)e).retry;
                // Offset / busy conflicts are safe retries with the SAME ID.
                if("print_offset_mismatch".equals(code)||"print_job_busy".equals(code))block=false;
                long delay=Math.min(60000,3000L*(1L<<Math.min(4,row.optInt("attempts"))));q.update(id,block?"blocked":row.getString("state"),row.optString("remote_state"),safe(code),delay,true);config.prefs.edit().putString("print_status",(block?"Needs attention: ":"Waiting / retry: ")+safe(code)).apply();
            }
        }catch(Exception e){config.prefs.edit().putString("print_status","Print queue retained. Reopen and inspect status.").apply();}finally{IN_FLIGHT.set(false);TransferLane.leave();}}
    private static String safe(String s){return s!=null&&s.matches("[a-z0-9_]{1,80}")?s:"print_error";}
    private interface Transport{JSONObject call(String path,JSONObject body)throws Exception;JSONObject chunk(String path,byte[] bytes)throws Exception;}
    private JSONObject local(LanPrintApi api,JSONObject j,File file)throws Exception{return send(new Transport(){public JSONObject call(String p,JSONObject b)throws Exception{return api.call(p,b);}public JSONObject chunk(String p,byte[] b)throws Exception{return api.chunk(p,b);}},"/v1/print/jobs",j,file);}
    private JSONObject cloud(CloudApi api,JSONObject j,File file)throws Exception{return send(new Transport(){public JSONObject call(String p,JSONObject b)throws Exception{return api.call(p,b);}public JSONObject chunk(String p,byte[] b)throws Exception{return api.chunk(p,b);}},"/v1/device/print-jobs",j,file);}
    private JSONObject send(Transport api,String root,JSONObject spec,File file)throws Exception{JSONObject status;try{status=api.call(root+"/"+spec.getString("id"),null);}catch(LanPrintApi.Failure e){if(e.status!=404)throw e;status=api.call(root,spec);}catch(CloudApi.Failure e){if(!e.code.equals("print_job_not_found"))throw e;status=api.call(root,spec);}String mismatch=PrintRules.receiptMismatch(status.getString("id"),spec.getString("id"),status.getString("sha256"),spec.getString("sha256"),status.getLong("bytes"),spec.getLong("bytes"),status.getString("station_id"),spec.getString("station_id"));if(mismatch!=null)throw new IllegalStateException(mismatch);
        String state=status.getString("state"),id=spec.getString("id");if(!PrintRules.acceptsBytes(state))return status;
        if(!file.exists()||file.length()!=spec.getLong("bytes"))throw new IllegalStateException("print_source_missing");long offset=status.getLong("offset");if(offset<0||offset>file.length())throw new IllegalStateException("invalid_print_offset");
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){input.seek(offset);byte[] buffer=new byte[1024*1024];while(offset<file.length()){if(!config.prefs.getBoolean("print_active",false))throw new IOException("Paused");int n=input.read(buffer,0,(int)Math.min(buffer.length,file.length()-offset));if(n<=0)throw new IllegalStateException("print_source_changed");byte[] b=n==buffer.length?buffer:Arrays.copyOf(buffer,n);status=api.chunk(root+"/"+id+"/chunks?offset="+offset,b);long next=status.getLong("offset");if(next!=offset+n)throw new IllegalStateException("invalid_print_receipt");offset=next;}}
        return api.call(root+"/"+id+"/complete",new JSONObject());}
}
