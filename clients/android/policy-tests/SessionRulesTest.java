package app.lensdrop.transfer;
public final class SessionRulesTest {
  static int checks;
  static void check(boolean pass,String name){checks++;if(!pass)throw new AssertionError(name);System.out.println("PASS "+name);}
  public static void main(String[] args){
    check(SessionRules.switchError("","A","","DCIM/A/",false)==null,"first pairing");
    check(SessionRules.switchError("A","A","DCIM/A/","DCIM/A/",false)==null,"resume original destination");
    check(SessionRules.switchError("A","B","DCIM/A/","DCIM/B/",true)!=null,"pending old work blocks switch");
    check(SessionRules.switchError("A","B","DCIM/A/","DCIM/A/",false)!=null,"same source blocked across events");
    check(SessionRules.switchError("A","A","DCIM/A/","DCIM/B/",false)!=null,"existing source cannot be repointed");
    check(SessionRules.switchError("A","B","DCIM/A/","DCIM/B/",false)==null,"drained event with distinct source");
    check(SessionRules.safeError("source_missing").equals("source_missing"),"known error retained");
    check(SessionRules.safeError("Bearer actual-secret.jpg /private/file").equals("other_error"),"raw secret cannot enter diagnostics");
    check(SessionRules.safeError("sensitive-token").equals("other_error"),"unknown error excluded");
    check(SessionRules.safeError(null).equals("unknown"),"null error safe");
    System.out.println(checks+" pure-Java policy assertions passed. NOT an Android build or device test.");
  }
}
