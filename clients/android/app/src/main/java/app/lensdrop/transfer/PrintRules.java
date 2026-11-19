package app.lensdrop.transfer;
/** Pure protocol decisions used by the Android worker and tested on the JVM. */
final class PrintRules {
    static boolean terminal(String state){return "simulated".equals(state)||"operator_confirmed".equals(state)||"cancelled".equals(state);}
    static boolean acceptsBytes(String state){return "receiving".equals(state)||"awaiting_file".equals(state);}
    static String receiptMismatch(String id,String wanted,String sha,String wantedSha,long bytes,long wantedBytes,String station,String wantedStation){
        return !id.equals(wanted)||!sha.equals(wantedSha)||bytes!=wantedBytes||!station.equals(wantedStation)?"invalid_print_receipt":null;
    }
}
