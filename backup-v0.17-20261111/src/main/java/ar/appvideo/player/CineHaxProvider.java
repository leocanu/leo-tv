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
        return HttpSupport.text(address, referer);
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
        return VimeosResolver.resolve(embed, "https://videoapp.mov/").url;
    }
    private static final java.util.LinkedHashMap<String, CatalogData.CatalogPage> pages = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, Long> pageTimes = new java.util.HashMap<>();
    public static CatalogData.CatalogPage catalog(String kind, String query, int page) throws Exception {
        if (page < 1) throw new IOException("Pagina invalida.");
        String route;
        if (!query.trim().isEmpty()) {
            route = "/search?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8") + "&page=" + page;
        } else if (kind.startsWith("genre:")) {
            String slug = kind.substring(6).trim();
            route = "/taxonomies/genre/" + java.net.URLEncoder.encode(slug, "UTF-8") + "?page=" + page;
        } else if (kind.startsWith("country:")) {
            String slug = kind.substring(8).trim();
            route = "/taxonomies/country/" + java.net.URLEncoder.encode(slug, "UTF-8") + "?page=" + page;
        } else if ("novel".equalsIgnoreCase(kind) || "turcas".equalsIgnoreCase(kind)) {
            route = "/taxonomies/country/tr?page=" + page;
        } else {
            route = "/listing?kind=" + java.net.URLEncoder.encode(kind, "UTF-8") + "&order=recent&page=" + page;
        }
        synchronized (pages) {
            if (pages.containsKey(route) && System.currentTimeMillis()-pageTimes.get(route) < 300000)
                return pages.get(route);
        }
        CatalogData.CatalogPage result = new CatalogData.CatalogPage(api(route), page);
        synchronized (pages) {
            if (!pages.containsKey(route) && pages.size() >= 120) {
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
