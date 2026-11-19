package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Staff enrollment only. Invitation, claim secret and new credential persist encrypted before networking. */
public final class StaffSetupActivity extends Activity {
    private Config config; private EditText invitation; private TextView status;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return CloudApi.hex(b);}
    @Override public void onCreate(Bundle state){super.onCreate(state);config=new Config(this);ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,32,24,24);scroll.addView(box);setContentView(scroll);
        TextView title=new TextView(this);title.setText("Staff Setup / QR pairing");title.setTextSize(25);box.addView(title);
        TextView note=new TextView(this);note.setText("Scan a STAFF invitation from web Admin or the paired Windows station. Never scan a guest photo QR here. Pairing needs internet and Admin approval. Existing queues are retained.");box.addView(note);
        add(box,"Scan staff setup QR",()->new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("LensDrop STAFF setup QR").setBeepEnabled(false).setOrientationLocked(false).initiateScan());
        invitation=new EditText(this);invitation.setHint("Or paste https://your-site/pair#...");invitation.setSingleLine();box.addView(invitation);add(box,"Review invitation",()->review(invitation.getText().toString().trim()));
        add(box,"Retry saved claim",()->work(this::claim));add(box,"Check Admin approval / finish setup",()->work(this::completePairing));
        add(box,"Print Station settings & test",()->startActivity(new Intent(this,PrintActivity.class)));
        status=new TextView(this);status.setTextSize(18);box.addView(status);show("Current event: "+config.get("event")+"\nCloud: "+config.get("base")+"\nAfter approval, choose the shared photo folder on the main screen.");
    }
    private void add(LinearLayout box,String text,Runnable run){Button b=new Button(this);b.setText(text);b.setOnClickListener(v->run.run());box.addView(b);}
    private void show(String value){runOnUiThread(()->{if(!isFinishing())status.setText(value);});}
    private interface Task{void run()throws Exception;}
    private void work(Task task){io.execute(()->{try{task.run();}catch(CloudApi.Failure e){show("Setup: "+e.code+". Saved claim retained.");}catch(Exception e){show("Setup not completed: "+(e instanceof IllegalStateException?e.getMessage():"check HTTPS, invitation, and internet")+". No queue was deleted.");}});}
    private void review(String raw){try{URI u=new URI(raw);String[] parts=(u.getFragment()==null?"":u.getFragment()).split("\\.");if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||!"/pair".equals(u.getPath())||parts.length!=2||!parts[0].matches("[0-9a-f-]{36}")||!parts[1].matches("[0-9a-f]{64}"))throw new IllegalArgumentException();String base="https://"+u.getRawAuthority();new AlertDialog.Builder(this).setTitle("Connect to this LensDrop server?").setMessage(base+"\nOnly continue when this is your event administrator's invitation.").setPositiveButton("Claim invitation",(d,n)->work(()->{if(config.active()||UploadEngine.IN_FLIGHT.get())throw new IllegalStateException("Pause photo transfer before enrollment");JSONObject pending=new JSONObject().put("base",base).put("id",parts[0]).put("secret",parts[1]).put("claim_secret",random()).put("credential",random()).put("client_id",UUID.randomUUID().toString());config.putSecret("pairing",pending.toString());claim();})).setNegativeButton("Cancel",null).show();}catch(Exception e){show("Not a LensDrop staff invitation. Use the STAFF QR, not a guest link.");}}
    private void claim()throws Exception{JSONObject p=new JSONObject(config.secret("pairing"));try(CloudApi api=new CloudApi(this,p.getString("base"),"",config.cellular())){JSONObject b=new JSONObject().put("id",p.getString("id")).put("secret",p.getString("secret")).put("claim_secret",p.getString("claim_secret")).put("credential_hash",CloudApi.sha(p.getString("credential").getBytes(StandardCharsets.UTF_8))).put("client_id",p.getString("client_id"));JSONObject r=api.call("/v1/pairings/claim",b);if(!"phone".equals(r.getString("kind")))throw new IllegalStateException("This invitation is for Windows, not a phone");show("Compare this code with web Admin:\n\n"+r.getString("code")+"\n\nEvent: "+r.getJSONObject("event").getString("name")+"\nDevice: "+r.getString("label")+"\nAsk Admin to approve, then press Check Admin approval.");}}
    private void completePairing()throws Exception{JSONObject p=new JSONObject(config.secret("pairing"));JSONObject result;try(CloudApi api=new CloudApi(this,p.getString("base"),p.getString("claim_secret"),config.cellular())){result=api.call("/v1/pairings/"+p.getString("id")+"/status",null);}if(!"approved".equals(result.getString("state"))){show("Still waiting for Admin approval. Code: "+result.optString("code"));return;}if(!"phone".equals(result.getString("kind")))throw new IllegalStateException("Wrong device type");String base=p.getString("base"),scope=base+"|"+result.getJSONObject("event").getString("id")+"|"+result.getString("entity_id");
        try(QueueDb q=new QueueDb(this);PrintQueue prints=new PrintQueue(this)){if(config.active()||UploadEngine.IN_FLIGHT.get()||PrintEngine.IN_FLIGHT.get())throw new IllegalStateException("Pause transfer and printing before applying a different pairing");if(!scope.equals(config.get("scope"))&&(q.hasOtherPending(scope)||prints.hasPending()))throw new IllegalStateException("Finish or resolve the existing queues before changing device identity");}
        boolean same=scope.equals(config.get("scope"));config.save(base,p.getString("credential"),scope,result.getJSONObject("event").getString("name"),same?config.get("folder"):"",config.cellular());if(!same){config.clearSecret("print_setup");config.prefs.edit().remove("cloud_snapshot").putBoolean("print_active",false).commit();}config.clearSecret("pairing");show("Paired to "+result.getJSONObject("event").getString("name")+".\nReturn to the main screen and choose your shared photo folder. Existing photos will be baselined, not imported.\nOpen Print Station settings to prepare local printing.");
    }
    @Override protected void onActivityResult(int request,int result,Intent data){IntentResult scan=IntentIntegrator.parseActivityResult(request,result,data);if(scan!=null){if(scan.getContents()!=null)review(scan.getContents());return;}super.onActivityResult(request,result,data);}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
