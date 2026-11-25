package app.lensdrop.transfer;
import android.content.Context;
import android.database.Cursor;
import java.nio.file.*;
import java.util.*;
/** Local-only verified SQLite behavior; no network, Android runtime or photo printer. */
public final class H5121InstantQrTest {
 static void ok(boolean check,String why){if(!check)throw new AssertionError(why);}
 static final String S="test-origin|test-event|test-device",F="Pictures/TEST";
 static String col(QueueDb q,String id,String column){try(Cursor c=q.getReadableDatabase().rawQuery("SELECT "+column+" FROM jobs WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getString(0):null;}}
 static String arrive(WatchLedger w,QueueDb q,String uri)throws Exception{
  w.arrival(S,uri,"IMG_0001.JPG",120,30,false,false);
  q.getWritableDatabase().execSQL("UPDATE watch_items SET stable_since=0 WHERE scope=? AND uri=?",new Object[]{S,uri});
  w.arrival(S,uri,"IMG_0001.JPG",120,30,false,false);
  try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id FROM jobs WHERE scope=? AND uri=?",new String[]{S,uri})){ok(c.moveToFirst(),"job was not created");return c.getString(0);}
 }
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("lensdrop-h5121-qr-");Config.instantEnabled=true;int passed=0;
  try{
   try(QueueDb q=new QueueDb(new Context(dir.toString()))){
    WatchLedger w=new WatchLedger(q);w.ensure(S,F);w.begin(S,F,"test");w.finishBaseline(S,0);q.archivePolicy(S,true);
    String first=arrive(w,q,"content://photo/a"),token=col(q,first,"early_token");ok(token!=null&&token.matches("[a-f0-9]{32}"),"secure early token missing");passed++;
    ok("queued".equals(col(q,first,"state")),"archived new photo must enter upload queue");passed++;
    w.reserve(S,"group");String g=w.nextGroup(S);String groupPath=w.group(S,g).getString("guest_path");ok(groupPath.matches("/p/[a-f0-9]{32}")&&!w.group(S,g).optBoolean("cloud_registered"),"group QR not created before network");passed++;
    w.select(S,first);ok(g.equals(col(q,first,"delivery_id")),"explicit group assignment lost");passed++;
    w.reserve(S,"group");String next=w.nextGroup(S);
    String latest=w.latestGroup(S);ok(g.equals(latest)&&next.equals(w.active(S)),"latest QR must not move receiver");passed++;
    w.cloudGroup(g,groupPath,"G0001");ok(w.group(S,g).optBoolean("cloud_registered"),"Cloud receipt must mark group synced");passed++;
    w.mode(S,"single");String second=arrive(w,q,"content://photo/b");String selected=w.select(S,second);
    ok(second.equals(selected)&&col(q,second,"delivery_id")==null&&col(q,second,"early_token")!=null,"Single mode should not require prepared Cloud group");passed++;
    String before=col(q,second,"early_token");q.primeInstant(S);ok(before.equals(col(q,second,"early_token")),"recovery must never regenerate token");passed++;
   }
   try(QueueDb reopened=new QueueDb(new Context(dir.toString()))){
    try(Cursor c=reopened.getReadableDatabase().rawQuery("PRAGMA user_version",null)){c.moveToFirst();ok(c.getInt(0)==10,"schema must be 10");}passed++;
    try(Cursor c=reopened.getReadableDatabase().rawQuery("SELECT count(*) FROM jobs WHERE early_token IS NOT NULL",null)){c.moveToFirst();ok(c.getLong(0)>=2,"durable tokens lost after restart");}passed++;
   }
   System.out.println("H5.12.1 instant local QR: "+passed+"/"+passed+" assertions PASS (SQLite bridge, not native APK)");
  }finally{Config.instantEnabled=false;try(var list=Files.walk(dir)){for(Path p:list.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
}
