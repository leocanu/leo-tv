package ar.appvideo.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Motor de búsqueda e integración de YouTube+ para Leo TV.
 * Utiliza la API oficial InnerTube sin requerir API Keys de Google Cloud ni cuotas.
 */
public final class YouTubeEngine {
    private static final String TAG = "YouTubeEngine";
    private static final String SEARCH_ENDPOINT = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "LeoTV-YouTubeWorker");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResults(List<CatalogData.Entry> results);
    }

    private YouTubeEngine() {}

    /**
     * Realiza una búsqueda asíncrona en YouTube InnerTube y retorna los resultados en el hilo principal.
     */
    public static void searchAsync(String query, Callback callback) {
        if (callback == null) return;
        if (query == null || query.trim().isEmpty()) {
            callback.onResults(Collections.emptyList());
            return;
        }

        EXECUTOR.execute(() -> {
            List<CatalogData.Entry> list = search(query.trim());
            MAIN_HANDLER.post(() -> callback.onResults(list));
        });
    }

    /**
     * Búsqueda síncrona en YouTube InnerTube.
     */
    public static List<CatalogData.Entry> search(String query) {
        List<CatalogData.Entry> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(SEARCH_ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept-Language", "es-419,es;q=0.9,en;q=0.8");

            // Payload InnerTube para cliente WEB en español latino
            JSONObject root = new JSONObject();
            JSONObject context = new JSONObject();
            JSONObject client = new JSONObject();
            client.put("clientName", "WEB");
            client.put("clientVersion", "2.20230522.01.00");
            client.put("hl", "es-419");
            client.put("gl", "AR");
            context.put("client", client);
            root.put("context", context);
            root.put("query", query);

            byte[] body = root.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "YouTube InnerTube HTTP " + code);
                return results;
            }

            StringBuilder sb = new StringBuilder(16384);
            try (InputStream is = conn.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject response = new JSONObject(sb.toString());
            extractVideos(response, results);

        } catch (Exception e) {
            Log.e(TAG, "Error en YouTubeEngine.search: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }

        return results;
    }

    /**
     * Recorre recursivamente el árbol JSON en busca de objetos 'videoRenderer'.
     */
    private static void extractVideos(Object obj, List<CatalogData.Entry> out) {
        if (obj instanceof JSONObject) {
            JSONObject json = (JSONObject) obj;
            if (json.has("videoRenderer")) {
                JSONObject vr = json.optJSONObject("videoRenderer");
                if (vr != null) {
                    CatalogData.Entry entry = parseVideoRenderer(vr);
                    if (entry != null) {
                        out.add(entry);
                    }
                }
            }

            JSONArray names = json.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i);
                    Object val = json.opt(key);
                    if (val instanceof JSONObject || val instanceof JSONArray) {
                        extractVideos(val, out);
                    }
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray) obj;
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.opt(i);
                if (val instanceof JSONObject || val instanceof JSONArray) {
                    extractVideos(val, out);
                }
            }
        }
    }

    private static CatalogData.Entry parseVideoRenderer(JSONObject vr) {
        String videoId = vr.optString("videoId", "");
        if (videoId.isEmpty()) return null;

        String title = "";
        JSONObject titleObj = vr.optJSONObject("title");
        if (titleObj != null) {
            JSONArray runs = titleObj.optJSONArray("runs");
            if (runs != null && runs.length() > 0) {
                title = runs.optJSONObject(0).optString("text", "");
            } else {
                title = titleObj.optString("simpleText", "");
            }
        }
        if (title.isEmpty()) return null;

        String duration = "";
        JSONObject lengthObj = vr.optJSONObject("lengthText");
        if (lengthObj != null) {
            duration = lengthObj.optString("simpleText", "");
        }

        String channel = "";
        JSONObject ownerObj = vr.optJSONObject("ownerText");
        if (ownerObj != null) {
            JSONArray runs = ownerObj.optJSONArray("runs");
            if (runs != null && runs.length() > 0) {
                channel = runs.optJSONObject(0).optString("text", "");
            }
        }

        String views = "";
        JSONObject viewObj = vr.optJSONObject("viewCountText");
        if (viewObj != null) {
            views = viewObj.optString("simpleText", "");
        }

        String thumbnail = "";
        JSONObject thumbObj = vr.optJSONObject("thumbnail");
        if (thumbObj != null) {
            JSONArray thumbs = thumbObj.optJSONArray("thumbnails");
            if (thumbs != null && thumbs.length() > 0) {
                thumbnail = thumbs.optJSONObject(thumbs.length() - 1).optString("url", "");
            }
        }
        if (thumbnail.isEmpty()) {
            thumbnail = "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
        }

        try {
            JSONObject json = new JSONObject();
            json.put("catalog_item_id", Math.abs((long) videoId.hashCode()));
            json.put("title", title);
            json.put("original_title", channel);
            json.put("kind", "youtube");
            json.put("year", 2026);
            json.put("runtime", 0);
            json.put("page_url", videoId);
            json.put("slug", duration);
            json.put("poster_path", thumbnail);
            json.put("backdrop_path", thumbnail);
            json.put("overview", (channel.isEmpty() ? "" : "Canal: " + channel + " · ") +
                                 (!duration.isEmpty() ? "Duración: " + duration : "") +
                                 (!views.isEmpty() ? " · " + views : ""));
            return new CatalogData.Entry(json, "YouTube+");
        } catch (Exception e) {
            return null;
        }
    }
}
