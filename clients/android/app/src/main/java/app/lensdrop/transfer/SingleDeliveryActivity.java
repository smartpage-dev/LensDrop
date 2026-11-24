package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import android.database.Cursor;
import android.view.View;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Independent Single QR display. Uploaded receipts are reconciled by UploadEngine.
 * Viewing history never changes a group receiver. */
public final class SingleDeliveryActivity extends Activity {
 private Config config;private TextView summary,notice;private WebView qr;private ImageView preview;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ArrayList<Entry> items=new ArrayList<>();private int selected=0;private boolean hold=false;private String shown="";
 private final Runnable refresh=new Runnable(){public void run(){load();handler.postDelayed(this,5000);}};
 private static final class Entry {String id,photo,label,path,name;Entry(String id,String photo,String label,String path,String name){this.id=id;this.photo=photo;this.label=label;this.path=path;this.name=name;}}
 @Override public void onCreate(Bundle b){super.onCreate(b);config=new Config(this);LinearLayout[] dock=new LinearLayout[1];LinearLayout page=LuceUi.dockedScreen(this,"Single Photo QR","One verified photograph = one stable private QR.",dock);
  LinearLayout box=LuceUi.card(this,page,"Latest verified Single QR","If upload or QR creation is pending, the previous valid QR remains visible.");
  summary=LuceUi.label(this,box,"Waiting for verified photos",17,true);
  qr=new WebView(this);qr.getSettings().setJavaScriptEnabled(true);qr.getSettings().setBlockNetworkLoads(true);qr.getSettings().setAllowFileAccess(false);qr.getSettings().setAllowContentAccess(false);qr.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,String url){return true;}});
  int w=getResources().getDisplayMetrics().widthPixels;int dp=(int)(w/getResources().getDisplayMetrics().density);int heightDp=Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);int size=Math.min(Math.max(160,heightDp-315),Math.min(350,Math.max(160,dp-64)));box.addView(qr,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,size)));
  notice=LuceUi.notice(this,page,"Auto latest is on. Hold current QR while a guest scans.");
  LinearLayout thumb=LuceUi.card(this,page,"Photo preview","Below the fixed QR controls. Tap the image for a larger preview.");preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);thumb.addView(preview,new LinearLayout.LayoutParams(-1,-2));preview.setOnClickListener(v->largePhoto());
  LinearLayout r1=LuceUi.column(this);r1.setOrientation(LinearLayout.HORIZONTAL);dock[0].addView(r1);button(r1,"Previous",()->{hold=true;selected=Math.min(selected+1,Math.max(0,items.size()-1));show();});button(r1,"Latest",()->{hold=false;selected=0;show();});button(r1,"Hold / Resume",()->{hold=!hold;if(!hold)selected=0;show();});
  LinearLayout r2=LuceUi.column(this);r2.setOrientation(LinearLayout.HORIZONTAL);dock[0].addView(r2);button(r2,"Full QR",this::fullQr);button(r2,"Print QR",()->{if(items.isEmpty())return;Intent i=new Intent(this,TicketActivity.class);i.putExtra("ticket_single_job",items.get(Math.min(selected,items.size()-1)).id);startActivity(i);});button(r2,"Print Photo",()->startActivity(new Intent(this,PrintActivity.class)));
 }
 private void button(LinearLayout row,String title,Runnable task){LinearLayout slot=LuceUi.column(this);row.addView(slot,new LinearLayout.LayoutParams(0,-2,1));Button b=LuceUi.action(this,slot,title,false,task);b.setTextSize(12);b.setMinHeight(LuceUi.dp(this,46));}
 private void load(){if(worker.isShutdown()||config.get("scope").isEmpty())return;worker.execute(()->{
  ArrayList<Entry> rows=new ArrayList<>();long pending=0;
  try(QueueDb q=new QueueDb(this);Cursor c=q.getReadableDatabase().rawQuery("SELECT id,photo_id,single_label,single_path,name FROM jobs WHERE scope=? AND single_id IS NOT NULL AND single_path IS NOT NULL ORDER BY created DESC,id DESC LIMIT 100",new String[]{config.get("scope")})){
   while(c.moveToNext())rows.add(new Entry(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)));
   try(Cursor p=q.getReadableDatabase().rawQuery("SELECT count(*) FROM jobs WHERE scope=? AND state='uploaded' AND is_test=0 AND single_id IS NULL",new String[]{config.get("scope")})){if(p.moveToFirst())pending=p.getLong(0);}
  }catch(Exception e){return;}
  long wait=pending;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;String previous=selected<items.size()?items.get(selected).id:null;items.clear();items.addAll(rows);if(hold&&previous!=null){for(int i=0;i<items.size();i++)if(items.get(i).id.equals(previous)){selected=i;break;}}else selected=0;notice.setText((hold?"HOLD current QR":"Auto latest ON")+" · "+wait+" QR(s) awaiting reconciliation");show();});
 });}
 private String url(){return items.isEmpty()?"":config.get("base")+items.get(Math.min(selected,items.size()-1)).path;}
 private String html(String link){try(java.io.InputStream in=getAssets().open("qr.js")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1)out.write(block,0,n);byte[] js=out.toByteArray();return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;display:grid;place-items:center;min-height:100vh;background:white}svg{width:min(90vw,90vh);height:auto}</style><div id='q'></div><script>"+new String(js,StandardCharsets.UTF_8)+"</script><script>document.getElementById('q').innerHTML=LensDropQR.toSvg("+JSONObject.quote(link)+");</script>";}catch(Exception e){return "<p>QR unavailable. Check installation.</p>";}}
 private void show(){if(items.isEmpty()){summary.setText("No Single QR yet. Verified uploads will be linked automatically.");return;}Entry e=items.get(Math.min(selected,items.size()-1));summary.setText(e.label+" · "+e.name+" · "+(selected+1)+" / "+items.size());String link=url();if(!link.equals(shown)){shown=link;qr.loadDataWithBaseURL("https://lensdrop.invalid/",html(link),"text/html","UTF-8",null);loadPhoto(e);}}
 private void loadPhoto(Entry e){worker.execute(()->{try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){byte[] bytes=api.thumbnail(e.photo);BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&shown.equals(config.get("base")+e.path))preview.setImageBitmap(b);else if(b!=null)b.recycle();});}catch(Exception ignored){}});}
 private void fullQr(){if(items.isEmpty())return;WebView v=new WebView(this);v.getSettings().setJavaScriptEnabled(true);v.getSettings().setBlockNetworkLoads(true);v.loadDataWithBaseURL("https://lensdrop.invalid/",html(url()),"text/html","UTF-8",null);new AlertDialog.Builder(this).setTitle(items.get(selected).label+" — scan this QR").setView(v).setPositiveButton("Close",(d,n)->v.destroy()).show();}
 private void largePhoto(){if(preview.getDrawable()==null)return;ImageView i=new ImageView(this);i.setImageDrawable(preview.getDrawable());i.setAdjustViewBounds(true);new AlertDialog.Builder(this).setView(i).setPositiveButton("Close",null).show();}
 @Override protected void onResume(){super.onResume();handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacks(refresh);worker.shutdown();qr.stopLoading();qr.destroy();super.onDestroy();}
}
