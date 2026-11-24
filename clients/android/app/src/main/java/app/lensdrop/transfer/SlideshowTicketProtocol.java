package app.lensdrop.transfer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
/** Strictly separate from personal guest QR. Never authorizes a photo print. */
final class SlideshowTicketProtocol {
    private SlideshowTicketProtocol(){}
    static String url(String origin,String path){
        if(origin==null||path==null||!path.matches("/s/[a-f0-9]{32}"))throw new IllegalArgumentException("Current enabled slideshow link required");
        try{URI u=new URI(origin);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getRawPath()==null||u.getRawPath().isEmpty()))throw new IllegalArgumentException("Invalid paired HTTPS origin");}catch(java.net.URISyntaxException ex){throw new IllegalArgumentException("Invalid paired HTTPS origin");}
        String result=origin+path;
        if(result.getBytes(StandardCharsets.US_ASCII).length>TicketProtocol.MAX_URL_BYTES||!StandardCharsets.US_ASCII.newEncoder().canEncode(result))throw new IllegalArgumentException("Slideshow URL exceeds the verified ticket profile; use screen QR");
        TicketProtocol.ascii(result,false);return result;
    }
    static byte[] packet(String url,String attempt){
        TicketProtocol.uuid(attempt);
        if(url==null||!url.matches("https://[^\\s]+/s/[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid slideshow URL");
        return TicketProtocol.join(TicketProtocol.b(27,64),TicketProtocol.ascii("LUCE EVENT SLIDESHOW\nPROJECTOR LINK - NOT A GUEST ALBUM\nTICKET "+attempt.substring(0,8).toUpperCase(java.util.Locale.ROOT)+"\n",true),TicketProtocol.nativeQr(url),TicketProtocol.ascii("SCAN TO OPEN THE EVENT SLIDESHOW\nInternet required. No photo print entitlement.\n\n\n",true));
    }
}
