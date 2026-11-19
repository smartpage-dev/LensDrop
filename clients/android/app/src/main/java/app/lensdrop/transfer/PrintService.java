package app.lensdrop.transfer;
import android.app.*;import android.content.Intent;import android.os.IBinder;import java.util.concurrent.*;

/** User-started print transfer; not a promise of perpetual Android background execution. */
public final class PrintService extends Service {
    private ScheduledExecutorService worker;
    public int onStartCommand(Intent intent,int flags,int id){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("lensdrop-print","Event print transfer",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,20,new Intent(this,PrintActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);startForeground(8,new Notification.Builder(this,"lensdrop-print").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("LensDrop Print Queue").setContentText("LAN / cloud print transfer. Tap for status or pause.").setContentIntent(open).setOngoing(true).build());if(worker==null){worker=Executors.newSingleThreadScheduledExecutor();PrintEngine engine=new PrintEngine(this);worker.scheduleWithFixedDelay(()->{if(!new Config(this).prefs.getBoolean("print_active",false)){stopSelf();return;}engine.tick();},0,3,TimeUnit.SECONDS);}return START_NOT_STICKY;}
    @Override public void onTimeout(int startId,int fgsType){new Config(this).prefs.edit().putBoolean("print_active",false).putString("print_status","Android paused background printing. Reopen and Resume; queue retained.").commit();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    public void onDestroy(){if(worker!=null)worker.shutdown();super.onDestroy();}
    public IBinder onBind(Intent intent){return null;}
}
