package ar.appvideo.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.List;

public final class CatalogUpdater {
    private static final String PREFS_NAME = "leo_tv_catalog_cache";
    private static final String KEY_LAST_UPDATE = "last_catalog_update_ms";

    public interface UpdateListener {
        void onProgress(String message);
        void onFinished(boolean success, int matchesCount, String summary);
    }

    public static boolean shouldAutoUpdate(Context ctx) {
        if (ctx == null) return false;
        long last = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_UPDATE, 0);
        // Auto update if last update was more than 2 hours ago
        return (System.currentTimeMillis() - last) > (2 * 60 * 60 * 1000L);
    }

    public static void runUpdateAsync(Context ctx, UpdateListener listener) {
        new Thread(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            try {
                if (listener != null) {
                    mainHandler.post(() -> listener.onProgress("🔄 Sincronizando agenda de deportes en vivo (Fútbol Libre)..."));
                }

                // 1. Fetch live sports diary
                List<LiveTvEngine.LiveSportEvent> events = LiveTvEngine.fetchSportsDiarySync(ctx, true);
                int count = events != null ? events.size() : 0;

                if (listener != null) {
                    mainHandler.post(() -> listener.onProgress("🎬 Actualizando catálogos y novedades de cine..."));
                }

                // 2. Pre-warm / fetch primary movie & series providers
                VideoProvider cinehax = VideoProviders.find("CineHax");
                if (cinehax != null) {
                    try {
                        cinehax.catalog("movie", "", 1);
                        cinehax.catalog("serie", "", 1);
                    } catch (Exception ignored) {}
                }
                VideoProvider solo = VideoProviders.find("SoloLatino");
                if (solo != null) {
                    try {
                        solo.catalog("movie", "", 1);
                    } catch (Exception ignored) {}
                }

                // 3. Save timestamp
                if (ctx != null) {
                    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply();
                }

                if (listener != null) {
                    mainHandler.post(() -> listener.onFinished(true, count, "Catálogo sincronizado: " + count + " partidos y estrenos actualizados"));
                }
            } catch (Exception ex) {
                if (listener != null) {
                    mainHandler.post(() -> listener.onFinished(false, 0, "No se pudo conectar a la red, usando caché"));
                }
            }
        }, "catalog-updater-worker").start();
    }
}
