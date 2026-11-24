package app.lensdrop.transfer;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Event-scoped, read-mostly gallery. Star/hide are LOCAL UI flags, not photo deletion. */
final class GalleryLedger {
    private final QueueDb q;
    GalleryLedger(QueueDb q){this.q=q;}
    JSONArray page(String scope,int limit)throws Exception{return page(scope,limit,false);}
    JSONArray page(String scope,int limit,boolean includeHidden)throws Exception{
        int bounded=Math.max(1,Math.min(90,limit));
        JSONArray result=new JSONArray();
        try(Cursor c=q.getReadableDatabase().rawQuery(
            "SELECT j.id,j.uri,j.name,j.size,j.modified,j.created,j.state,j.photo_id,j.spool,j.delivery_id,j.archive_private,j.delivery_synced,COALESCE(m.starred,0),COALESCE(m.hidden,0),g.label " +
            "FROM jobs j LEFT JOIN gallery_marks m ON m.scope=j.scope AND m.job_id=j.id " +
            "LEFT JOIN guest_groups g ON g.scope=j.scope AND g.id=j.delivery_id " +
            "WHERE j.scope=? AND j.is_test=0 " +(includeHidden?"":"AND COALESCE(m.hidden,0)=0 ") +
            "ORDER BY j.created DESC,j.id DESC LIMIT ?",new String[]{scope,Integer.toString(bounded)})){
            while(c.moveToNext()){
                JSONObject p=new JSONObject();p.put("id",c.getString(0));p.put("uri",c.getString(1));p.put("name",c.getString(2));
                p.put("size",c.getLong(3));p.put("modified",c.getLong(4));p.put("created",c.getLong(5));
                p.put("state",c.getString(6));p.put("photo_id",c.getString(7));p.put("spool",c.getString(8));
                p.put("delivery_id",c.getString(9));p.put("archive_private",c.getInt(10)==1);
                p.put("delivery_synced",c.getInt(11)==1);p.put("starred",c.getInt(12)==1);p.put("hidden",c.getInt(13)==1);
                p.put("guest_label",c.getString(14));result.put(p);
            }
        }
        return result;
    }
    void mark(String scope,String id,String kind,boolean enabled){
        if(!kind.equals("starred")&&!kind.equals("hidden"))throw new IllegalArgumentException("Invalid local gallery mark");
        SQLiteDatabase db=q.getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT 1 FROM jobs WHERE scope=? AND id=? AND is_test=0",new String[]{scope,id})){
            if(!c.moveToFirst())throw new IllegalStateException("Photo not in selected event");
        }
        db.beginTransaction();
        try{
            db.execSQL("INSERT OR IGNORE INTO gallery_marks(scope,job_id,changed) VALUES(?,?,?)",new Object[]{scope,id,System.currentTimeMillis()});
            db.execSQL("UPDATE gallery_marks SET "+kind+"=?,changed=? WHERE scope=? AND job_id=?",new Object[]{enabled?1:0,System.currentTimeMillis(),scope,id});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
