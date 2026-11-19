package app.lensdrop.transfer;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.content.ContentResolver;

/** Primary shared JPEG library only; does not claim access to app-private files. */
final class MediaScanner {
    static final Uri COLLECTION=MediaStore.Images.Media.getContentUri("external_primary");
    private final Context context;
    private boolean recovered=true;
    MediaScanner(Context context){this.context=context;}
    private void permission(){
        String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        if(context.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Full photo access required");
    }
    private String version(){return Build.VERSION.SDK_INT>=30?MediaStore.getVersion(context,"external_primary"):MediaStore.getVersion(context);}
    private Cursor page(String folder,long after){
        Bundle args=new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"mime_type='image/jpeg' AND relative_path=? AND _id>?");
        args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,new String[]{folder,Long.toString(after)});
        args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"_id ASC");
        args.putString(ContentResolver.QUERY_ARG_SQL_LIMIT,"256");
        return context.getContentResolver().query(COLLECTION,new String[]{"_id","_display_name","_size","date_modified","is_pending"},args,null);
    }
    void baseline(Config config,QueueDb queue){
        permission();String scope=config.get("scope"),folder=config.get("folder");
        if(folder.isEmpty())throw new IllegalStateException("Choose a shared folder first");
        WatchLedger ledger=new WatchLedger(queue);if(ledger.ready(scope))return;
        String v=version();long after=-1,count=0;
        android.database.sqlite.SQLiteDatabase db=queue.getWritableDatabase();db.beginTransaction();
        try{
            ledger.begin(scope,folder,v);
            while(true){int n=0;try(Cursor c=page(folder,after)){
                if(c==null)throw new IllegalStateException("Folder provider returned no cursor; baseline NOT accepted");
                while(c.moveToNext()){long row=c.getLong(0);if(row<=after)throw new IllegalStateException("Provider ordering changed");after=row;n++;
                    ledger.exclude(scope,ContentUris.withAppendedId(COLLECTION,row).toString(),c.getLong(2),c.getLong(3));count++;
                }
            }if(n==0)break;}
            if(!v.equals(version()))throw new IllegalStateException("Photo library changed during baseline; start again before shooting");
            ledger.finishBaseline(scope,count);db.setTransactionSuccessful();recovered=false;
        }finally{db.endTransaction();}
    }
    void scan(Config config,QueueDb queue){
        permission();WatchLedger ledger=new WatchLedger(queue);String scope=config.get("scope");
        if(!ledger.ready(scope))throw new IllegalStateException("Start a new-photos-only watch session first");
        if(!ledger.version(scope).equals(version()))throw new IllegalStateException("Photo library was rebuilt. Pause; preserve queue and review session identity.");
        long after=-1;boolean gap=recovered||System.currentTimeMillis()-ledger.lastScan(scope)>90000;
        while(true){int n=0;try(Cursor c=page(config.get("folder"),after)){
            if(c==null)throw new IllegalStateException("Cannot reconcile folder");
            while(c.moveToNext()){
                long row=c.getLong(0);if(row<=after)throw new IllegalStateException("Provider ordering changed");after=row;n++;
                String name=c.getString(1);if(name==null)name="received.jpg";
                ledger.arrival(scope,ContentUris.withAppendedId(COLLECTION,row).toString(),name,c.getLong(2),c.getLong(3),c.getInt(4)!=0,gap);
            }
        }if(n==0)break;}
        ledger.scanned(scope);recovered=false;
    }
    void settle(Config config,QueueDb queue)throws Exception{
        permission();WatchLedger ledger=new WatchLedger(queue);String scope=config.get("scope");
        if(!ledger.ready(scope))return;
        org.json.JSONArray pending=ledger.pending(scope);
        for(int i=0;i<pending.length();i++){
            String uri=pending.getString(i);
            try(Cursor c=context.getContentResolver().query(Uri.parse(uri),new String[]{"_display_name","_size","date_modified","is_pending","relative_path"},null,null,null)){
                if(c!=null&&c.moveToFirst()&&config.get("folder").equals(c.getString(4)))ledger.arrival(scope,uri,c.getString(0),c.getLong(1),c.getLong(2),c.getInt(3)!=0,true);
            }
        }
    }
    boolean unchanged(QueueDb.Job job){
        try(Cursor c=context.getContentResolver().query(Uri.parse(job.uri),new String[]{"_size","date_modified","is_pending"},null,null,null)){
            return c!=null&&c.moveToFirst()&&c.getLong(0)==job.size&&c.getLong(1)==job.modified&&c.getInt(2)==0;
        }
    }
}
