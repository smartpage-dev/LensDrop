package app.lensdrop.transfer;

import java.security.SecureRandom;
/** Never derive a guest bearer URL from a filename, photographer code, photo hash or counter. */
final class InstantQr {
    private static final SecureRandom RNG=new SecureRandom();
    static String token(){byte[] bytes=new byte[16];RNG.nextBytes(bytes);char[] hex="0123456789abcdef".toCharArray();char[] result=new char[32];for(int i=0;i<16;i++){result[i*2]=hex[(bytes[i]&255)>>>4];result[i*2+1]=hex[bytes[i]&15];}return new String(result);}
    static String path(String token){if(token==null||!token.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid private token");return "/p/"+token;}
    private InstantQr(){}
}
