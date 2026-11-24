package app.lensdrop.transfer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 3-column gallery of this phone's event photos. No auto-delete or remote publishing. */
public final class GalleryActivity extends Activity {
    private Config config;
    private TextView status;
    private LinearLayout grid;
    private Button more;
    private android.widget.CheckBox showHidden;
    private int visibleCount=30;
    private String lastSignature="";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService db=Executors.newSingleThreadExecutor();
    private final ExecutorService thumbs=Executors.newFixedThreadPool(2);
    private final AtomicBoolean reading=new AtomicBoolean(false);
    private volatile boolean active;
    private volatile int generation;
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
    };
    private final Runnable timer=new Runnable(){public void run(){if(!active)return;reload(false);ui.postDelayed(this,5000);}};

    @Override public void onCreate(Bundle saved){super.onCreate(saved);config=new Config(this);
        LinearLayout body=LuceUi.screen(this,"Event photo gallery","Three columns · new photos appear automatically · local gallery view",true);
        status=LuceUi.notice(this,body,"Loading photos from this phone's event queue...");
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);body.addView(actions);
        Button refresh=compact(actions,"Refresh",()->reload(true));refresh.setContentDescription("Refresh the photo gallery now");
        more=compact(actions,"More photos",()->{visibleCount=Math.min(90,visibleCount+30);reload(true);});
        showHidden=LuceUi.check(this,body,"Show hidden tiles (local only)",false);
        showHidden.setOnCheckedChangeListener((b,checked)->reload(true));
        grid=LuceUi.column(this);body.addView(grid);
        LuceUi.label(this,body,"Star and Hide are local gallery controls. Hiding does not delete the database record, cloud photo, guest link or original file. Use Support / Recovery for audited queue retirement.",13,false);
        if(config.get("scope").isEmpty())status.setText("Join an event first. Your existing files are not changed.");
    }
    private Button compact(LinearLayout parent,String title,Runnable run){
        LinearLayout slot=LuceUi.column(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);parent.addView(slot,lp);
        return LuceUi.action(this,slot,title,false,run);
    }
    private String signature(JSONArray a){StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)b.append(x.optString("id")).append(':').append(x.optString("state")).append(':').append(x.optString("photo_id")).append(':').append(x.optString("delivery_id")).append(':').append(x.optBoolean("starred")).append(';');}return b.toString();}
    private void reload(boolean force){
        final String scope=config.get("scope");if(scope.isEmpty()||db.isShutdown()||!reading.compareAndSet(false,true))return;
        db.execute(()->{
            try(QueueDb q=new QueueDb(this)){
                JSONArray rows=new GalleryLedger(q).page(scope,visibleCount,showHidden.isChecked());String next=signature(rows);
                runOnUiThread(()->{
                    if(!active||isFinishing()||isDestroyed()||!scope.equals(config.get("scope")))return;
                    status.setText(rows.length()+" photos shown · updates every 5s · " + new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date()) +
                        "\nOnly photos observed by this phone. Cloud archive/guest release are separate.");
                    more.setEnabled(rows.length()>=visibleCount&&visibleCount<90);
                    if(force||!next.equals(lastSignature)){lastSignature=next;render(rows,scope);}
                });
            }catch(Exception e){runOnUiThread(()->{if(active)status.setText("Gallery unavailable. Saved queue and original photos are unchanged.");});}
            finally{reading.set(false);}
        });
    }
    private void render(JSONArray rows,String scope){
        generation++;final int gen=generation;grid.removeAllViews();LinearLayout row=null;
        for(int i=0;i<rows.length();i++){
            if(i%3==0){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);grid.addView(row);}
            JSONObject p=rows.optJSONObject(i);if(p==null)continue;
            LinearLayout tile=LuceUi.column(this);LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);cell.setMargins(LuceUi.dp(this,2),LuceUi.dp(this,3),LuceUi.dp(this,2),LuceUi.dp(this,3));row.addView(tile,cell);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(LuceUi.shape(this,LuceUi.SOFT,LuceUi.LINE));
            tile.addView(image,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,114)));
            String name=p.optString("name");TextView label=LuceUi.label(this,tile,(p.optBoolean("hidden")?"[hidden] ":"")+(p.optBoolean("starred")?"★ ":"")+name,11,false);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView state=LuceUi.label(this,tile,p.optString("state"),11,false);state.setMaxLines(1);
            View.OnClickListener click=v->showPhoto(p,scope);image.setOnClickListener(click);tile.setOnClickListener(click);
            image.setContentDescription("Open photo "+name);loadThumb(p,image,scope,gen,192);
        }
        if(rows.length()==0)LuceUi.notice(this,grid,"No photos in this event yet. Start watching the selected folder; existing baseline files stay excluded.");
    }
    private void loadThumb(JSONObject p,ImageView target,String scope,int gen,int px){
        final String id=p.optString("id"),key=id+"/"+p.optString("photo_id")+"/"+px;
        Bitmap cached=cache.get(key);if(cached!=null){target.setImageBitmap(cached);return;}
        target.setTag(key);
        thumbs.execute(()->{
            Bitmap bitmap=null;
            try{
                String uri=p.optString("uri");
                if(uri.startsWith("content://"))bitmap=getContentResolver().loadThumbnail(Uri.parse(uri),new Size(px,px),null);
            }catch(Exception ignored){}
            if(bitmap==null){try{
                String path=p.optString("spool");if(!path.isEmpty()){
                    File f=new File(path),parent=new File(getFilesDir(),"spool").getCanonicalFile();
                    if(f.isFile()&&f.getCanonicalFile().getParentFile().equals(parent)){
                        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,opts);
                        opts.inJustDecodeBounds=false;opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>px)opts.inSampleSize*=2;
                        bitmap=BitmapFactory.decodeFile(path,opts);
                    }
                }
            }catch(Exception ignored){}}
            if(bitmap==null&&!p.optString("photo_id").isEmpty())try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){
                byte[] bytes=api.thumbnail(p.optString("photo_id"));BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=px<=200?2:1;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);
            }catch(Exception ignored){}
            final Bitmap done=bitmap;
            if(done!=null){cache.put(key,done);ui.post(()->{
                if(active&&gen==generation&&scope.equals(config.get("scope"))&&key.equals(target.getTag()))target.setImageBitmap(done);
            });}
        });
    }
    private void showPhoto(JSONObject item,String scope){
        if(!scope.equals(config.get("scope")))return;
        String id=item.optString("id");
        LinearLayout box=LuceUi.column(this);box.setPadding(LuceUi.dp(this,12),LuceUi.dp(this,6),LuceUi.dp(this,12),LuceUi.dp(this,6));
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(image,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,270)));
        String key=id+"/"+item.optString("photo_id")+"/192";Bitmap bitmap=cache.get(key);if(bitmap!=null)image.setImageBitmap(bitmap);loadThumb(item,image,scope,generation,900);
        TextView detail=LuceUi.label(this,box,item.optString("name")+"\n"+item.optString("state")+" · "+(item.optString("guest_label").isEmpty()?"Not assigned to a guest":item.optString("guest_label")),14,true);
        ScrollView dialogScroll=new ScrollView(this);dialogScroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Photo actions").setView(dialogScroll).setNegativeButton("Close",null).create();
        LuceUi.action(this,box,item.optBoolean("starred")?"★ Remove star":"☆ Star photo",false,()->{
            dialog.dismiss();db.execute(()->{try(QueueDb q=new QueueDb(this)){new GalleryLedger(q).mark(scope,id,"starred",!item.optBoolean("starred"));}catch(Exception ignored){}runOnUiThread(()->reload(true));});
        });
        LuceUi.action(this,box,"Show guest QR / assign photo",true,()->{
            dialog.dismiss();Intent i=new Intent(this,DeliveryActivity.class);i.putExtra("gallery_job_id",id);i.putExtra("gallery_show_delivery",item.optString("delivery_id"));startActivity(i);
        });
        LuceUi.action(this,box,"Print photo (staff confirmation)",false,()->{
            dialog.dismiss();Intent i=new Intent(this,PrintActivity.class);i.putExtra("gallery_job_id",id);startActivity(i);
        });
        LuceUi.action(this,box,"Print guest QR ticket",false,()->{
            if(item.optString("delivery_id").isEmpty()){
                new AlertDialog.Builder(this).setMessage("This photo has no guest yet. Assign it and show its guest QR first; no ticket will print automatically.").setPositiveButton("Open guest delivery",(d,n)->{
                    dialog.dismiss();Intent i=new Intent(this,DeliveryActivity.class);i.putExtra("gallery_job_id",id);startActivity(i);
                }).setNegativeButton("Cancel",null).show();return;
            }
            dialog.dismiss();Intent i=new Intent(this,DeliveryActivity.class);i.putExtra("gallery_show_delivery",item.optString("delivery_id"));i.putExtra("gallery_ticket_hint",true);startActivity(i);
        });
        LuceUi.action(this,box,"File info",false,()->new AlertDialog.Builder(this).setTitle("Photo information").setMessage(
            "Name: "+item.optString("name")+"\nBytes: "+item.optLong("size")+"\nLocal state: "+item.optString("state")+
            "\nCloud ID: "+(item.optString("photo_id").isEmpty()?"Not yet verified":item.optString("photo_id"))+
            "\nGuest: "+(item.optString("guest_label").isEmpty()?"Unassigned":item.optString("guest_label"))+
            "\nGuest link sync: "+item.optBoolean("delivery_synced")+
            "\nPhoto job: "+id+"\nCreated: "+new java.util.Date(item.optLong("created"))+
            "\nNo source paths, credentials or guest bearer URLs are displayed.").setPositiveButton("Close",null).show());
        LuceUi.action(this,box,item.optBoolean("hidden")?"Restore hidden tile":"Hide from this gallery (NO DELETE)",false,()->new AlertDialog.Builder(this)
            .setTitle(item.optBoolean("hidden")?"Restore this tile?":"Hide locally, not delete")
            .setMessage("This changes only visibility on this phone. It does NOT remove the database record, cloud photo, guest link, original file, or any queued print. Use Support / Recovery for audited retirement.")
            .setPositiveButton(item.optBoolean("hidden")?"Restore tile":"Hide tile",(d,n)->{dialog.dismiss();db.execute(()->{try(QueueDb q=new QueueDb(this)){new GalleryLedger(q).mark(scope,id,"hidden",!item.optBoolean("hidden"));}catch(Exception ignored){}runOnUiThread(()->reload(true));});})
            .setNegativeButton("Cancel",null).show());
        dialog.show();
    }
    @Override protected void onResume(){super.onResume();active=true;ui.removeCallbacks(timer);ui.post(timer);}
    @Override protected void onPause(){active=false;ui.removeCallbacks(timer);generation++;super.onPause();}
    @Override protected void onDestroy(){active=false;ui.removeCallbacks(timer);db.shutdown();thumbs.shutdownNow();cache.evictAll();super.onDestroy();}
}
