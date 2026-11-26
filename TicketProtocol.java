package app.lensdrop.transfer;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** H5.3 manual pilot: the same Model-2/M/4-dot command sequence as Printer Lab J.
 * No raster, cutter, status query, nonvolatile settings or arbitrary raw command input. */
final class TicketProtocol {
    static final int MAX_URL_BYTES=160, CHUNK_BYTES=128, CHUNK_DELAY_MS=20;
    static final String PROFILE="58b3-native-m4-v1";
    private TicketProtocol(){}
    static String guestUrl(String base,String path){
        if(base==null||path==null||!path.matches("/p/[a-f0-9]{32}"))throw new IllegalArgumentException("A prepared personal guest link is required");
        try{
            URI origin=new URI(base);
            if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getRawUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||!(origin.getRawPath()==null||origin.getRawPath().isEmpty())||origin.getPort()==0)throw new IllegalArgumentException("Use the paired HTTPS origin, not an admin or setup link");
        }catch(java.net.URISyntaxException ex){throw new IllegalArgumentException("Invalid paired origin");}
        String url=base+path;
        if(url.length()>MAX_URL_BYTES)throw new IllegalArgumentException("Guest link exceeds the 160-byte pilot limit; use screen QR instead");
        ascii(url,false);return url;
    }
    static String validateUrlForScope(String scope,String url){
        if(scope==null||url==null)throw new IllegalArgumentException("Missing event identity");
        String[] p=scope.split("\\|",-1);
        if(p.length!=3||p[1].isEmpty()||p[2].isEmpty()||!url.startsWith(p[0]+"/p/"))throw new IllegalArgumentException("Guest link does not belong to the paired event origin");
        return guestUrl(p[0],url.substring(p[0].length()));
    }
    static String uuid(String s){
        if(s==null||!s.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")||!UUID.fromString(s).toString().equals(s))throw new IllegalArgumentException("Invalid snapshot identity");return s;
    }
    static String printer(String s){if(s==null||!s.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))throw new IllegalArgumentException("Select and confirm one paired printer");return s;}
    static String label(String s){if(s==null||!s.matches("(?:Group|Photo) [0-9]{4,12}|Photo [0-9A-F]{8}|[SG][0-9]{4,8}"))throw new IllegalArgumentException("Unexpected guest label; reopen QR preview");return s;}
    /** Stable text for an early Single ticket before the Cloud allocates S####.
     * A job UUID, not a status message, identifies the same private link. */
    static String earlySingleLabel(String job){return "Photo "+uuid(job).replace("-","").substring(0,8).toUpperCase(Locale.ROOT);}
    static byte[] b(int... n){byte[] out=new byte[n.length];for(int i=0;i<n.length;i++)out[i]=(byte)n[i];return out;}
    static byte[] ascii(String s,boolean lines){
        if(s==null||s.length()>1024)throw new IllegalArgumentException("Text length");
        for(char c:s.toCharArray())if(c<32&&!(lines&&c=='\n')||c>126)throw new IllegalArgumentException("ASCII-only pilot: control characters and non-ASCII text are not allowed");
        return s.getBytes(StandardCharsets.US_ASCII);
    }
    static byte[] join(byte[]... chunks){ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] chunk:chunks){if(out.size()+chunk.length>2048)throw new IllegalArgumentException("Ticket too large");out.write(chunk,0,chunk.length);}return out.toByteArray();}
    static byte[] nativeQr(String url){
        ascii(url,false);if(url.length()>MAX_URL_BYTES)throw new IllegalArgumentException("URL too long");byte[] data=ascii(url,false);int length=data.length+3;
        return join(b(27,64,27,97,1,10,10),b(29,40,107,4,0,49,65,50,0),b(29,40,107,3,0,49,67,4),b(29,40,107,3,0,49,69,49),b(29,40,107,length&255,length>>8,49,80,48),data,b(29,40,107,3,0,49,81,48,10,10,10,27,97,0));
    }
    static byte[] packet(String scope,String id,String delivery,String text,String url){
        uuid(id);uuid(delivery);label(text);validateUrlForScope(scope,url);
        return join(b(27,64),ascii("LUCE PHOTO COLLECTION\n"+text+"\nREF "+delivery.substring(0,8).toUpperCase(Locale.ROOT)+"\nTICKET "+id.substring(0,8).toUpperCase(Locale.ROOT)+"\n",true),nativeQr(url),ascii("SCAN TO VIEW YOUR PHOTOS\nPhotos may still be uploading.\nNot a photo-print entitlement.\n\n\n",true));
    }
    static boolean observation(String s){return "not_checked".equals(s)||"printed_seen".equals(s)||"qr_matches".equals(s)||"no_output".equals(s)||"garbled".equals(s)||"qr_unreadable".equals(s)||"qr_mismatch".equals(s);}
    static boolean finished(String s){return "sent_unconfirmed".equals(s)||"outcome_unknown".equals(s)||"failed_before_send".equals(s)||"cancelled_before_send".equals(s);}
}
