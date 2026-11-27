package ar.appvideo.player;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Motor de subtítulos automáticos en español usando la API de OpenSubtitles v3 y SubtitleCat.
 */
public final class SubtitleEngine {

    public static final class SubtitleTrack {
        public final String id;
        public final String lang;
        public final String label;
        public final String url;

        public SubtitleTrack(String id, String lang, String label, String url) {
            this.id = id;
            this.lang = lang;
            this.label = label;
            this.url = url;
        }
    }

    public interface SubtitleCallback {
        void onSubtitlesLoaded(List<SubtitleTrack> tracks);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "subtitle-resolver");
        t.setDaemon(true);
        return t;
    });

    private SubtitleEngine() {}

    /**
     * Busca subtítulos en español en OpenSubtitles v3.
     */
    public static void findSpanishSubtitlesAsync(String title, int year, boolean isSeries, String imdbId,
            SubtitleCallback callback) {
        EXECUTOR.execute(() -> {
            List<SubtitleTrack> results = new ArrayList<>();
            try {
                String targetId = imdbId;
                if (targetId == null || !targetId.startsWith("tt")) {
                    targetId = resolveImdbId(title, year, isSeries);
                }

                if (targetId != null && targetId.startsWith("tt")) {
                    String type = isSeries ? "series" : "movie";
                    String queryUrl = "https://opensubtitles-v3.strem.io/subtitles/" + type + "/" + targetId + ".json";
                    HttpURLConnection conn = (HttpURLConnection) new URL(queryUrl).openConnection();
                    conn.setRequestProperty("User-Agent", HttpSupport.USER_AGENT);
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    if (conn.getResponseCode() == 200) {
                        String body = readStreamToString(conn.getInputStream());
                        JSONObject json = new JSONObject(body);
                        JSONArray subs = json.optJSONArray("subtitles");
                        if (subs != null) {
                            for (int i = 0; i < subs.length(); i++) {
                                JSONObject s = subs.getJSONObject(i);
                                String lang = s.optString("lang", "").toLowerCase();
                                String subUrl = s.optString("url", "");
                                if (!subUrl.isEmpty() && (lang.equals("spa") || lang.equals("es") || lang.contains("spanish") || lang.equals("es-419") || lang.equals("es-es"))) {
                                    String id = s.optString("id", "sub_" + i);
                                    String label = "Español " + (lang.contains("419") ? "Latino" : "") + " (OpenSubtitles #" + (results.size() + 1) + ")";
                                    results.add(new SubtitleTrack(id, "es", label, subUrl));
                                    if (results.size() >= 5) break; // Máximo 5 mejores opciones
                                }
                            }
                        }
                    }
                    conn.disconnect();
                }
            } catch (Throwable ignored) {
            }

            if (callback != null) {
                callback.onSubtitlesLoaded(results);
            }
        });
    }

    /**
     * Resuelve el ID de IMDb buscando en TMDB o Sugerencias.
     */
    private static String resolveImdbId(String title, int year, boolean isSeries) {
        if (title == null || title.trim().isEmpty()) return null;
        try {
            String q = title.replaceAll("\\s*(?:[·|\\-|–—]\\s*)?(?i)(?:Temporada|Capítulo|Cap\\.?|Ep\\.?|Season|T\\d+).*", "").trim();
            String searchUrl = "https://v3-cinemeta.strem.io/catalog/" + (isSeries ? "series" : "movie") + "/top/search="
                    + java.net.URLEncoder.encode(q, "UTF-8") + ".json";
            HttpURLConnection conn = (HttpURLConnection) new URL(searchUrl).openConnection();
            conn.setRequestProperty("User-Agent", HttpSupport.USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                String body = readStreamToString(conn.getInputStream());
                JSONObject json = new JSONObject(body);
                JSONArray metas = json.optJSONArray("metas");
                if (metas != null && metas.length() > 0) {
                    JSONObject first = metas.getJSONObject(0);
                    return first.optString("imdb_id", first.optString("id", null));
                }
            }
            conn.disconnect();
        } catch (Throwable ignored) {}
        return null;
    }

    private static String readStreamToString(InputStream in) {
        if (in == null) return "";
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            in.close();
            return out.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
