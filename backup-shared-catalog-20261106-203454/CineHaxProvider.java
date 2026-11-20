package ar.appvideo.player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Catalogo y resolucion propios de CineHax. No incorpora codigo de Nuvio. */
public final class CineHaxProvider {
    public static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private CineHaxProvider() {}
    private static String fetch(String address, String referer) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) throw new IOException("El servidor no ofrece HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (referer != null) connection.setRequestProperty("Referer", referer);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("El servidor respondio HTTP " + code + ".");
            try (InputStream in = connection.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 1048576) throw new IOException("Respuesta demasiado grande.");
                    out.write(buffer, 0, count);
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }
    private static String clean(String text) {
        return text.replace("&amp;", "&").replace("&#38;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("\\/", "/");
    }
    private static boolean videoHost(String address) {
        try {
            URL url = new URL(address);
            String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equals(url.getProtocol())
                && (host.equals("vimeos.net") || host.endsWith(".vimeos.net")
                    || host.equals("vimeos.zip") || host.endsWith(".vimeos.zip"));
        } catch (Exception ignored) { return false; }
    }
    public static String resolveMatrix() throws IOException { return resolve(603, 0, 0); }
    public static String resolve(long tmdbId, int season, int episode) throws IOException {
        if (tmdbId <= 0 || season < 0 || episode < 0) throw new IOException("Identificador invalido.");
        String route = episode > 0 ? "/e/tv/" + tmdbId + "/" + season + "/" + episode
            : "/e/movie/" + tmdbId;
        String page = fetch("https://videoapp.mov" + route, "https://cinehax.org/");
        Matcher frame = Pattern.compile("<iframe\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)",
            Pattern.CASE_INSENSITIVE).matcher(page);
        String embed = null;
        while (frame.find()) {
            String candidate = clean(frame.group(1));
            if (videoHost(candidate)) { embed = candidate; break; }
        }
        if (embed == null) throw new IOException("El titulo no tiene un reproductor disponible.");
        String html = clean(fetch(embed, "https://videoapp.mov/"));
        Matcher links = Pattern.compile("https://[^\"'\\s<>\\\\]+\\.m3u8[^\"'\\s<>\\\\]*",
            Pattern.CASE_INSENSITIVE).matcher(html);
        String stream = null;
        while (links.find()) {
            String candidate = links.group();
            if (!videoHost(candidate)) continue;
            if (stream == null) stream = candidate;
            if (candidate.contains("master.m3u8")) { stream = candidate; break; }
        }
        if (stream == null) throw new IOException("No se encontro un enlace de video.");
        if (!fetch(stream, null).trim().startsWith("#EXTM3U"))
            throw new IOException("El servidor no devolvio una lista de video valida.");
        return stream;
    }
    public static final class Entry {
        public final long id, tmdb;
        public final String title, kind, poster, overview;
        public final int year;
        Entry(org.json.JSONObject json) {
            id = json.optLong("catalog_item_id");
            tmdb = json.optLong("tmdb_id");
            title = json.optString("title", "Sin titulo");
            kind = json.optString("kind", "movie");
            year = json.optInt("year");
            String path = json.optString("poster_path", "");
            poster = path.startsWith("/") ? "https://image.tmdb.org/t/p/w342" + path : "";
            overview = json.isNull("overview") ? "" : json.optString("overview", "");
        }
        public boolean isMovie() { return kind.equals("movie"); }
    }
    public static final class CatalogPage {
        public final java.util.List<Entry> entries = new java.util.ArrayList<>();
        public final boolean next;
        public final int page, total;
        CatalogPage(org.json.JSONObject json, int requested) {
            org.json.JSONArray items = json.optJSONArray("items");
            if (items != null) for (int i=0; i<items.length(); i++) {
                org.json.JSONObject item = items.optJSONObject(i);
                if (item != null && item.optLong("tmdb_id") > 0) entries.add(new Entry(item));
            }
            org.json.JSONObject pagination = json.optJSONObject("pagination");
            next = pagination != null && pagination.optBoolean("has_next");
            page = pagination == null ? requested : pagination.optInt("page", requested);
            total = pagination == null ? entries.size() : pagination.optInt("total");
        }
    }
    private static final java.util.LinkedHashMap<String, CatalogPage> pages = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, Long> pageTimes = new java.util.HashMap<>();
    public static CatalogPage catalog(String kind, String query, int page) throws Exception {
        if (page < 1) throw new IOException("Pagina invalida.");
        String route = query.trim().isEmpty()
            ? "/listing?kind=" + java.net.URLEncoder.encode(kind, "UTF-8") + "&order=recent&page=" + page
            : "/search?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8") + "&page=" + page;
        synchronized (pages) {
            if (pages.containsKey(route) && System.currentTimeMillis()-pageTimes.get(route) < 300000)
                return pages.get(route);
        }
        CatalogPage result = new CatalogPage(api(route), page);
        synchronized (pages) {
            if (!pages.containsKey(route) && pages.size() >= 12) {
                String oldest = pages.keySet().iterator().next();
                pages.remove(oldest); pageTimes.remove(oldest);
            }
            pages.put(route, result); pageTimes.put(route, System.currentTimeMillis());
        }
        return result;
    }
    public static org.json.JSONObject api(String route) throws Exception {
        if (!route.startsWith("/") || route.contains("://")) throw new IOException("Ruta invalida.");
        return new org.json.JSONObject(fetch("https://cinehax.org/api/rest/v1" + route, "https://cinehax.org/"));
    }
    public static org.json.JSONArray seasons(long itemId) throws Exception {
        org.json.JSONArray result = api("/episode-seasons?item_id=" + itemId).optJSONArray("seasons");
        return result == null ? new org.json.JSONArray() : result;
    }
    public static org.json.JSONObject episodes(long itemId, int season, int page) throws Exception {
        return api("/episodes?item_id=" + itemId + "&season=" + season + "&limit=30&page=" + page);
    }

}
