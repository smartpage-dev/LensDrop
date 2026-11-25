package app.lensdrop.transfer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Config {
    private static final String ALIAS = "lensdrop.device.credential.v1";
    final SharedPreferences prefs;
    Config(Context context) { prefs=context.getSharedPreferences("transfer",Context.MODE_PRIVATE); }
    String get(String key) { return prefs.getString(key,""); }
    boolean active() { return prefs.getBoolean("active",false); }
    boolean cellular() { return prefs.getBoolean("cellular",false); }
    boolean instantQr() { return prefs.getBoolean("instant_qr_enabled",true); }
    long started() { return prefs.getLong("started",0); }
    void active(boolean value) { prefs.edit().putBoolean("active",value).commit(); }
    void status(String value) { if(!get("status").equals(value))prefs.edit().putString("status",value).apply(); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return gen.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    void putSecret(String name,String value)throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        prefs.edit().putString("secure_"+name,Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)).putString("secure_iv_"+name,Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).commit();
    }
    String secret(String name)throws Exception {
        String bytes=get("secure_"+name);if(bytes.isEmpty())throw new IllegalStateException("Complete staff / station setup first");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(get("secure_iv_"+name),Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(bytes,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    void clearSecret(String name){prefs.edit().remove("secure_"+name).remove("secure_iv_"+name).commit();}
    void save(String base,String token,String scope,String event,String folder,boolean cellular) throws Exception {
        if(!get("scope").isEmpty()&&!get("scope").equals(scope))archiveCurrent();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String encrypted=Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
        long boundary=get("scope").equals(scope)?started():System.currentTimeMillis()/1000;
        prefs.edit().putString("base",base).putString("secret",encrypted).putString("iv",iv)
            .putString("scope",scope).putString("event",event).putString("folder",folder)
            .putBoolean("cellular",cellular).putLong("started",boundary).commit();
    }
    void archiveCurrent()throws Exception{
        if(get("scope").isEmpty())return;
        org.json.JSONObject old=new org.json.JSONObject().put("scope",get("scope")).put("base",get("base")).put("event",get("event")).put("folder",get("folder")).put("token",token());
        String setup=get("secure_print_setup");if(!setup.isEmpty())old.put("print_setup",secret("print_setup"));
        putSecret("event_archive_"+CloudApi.sha(get("scope").getBytes(StandardCharsets.UTF_8)),old.toString());
    }
    String token() throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(get("iv"),Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(get("secret"),Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
}
