package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only public Event Gallery QR; never exposes personal guest collections. */
public final class EventGalleryActivity extends Activity {
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private Config config;private TextView note;private ImageView qr;private String currentUrl="",scope="";
 @Override public void onCreate(Bundle b){super.onCreate(b);config=new Config(this);
  LinearLayout page=LuceUi.column(this);page.setPadding(LuceUi.dp(this,16),LuceUi.dp(this,16),LuceUi.dp(this,16),LuceUi.dp(this,16));setContentView(page);
  LuceUi.label(this,page,"Event Gallery QR",22,true);
  note=LuceUi.label(this,page,"Checking gallery visibility…",15,false);
  qr=new ImageView(this);qr.setAdjustViewBounds(true);page.addView(qr,new LinearLayout.LayoutParams(-1,0,1));
  LuceUi.action(this,page,"Refresh Gallery QR",true,this::load);
  LuceUi.action(this,page,"Show QR larger",false,()->{if(!ready())return;ImageView big=new ImageView(this);big.setImageBitmap(render(currentUrl,800));new AlertDialog.Builder(this).setTitle("Public Event Gallery QR").setView(big).setPositiveButton("Close",null).show();});
  LuceUi.action(this,page,"Open Event Gallery",false,()->{if(ready())startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(currentUrl)));});
  LuceUi.action(this,page,"Copy Event Gallery URL",false,()->{if(ready()){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Event Gallery",currentUrl));note.setText("Copied public gallery link. Avoid sharing private guest links.");}});
  LuceUi.label(this,page,"The public gallery has separate Admin visibility settings. Personal QR pages are always separate.",12,false);
 }
 private boolean ready(){if(currentUrl.isEmpty()||!scope.equals(config.get("scope"))){note.setText("Gallery URL unavailable. Refresh while paired to the event.");return false;}return true;}
 private void load(){currentUrl="";qr.setImageDrawable(null);final String expected=config.get("scope"),base=config.get("base");
  if(expected.isEmpty()){note.setText("Pair phone to the event first.");return;}note.setText("Verifying gallery link and event identity…");
  io.execute(()->{try(CloudApi api=new CloudApi(this,base,config.token(),config.cellular())){
    JSONObject r=api.call("/v1/device/gallery",null);String[] parts=expected.split("\\|",-1);
    if(parts.length!=3||!parts[1].equals(r.getString("event_id")))throw new IllegalStateException("Event identity changed");
    String path=r.getString("gallery_path");if(!path.matches("/g/[a-z0-9-]{1,150}"))throw new IllegalStateException("Unexpected gallery URL");
    String url=base+path;Bitmap b=render(url,600);
    runOnUiThread(()->{if(isFinishing()||isDestroyed()||!expected.equals(config.get("scope"))){b.recycle();return;}
      scope=expected;currentUrl=url;qr.setImageBitmap(b);
      note.setText("Event: "+r.optString("event_name")+" · Public gallery photos: "+r.optLong("public_gallery")+"\nIf Admin has disabled public access, this QR will not expose private photographs.");
    });
  }catch(Exception e){runOnUiThread(()->{if(!isFinishing())note.setText("Gallery not verified. Check event pairing / Hostinger / public gallery visibility.");});}});
 }
 private static Bitmap render(String text,int size){try{BitMatrix matrix=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size);Bitmap bmp=Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565);for(int y=0;y<size;y++)for(int x=0;x<size;x++)bmp.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);return bmp;}catch(Exception e){throw new IllegalStateException("Gallery QR generator unavailable",e);}}
 @Override protected void onResume(){super.onResume();load();}
 @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
