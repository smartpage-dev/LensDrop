package app.lensdrop.transfer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;
import java.util.List;

/** Foreground, in-app, QR-only scan. Failures remain visible; no enrollment/network side effects. */
public final class StaffQrScannerActivity extends Activity {
    static final String TEXT="staff_qr_text", REASON="staff_scan_reason";
    private static final int CAMERA_PERMISSION=62;
    private BarcodeView camera;private TextView status;private boolean foreground,delivered,failed,permissionAsked,opening;
    private LinearLayout frame;private Button retry;private String failureReason="camera_error";
    @Override public void onCreate(Bundle state){super.onCreate(state);
        LinearLayout page=LuceUi.screen(this,"Scan staff QR","Use the fresh invitation from your event Admin. A guest photo QR will not pair this phone.",false);
        status=LuceUi.notice(this,page,"Preparing camera...");
        frame=LuceUi.card(this,page,"Point at the invitation","Keep the whole QR inside the preview.");
        retry=LuceUi.action(this,page,"Allow camera / retry",true,()->{failed=false;permissionAsked=false;begin();});
        LuceUi.action(this,page,"Paste invitation instead",false,()->leave("paste"));
        LuceUi.action(this,page,"Open app permissions",false,()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(RuntimeException e){status.setText("Open Android Settings > Apps > LensDrop > Permissions > Camera.");}});
        LuceUi.action(this,page,"Cancel scanning",false,()->leave("cancelled"));
        setResult(RESULT_CANCELED,new Intent().putExtra(REASON,"cancelled"));
        if(state!=null){failed=state.getBoolean("failed",false);permissionAsked=state.getBoolean("permission_asked",false);failureReason=state.getString("failure_reason","camera_error");}
    }
    private void ensureCamera(){if(camera!=null)return;
        BarcodeView next=new BarcodeView(this);next.setUseTextureView(false);next.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        next.addStateListener(new CameraPreview.StateListener(){public void previewSized(){}public void previewStarted(){if(foreground&&!failed&&!delivered)status.setText("Camera ready. Hold steady over the STAFF QR.");}public void previewStopped(){}public void cameraClosed(){}public void cameraError(Exception e){new Config(StaffQrScannerActivity.this).prefs.edit().putString("staff_scan_failure_type",e==null?"Unknown":e.getClass().getSimpleName()).apply();showFailure("camera_error");}});
        frame.addView(next,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,300)));camera=next;
    }
    private void begin(){
        if(!foreground||delivered||failed||opening)return;
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)){showFailure("no_camera");return;}
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            status.setText("Camera permission is needed only to scan. Pasting an invitation remains available.");
            if(!permissionAsked){permissionAsked=true;requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_PERMISSION);}return;
        }
        if(!SetupFlow.mayLaunchScanner(true,foreground,delivered))return;
        try{
            ensureCamera();
            opening=true;status.setText("Opening camera...");retry.setEnabled(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            camera.decodeSingle(new BarcodeCallback(){public void barcodeResult(BarcodeResult result){
                if(!foreground||delivered||failed)return;
                String text=result==null?null:result.getText();
                try{StaffInvitation.parse(text);}catch(IllegalArgumentException e){showFailure("wrong_qr");return;}
                delivered=true;camera.pause();setResult(RESULT_OK,new Intent().putExtra(TEXT,text));finish();
            }public void possibleResultPoints(List<ResultPoint> points){}});camera.resume();
        }catch(RuntimeException e){new Config(this).prefs.edit().putString("staff_scan_failure_type",e.getClass().getSimpleName()).apply();showFailure("camera_error");}
    }
    private void showFailure(String reason){if(isFinishing()||isDestroyed()||delivered)return;failed=true;opening=false;failureReason=reason;if(!"camera_error".equals(reason))new Config(this).prefs.edit().putString("staff_scan_failure_type","").apply();new Config(this).prefs.edit().putString("staff_scan_failure",reason).putLong("staff_scan_failure_at",System.currentTimeMillis()).apply();if(camera!=null)camera.pause();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(retry!=null)retry.setEnabled(true);status.setText(SetupFlow.scanResult(reason));setResult(RESULT_CANCELED,new Intent().putExtra(REASON,reason));}
    private void leave(String reason){if(delivered)return;delivered=true;if(camera!=null)camera.pause();setResult(RESULT_CANCELED,new Intent().putExtra(REASON,reason));finish();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request!=CAMERA_PERMISSION)return;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){failed=false;begin();}else showFailure("permission_denied");}
    @Override protected void onResume(){super.onResume();foreground=true;if(failed){retry.setEnabled(true);status.setText(SetupFlow.scanResult(failureReason));}else begin();}
    @Override protected void onPause(){foreground=false;opening=false;if(camera!=null)camera.pause();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(retry!=null)retry.setEnabled(true);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("failed",failed);state.putBoolean("permission_asked",permissionAsked);state.putString("failure_reason",failureReason);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){leave("cancelled");}
}
