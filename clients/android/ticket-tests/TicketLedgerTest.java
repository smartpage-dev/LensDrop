package app.lensdrop.transfer;
import android.content.Context;import android.database.Cursor;import org.json.*;import java.nio.file.*;import java.util.*;
public final class TicketLedgerTest {
 static int passed;static final String B="https://example.org",S=B+"|event-a|device-a",OTHER=B+"|event-b|device-b",P="02:00:00:00:00:01";
 interface Check{void run(F f)throws Exception;}interface Bad{void run()throws Exception;}
 static void yes(boolean b){if(!b)throw new AssertionError();}static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 static void reject(Bad b)throws Exception{try{b.run();}catch(IllegalArgumentException|IllegalStateException e){return;}throw new AssertionError("must reject");}
 static final class F implements AutoCloseable {
  final Path dir=Files.createTempDirectory("lensdrop-ticket-");QueueDb q;WatchLedger w;TicketLedger t;
  F()throws Exception{open();}void open(){q=new QueueDb(new Context(dir.toString()));w=new WatchLedger(q);t=new TicketLedger(q);w.ensure(S,"Pictures/Test/");}
  void restart(){q.close();open();}
  String group(String kind)throws Exception{w.reserve(S,kind);JSONArray a=w.toSync(S);for(int i=0;i<a.length();i++)w.cloudGroup(a.getJSONObject(i).getString("id"),"/p/"+UUID.randomUUID().toString().replace("-",""));if(kind.equals("group"))return w.nextGroup(S);w.mode(S,"single");String id=UUID.randomUUID().toString();q.add(S,"content://image/"+id,"ONE.JPG",100,1);q.getWritableDatabase().execSQL("UPDATE jobs SET state='candidate' WHERE uri=?",new Object[]{"content://image/"+id});String job;try(Cursor c=q.getReadableDatabase().rawQuery("SELECT id FROM jobs WHERE uri=?",new String[]{"content://image/"+id})){c.moveToFirst();job=c.getString(0);}return w.select(S,job);}
  String ticket(String g,String parent)throws Exception{String id=UUID.randomUUID().toString();JSONObject group=w.group(S,g);t.create(S,id,g,group.getString("label"),B+group.getString("guest_path"),P,parent);return id;}
  long count(String sql){try(Cursor c=q.getReadableDatabase().rawQuery(sql,null)){c.moveToFirst();return c.getLong(0);}}
  public void close()throws Exception{q.close();try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
 static void test(String n,Check c)throws Exception{try(F f=new F()){c.run(f);passed++;System.out.println("PASS "+n);}}
 public static void main(String[] args)throws Exception{
  test("New queue schema6 and empty ticket journal",f->{eq(f.count("PRAGMA user_version"),10L);eq(f.count("SELECT count(*) FROM qr_tickets"),0L);});
  test("Snapshot binds shown link without changing receiver",f->{String a=f.group("group"),b=f.group("group");f.w.show(S,a);String id=f.ticket(a,null);eq(f.w.active(S),b);eq(f.t.get(S,id).getString("delivery_id"),a);});
  test("Repeated preview-confirm identity inserts only once",f->{String g=f.group("group"),id=f.ticket(g,null);JSONObject r=f.t.get(S,id);f.t.create(S,id,g,r.getString("label"),r.getString("guest_url"),P,null);eq(f.count("SELECT count(*) FROM qr_tickets"),1L);});
  test("Same ticket ID with different printer is rejected",f->{String g=f.group("group"),id=f.ticket(g,null);JSONObject r=f.t.get(S,id);reject(()->f.t.create(S,id,g,r.getString("label"),r.getString("guest_url"),"02:00:00:00:00:02",null));});
  test("Cannot print a group from another scope",f->{String g=f.group("group");JSONObject r=f.w.group(S,g);reject(()->f.t.create(OTHER,UUID.randomUUID().toString(),g,r.getString("label"),B+r.getString("guest_path"),P,null));});
  test("Cannot print a retired empty group",f->{f.group("group");String g=f.group("group");f.w.undo(S);reject(()->f.ticket(g,null));});
  test("Open single-photo QR can produce a ticket",f->{String g=f.group("single"),id=f.ticket(g,null);eq(f.t.get(S,id).getString("delivery_id"),g);});
  test("First claim only; duplicate send claim rejected",f->{String id=f.ticket(f.group("group"),null);yes(f.t.claim(S,id));yes(!f.t.claim(S,id));});
  test("Sent is explicitly unconfirmed until operator observation",f->{String id=f.ticket(f.group("group"),null);f.t.claim(S,id);f.t.end(S,id,"sent_unconfirmed",null);eq(f.t.get(S,id).getString("observation"),"not_checked");});
  test("Reconnect/reopen cannot re-claim sent ticket",f->{String id=f.ticket(f.group("group"),null);f.t.claim(S,id);f.t.end(S,id,"sent_unconfirmed",null);f.restart();f.t.recover();yes(!f.t.claim(S,id));eq(f.t.get(S,id).getString("state"),"sent_unconfirmed");});
  test("Crash during sending becomes outcome_unknown, never queued",f->{String id=f.ticket(f.group("group"),null);f.t.claim(S,id);f.restart();f.t.recover();eq(f.t.get(S,id).getString("state"),"outcome_unknown");yes(!f.t.claim(S,id));});
  test("Prepared but undispatched attempt is cancelled on reopen",f->{String id=f.ticket(f.group("group"),null);f.restart();f.t.recover();eq(f.t.get(S,id).getString("state"),"cancelled_before_send");});
  test("Explicit reprint gets new ID but same guest URL",f->{String g=f.group("group"),first=f.ticket(g,null);f.t.claim(S,first);f.t.end(S,first,"outcome_unknown","connection_interrupted");String next=f.ticket(g,first);yes(!first.equals(next));eq(f.t.get(S,next).getString("guest_url"),f.t.get(S,first).getString("guest_url"));eq(f.t.get(S,next).getString("parent_id"),first);});
  test("Cannot reprint a sending attempt",f->{String g=f.group("group"),id=f.ticket(g,null);f.t.claim(S,id);reject(()->f.ticket(g,id));});
  test("Cannot use different group as reprint parent",f->{String a=f.ticket(f.group("group"),null);String b=f.group("group");reject(()->f.ticket(b,a));});
  test("Issuing even empty group protects guest QR from Undo",f->{String a=f.group("group"),b=f.group("group");f.ticket(b,null);reject(()->f.w.undo(S));eq(f.w.active(S),b);f.w.resume(S,a);eq(f.w.active(S),a);eq(f.w.group(S,b).getString("state"),"open");f.w.show(S,b);eq(f.w.shown(S),b);});
  test("Old empty-group Undo behavior retained when no ticket exists",f->{String a=f.group("group"),b=f.group("group");f.w.undo(S);eq(f.w.group(S,b).getString("state"),"cancel_pending");eq(f.w.active(S),a);});
  test("Group changes do not mutate saved QR or printer",f->{String a=f.group("group"),id=f.ticket(a,null);JSONObject before=f.t.get(S,id);f.group("group");f.w.mode(S,"single");f.restart();eq(f.t.get(S,id).getString("guest_url"),before.getString("guest_url"));eq(f.t.get(S,id).getString("printer"),P);});
  test("Database rejects direct snapshot mutation",f->{String id=f.ticket(f.group("group"),null);reject(()->f.q.getWritableDatabase().execSQL("UPDATE qr_tickets SET guest_url='https://evil.example' WHERE id=?",new Object[]{id}));});
  test("Other event cannot see or claim ticket",f->{String id=f.ticket(f.group("group"),null);eq(f.t.get(OTHER,id),null);yes(!f.t.claim(OTHER,id));eq(f.t.history(OTHER,0).length(),0);});
  test("Operator scan observation does not rewrite send state",f->{String id=f.ticket(f.group("group"),null);f.t.claim(S,id);f.t.end(S,id,"sent_unconfirmed",null);f.t.observe(S,id,"qr_matches");eq(f.t.get(S,id).getString("observation"),"qr_matches");eq(f.t.get(S,id).getString("state"),"sent_unconfirmed");});
  test("Observations do not mark pending jobs done",f->{String id=f.ticket(f.group("group"),null);f.t.observe(S,id,"qr_matches");eq(f.t.get(S,id).getString("observation"),"not_checked");});
  test("Unsafe states and raw error strings rejected",f->{String id=f.ticket(f.group("group"),null);reject(()->f.t.end(S,id,"printed",null));reject(()->f.t.end(S,id,"outcome_unknown","private URL=https://secret"));reject(()->f.t.observe(S,id,"success"));});
  test("Diagnostics omit guest URL and printer identity",f->{String id=f.ticket(f.group("group"),null);String out=f.t.diagnostics(S).toString();yes(!out.contains(P));yes(!out.contains(B));yes(!out.contains(id));yes(!out.contains(f.t.get(S,id).getString("guest_url")));});
  test("History pages keep all attempts; no pending eviction",f->{String g=f.group("group");for(int i=0;i<55;i++)f.ticket(g,null);eq(f.t.history(S,0).length(),50);eq(f.t.history(S,50).length(),5);eq(f.count("SELECT count(*) FROM qr_tickets"),55L);});
  test("V3 to V6 migration preserves original jobs and settings",f->{f.q.close();Files.delete(f.dir.resolve("lensdrop-queue.sqlite"));try(LegacyQueueDb old=new LegacyQueueDb(new Context(f.dir.toString()))){old.claimSource(S,"Pictures/Test/");old.add(S,"content://existing","existing.JPG",123,9);old.getWritableDatabase().execSQL("INSERT INTO watch_sessions(scope,folder,provider_version,baseline_ready,baseline_count) VALUES(?,?,?,1,500)",new Object[]{S,"Pictures/Test/","v3"});}f.open();eq(f.count("PRAGMA user_version"),10L);eq(f.count("SELECT count(*) FROM jobs"),1L);eq(f.w.summary(S).optLong("excluded"),500L);eq(f.count("SELECT count(*) FROM qr_tickets"),0L);});
  test("A missing prepared guest path cannot create a ticket",f->{f.w.reserve(S,"group");JSONObject r=f.w.toSync(S).getJSONObject(0);reject(()->f.t.create(S,UUID.randomUUID().toString(),r.getString("id"),r.getString("label"),B+"/p/"+"0".repeat(32),P,null));});
  test("Claim for unknown ID is harmless",f->{yes(!f.t.claim(S,UUID.randomUUID().toString()));eq(f.count("SELECT count(*) FROM qr_tickets"),0L);});
  System.out.println("Ticket ledger checks passed: "+passed+" against actual SQLite via JVM bridge. Not Android instrumentation.");
 }
}
