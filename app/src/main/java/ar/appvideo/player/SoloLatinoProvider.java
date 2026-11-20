package ar.appvideo.player;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Proveedor SoloLatino: películas y series en audio latino.
 * Utiliza scraping web de sololatino.net y resolución por MediaPageResolver / API.
 */
final class SoloLatinoProvider implements VideoProvider {
    private static final String BASE = "https://sololatino.net";

    @Override public String name() { return "SoloLatino"; }
    @Override public boolean supportsNovels() { return false; }
    @Override public boolean supportsSeries() { return true; }
    @Override public String primaryCategoryLabel() { return "Películas y Series Latino"; }
    @Override public String homeLabel() { return "SOLOLATINO · AUDIO LATINO"; }
    @Override public String description() { return "Catálogo de películas y series en español latino desde sololatino.net."; }

    @Override
    public CatalogData.CatalogPage catalog(String kind, String query, int page) throws Exception {
        JSONArray items = new JSONArray();
        String q = query != null ? query.trim() : "";
        String url;
        if (!q.isEmpty()) {
            url = BASE + "/buscar?q=" + URLEncoder.encode(q, "UTF-8");
        } else if ("serie".equals(kind)) {
            url = BASE + "/series" + (page > 1 ? "?page=" + page : "");
        } else {
            url = BASE + "/peliculas" + (page > 1 ? "?page=" + page : "");
        }

        String html = HttpSupport.text(url, BASE + "/");
        Document doc = Jsoup.parse(html, url);

        for (Element card : doc.select("article, .item, a[href*='/pelicula/'], a[href*='/serie/']")) {
            Element a = card.tagName().equals("a") ? card : card.selectFirst("a[href]");
            if (a == null) continue;
            String href = a.absUrl("href");
            if (!href.startsWith(BASE)) continue;

            Element img = card.selectFirst("img");
            String title = a.attr("title");
            if (title.isEmpty() && img != null) title = img.attr("alt");
            if (title.isEmpty()) title = card.text().trim();
            if (title.isEmpty() || title.length() < 2) continue;

            String poster = img != null ? img.absUrl("src") : "";
            if (poster.isEmpty() && img != null) poster = img.absUrl("data-src");

            boolean isSerie = href.contains("/serie/");
            items.put(new JSONObject()
                .put("title", title)
                .put("page_url", href)
                .put("poster_path", poster)
                .put("kind", isSerie ? "serie" : "movie")
                .put("year", 0));
            if (items.length() >= 24) break;
        }

        if (items.length() == 0) {
            throw new IOException("No se hallaron títulos en SoloLatino para esta búsqueda.");
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
        try {
            String html = HttpSupport.text(entry.pageUrl, BASE + "/");
            Document doc = Jsoup.parse(html, entry.pageUrl);
            int idx = 1;
            for (Element a : doc.select("a[href*='/episodio/'], a[href*='/capitulo/']")) {
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
            for (int i = 1; i <= 12; i++) {
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

        String html = HttpSupport.text(url, BASE + "/");
        Document doc = Jsoup.parse(html, url);

        for (Element frame : doc.select("iframe[src], iframe[data-src]")) {
            String src = frame.hasAttr("data-src") ? frame.absUrl("data-src") : frame.absUrl("src");
            if (MediaPageResolver.supported(src)) {
                try { return MediaPageResolver.resolve(src, url); } catch (Exception ignored) {}
            }
        }

        for (Element el : doc.select("[data-server], [data-player]")) {
            String ds = el.hasAttr("data-server") ? el.attr("data-server") : el.attr("data-player");
            if (ds.startsWith("//")) ds = "https:" + ds;
            if (MediaPageResolver.supported(ds)) {
                try { return MediaPageResolver.resolve(ds, url); } catch (Exception ignored) {}
            }
        }

        throw new IOException("SoloLatino: No se encontró reproductor compatible para este título.");
    }
}

