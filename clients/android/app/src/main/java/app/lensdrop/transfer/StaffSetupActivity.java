package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Three-step staff onboarding. Claims persist encrypted; scanning never enrolls by itself. */
public final class StaffSetupActivity extends Activity {
    private static final int SCAN_REQUEST=61;
    private static final AtomicBoolean ENROLL_BUSY=new AtomicBoolean(false);
    private Config config;private EditText invitation;private TextView status,code,context,paired;
    private LinearLayout pendingCard,pairedCard,pasteBox;private Button scan,reviewButton,check,retry;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());private boolean foreground;
    private final Runnable waitForLocalClaim=()->{if(foreground)renderState();};
    private static String random(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return CloudApi.hex(b);}
    @Override public void onCreate(Bundle state){super.onCreate(state);config=new Config(this);
        LinearLayout page=LuceUi.screen(this,"Join your event","Staff setup only. Your guest photos and queues are kept.\n"+LuceUi.version(this),true);
        status=LuceUi.notice(this,page,"Start with a fresh STAFF invitation from web Admin.");
        LinearLayout first=LuceUi.card(this,page,"1  Scan or paste invitation","Admin: select your event, choose Photographer phone, then Create 5-minute pairing QR.");
        scan=LuceUi.action(this,first,"Scan staff QR",true,this::launchScanner);
        pasteBox=LuceUi.disclosure(this,first,"Paste invitation instead");
        invitation=LuceUi.field(this,pasteBox,"Complete https://your-site/pair#... invitation","",true);
        reviewButton=LuceUi.action(this,pasteBox,"Review pasted invitation",false,()->review(invitation.getText().toString()));
        LuceUi.label(this,first,"The gallery, projector and personal-photo QRs do not pair a phone. Never enter an Admin Key here.",14,false);
        pendingCard=LuceUi.card(this,page,"2  Ask Admin to approve this phone","Show this code on YOUR phone. Admin enters it under Approve matching device.");
        code=LuceUi.label(this,pendingCard,"------",36,true);code.setTextIsSelectable(false);code.setSaveEnabled(false);
        context=LuceUi.label(this,pendingCard,"",15,false);
        check=LuceUi.action(this,pendingCard,"Check approval & finish setup",true,()->work(this::completePairing));
        retry=LuceUi.action(this,pendingCard,"Retry saved claim",false,()->work(this::claim));
        pairedCard=LuceUi.card(this,page,"3  Continue to photo source","Pairing is not the same as watching. Choose a folder and save its archive policy next.");
        paired=LuceUi.label(this,pairedCard,"",16,true);
        LuceUi.action(this,pairedCard,"Continue to photo source",true,()->{setResult(RESULT_OK);finish();});
        LinearLayout help=LuceUi.disclosure(this,page,"Pairing help");
        LuceUi.label(this,help,"Internet and Admin approval are required. Five wrong code entries lock an invitation. If expired, ask for a new one; a saved claim can be retried without changing its identity. Pause uploads and printing before replacing an existing pairing.",14,false);
        LuceUi.action(this,help,"Print Station settings (optional)",false,()->startActivity(new Intent(this,PrintActivity.class)));
        renderState();
    }
    private void renderState(){if(isFinishing()||isDestroyed()||config==null)return;
        boolean pending=!config.get("secure_pairing").isEmpty();pendingCard.setVisibility(pending?View.VISIBLE:View.GONE);
        if(pending)try{JSONObject p=new JSONObject(config.secret("pairing"));String value=p.optString("display_code");code.setText(value.matches("[0-9]{6}")?value:"------");context.setText("Event: "+p.optString("display_event","waiting for server")+"\nPhone: "+p.optString("display_label","your invitation")+"\nCloud: "+p.optString("base"));}catch(Exception e){code.setText("------");context.setText("Saved claim needs attention. Do not clear app data.");}
        boolean joined=!config.get("scope").isEmpty();pairedCard.setVisibility(joined&&!pending?View.VISIBLE:View.GONE);paired.setText(config.get("event")+"\n"+config.get("base"));setBusy(ENROLL_BUSY.get());uiHandler.removeCallbacks(waitForLocalClaim);if(foreground&&ENROLL_BUSY.get())uiHandler.postDelayed(waitForLocalClaim,600);
    }
    private void setBusy(boolean value){scan.setEnabled(!value);reviewButton.setEnabled(!value);check.setEnabled(!value);retry.setEnabled(!value);}
    private void show(String value){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){status.setText(value);renderState();}});}
    private interface Task{void run()throws Exception;}
    private void work(Task task){
        if(io.isShutdown())return;if(!ENROLL_BUSY.compareAndSet(false,true)){show("A pairing request is already in progress. Keep this screen open.");return;}setBusy(true);
        io.execute(()->{try{task.run();}catch(CloudApi.Failure e){show("Setup: "+e.code+". Your saved claim and queues are retained.");}catch(Exception e){show("Setup not completed: "+(e instanceof IllegalStateException?e.getMessage():"check server, invitation and internet")+". No queue was deleted.");}finally{ENROLL_BUSY.set(false);runOnUiThread(this::renderState);}});
    }
    private void launchScanner(){
        if(ENROLL_BUSY.get()){show("Finish the current pairing request first.");return;}
        try{config.prefs.edit().putString("staff_scan_result","scanner_open_requested").putLong("staff_scan_at",System.currentTimeMillis()).apply();startActivityForResult(new Intent(this,StaffQrScannerActivity.class),SCAN_REQUEST);}
        catch(RuntimeException e){scanNotice("launch_error");}
    }
    private void scanNotice(String reason){if(reason==null)reason="empty_result";config.prefs.edit().putString("staff_scan_result",reason).putLong("staff_scan_at",System.currentTimeMillis()).apply();show(SetupFlow.scanResult(reason));if("paste".equals(reason)||"launch_error".equals(reason)||"permission_denied".equals(reason))pasteBox.setVisibility(View.VISIBLE);}
    private void review(String raw){
        final StaffInvitation parsed;
        try{parsed=StaffInvitation.parse(raw);}catch(IllegalArgumentException e){show(e.getMessage());return;}
        if(ENROLL_BUSY.get()){show("Finish the current pairing request first.");return;}
        boolean replacing=!config.get("secure_pairing").isEmpty();
        String note=parsed.base+"\n\nOnly continue for your event administrator's server. Your existing queues will not be deleted."+(replacing?"\n\nThis will replace a saved PENDING claim. Use Retry saved claim when it is still valid.":"");
        new AlertDialog.Builder(this).setTitle("Connect to this LensDrop server?").setMessage(note).setPositiveButton("Claim invitation",(d,n)->work(()->{
            if(config.active()||UploadEngine.IN_FLIGHT.get()||config.prefs.getBoolean("print_active",false)||PrintEngine.IN_FLIGHT.get())throw new IllegalStateException("Pause photo and print transfers before enrollment");
            JSONObject pending=new JSONObject().put("base",parsed.base).put("id",parsed.id).put("secret",parsed.secret).put("claim_secret",random()).put("credential",random()).put("client_id",UUID.randomUUID().toString());
            config.putSecret("pairing",pending.toString());runOnUiThread(()->invitation.setText(""));claim();
        })).setNegativeButton("Cancel",null).show();
    }
    private void claim()throws Exception{
        JSONObject p=new JSONObject(config.secret("pairing"));
        try(CloudApi api=new CloudApi(this,p.getString("base"),"",config.cellular())){
            JSONObject b=new JSONObject().put("id",p.getString("id")).put("secret",p.getString("secret")).put("claim_secret",p.getString("claim_secret")).put("credential_hash",CloudApi.sha(p.getString("credential").getBytes(StandardCharsets.UTF_8))).put("client_id",p.getString("client_id"));
            JSONObject r=api.call("/v1/pairings/claim",b);
            if(!"phone".equals(r.getString("kind")))throw new IllegalStateException("This invitation is for Windows. Ask Admin for a Photographer phone invitation");
            p.put("display_code",r.getString("code")).put("display_event",r.getJSONObject("event").getString("name")).put("display_label",r.getString("label"));config.putSecret("pairing",p.toString());
            show("Invitation claimed. Show the code below to Admin, then choose Check approval & finish setup.");
        }
    }
    private void completePairing()throws Exception{
        JSONObject p=new JSONObject(config.secret("pairing"));JSONObject result;
        try(CloudApi api=new CloudApi(this,p.getString("base"),p.getString("claim_secret"),config.cellular())){result=api.call("/v1/pairings/"+p.getString("id")+"/status",null);}
        if(!"approved".equals(result.getString("state"))){show("Waiting for Admin approval. Keep this saved claim; enter the phone's code in Admin.");return;}
        if(!"phone".equals(result.getString("kind")))throw new IllegalStateException("Wrong device type");
        String base=p.getString("base"),scope=base+"|"+result.getJSONObject("event").getString("id")+"|"+result.getString("entity_id");
        try(QueueDb q=new QueueDb(this);PrintQueue prints=new PrintQueue(this)){
            if(config.active()||UploadEngine.IN_FLIGHT.get()||config.prefs.getBoolean("print_active",false)||PrintEngine.IN_FLIGHT.get())throw new IllegalStateException("Pause transfer and printing before applying a different pairing");
            if(!scope.equals(config.get("scope"))&&(q.hasOtherPending(scope)||prints.hasPending()))throw new IllegalStateException("Finish or resolve the existing queues before changing device identity");
        }
        boolean same=scope.equals(config.get("scope"));
        config.save(base,p.getString("credential"),scope,result.getJSONObject("event").getString("name"),same?config.get("folder"):"",config.cellular());
        if(!same){config.clearSecret("print_setup");config.prefs.edit().remove("cloud_snapshot").putBoolean("print_active",false).commit();}
        config.clearSecret("pairing");show("Paired to "+result.getJSONObject("event").getString("name")+". Continue below to choose your photo source. No queue was reset.");
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=SCAN_REQUEST)return;
        if(result==RESULT_OK&&data!=null&&data.getStringExtra(StaffQrScannerActivity.TEXT)!=null){config.prefs.edit().putString("staff_scan_result","qr_received").putLong("staff_scan_at",System.currentTimeMillis()).apply();review(data.getStringExtra(StaffQrScannerActivity.TEXT));}
        else scanNotice(data==null?"empty_result":data.getStringExtra(StaffQrScannerActivity.REASON));
    }
    @Override protected void onResume(){super.onResume();foreground=true;renderState();}
    @Override protected void onPause(){foreground=false;uiHandler.removeCallbacks(waitForLocalClaim);super.onPause();}
    @Override protected void onDestroy(){uiHandler.removeCallbacks(waitForLocalClaim);io.shutdown();super.onDestroy();}
}
