package app.lensdrop.transfer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Pure rules: QR auto-print is foreground-only, per event, after Cloud has
 * acknowledged the early link. Never replay old arrivals when re-armed. */
final class SingleQuickPrintRules {
    private SingleQuickPrintRules() {}
    static boolean eligible(boolean enabled, boolean foreground, boolean registered,
                            long photoCreated, long armedAfter) {
        return enabled && foreground && registered && armedAfter > 0 && photoCreated >= armedAfter;
    }
    static String autoTicketId(String scope,String jobId) {
        TicketProtocol.uuid(jobId);
        return UUID.nameUUIDFromBytes(("lensdrop-single-auto-ticket-v1|"+scope+"|"+jobId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
    static String firstPhotoPrintId(String scope,String jobId) {
        TicketProtocol.uuid(jobId);
        return UUID.nameUUIDFromBytes(("lensdrop-single-quick-photo-v1|"+scope+"|"+jobId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
