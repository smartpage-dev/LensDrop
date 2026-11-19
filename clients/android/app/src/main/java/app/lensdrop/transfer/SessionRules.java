package app.lensdrop.transfer;

/** Pure-Java safety policy. JVM tests do not qualify Android hardware behaviour. */
final class SessionRules {
    static String switchError(String currentScope,String nextScope,String currentFolder,String nextFolder,boolean otherPending){
        if(otherPending)return "Finish or repair the previous event queue before switching. Nothing has been reassigned.";
        if(!currentScope.isEmpty()&&!currentScope.equals(nextScope)&&currentFolder.equals(nextFolder))return "Use a different shared folder for the next event. Delayed photographs cannot be assigned by arrival time alone.";
        if(currentScope.equals(nextScope)&&!currentFolder.isEmpty()&&!currentFolder.equals(nextFolder))return "The current queue is bound to its original source folder. Do not repoint it.";
        return null;
    }
    static String safeError(String value){
        if(value==null)return "unknown";
        switch(value){
            case "personal_delivery_requires_h4": case "delivery_identity_mismatch": case "delivery_cancelled":
            case "single_delivery_already_bound": case "photo_already_bound_elsewhere":
            case "source_missing": case "source_missing_or_changed": case "source_changed":
            case "network_or_io_error": case "device_token_invalid_or_revoked": case "device_revoked":
            case "phone_storage_low": case "cloud_storage_low": case "event_quota_reached":
            case "staging_limit_reached": case "photo_permission_required": case "receipt_mismatch":
            case "server_identity_mismatch": case "queue_scope_mismatch": case "preflight_requires_h2_server":
            case "test_production_content_conflict": case "waiting_for_validated_internet":
            case "waiting_for_vpn_internet": case "mobile_data_not_allowed": case "resource_or_user_pause":
            case "invalid_or_unsupported_jpeg": case "file_checksum_mismatch": return value;
            default:return "other_error";
        }
    }
}
