package app.lensdrop.transfer;
import android.database.Cursor;
import org.json.*;
import static app.lensdrop.transfer.WatchLedgerIntegrationTest.*;
public final class GalleryLedgerTest {
 static int total=0;interface Case{void run(Fixture f)throws Exception;}
 static void run(String name,Case c)throws Exception{try(Fixture f=new Fixture()){c.run(f);total++;System.out.println("PASS "+name);}}
 static String fresh(Fixture f,String uri)throws Exception{f.baseline(0);f.q.archivePolicy(S,true);return f.incoming(uri,false);}
 public static void main(String[] args)throws Exception{
 run("Gallery displays unassigned and uploaded event photos without publishing",f->{String j=fresh(f,"content://gallery");GalleryLedger g=new GalleryLedger(f.q);JSONArray a=g.page(S,30);eq(a.length(),1);eq(a.getJSONObject(0).getString("id"),j);eq(a.getJSONObject(0).optString("guest_label"),"");eq(f.delivery(j),null);});
 run("Gallery star survives database reopen and does not change upload state",f->{String j=fresh(f,"content://star");new GalleryLedger(f.q).mark(S,j,"starred",true);f.restart();eq(new GalleryLedger(f.q).page(S,30).getJSONObject(0).optBoolean("starred"),true);eq(f.q.jobState(S,j),"queued");});
 run("Hide only removes gallery tile; archive job remains due",f->{String j=fresh(f,"content://hide");new GalleryLedger(f.q).mark(S,j,"hidden",true);eq(new GalleryLedger(f.q).page(S,30).length(),0);eq(f.q.due(S).id,j);eq(f.q.jobState(S,j),"queued");});
 run("Gallery cannot mark a photo from another event",f->{String j=fresh(f,"content://scope");throwsIt(()->new GalleryLedger(f.q).mark("other",j,"hidden",true));eq(new GalleryLedger(f.q).page(S,30).length(),1);});
 run("Gallery does not include isolated test JPEG",f->{f.baseline(0);f.q.addTest(S,"content://test","test.jpg",50,1);eq(new GalleryLedger(f.q).page(S,30).length(),0);});
 System.out.println("PASSED "+total+" gallery metadata tests.");
 }
}
