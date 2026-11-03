package com.joe.waterlite;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

final class Store {
    private final SharedPreferences prefs;
    private static final String ALIAS = "waterlite.session.v1";
    Store(Context context) { prefs = context.getSharedPreferences("waterlite", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    String phone() { return prefs.getString("phone", ""); }
    String token() throws Exception {
        String payload = prefs.getString("session", "");
        if (payload.isEmpty()) return "";
        String[] parts = payload.split(":");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "UTF-8");
    }
    void login(String phone, String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String payload = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(token.getBytes("UTF-8")), Base64.NO_WRAP);
        if (!prefs.edit().putString("phone", phone).putString("session", payload).putBoolean("reauth", false).commit()) throw new Exception("登录信息保存失败，请重试");
    }
    boolean reauth() { return prefs.getBoolean("reauth", false); }
    void expire() { prefs.edit().putBoolean("reauth", true).commit(); }
    void logout() { prefs.edit().remove("session").putBoolean("reauth", false).commit(); }
    List<Device> devices(String phone) {
        List<Device> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(prefs.getString("devices." + phone, "[]")); for (int i=0; i<a.length(); i++) { Device d = Device.from(a.getJSONObject(i)); if (DeviceIdParser.valid(d.id)) out.add(d); } } catch (Exception ignored) {}
        return out;
    }
    void devices(String phone, List<Device> devices) throws Exception {
        JSONArray a = new JSONArray(); for (Device d : devices) a.put(d.json());
        String name="devices." + phone, value=a.toString();
        if (!value.equals(prefs.getString(name, "[]"))) prefs.edit().putString(name, value).apply();
    }
    Set<String> hidden(String phone) { return new HashSet<>(prefs.getStringSet("hidden." + phone, Collections.emptySet())); }
    void hidden(String phone, Set<String> ids) throws Exception { if (!ids.equals(hidden(phone))) prefs.edit().putStringSet("hidden." + phone, new HashSet<>(ids)).apply(); }
    long cooldown() { return prefs.getLong("smsDeadline", 0); }
    void cooldown(long deadline) { prefs.edit().putLong("smsDeadline", deadline).apply(); }
}
