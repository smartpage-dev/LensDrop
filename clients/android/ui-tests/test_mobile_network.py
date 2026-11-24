#!/usr/bin/env python3
"""Compile the real CloudApi against minimal Android connectivity fakes.

Tests network-selection behavior, not actual Android radio/VPN or HTTPS traffic.
"""
from pathlib import Path
import subprocess, tempfile, textwrap
SRC=Path(__file__).resolve().parents[1]/'app/src/main/java/app/lensdrop/transfer/CloudApi.java'
STUBS={
'android/content/Context.java':'''package android.content;
import android.net.ConnectivityManager;
public class Context {public ConnectivityManager manager;
@SuppressWarnings("unchecked") public <T> T getSystemService(Class<T> c){return (T)manager;}}
''',
'android/net/NetworkCapabilities.java':'''package android.net;
public class NetworkCapabilities {
 public static final int NET_CAPABILITY_VALIDATED=1,NET_CAPABILITY_NOT_METERED=2,NET_CAPABILITY_INTERNET=3;
 public static final int TRANSPORT_CELLULAR=4,TRANSPORT_VPN=5;
 public boolean validated,metered,vpn;
 public NetworkCapabilities(boolean v,boolean m,boolean p){validated=v;metered=m;vpn=p;}
 public boolean hasCapability(int n){return n==1?validated:n==2?!metered:n==3;}
 public boolean hasTransport(int n){return n==5&&vpn;}
}''',
'android/net/Network.java':'''package android.net;
import java.net.*;
public class Network {public int opens;
 public URLConnection openConnection(URL u){opens++;return new HttpURLConnection(u){
 public void disconnect(){} public boolean usingProxy(){return false;} public void connect(){} };}
}''',
'android/net/ConnectivityManager.java':'''package android.net;
public class ConnectivityManager {
 public static class NetworkCallback {
  public void onCapabilitiesChanged(Network n,NetworkCapabilities c){}
  public void onLost(Network n){}
 }
 public Network active;public NetworkCapabilities caps;public NetworkCallback cb;public int requests,unregisters;
 public Network getActiveNetwork(){return active;}
 public NetworkCapabilities getNetworkCapabilities(Network n){return n==active?caps:null;}
 public void requestNetwork(NetworkRequest req,NetworkCallback cb){requests++;this.cb=cb;}
 public void unregisterNetworkCallback(NetworkCallback cb){unregisters++;}
}''',
'android/net/NetworkRequest.java':'''package android.net;
public class NetworkRequest {public static class Builder {
 public Builder addTransportType(int n){return this;}
 public Builder addCapability(int n){return this;}
 public NetworkRequest build(){return new NetworkRequest();}
}}''',
'org/json/JSONObject.java':'''package org.json;
public class JSONObject {public JSONObject(String s){} public String optString(String k,String def){return def;}}''',
'app/lensdrop/transfer/CloudNetworkTest.java':'''package app.lensdrop.transfer;
import android.content.Context;import android.net.*;import java.lang.reflect.*;import java.net.*;
public class CloudNetworkTest {
 static int count;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;}
 static Object connect(CloudApi a)throws Exception {Method m=CloudApi.class.getDeclaredMethod("connection",URL.class);m.setAccessible(true);
  try{return m.invoke(a,new URL("https://example.org/health"));}catch(InvocationTargetException e){throw (Exception)e.getCause();}}
 static Context ctx(Network n,NetworkCapabilities c){Context x=new Context();x.manager=new ConnectivityManager();x.manager.active=n;x.manager.caps=c;return x;}
 static void expect(CloudApi a,String code)throws Exception{try{connect(a);throw new AssertionError("expected "+code);}catch(CloudApi.Failure f){check(code.equals(f.code),"wrong failure: "+f.code);}}
 public static void main(String[] args)throws Exception {
  Network cell=new Network();Context x=ctx(cell,new NetworkCapabilities(true,true,false));
  try(CloudApi api=new CloudApi(x,"https://example.org","",false)){
   check(connect(api) instanceof HttpURLConnection,"validated metered cellular must work without opt-in");
   check(cell.opens==1,"active 5G used");check(x.manager.requests==0,"no forced cellular request");
  }
  Network wifi=new Network();Context w=ctx(wifi,new NetworkCapabilities(true,true,false));
  try(CloudApi api=new CloudApi(w,"https://example.org","",false)){
   check(connect(api) instanceof HttpURLConnection,"validated metered Wi-Fi must work");
  }
  Network vpn=new Network();Context v=ctx(vpn,new NetworkCapabilities(true,true,true));
  try(CloudApi api=new CloudApi(v,"https://example.org","",true)){
   check(connect(api) instanceof HttpURLConnection,"validated VPN route remains active");
   check(vpn.opens==1,"VPN active route used");
  }
  Network deadVpn=new Network();Context d=ctx(deadVpn,new NetworkCapabilities(false,true,true));
  try(CloudApi api=new CloudApi(d,"https://example.org","",true)){
   Network alternative=new Network();d.manager.cb.onCapabilitiesChanged(alternative,new NetworkCapabilities(true,true,false));
   expect(api,"waiting_for_vpn_internet");check(alternative.opens==0,"no VPN bypass");
  }
  Context offline=ctx(null,null);
  try(CloudApi api=new CloudApi(offline,"https://example.org","",false)){
   expect(api,"waiting_for_validated_internet");
  }
  Context fallback=ctx(null,null);Network alternate=new Network();
  try(CloudApi api=new CloudApi(fallback,"https://example.org","",true)){
   check(fallback.manager.requests==1,"optional fallback requested");
   fallback.manager.cb.onCapabilitiesChanged(alternate,new NetworkCapabilities(true,true,false));
   check(connect(api) instanceof HttpURLConnection,"fallback used when no active network");
   check(alternate.opens==1,"fallback network used");
  }
  check(fallback.manager.unregisters==1,"fallback callback released");
  System.out.println("PASS "+count+" CloudApi network behavior assertions (fake Android networking)");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='lensdrop-network-policy-') as tmp:
 root=Path(tmp)
 for rel,body in STUBS.items():
  p=root/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(textwrap.dedent(body))
 classes=root/'classes'
 cmd=['javac','--release','17','-d',str(classes),str(SRC)]+[str(root/p) for p in STUBS]
 result=subprocess.run(cmd,capture_output=True,text=True,timeout=45)
 if result.returncode:raise SystemExit('Java compile failed:\n'+result.stderr)
 result=subprocess.run(['java','-cp',str(classes),'app.lensdrop.transfer.CloudNetworkTest'],capture_output=True,text=True,timeout=20)
 if result.returncode:raise SystemExit('Java test failed:\n'+result.stderr)
 print(result.stdout.strip())
 # Negative control: restored old gate must fail at least the 5G case.
 source=SRC.read_text();needle='return (HttpURLConnection)active.openConnection(url);'
 bad=source.replace(needle,'if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)&&!allowCellular)throw new Failure("mobile_data_not_allowed",true,30000);\n            '+needle,1)
 q=root/'CloudApi.java';q.write_text(bad)
 neg=subprocess.run(['javac','--release','17','-d',str(classes),str(q)]+[str(root/p) for p in STUBS],capture_output=True,text=True,timeout=45)
 if neg.returncode:raise SystemExit('Negative control did not compile: '+neg.stderr)
 neg=subprocess.run(['java','-cp',str(classes),'app.lensdrop.transfer.CloudNetworkTest'],capture_output=True,text=True,timeout=20)
 assert neg.returncode!=0 and 'mobile_data_not_allowed' in neg.stderr,neg.stderr
 print('PASS negative control: old metered-network block rejected')
