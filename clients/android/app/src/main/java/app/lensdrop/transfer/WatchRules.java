package app.lensdrop.transfer;
/** Pure Java decisions shared by the Android ledger and JVM policy tests. */
final class WatchRules {
    static final long MAX_BYTES=64L*1024*1024;
    static boolean stable(long now,long since,long size,boolean pending){return !pending&&size>0&&size<=MAX_BYTES&&now-since>=2000;}
    static boolean autoAssign(String mode,boolean trustedOrderedSource,boolean recovered,String group){
        return "group".equals(mode)&&trustedOrderedSource&&!recovered&&group!=null&&!group.isEmpty();
    }
    static boolean excluded(String disposition){return "excluded".equals(disposition);}
    static boolean cancelOnUndo(long assigned){return assigned==0;}
    static boolean validPath(String path){return path!=null&&!path.isEmpty()&&path.endsWith("/")&&!path.startsWith("/")&&!path.contains("..");}
    static boolean canAssign(String state,String delivery){return "candidate".equals(state)&&(delivery==null||delivery.isEmpty());}
}
