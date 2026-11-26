package app.lensdrop.transfer;
import org.json.JSONObject;
import java.util.function.BiFunction;
/** Network-free Cloud contract stub for deterministic race/error regression tests. */
final class CloudApi implements AutoCloseable {
 static final class Failure extends Exception { final String code;final boolean retry;final long retryAfter;
  Failure(String code,boolean retry,long retryAfter){super(code);this.code=code;this.retry=retry;this.retryAfter=retryAfter;} }
 static BiFunction<String,JSONObject,Object> handler;
 static java.util.List<String> calls=new java.util.ArrayList<>();
 String origin(){return "https://example.org";}
 JSONObject call(String route,JSONObject body)throws Exception{calls.add(route);Object r=handler.apply(route,body);if(r instanceof Failure)throw (Failure)r;return (JSONObject)r;}
 @Override public void close(){}
}