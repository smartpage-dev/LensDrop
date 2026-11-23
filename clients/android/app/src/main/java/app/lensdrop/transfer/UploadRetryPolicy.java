package app.lensdrop.transfer;
/** File-local failures must not set the global network cooldown. */
final class UploadRetryPolicy {
    static final int FILE_LIMIT=5;
    static boolean fileLocal(String code){return code!=null&&(code.startsWith("jpeg_")||code.startsWith("source_")||code.equals("invalid_or_unsupported_jpeg")||code.equals("photo_permission_required"));}
    static boolean retry(String code,boolean requested,int previousFileFailures){return requested&&(!fileLocal(code)||previousFileFailures+1<FILE_LIMIT);}
    static boolean sharedCooldown(String code,boolean retry){return retry&&!fileLocal(code)&&!"paused".equals(code)&&!"resource_or_user_pause".equals(code);}
}
