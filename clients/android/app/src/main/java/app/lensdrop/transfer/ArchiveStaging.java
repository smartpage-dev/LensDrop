package app.lensdrop.transfer;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;

/** Archive an exact confirmed print image independently of physical-print success. */
final class ArchiveStaging {
    static String register(Context context,String scope,File printFile,JSONObject spec,String delivery)throws Exception{
        String sha=spec.getString("sha256");long size=spec.getLong("bytes");
        try(QueueDb q=new QueueDb(context)){
            if(delivery!=null&&!delivery.isEmpty())try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id FROM jobs WHERE scope=? AND sha=? AND size=? AND is_test=0 AND delivery_id IS NOT NULL AND delivery_id<>? LIMIT 1",new String[]{scope,sha,Long.toString(size),delivery})){if(c.moveToFirst())throw new IllegalStateException("This photo already has a different guest assignment. No new print queued.");}
            String existing=null;
            try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id FROM jobs WHERE scope=? AND sha=? AND size=? AND is_test=0 AND archive_private=1 AND state NOT IN ('retired_local','skipped') ORDER BY created LIMIT 1",new String[]{scope,sha,Long.toString(size)})){if(c.moveToFirst())existing=c.getString(0);}
            if(existing!=null){assign(q,scope,existing,delivery);return existing;}
            File dir=new File(context.getFilesDir(),"spool");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Archive staging unavailable");
            long used=0;File[] list=dir.listFiles();if(list!=null)for(File f:list)if(f.isFile())used+=f.length();
            if(used+size>256L*1024*1024||dir.getUsableSpace()<1024L*1024*1024+size)throw new IllegalStateException("Archive storage limit reached. No print job created; reconcile pending archives first.");
            String id=UUID.randomUUID().toString();File target=new File(dir,id+".jpg");boolean committed=false;
            try{
                MessageDigest hash=MessageDigest.getInstance("SHA-256");long n=0;
                try(InputStream in=new FileInputStream(printFile);FileOutputStream out=new FileOutputStream(target)){byte[] b=new byte[65536];int count;while((count=in.read(b))!=-1){n+=count;if(n>size)throw new IOException("Print snapshot changed");hash.update(b,0,count);out.write(b,0,count);}out.getFD().sync();}
                if(n!=size||!CloudApi.hex(hash.digest()).equals(sha))throw new IOException("Print archive checksum mismatch");DirectorySync.sync(dir);
                SQLiteDatabase db=q.getWritableDatabase();db.beginTransaction();try{
                    ContentValues v=new ContentValues();v.put("id",id);v.put("scope",scope);v.put("uri","lensdrop-print:"+spec.getString("id"));v.put("name","Print-"+spec.getString("id")+".jpg");v.put("size",size);v.put("modified",0L);v.put("created",System.currentTimeMillis());v.put("state","queued");v.put("spool",target.getAbsolutePath());v.put("sha",sha);v.put("archive_private",1);db.insertOrThrow("jobs",null,v);
                    assignInTransaction(db,scope,id,delivery);db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                committed=true;return id;
            }finally{if(!committed)target.delete();}
        }
    }
    private static void assign(QueueDb q,String scope,String job,String delivery){SQLiteDatabase db=q.getWritableDatabase();db.beginTransaction();try{assignInTransaction(db,scope,job,delivery);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    private static void assignInTransaction(SQLiteDatabase db,String scope,String job,String delivery){
        if(delivery==null||delivery.isEmpty())return;
        String old;try(Cursor c=db.rawQuery("SELECT delivery_id FROM jobs WHERE scope=? AND id=?",new String[]{scope,job})){if(!c.moveToFirst())throw new IllegalStateException("Archive unavailable");old=c.getString(0);}
        if(old!=null&&!old.equals(delivery))throw new IllegalStateException("This exact photo is assigned to another guest. No new print queued.");
        String kind;try(Cursor c=db.rawQuery("SELECT kind FROM guest_groups WHERE scope=? AND id=? AND state='open' AND guest_path IS NOT NULL",new String[]{scope,delivery})){if(!c.moveToFirst())throw new IllegalStateException("Guest QR changed; reopen preview");kind=c.getString(0);}
        if(kind.equals("single"))try(Cursor c=db.rawQuery("SELECT id FROM jobs WHERE scope=? AND delivery_id=? AND id<>? LIMIT 1",new String[]{scope,delivery,job})){if(c.moveToFirst())throw new IllegalStateException("Single Photo QR is already bound. Use its existing image; do not replace it.");}
        db.execSQL("UPDATE jobs SET delivery_id=? WHERE scope=? AND id=?",new Object[]{delivery,scope,job});
    }
}
