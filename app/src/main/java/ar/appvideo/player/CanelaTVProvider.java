package ar.appvideo.player;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Proveedor Canela TV: películas, series y telenovelas oficiales y gratuitas en español.
 * Integración con CDN público de Canela Media y catálogo web.
 */
final class CanelaTVProvider implements VideoProvider {
    private static final String BASE = "https://www.canela.tv";
    private static final String SEARCH_API = "https://content-service-cdn.cms.api.canela.tv/content/search?mode=detail&st=published&term=";
    private static final String CONFIG_API = "https://config-service-cdn.cms.api.canela.tv/launch/config?device=web&client=canela-canela-web";

    @Override public String name() { return "Canela TV"; }
    @Override public boolean supportsNovels() { return true; }
    @Override public boolean supportsSeries() { return true; }
    @Override public String primaryCategoryLabel() { return "Películas y Telenovelas"; }
    @Override public String homeLabel() { return "CANELA TV · CONTENIDO OFICIAL"; }
    @Override public String description() { return "Películas, telenovelas y series gratuitas y oficiales en Español desde canela.tv."; }

    @Override
    public CatalogData.CatalogPage catalog(String kind, String query, int page) throws Exception {
        JSONArray items = new JSONArray();
        String q = query != null ? query.trim() : "";

        if (!q.isEmpty()) {
            try {
                String searchUrl = SEARCH_API + URLEncoder.encode(q, "UTF-8") + "&pageNumber=" + page + "&pageSize=24";
                String jsonText = HttpSupport.text(searchUrl, BASE + "/");
                JSONObject root = new JSONObject(jsonText);
                JSONArray data = root.optJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item == null) continue;
                        String id = item.optString("id", item.optString("_id", ""));
                        String title = item.optString("title", item.optString("name", ""));
                        if (title.isEmpty()) continue;
                        String type = item.optString("contentType", item.optString("type", "movie")).toLowerCase(Locale.ROOT);
                        boolean isSerie = type.contains("series") || type.contains("tv") || type.contains("show");
                        if (q.isEmpty()) {
                            if (isSerie && "movie".equals(kind)) continue;
                            if (!isSerie && "serie".equals(kind)) continue;
                        }
                        String poster = item.optString("posterImage", item.optString("image", ""));
                        if (poster.isEmpty() && item.has("images")) {
                            JSONObject imgs = item.optJSONObject("images");
                            if (imgs != null) poster = imgs.optString("portrait", imgs.optString("poster", ""));
                        }
                        String detailUrl = BASE + "/" + (isSerie ? "series" : "peliculas") + "/" + id;
                        items.put(new JSONObject()
                            .put("id", id)
                            .put("title", title)
                            .put("page_url", detailUrl)
                            .put("poster_path", poster)
                            .put("kind", isSerie ? "serie" : "movie")
                            .put("year", item.optInt("releaseYear", item.optInt("year", 0))));
                    }
                }
            } catch (Exception ignored) {}
        }

        // Si la API de búsqueda no entregó ítems o la consulta está vacía, explorar catálogo web
        if (items.length() == 0) {
            String pageUrl = BASE + ("serie".equals(kind) ? "/series" : "/peliculas");
            try {
                String html = HttpSupport.text(pageUrl, BASE + "/");
                Document doc = Jsoup.parse(html, pageUrl);
                for (Element a : doc.select("a[href*='/series/'], a[href*='/peliculas/'], a[href*='/ver/']")) {
                    String href = a.absUrl("href");
                    Element img = a.selectFirst("img");
                    String title = a.attr("title");
                    if (title.isEmpty() && img != null) title = img.attr("alt");
                    if (title.isEmpty()) title = a.text().trim();
                    if (title.isEmpty() || title.length() < 2) continue;

                    String poster = img != null ? img.absUrl("src") : "";
                    if (poster.isEmpty() && img != null) poster = img.absUrl("data-src");

                    boolean isSerie = href.contains("/series/");
                    items.put(new JSONObject()
                        .put("id", href.substring(href.lastIndexOf('/') + 1))
                        .put("title", title)
                        .put("page_url", href)
                        .put("poster_path", poster)
                        .put("kind", isSerie ? "serie" : "movie")
                        .put("year", 0));
                    if (items.length() >= 24) break;
                }
            } catch (Exception ignored) {}
        }

        if (items.length() == 0) {
            throw new IOException("No se hallaron títulos disponibles en Canela TV para esta consulta.");
        }

        JSONObject pagination = new JSONObject().put("page", page).put("has_next", items.length() >= 20);
        return new CatalogData.CatalogPage(new JSONObject().put("items", items).put("pagination", pagination), page);
    }

    @Override
    public JSONArray seasons(CatalogData.Entry entry) throws Exception {
        JSONArray choices = new JSONArray();
        choices.put(new JSONObject().put("season", 1).put("name", "Temporada 1"));
        try {
            String html = HttpSupport.text(entry.pageUrl, BASE + "/");
            Document doc = Jsoup.parse(html, entry.pageUrl);
            Set<Integer> seasonNums = new TreeSet<>();
            for (Element el : doc.select("[data-season], a[href*='/temporada/']")) {
                String val = el.hasAttr("data-season") ? el.attr("data-season") : el.attr("href");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("temporada[/\\-](\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(val);
                if (m.find()) {
                    try { seasonNums.add(Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
                }
            }
            if (!seasonNums.isEmpty()) {
                choices = new JSONArray();
                for (int s : seasonNums) choices.put(new JSONObject().put("season", s).put("name", "Temporada " + s));
            }
        } catch (Exception ignored) {}
        return choices;
    }

    @Override
    public JSONObject episodes(CatalogData.Entry entry, int season, int page) throws Exception {
        JSONArray epArray = new JSONArray();
        int count = 12;
        try {
            String html = HttpSupport.text(entry.pageUrl, BASE + "/");
            Document doc = Jsoup.parse(html, entry.pageUrl);
            int idx = 1;
            for (Element a : doc.select("a[href*='/episodio/'], a[href*='/ver/']")) {
                String epTitle = a.text().trim();
                if (epTitle.isEmpty()) epTitle = "Episodio " + idx;
                epArray.put(new JSONObject()
                    .put("season", season)
                    .put("episode", idx)
                    .put("title", epTitle)
                    .put("playable", true));
                idx++;
                if (idx > 30) break;
            }
        } catch (Exception ignored) {}

        if (epArray.length() == 0) {
            for (int i = 1; i <= count; i++) {
                epArray.put(new JSONObject()
                    .put("season", season)
                    .put("episode", i)
                    .put("title", "Episodio " + i)
                    .put("playable", true));
            }
        }
        return new JSONObject().put("episodes", epArray).put("pagination", new JSONObject().put("has_next", false));
    }

    @Override
    public VideoLink resolve(CatalogData.Entry entry, int season, int episode) throws Exception {
        String url = entry.pageUrl;
        if (!entry.directPlayback()) {
            url = entry.pageUrl + "/temporada/" + season + "/episodio/" + episode;
        }
        try {
            return MediaPageResolver.resolve(url, BASE + "/");
        } catch (Exception ignored) {}

        // Buscar enlaces de streaming o reproductores en la página
        String html = HttpSupport.text(url, BASE + "/");
        java.util.regex.Matcher hls = java.util.regex.Pattern.compile("https://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*").matcher(html);
        if (hls.find()) {
            String stream = hls.group();
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", HttpSupport.USER_AGENT);
            headers.put("Referer", BASE + "/");
            headers.put("Origin", BASE);
            return new VideoLink(stream, headers);
        }

        Document doc = Jsoup.parse(html, url);
        for (Element frame : doc.select("iframe[src]")) {
            String src = frame.absUrl("src");
            if (MediaPageResolver.supported(src)) {
                return MediaPageResolver.resolve(src, url);
            }
        }

        throw new IOException("Canela TV: No se encontró señal de streaming abierta para este título.");
    }
}

