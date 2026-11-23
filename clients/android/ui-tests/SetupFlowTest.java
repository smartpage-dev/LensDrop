package app.lensdrop.transfer;

/** Actual production parser/readiness logic. No Android runtime or camera is simulated here. */
public final class SetupFlowTest {
    private static int assertions;
    private static final String ID="10000000-1234-4000-8000-123456789abc", SECRET="a".repeat(64);
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    private static void invalid(String value,String why){try{StaffInvitation.parse(value);throw new AssertionError("Accepted "+why);}catch(IllegalArgumentException expected){assertions++;}}
    public static void main(String[] args){
        String good="https://events.example.invalid/pair#"+ID+"."+SECRET;
        StaffInvitation p=StaffInvitation.parse(good);check(p.base.equals("https://events.example.invalid"),"origin");check(p.id.equals(ID),"invitation id");check(p.secret.equals(SECRET),"unchanged secret");
        check(StaffInvitation.parse("  "+good+"\n").id.equals(ID),"surrounding whitespace");check(StaffInvitation.parse(good.replace(".invalid/",".invalid:8443/")).base.endsWith(":8443"),"explicit HTTPS port");
        invalid(null,"null");invalid("","empty");invalid(good.replace("https:","http:"),"HTTP");invalid(good.replace("https:","javascript:"),"script URI");invalid(good.replace("events.example.invalid","name:secret@events.example.invalid"),"userinfo");
        invalid(good.replace("/pair#","/g/#"),"event gallery");invalid(good.replace("/pair#","/p/#"),"personal gallery");invalid(good.replace("/pair#","/s/#"),"slideshow");invalid(good.replace("/pair#","/pair/#"),"extra slash");invalid(good.replace("/pair#","/pair?x=1#"),"query");invalid(good.replace("/pair#","/%70air#"),"encoded path");
        invalid(good.substring(0,good.indexOf('#')),"missing fragment");invalid(good+".extra","extra fragment part");invalid(good.substring(0,good.length()-1),"short secret");invalid(good.replace(SECRET,"A".repeat(64)),"wrong secret case");invalid(good.replace(ID,"-".repeat(36)),"bad UUID");invalid(good.replace(".invalid/",".invalid:0/"),"port zero");invalid(good.replace(".invalid/",".invalid:65536/"),"invalid port");invalid("https://"+"a".repeat(2100),"oversized input");invalid(good.replace("events.example","events\n.example"),"control character");
        check(SetupFlow.next(false,false,false)==SetupFlow.Step.JOIN,"pairing first");check(SetupFlow.next(false,true,true)==SetupFlow.Step.JOIN,"folder never implies identity");check(SetupFlow.next(true,false,false)==SetupFlow.Step.SOURCE,"source next");check(SetupFlow.next(true,true,false)==SetupFlow.Step.PERMISSION,"permissions next");check(SetupFlow.next(true,true,true)==SetupFlow.Step.READY,"readiness not automatic start");
        check(SetupFlow.action(SetupFlow.Step.READY,false).contains("Start"),"explicit resume");check(SetupFlow.action(SetupFlow.Step.READY,true).contains("Pause"),"explicit pause");
        for(boolean permission:new boolean[]{false,true})for(boolean front:new boolean[]{false,true})for(boolean delivered:new boolean[]{false,true})check(SetupFlow.mayLaunchScanner(permission,front,delivered)==(permission&&front&&!delivered),"camera lifecycle guard");
        for(String s:new String[]{"wrong_qr","permission_denied","no_camera","camera_error","launch_error","paste","cancelled","empty_result",null}){String msg=SetupFlow.scanResult(s);check(msg!=null&&msg.length()>25,"actionable result");check(!msg.contains(SECRET),"no invitation in diagnostic");}
        check(SetupFlow.scanResult("cancelled").contains("unchanged"),"cancel does not enroll");check(SetupFlow.scanResult("permission_denied").contains("paste"),"permission fallback");
        System.out.println("PASSED "+assertions+" production parser/readiness assertions; not a camera or SDK test.");
    }
}
