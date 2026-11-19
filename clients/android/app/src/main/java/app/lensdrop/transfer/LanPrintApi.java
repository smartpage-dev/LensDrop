package app.lensdrop.transfer;

import android.content.Context;
import android.net.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Explicit per-station certificate trust, never a global trust-all client. No redirects. */
final class LanPrintApi {
    static final class Failure extends Exception{final String code;final int status;Failure(String code,int status){super(code);this.code=code;this.status=status;}}
    private final Context context;private final JSONObject setup;private final String endpoint,pin;private final SSLSocketFactory factory;
    LanPrintApi(Context context,JSONObject setup)throws Exception{this.context=context;this.setup=setup;JSONObject station=setup.getJSONObject("station");endpoint=station.getString("endpoint");pin=station.getString("certificate_sha256");URL u=new URL(endpoint);if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||u.getQuery()!=null||u.getRef()!=null||!u.getHost().matches("(10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.).*")||!pin.matches("[0-9a-f]{64}"))throw new IllegalStateException("Invalid paired station identity");
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Client certificate not supported");}public void checkServerTrusted(X509Certificate[] certs,String a)throws CertificateException{try{if(certs==null||certs.length<1)throw new CertificateException("Missing certificate");certs[0].checkValidity();if(!CloudApi.sha(certs[0].getEncoded()).equals(pin))throw new CertificateException("Station fingerprint mismatch");}catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException(e);}}}},new SecureRandom());factory=tls.getSocketFactory();}
    JSONObject call(String route,JSONObject body)throws Exception{return request(route,body==null?null:body.toString().getBytes(StandardCharsets.UTF_8),null);}
    JSONObject chunk(String route,byte[] bytes)throws Exception{return request(route,bytes,CloudApi.sha(bytes));}
    private JSONObject request(String route,byte[] body,String hash)throws Exception{
        ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);URL url=new URL(endpoint+route);InetAddress address=InetAddress.getByName(url.getHost());Network selected=null;int best=-1;
        for(Network n:manager.getAllNetworks()){NetworkCapabilities caps=manager.getNetworkCapabilities(n);LinkProperties links=manager.getLinkProperties(n);if(caps==null||links==null||!(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)))continue;for(RouteInfo r:links.getRoutes()){int prefix=r.getDestination().getPrefixLength();if(prefix>0&&prefix>best&&r.matches(address)){selected=n;best=prefix;}}}
        if(selected==null)throw new IOException("Station is not on an available local subnet");
        HttpsURLConnection c=(HttpsURLConnection)selected.openConnection(url);c.setSSLSocketFactory(factory);c.setHostnameVerifier((host,session)->{try{return host.equals(url.getHost())&&CloudApi.sha(session.getPeerCertificates()[0].getEncoded()).equals(pin);}catch(Exception e){return false;}});
        try{c.setConnectTimeout(5000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Authorization","Bearer "+setup.getString("lan_secret"));c.setRequestProperty("X-LensDrop-Grant",setup.getString("grant"));c.setRequestMethod(body==null?"GET":"POST");if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);c.setRequestProperty("Content-Type",hash==null?"application/json":"application/octet-stream");if(hash!=null)c.setRequestProperty("X-Chunk-SHA256",hash);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int code=c.getResponseCode();InputStream stream=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(stream!=null)try(InputStream in=stream){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0){if(out.size()+n>65536)throw new IOException("Station response too large");out.write(b,0,n);}}JSONObject result=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));if(code<200||code>=300)throw new Failure(result.optString("error","station_http_"+code),code);return result;
        }finally{c.disconnect();}
    }
}
