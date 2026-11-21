package app.lensdrop.transfer;
import java.util.*;import java.nio.charset.StandardCharsets;
public final class TicketProtocolTest {
 static int passed;interface Check{void run()throws Exception;}
 static void test(String name,Check c)throws Exception{c.run();passed++;System.out.println("PASS "+name);}
 static void yes(boolean b){if(!b)throw new AssertionError();}
 static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("expected rejection");}
 public static void main(String[] args)throws Exception{
  String base="https://example.org",path="/p/0123456789abcdef0123456789abcdef",scope=base+"|event-test|device-test",id="12345678-1234-4123-8123-123456789abc",group="fedcba98-1234-4123-8123-123456789abc";
  test("Prepared HTTPS URL is preserved byte-for-byte",()->yes((base+path).equals(TicketProtocol.guestUrl(base,path))));
  test("Model2/M/4 commands identical to independently retained Lab protocol",()->yes(Arrays.equals(TicketProtocol.nativeQr(base+path),LabProtocolReference.nativeQr(base+path,4))));
  test("32-byte fixture command equality (does not claim physical scan)",()->{String url="https://example.org/123456789012";yes(url.length()==32);yes(Arrays.equals(TicketProtocol.nativeQr(url),LabProtocolReference.nativeQr(url,4)));});
  test("Long host guest-link command equality",()->{String url=TicketProtocol.guestUrl("https://event-subdomain-long-name.example.org",path);yes(Arrays.equals(TicketProtocol.nativeQr(url),LabProtocolReference.nativeQr(url,4)));});
  test("Max URL boundary accepted",()->{String b="https://"+"a".repeat(52)+"."+"b".repeat(52)+".example.org";String url=TicketProtocol.guestUrl(b,path);yes(url.length()==160);});
  test("Overlong guest URL blocked, never silently shortened",()->reject(()->TicketProtocol.guestUrl("https://"+"a".repeat(140)+".example.org",path)));
  test("HTTP origin blocked",()->reject(()->TicketProtocol.guestUrl("http://example.org",path)));
  test("User credentials blocked",()->reject(()->TicketProtocol.guestUrl("https://secret@example.org",path)));
  test("Origin path blocked",()->reject(()->TicketProtocol.guestUrl("https://example.org/admin",path)));
  test("Origin trailing slash blocked rather than normalized after signing",()->reject(()->TicketProtocol.guestUrl(base+"/",path)));
  test("Query token origin blocked",()->reject(()->TicketProtocol.guestUrl(base+"?token=x",path)));
  test("Fragment origin blocked",()->reject(()->TicketProtocol.guestUrl(base+"#secret",path)));
  test("Staff QR cannot become guest ticket",()->reject(()->TicketProtocol.guestUrl(base,"/pair/0123456789abcdef0123456789abcdef")));
  test("Guest query/fragment additions blocked",()->{reject(()->TicketProtocol.guestUrl(base,path+"?x=1"));reject(()->TicketProtocol.guestUrl(base,path+"#x"));});
  test("Cross-origin guest URL blocked",()->reject(()->TicketProtocol.validateUrlForScope(scope,"https://other.example"+path)));
  test("Missing or malformed session rejected",()->reject(()->TicketProtocol.validateUrlForScope("bad",base+path)));
  test("Control-character URL blocked",()->reject(()->TicketProtocol.guestUrl("https://example.org\n",path)));
  test("Unicode labels not sent to unknown printer font",()->reject(()->TicketProtocol.label("Group \u4e00")));
  test("Command injection labels blocked",()->reject(()->TicketProtocol.label("Group 0001\n\u001b@")));
  test("Normal Group and single Photo labels accepted",()->{yes(TicketProtocol.label("Group 0001").equals("Group 0001"));yes(TicketProtocol.label("Photo 0002").equals("Photo 0002"));});
  test("Only explicit address-shaped device target accepted",()->{yes(TicketProtocol.printer("02:00:00:00:00:01").endsWith("01"));reject(()->TicketProtocol.printer("T58"));});
  test("Malformed snapshot UUID rejected",()->reject(()->TicketProtocol.uuid("1-1-1-1-1")));
  test("Ticket contains exact URL and bounded ASCII framing",()->{byte[] b=TicketProtocol.packet(scope,id,group,"Group 0001",base+path);String s=new String(b,StandardCharsets.ISO_8859_1);yes(s.contains(base+path));yes(s.contains("REF FEDCBA98"));yes(b.length<2048);});
  test("No cutter/status/nonvolatile commands in generated framing",()->{byte[] b=TicketProtocol.packet(scope,id,group,"Photo 0001",base+path);for(int i=0;i<b.length-1;i++){yes(!(b[i]==29&&b[i+1]==86));yes(!(b[i]==16&&b[i+1]==4));yes(!(b[i]==27&&b[i+1]==112));}});
  test("Native QR limit applies independently",()->reject(()->TicketProtocol.nativeQr("x".repeat(161))));
  test("Outcome allowlist separates write result from observation",()->{yes(TicketProtocol.finished("sent_unconfirmed"));yes(TicketProtocol.finished("outcome_unknown"));yes(!TicketProtocol.finished("printed"));yes(TicketProtocol.observation("qr_matches"));yes(!TicketProtocol.observation("success"));});
  test("Exact lab chunking constants preserved",()->{yes(TicketProtocol.CHUNK_BYTES==128);yes(TicketProtocol.CHUNK_DELAY_MS==20);});
  System.out.println("Ticket protocol checks passed: "+passed+". No Android transport or hardware tested.");
 }
}
