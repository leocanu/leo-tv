package ar.appvideo.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class ParentalControlEngine {
    private static final String PREF_NAME = "leo_tv_parental";
    private static final String KEY_PIN_HASH = "pin_sha256";
    private static final String KEY_ENABLED = "parental_enabled";
    private static final String DEFAULT_PIN = "0000";

    // Estado en memoria de la sesión activa
    private static boolean sessionUnlocked = false;

    private ParentalControlEngine() {}

    private static String hashPin(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(("leo_tv_salt_" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    public static boolean isSessionUnlocked() {
        return sessionUnlocked;
    }

    public static boolean isParentalLocked(Context ctx) {
        if (sessionUnlocked) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_ENABLED, true);
    }

    public static boolean verifyPin(Context ctx, String inputPin) {
        if (inputPin == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedHash = sp.getString(KEY_PIN_HASH, null);
        if (savedHash == null) {
            savedHash = hashPin(DEFAULT_PIN);
            sp.edit().putString(KEY_PIN_HASH, savedHash).apply();
        }
        boolean ok = savedHash.equals(hashPin(inputPin));
        if (ok) {
            sessionUnlocked = true;
        }
        return ok;
    }

    public static boolean changePin(Context ctx, String oldPin, String newPin) {
        if (!verifyPin(ctx, oldPin)) return false;
        if (newPin == null || newPin.length() < 4) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_PIN_HASH, hashPin(newPin)).apply();
        sessionUnlocked = true;
        return true;
    }

    public static void lockSession() {
        sessionUnlocked = false;
    }

    public static void unlockSession() {
        sessionUnlocked = true;
    }

    public static boolean isAdultContent(CatalogData.Entry entry) {
        if (entry == null) return false;
        return isAdultContent(entry.title, entry.kind, entry.providerName);
    }

    public static boolean isAdultContent(String title, String kind, String provider) {
        String t = (title != null ? title.toLowerCase() : "");
        String k = (kind != null ? kind.toLowerCase() : "");
        String p = (provider != null ? provider.toLowerCase() : "");

        // Solo filtrar bajo PIN las que tienen sexo explícito o pornografía
        if (k.contains("xxx") || k.contains("adulto_explicito") || k.contains("hentai") || k.contains("hardcore")) return true;
        if (p.contains("xxx") || p.contains("adult_explicit")) return true;
        if (t.contains("xxx") || t.contains("porn") || t.contains("porno") || t.contains("hentai") || t.contains("sexo explícito") || t.contains("sexo explicito")) return true;
        return false;
    }
}

