package app.lensdrop.transfer;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecoveryJob extends JobService {
    private static final int ID=42;
    private volatile boolean stopped;private volatile UploadEngine engine;
    private ExecutorService executor;
    static void schedule(Context context){
        JobInfo info=new JobInfo.Builder(ID,new ComponentName(context,RecoveryJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(15*60*1000L).build();
        int result=context.getSystemService(JobScheduler.class).schedule(info);
        if(result==JobScheduler.RESULT_FAILURE)new Config(context).status("Periodic recovery unavailable; reopen app to resume.");
    }
    static void cancel(Context context){context.getSystemService(JobScheduler.class).cancel(ID);}
    @Override public boolean onStartJob(JobParameters parameters){
        if(EventService.RUNNING.get()||!new Config(this).active())return false;
        stopped=false;executor=Executors.newSingleThreadExecutor();
        executor.execute(()->{
            try{
                engine=new UploadEngine(this);long deadline=System.currentTimeMillis()+60000;
                while(!stopped&&!EventService.RUNNING.get()&&System.currentTimeMillis()<deadline){engine.tick();Thread.sleep(2000);}
            }catch(Exception ignored){}finally{
                if(engine!=null)engine.close();if(!stopped)jobFinished(parameters,false);executor.shutdown();
            }
        });return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){stopped=true;if(engine!=null)engine.stopped=true;return true;}
}
