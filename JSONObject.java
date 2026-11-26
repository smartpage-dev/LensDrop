package org.json;import java.util.*;
/** Minimal JVM test stub, not compiled into the Android app. */
public class JSONObject {
 private final Map<String,Object> m=new LinkedHashMap<>();
 public JSONObject put(String k,Object v){if(v==null)m.remove(k);else m.put(k,v);return this;}
 public Object get(String k){if(!m.containsKey(k))throw new IllegalStateException("Missing "+k);return m.get(k);}
 public String getString(String k){return String.valueOf(get(k));}
 public long getLong(String k){return Long.parseLong(String.valueOf(get(k)));}
 public int getInt(String k){return (int)getLong(k);}
 public long optLong(String k){return m.containsKey(k)?getLong(k):0;}
 public JSONObject optJSONObject(String k){return m.get(k) instanceof JSONObject?(JSONObject)m.get(k):null;}
 public JSONObject getJSONObject(String k){return (JSONObject)get(k);}
 public JSONArray getJSONArray(String k){return (JSONArray)get(k);}
 public boolean isNull(String k){return !m.containsKey(k)||m.get(k)==null;}
 public String optString(String k){return m.containsKey(k)?getString(k):"";}
 public boolean optBoolean(String k){return Boolean.TRUE.equals(m.get(k));}
 public boolean optBoolean(String k,boolean def){return m.containsKey(k)?optBoolean(k):def;}
 public boolean has(String k){return m.containsKey(k);}
 public String toString(){return m.toString();}
}