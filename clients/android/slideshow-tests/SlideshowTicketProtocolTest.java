package app.lensdrop.transfer;
import java.util.UUID;
public final class SlideshowTicketProtocolTest {
 static int n=0;
 static void ok(boolean b){if(!b)throw new AssertionError("case "+n);n++;}
 static void reject(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
 public static void main(String[] args){
  String origin="https://example.com",path="/s/"+"a".repeat(32),url=SlideshowTicketProtocol.url(origin,path);
  ok(url.equals(origin+path));ok(SlideshowTicketProtocol.packet(url,UUID.randomUUID().toString()).length>100);
  reject(()->SlideshowTicketProtocol.url("http://example.com",path));
  reject(()->SlideshowTicketProtocol.url("https://example.com/admin",path));
  reject(()->SlideshowTicketProtocol.url(origin,"/p/"+"a".repeat(32)));
  reject(()->SlideshowTicketProtocol.url(origin,"/s/"+"x".repeat(32)));
  reject(()->SlideshowTicketProtocol.url("https://example.com?x=1",path));
  reject(()->SlideshowTicketProtocol.packet("https://example.com/p/"+"a".repeat(32),UUID.randomUUID().toString()));
  ok(new String(SlideshowTicketProtocol.packet(url,UUID.randomUUID().toString()),java.nio.charset.StandardCharsets.ISO_8859_1).contains("NOT A GUEST ALBUM"));
  System.out.println("PASS "+n+" slideshow ticket protocol checks");
 }
}
