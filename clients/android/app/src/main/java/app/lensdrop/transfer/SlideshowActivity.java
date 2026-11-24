package app.lensdrop.transfer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Staff-controlled event slideshow sharing. No automatic ticketing, no guest-photo print job. */
public final class SlideshowActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Config config;private TextView info;private ImageView qr;
    private volatile boolean foreground;private volatile TicketTransport operation;
    private String currentUrl="",currentEvent="",currentScope="";
    @Override public void onCreate(Bundle saved){super.onCreate(saved);config=new Config(this);
        LinearLayout page=LuceUi.screen(this,"Event slideshow","Public projector link for this paired event. Not a personal guest gallery.",true);
        info=LuceUi.notice(this,page,"Loading current slideshow link. Internet required to verify enabled status.");
        LinearLayout panel=LuceUi.card(this,page,"Projector QR","Use this to open the live slideshow on a second phone or projector computer.");
        qr=new ImageView(this);qr.setContentDescription("Current event slideshow QR");panel.addView(qr,new LinearLayout.LayoutParams(-1,LuceUi.dp(this,310)));
        LuceUi.action(this,panel,"Refresh event slideshow link",true,this::load);
        LuceUi.action(this,panel,"Open slideshow page",false,()->{if(!ready())return;startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(currentUrl)));});
        LuceUi.action(this,panel,"Show QR larger",false,()->{if(!ready())return;ImageView large=new ImageView(this);large.setImageBitmap(render(currentUrl,850));new AlertDialog.Builder(this).setTitle("Event slideshow QR - public link").setView(large).setPositiveButton("Close",null).show();});
        LinearLayout ticket=LuceUi.card(this,page,"Print a slideshow QR ticket","Uses your dedicated Bluetooth ticket printer; manual confirmation required.");
        LuceUi.action(this,ticket,"Print ONE event slideshow ticket",true,this::confirmPrint);
        LuceUi.label(this,ticket,"This is a public projector link. It never authorizes a CP9550DW photo print. A rotated link makes old paper QR invalid.",14,false);
        LuceUi.action(this,ticket,"Set up dedicated Bluetooth printer",false,()->startActivity(new Intent(this,TicketActivity.class)));
        LuceUi.action(this,ticket,"Review slideshow ticket attempts",false,this::history);
        try(QueueDb q=new QueueDb(this)){
            q.getWritableDatabase().execSQL("UPDATE slideshow_tickets SET state='outcome_unknown',error='interrupted_before_recovery' WHERE state='sending'");
            q.getWritableDatabase().execSQL("UPDATE slideshow_tickets SET state='cancelled_before_send' WHERE state='prepared'");
        }catch(Exception e){note("Slideshow ticket history needs attention. No automatic resend.");}
    }
    private void note(String s){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())info.setText(s);});}
    private boolean ready(){if(currentUrl.isEmpty()||!config.get("scope").equals(currentScope)){note("No verified current slideshow link. Refresh first.");return false;}return true;}
    private void load(){currentUrl="";currentScope="";qr.setImageDrawable(null);note("Checking enabled event slideshow with Hostinger...");
        final String scope=config.get("scope"),base=config.get("base");
        if(scope.isEmpty()){note("Pair this phone to an event first.");return;}
        io.execute(()->{try(CloudApi api=new CloudApi(this,base,config.token(),config.cellular())){
            JSONObject r=api.call("/v1/device/slideshow",null);
            if(!scope.equals(config.get("scope"))||!foreground)return;
            if(!r.optBoolean("enabled")||r.isNull("slideshow_path")){note("Slideshow disabled for this event. Ask Admin to enable it in 05 Slideshow.");return;}
            String[] parts=scope.split("\\|",-1);if(parts.length!=3||!parts[1].equals(r.getString("event_id")))throw new IllegalArgumentException("Event identity mismatch");
            final String url=SlideshowTicketProtocol.url(base,r.getString("slideshow_path"));
            final Bitmap image=render(url,600);
            runOnUiThread(()->{if(!foreground||!scope.equals(config.get("scope")))return;currentUrl=url;currentScope=scope;currentEvent=r.optString("event_name");qr.setImageBitmap(image);info.setText("Event: "+currentEvent+"\nProjector photos: "+r.optInt("projector_photos")+"\nLink verified for this event. Public projector access; internet required.");});
        }catch(Exception e){note("Cannot verify slideshow: "+(e instanceof CloudApi.Failure?((CloudApi.Failure)e).code:"Check pairing / internet / slideshow setting")+". No QR printed.");}});
    }
    private static Bitmap render(String value,int pixels){try{EnumMap<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,4);BitMatrix m=new MultiFormatWriter().encode(value,BarcodeFormat.QR_CODE,pixels,pixels,hints);Bitmap bmp=Bitmap.createBitmap(pixels,pixels,Bitmap.Config.RGB_565);for(int y=0;y<pixels;y++)for(int x=0;x<pixels;x++)bmp.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);return bmp;}catch(Exception e){throw new IllegalArgumentException("Unable to render slideshow QR");}}
    private boolean bluetoothAllowed(){return Build.VERSION.SDK_INT<31||(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED);}
    private void confirmPrint(){if(!ready())return;
        final String url=currentUrl,scope=currentScope,printer=config.get("ticket_printer"),event=currentEvent;
        if(!bluetoothAllowed()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},68);note("Grant Bluetooth permissions and press Print again. Nothing sent.");return;}
        try{TicketProtocol.printer(printer);SlideshowTicketProtocol.url(config.get("base"),url.substring(config.get("base").length()));}catch(Exception e){note("Choose your dedicated ticket printer in QR Ticket setup first.");return;}
        note("Rechecking the slideshow link before printing. No bytes sent yet...");
        io.execute(()->{try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){
            JSONObject latest=api.call("/v1/device/slideshow",null);
            if(!latest.optBoolean("enabled")||latest.isNull("slideshow_path")||!latest.getString("event_id").equals(scope.split("\\|",-1)[1])||!SlideshowTicketProtocol.url(config.get("base"),latest.getString("slideshow_path")).equals(url)){
                note("Slideshow link was disabled or changed. Refresh QR before printing; nothing sent.");return;
            }
            runOnUiThread(()->{if(!foreground||!scope.equals(config.get("scope")))return;
                new AlertDialog.Builder(this).setTitle("Print ONE EVENT SLIDESHOW ticket?")
                .setMessage("Event: "+event+"\nURL: "+url+"\nPrinter: "+config.get("ticket_printer_name")+"\n\nPublic projector link, not a personal guest photo QR. No auto retry.")
                .setNegativeButton("Cancel",null).setPositiveButton("Print ONE",(d,n)->send(url,scope,printer)).show();});
        }catch(Exception e){note("Cannot verify current slideshow link. No ticket printed. Check internet and refresh.");}});
    }
    private void send(String url,String scope,String printer){if(!TicketTransport.BUSY.compareAndSet(false,true)){note("Another ticket operation is active.");return;}
        final String attempt=UUID.randomUUID().toString();final String event=scope.split("\\|",-1)[1];
        io.execute(()->{TicketTransport op=new TicketTransport();operation=op;boolean saved=false;
            try{
                // Revalidate immediately before the immutable ticket snapshot and Bluetooth send.
                // An old/disabled/rotated projector token must never be sent from a cached screen.
                try(CloudApi api=new CloudApi(this,config.get("base"),config.token(),config.cellular())){
                    JSONObject fresh=api.call("/v1/device/slideshow",null);
                    if(!fresh.optBoolean("enabled")||fresh.isNull("slideshow_path")||!fresh.getString("event_id").equals(event)||!SlideshowTicketProtocol.url(config.get("base"),fresh.getString("slideshow_path")).equals(url))
                        throw new IllegalStateException("Slideshow link changed or disabled. Refresh before printing.");
                }
                try(QueueDb q=new QueueDb(this)){
                    SQLiteDatabase db=q.getWritableDatabase();ContentValues values=new ContentValues();values.put("id",attempt);values.put("scope",scope);values.put("event_id",event);values.put("url",url);values.put("printer",printer);values.put("created",System.currentTimeMillis());values.put("state","prepared");
                    db.insertOrThrow("slideshow_tickets",null,values);saved=true;
                }
                if(!foreground||!scope.equals(config.get("scope")))throw new IllegalStateException("Event or screen changed; nothing will be retried");
                byte[] bytes=SlideshowTicketProtocol.packet(url,attempt);
                BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
                op.connect(adapter,printer);
                try(QueueDb q=new QueueDb(this)){q.getWritableDatabase().execSQL("UPDATE slideshow_tickets SET state='sending' WHERE id=?",new Object[]{attempt});}
                op.send(bytes);
                try(QueueDb q=new QueueDb(this)){q.getWritableDatabase().execSQL("UPDATE slideshow_tickets SET state='sent_unconfirmed' WHERE id=?",new Object[]{attempt});}
                note("Slideshow ticket bytes sent (unconfirmed). Scan paper to verify exact URL. No automatic reprint.");
            }catch(Exception e){if(saved)try(QueueDb q=new QueueDb(this)){q.getWritableDatabase().execSQL("UPDATE slideshow_tickets SET state=?,error=? WHERE id=?",new Object[]{op.writeAttempted()?"outcome_unknown":"failed_before_send","operator_check_required",attempt});}catch(Exception ignored){}note("Slideshow ticket not confirmed. Inspect paper before attempting another. No auto retry.");}
            finally{op.close();operation=null;TicketTransport.BUSY.set(false);}
        });
    }
    private void history(){final String scope=config.get("scope");try(QueueDb q=new QueueDb(this);android.database.Cursor c=q.getReadableDatabase().rawQuery("SELECT id,state,observation FROM slideshow_tickets WHERE scope=? ORDER BY created DESC LIMIT 25",new String[]{scope})){
        java.util.ArrayList<String> items=new java.util.ArrayList<>(),ids=new java.util.ArrayList<>();
        while(c.moveToNext()){ids.add(c.getString(0));items.add(c.getString(0).substring(0,8)+" / "+c.getString(1)+" / "+c.getString(2));}
        new AlertDialog.Builder(this).setTitle("Slideshow ticket attempts (NO REPRINT)").setItems(items.toArray(new String[0]),(d,n)->{
            final String ticket=ids.get(n);String[] values={"not_checked","printed_seen","qr_matches","no_output","qr_unreadable","qr_mismatch"};
            new AlertDialog.Builder(this).setTitle("Record actual paper observation").setItems(values,(x,index)->{
                if(!scope.equals(config.get("scope"))){note("Event changed. Observation not saved.");return;}
                try(QueueDb db=new QueueDb(this)){db.getWritableDatabase().execSQL("UPDATE slideshow_tickets SET observation=? WHERE id=? AND scope=?",new Object[]{values[index],ticket,scope});note("Operator observation saved. This is not printer telemetry.");}
                catch(Exception ex){note("Could not save observation; ticket history retained.");}
            }).setNegativeButton("Cancel",null).show();
        }).setPositiveButton("Close",null).show();
    }catch(Exception e){note("Ticket history unavailable. Records were not cleared.");}}
    @Override protected void onResume(){super.onResume();foreground=true;load();}
    @Override protected void onPause(){foreground=false;TicketTransport op=operation;if(op!=null)op.cancel();super.onPause();}
    @Override protected void onDestroy(){TicketTransport op=operation;if(op!=null)op.cancel();io.shutdownNow();super.onDestroy();}
}
