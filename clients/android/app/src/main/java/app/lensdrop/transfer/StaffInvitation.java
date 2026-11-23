package app.lensdrop.transfer;

import java.net.URI;
/** Same server-scoped enrollment contract; never logs or visits the scanned URL. */
final class StaffInvitation {
    final String base,id,secret;
    private StaffInvitation(String b,String i,String s){base=b;id=i;secret=s;}
    static StaffInvitation parse(String input){
        if(input==null)throw new IllegalArgumentException("Use the STAFF pairing QR, not a guest link.");
        String raw=input.trim();if(raw.length()>2048)throw new IllegalArgumentException("Invitation is too long. Copy a fresh staff invitation.");
        try{URI u=new URI(raw);String f=u.getRawFragment();String[] bits=f==null?new String[0]:f.split("\\.",-1);
            if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getPort()==0||u.getPort()>65535||!"/pair".equals(u.getRawPath())||bits.length!=2||!bits[0].matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")||!bits[1].matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
            return new StaffInvitation("https://"+u.getRawAuthority(),bits[0],bits[1]);
        }catch(Exception e){throw new IllegalArgumentException("Not a staff invitation. Use Admin > Create 5-minute pairing QR, or paste its COMPLETE invitation URL.");}
    }
}
