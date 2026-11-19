package app.lensdrop.transfer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class EventService extends Service {
    static final java.util.concurrent.atomic.AtomicBoolean RUNNING=new java.util.concurrent.atomic.AtomicBoolean(false);
    private String lastNotice="";private long lastNoticeAt;
    private static final String CHANNEL="lensdrop-transfer";
    private ScheduledExecutorService executor;private UploadEngine engine;
    private final ContentObserver observer=new ContentObserver(new Handler(Looper.getMainLooper())){
        @Override public void onChange(boolean selfChange){if(engine!=null)engine.dirty=true;}
    };
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        RUNNING.set(true);
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Event photo transfer",NotificationManager.IMPORTANCE_LOW));
        startForeground(7,notification("Watch-folder transfer active. Tap to inspect queue."));
        if(engine!=null)return START_NOT_STICKY;
        try{
            engine=new UploadEngine(this);
            getContentResolver().registerContentObserver(MediaScanner.COLLECTION,true,observer);
            executor=Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(()->{
                if(!new Config(this).active()){stopSelf();return;}
                if(!PrintEngine.IN_FLIGHT.get())engine.tick();
                String text=engine.config.get("status");if(!text.equals(lastNotice)||System.currentTimeMillis()-lastNoticeAt>30000){nm.notify(7,notification(text));lastNotice=text;lastNoticeAt=System.currentTimeMillis();}
            },0,2,TimeUnit.SECONDS);
            RecoveryJob.schedule(this);
        }catch(Exception e){new Config(this).status("Cannot start: check pairing and permissions.");stopSelf();}
        // Do not silently restart a user-stopped foreground service.
        return START_NOT_STICKY;
    }
    private Notification notification(String text){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("LensDrop Transfer Alpha")
            .setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).build();
    }
    @Override public void onTimeout(int startId,int fgsType){
        new Config(this).status("Android stopped the background session. Open LensDrop to resume; queue retained.");
        if(engine!=null)engine.stopped=true;
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){
        RUNNING.set(false);
        try{getContentResolver().unregisterContentObserver(observer);}catch(IllegalArgumentException ignored){}
        if(engine!=null)engine.stopped=true;
        if(executor!=null){
            // Queue closure runs after any current checkpoint, never during a DB write.
            executor.execute(()->{if(engine!=null)engine.close();});executor.shutdown();
        }else if(engine!=null)engine.close();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
