package app.lensdrop.transfer;
import android.content.Context;
import android.database.Cursor;
import org.json.JSONObject;
import java.nio.file.*;
import java.util.*;
/** Real SQLite bridge, network-free fake Cloud: targeted LD-001/002/003. */
public final class H5122P1RegressionTest {
 static final String S="https://example.org|event-a|device-a",F="Pictures/Test/",PRINTER="02:00:00:00:00:01";
 static int tests;
 static void ok(boolean cond,String reason){if(!cond)throw new AssertionError(reason);}
 static String col(QueueDb q,String id,String field){try(Cursor c=q.getReadableDatabase().rawQuery("SELECT "+field+" FROM jobs WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getString(0):null;}}
 static String arrive(WatchLedger w,QueueDb q,String uri)throws Exception{
  w.arrival(S,uri,"IMG_0001.JPG",120,30,false,false);
  q.getWritableDatabase().execSQL("UPDATE watch_items SET stable_since=0 WHERE scope=? AND uri=?",new Object[]{S,uri});
  w.arrival(S,uri,"IMG_0001.JPG",120,30,false,false);
  try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id FROM jobs WHERE scope=? AND uri=?",new String[]{S,uri})){ok(c.moveToFirst(),"new photo absent");return c.getString(0);}
 }
 interface Case {void run(QueueDb q,WatchLedger w)throws Exception;}
 static void test(String name,Case body)throws Exception{
  Path dir=Files.createTempDirectory("h5122-regression-");Config.instantEnabled=true;CloudApi.calls.clear();
  try(QueueDb q=new QueueDb(new Context(dir.toString()))){WatchLedger w=new WatchLedger(q);w.ensure(S,F);w.begin(S,F,"test");w.finishBaseline(S,0);q.archivePolicy(S,true);body.run(q,w);tests++;System.out.println("PASS "+name);}
  finally{Config.instantEnabled=false;try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
 static void reject(Runnable work){try{work.run();}catch(IllegalArgumentException|IllegalStateException e){return;}throw new AssertionError("unsafe operation accepted");}
 public static void main(String[] args)throws Exception{
  test("LD-001 registered G-label prints; unsynced QR URL preserved",(q,w)->{
   w.reserve(S,"group");String g=w.nextGroup(S),path=w.group(S,g).getString("guest_path");w.cloudGroup(g,path,"G0001");
   String id=UUID.randomUUID().toString();new TicketLedger(q).create(S,id,g,"G0001","https://example.org"+path,PRINTER,null);
   ok(new TicketLedger(q).get(S,id)!=null,"group ticket missing");
   reject(()->TicketProtocol.label("G0001\nESC"));
  });
  test("LD-001 early Single, registered Single and deliberate same-link reprint",(q,w)->{
   String job=arrive(w,q,"content://photo/single");String token=col(q,job,"early_token"),path="/p/"+token;
   String first=UUID.randomUUID().toString(),label=TicketProtocol.earlySingleLabel(job),url="https://example.org"+path;
   TicketLedger t=new TicketLedger(q);t.create(S,first,job,label,url,PRINTER,null);
   String cloudId=UUID.randomUUID().toString();q.getWritableDatabase().execSQL("UPDATE jobs SET single_id=?,single_label='S0001',single_path=?,early_registered=1 WHERE id=?",new Object[]{cloudId,path,job});
   t.create(S,UUID.randomUUID().toString(),cloudId,"S0001",url,PRINTER,null);
   t.create(S,UUID.randomUUID().toString(),job,label,url,PRINTER,first);
   reject(()->{try{t.create(S,UUID.randomUUID().toString(),cloudId,"S0001","https://example.org/p/"+"0".repeat(32),PRINTER,null);}catch(IllegalStateException|IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}});
   ok(t.get(S,first).getString("guest_url").equals(url),"printed link changed");
  });
  test("LD-002 uploaded before registration waits, then attaches without manual retry",(q,w)->{
   w.reserve(S,"group");String g=w.nextGroup(S);String job=arrive(w,q,"content://photo/group");w.select(S,job);
   String photo=UUID.randomUUID().toString();q.getWritableDatabase().execSQL("UPDATE jobs SET state='uploaded',photo_id=? WHERE id=?",new Object[]{photo,job});
   Set<String> registered=new HashSet<>();CloudApi.handler=(route,body)->{
    if(route.equals("/v1/device/deliveries")){registered.add(body.optString("id"));return new JSONObject().put("id",body.getString("id")).put("kind","group").put("guest_path","/p/"+body.getString("token")).put("label","G0001");}
    if(route.endsWith("/delivery"))return registered.contains(body.optString("delivery_id"))?new JSONObject().put("photo_id",photo).put("delivery_id",g):new CloudApi.Failure("delivery_not_found",false,0);
    return new CloudApi.Failure("unexpected",false,0);
   };
   CloudApi api=new CloudApi();DeliveryCoordinator.syncAssignments(q,S,api);
   ok(CloudApi.calls.isEmpty(),"unregistered group was sent to the Cloud");ok("0".equals(col(q,job,"delivery_synced")),"assigned before registration");ok(q.needsGroupRegistration(S),"pending group not detected");
   DeliveryCoordinator.sync(q,S,api);DeliveryCoordinator.syncAssignments(q,S,api);
   ok("1".equals(col(q,job,"delivery_synced")),"not attached after group receipt");ok("0".equals(col(q,job,"delivery_next")),"old 404 poisoned assignment");
  });
  test("LD-002 server lost group: same ID is re-registered and retried",(q,w)->{
   w.reserve(S,"group");String g=w.nextGroup(S),path=w.group(S,g).getString("guest_path");w.cloudGroup(g,path,"G0001");
   String job=arrive(w,q,"content://photo/lost");w.select(S,job);String photo=UUID.randomUUID().toString();q.getWritableDatabase().execSQL("UPDATE jobs SET state='uploaded',photo_id=? WHERE id=?",new Object[]{photo,job});
   Set<String> registered=new HashSet<>();CloudApi.handler=(route,body)->{
    if(route.equals("/v1/device/deliveries")){registered.add(body.optString("id"));return new JSONObject().put("id",body.getString("id")).put("kind","group").put("guest_path",path).put("label","G0001");}
    if(route.endsWith("/delivery"))return registered.contains(g)?new JSONObject().put("photo_id",photo).put("delivery_id",g):new CloudApi.Failure("delivery_not_found",false,0);
    return new CloudApi.Failure("unexpected",false,0);
   };
   CloudApi api=new CloudApi();DeliveryCoordinator.syncAssignments(q,S,api);
   ok(q.needsGroupRegistration(S),"missing group did not trigger re-registration");
   DeliveryCoordinator.sync(q,S,api);q.getWritableDatabase().execSQL("UPDATE jobs SET delivery_next=0 WHERE id=?",new Object[]{job});DeliveryCoordinator.syncAssignments(q,S,api);
   ok("1".equals(col(q,job,"delivery_synced")),"assignment not recovered");ok(path.equals(w.group(S,g).getString("guest_path")),"QR identity changed");
  });
  test("LD-003 401 resume with same Single token after Retry",(q,w)->{
   String job=arrive(w,q,"content://photo/reauth"),token=col(q,job,"early_token");
   CloudApi.handler=(route,body)->new CloudApi.Failure("device_token_invalid_or_revoked",false,0);
   CloudApi api=new CloudApi();SingleSync.one(q,S,api);
   ok(!String.valueOf(Long.MAX_VALUE).equals(col(q,job,"early_next")),"401 was parked forever");
   q.retryBlocked(S);ok("0".equals(col(q,job,"early_next")),"manual Retry did not resume Single");
   CloudApi.handler=(route,body)->new JSONObject().put("id",UUID.randomUUID().toString()).put("kind","single").put("label","S0001").put("guest_path","/p/"+body.getString("token"));
   SingleSync.one(q,S,api);ok("1".equals(col(q,job,"early_registered")),"Single not registered after reauth");ok(token.equals(col(q,job,"early_token")),"Single token replaced");
  });
  test("LD-003 400 visible parked error can be manually retried, no new token",(q,w)->{
   String job=arrive(w,q,"content://photo/invalid"),token=col(q,job,"early_token");
   CloudApi.handler=(route,body)->new CloudApi.Failure("invalid_preissue_filename",false,0);
   CloudApi api=new CloudApi();SingleSync.one(q,S,api);
   ok(String.valueOf(Long.MAX_VALUE).equals(col(q,job,"early_next")),"validation failure was not parked");
   q.retryBlocked(S);ok("0".equals(col(q,job,"early_next")),"explicit Retry did not unpark");ok(token.equals(col(q,job,"early_token")),"token changed");
  });
  test("Old H5.12.1 parked rows are recovered once without changing identity",(q,w)->{
   String job=arrive(w,q,"content://photo/old"),token=col(q,job,"early_token");
   q.getWritableDatabase().execSQL("UPDATE jobs SET early_next=? WHERE id=?",new Object[]{Long.MAX_VALUE,job});
   q.recoverParkedLinks(S);ok("0".equals(col(q,job,"early_next")),"old Single not recovered");ok(token.equals(col(q,job,"early_token")),"guest link changed");
   q.recoverParkedLinks(S);ok("0".equals(col(q,job,"early_next")),"second recovery should be idempotent");
  });
  System.out.println("H5.12.2 Claude P1 regression: "+tests+"/"+tests+" PASS (SQLite/JVM bridge, not physical device)");
 }
}