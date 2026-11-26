package app.lensdrop.transfer;

public final class SingleDirectPrintRulesTest {
    private static int checks;
    private static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args) {
        String scope="https://lensdrop.example|event-a|device-a";
        String job="11111111-1111-4111-8111-111111111111";
        String other="22222222-2222-4222-8222-222222222222";
        String auto=SingleQuickPrintRules.autoTicketId(scope,job);
        String photo=SingleQuickPrintRules.firstPhotoPrintId(scope,job);
        ok(auto.equals(SingleQuickPrintRules.autoTicketId(scope,job)),"auto QR stable after restart");
        ok(photo.equals(SingleQuickPrintRules.firstPhotoPrintId(scope,job)),"first photo print stable after restart");
        ok(!auto.equals(photo),"photo and ticket attempts independent");
        ok(!auto.equals(SingleQuickPrintRules.autoTicketId(scope,other)),"different photos independent");
        ok(!auto.equals(SingleQuickPrintRules.autoTicketId("https://lensdrop.example|event-b|device-a",job)),"different event independent");
        ok(!auto.equals(SingleQuickPrintRules.autoTicketId("https://lensdrop.example|event-a|device-b",job)),"different photographer independent");
        TicketProtocol.uuid(auto);TicketProtocol.uuid(photo);
        ok(SingleQuickPrintRules.eligible(true,true,true,110,100),"new Cloud-registered arrival prints");
        ok(!SingleQuickPrintRules.eligible(false,true,true,110,100),"paused never prints");
        ok(!SingleQuickPrintRules.eligible(true,false,true,110,100),"background never prints");
        ok(!SingleQuickPrintRules.eligible(true,true,false,110,100),"no ticket before Cloud link registration");
        ok(!SingleQuickPrintRules.eligible(true,true,true,99,100),"old photos never auto print");
        ok(!SingleQuickPrintRules.eligible(true,true,true,110,0),"unarmed session never auto print");
        ok(TicketProtocol.packet(scope,auto,job,"Photo 11111111","https://lensdrop.example/p/"+"a".repeat(32)).length>100,
            "real native QR ticket packet retained");
        System.out.println("Single Direct Print rules: "+checks+"/"+checks+" PASS (pure JVM; not Android/hardware)");
    }
}
