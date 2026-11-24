package app.lensdrop.transfer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

final class CloudApi implements AutoCloseable {
    static final class Failure extends Exception {
        final String code;final boolean retry;final long retryAfter;
        Failure(String code,boolean retry,long retryAfter){super(code);this.code=code;this.retry=retry;this.retryAfter=retryAfter;}
    }
    private final String base,token;private final boolean allowCellular;
    private final ConnectivityManager manager;
    private volatile Network cell;
    private final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){
            if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))cell=network;
        }
        @Override public void onLost(Network network){if(network.equals(cell))cell=null;}
    };
    private boolean registered;
    CloudApi(Context context,String base,String token,boolean allowCellular)throws Exception{
        URL url=new URL(base);
        if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null||(!url.getPath().isEmpty()&&!url.getPath().equals("/")))throw new IllegalArgumentException("Use an HTTPS server origin");
        this.base=base.replaceAll("/+$","");this.token=token;this.allowCellular=allowCellular;
        manager=context.getSystemService(ConnectivityManager.class);
        if(allowCellular){
            NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            manager.requestNetwork(request,callback);registered=true;
        }
    }
    private HttpURLConnection connection(URL url)throws Exception{
        Network active=manager.getActiveNetwork();NetworkCapabilities caps=active==null?null:manager.getNetworkCapabilities(active);
        if(caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)){
            // Do not deliberately bypass a VPN. Bind cloud traffic only, never the process.
            // A validated active network is allowed whether it is Wi-Fi, 4G/5G,
            // or metered Wi-Fi. The saved cellular flag only controls an OPTIONAL
            // alternative cellular request when the active network is unusable.
            // Keep using the active network to respect Android's VPN routing.
            return (HttpURLConnection)active.openConnection(url);
        }
        if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))throw new Failure("waiting_for_vpn_internet",true,5000);
        if(allowCellular&&cell!=null)return (HttpURLConnection)cell.openConnection(url);
        throw new Failure("waiting_for_validated_internet",true,5000);
    }
    String origin(){return base;}
    byte[] thumbnail(String photo)throws Exception{
        if(!photo.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid photo ID");
        HttpURLConnection c=connection(new URL(base+"/v1/device/photos/"+photo+"/thumb"));
        try{c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+token);
            if(c.getResponseCode()!=200)throw new Failure("archived_preview_unavailable",true,10000);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new Failure("preview_too_large",false,0);out.write(b,0,n);}}return out.toByteArray();
        }finally{c.disconnect();}
    }
    byte[] groupThumbnail(String group,String photo)throws Exception{
        if(!group.matches("[0-9a-f-]{36}")||!photo.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid group/photo ID");
        HttpURLConnection c=connection(new URL(base+"/v1/device/deliveries/"+group+"/members/"+photo+"/thumb"));
        try{c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+token);
            if(c.getResponseCode()!=200)throw new Failure("group_thumbnail_unavailable",true,10000);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024*1024)throw new Failure("preview_too_large",false,0);out.write(b,0,n);}}return out.toByteArray();
        }finally{c.disconnect();}
    }
    JSONObject call(String route,JSONObject body)throws Exception{return request(route,body==null?null:body.toString().getBytes(StandardCharsets.UTF_8),null);}
    JSONObject chunk(String route,byte[] body)throws Exception{return request(route,body,sha(body));}
    private JSONObject request(String route,byte[] body,String chunkHash)throws Exception{
        HttpURLConnection c=connection(new URL(base+route));
        try{
            c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Authorization","Bearer "+token);
            c.setRequestMethod(body==null?"GET":"POST");
            if(body!=null){
                c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);
                c.setRequestProperty("Content-Type",chunkHash==null?"application/json":"application/octet-stream");
                if(chunkHash!=null)c.setRequestProperty("X-Chunk-SHA256",chunkHash);
                try(OutputStream out=c.getOutputStream()){out.write(body);}
            }
            int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(stream!=null)try(InputStream in=stream){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>65536)throw new Failure("response_too_large",false,0);bytes.write(b,0,n);}}
            JSONObject result;
            try{result=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));}catch(Exception e){throw new Failure("invalid_response",true,10000);}
            if(status<200||status>=300){
                String code=result.optString("error","http_"+status);long delay=0;
                try{delay=Long.parseLong(c.getHeaderField("Retry-After"))*1000;}catch(Exception ignored){}
                boolean retry=status==408||status==409||status==425||status==429||status==500||status==502||status==503||status==504||status==507;
                if(code.equals("print_job_identity_conflict")||code.equals("profile_not_authorized")||code.equals("profile_changed_reselect")||code.equals("print_scope_mismatch")||code.equals("invalid_print_receipt")||code.equals("single_delivery_already_bound")||code.equals("photo_already_bound_elsewhere")||code.equals("delivery_cancelled")||code.equals("delivery_identity_conflict")||code.equals("test_cannot_have_delivery")||code.equals("client_id_reused_for_different_content")||code.equals("chunk_conflict")||code.equals("test_production_content_conflict")||code.equals("test_photo_cannot_publish"))retry=false;
                throw new Failure(code,retry,delay);
            }
            return result;
        }finally{c.disconnect();}
    }
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static String sha(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Override public void close(){if(registered){manager.unregisterNetworkCallback(callback);registered=false;}}
}
