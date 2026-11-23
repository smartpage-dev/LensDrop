package app.lensdrop.transfer;
/** Small deterministic UI policy. It never modifies transfer, guest or print records. */
final class SetupFlow {
    enum Step { JOIN, SOURCE, PERMISSION, READY }
    static Step next(boolean paired,boolean folder,boolean imagePermission){if(!paired)return Step.JOIN;if(!folder)return Step.SOURCE;if(!imagePermission)return Step.PERMISSION;return Step.READY;}
    static String title(Step s){switch(s){case JOIN:return "Join your event";case SOURCE:return "Choose your photo folder";case PERMISSION:return "Allow photo access";default:return "Ready to start watching";}}
    static String action(Step s,boolean active){if(s!=Step.READY)return title(s);return active?"Pause uploads":"Start / resume uploads";}
    static boolean mayLaunchScanner(boolean cameraPermission,boolean foreground,boolean delivered){return cameraPermission&&foreground&&!delivered;}
    static String scanResult(String code){
        if("wrong_qr".equals(code))return "Not a staff invitation. Scan Admin > Create 5-minute pairing QR, then retry. Guest photo QRs do not enroll a phone.";
        if("permission_denied".equals(code))return "Camera permission was not granted. Allow Camera in Android Settings, retry, or paste the invitation.";
        if("no_camera".equals(code))return "No camera is available on this device. Paste the invitation instead.";
        if("camera_error".equals(code))return "Camera could not open. Check Android camera privacy and close other camera apps, then retry or paste.";
        if("launch_error".equals(code))return "The scanner could not open. Your pairing is unchanged. Retry or paste the invitation.";
        if("paste".equals(code))return "Paste the complete staff invitation below. Nothing has been paired yet.";
        if("cancelled".equals(code))return "Scanning cancelled. Pairing and queued work are unchanged.";
        return "No QR was returned. Nothing has been paired. Retry scanning or paste the invitation.";
    }
    private SetupFlow(){}
}
