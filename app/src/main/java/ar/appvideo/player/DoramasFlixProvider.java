package ar.appvideo.player;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Proveedor DoramasFlix: catálogo público Next.js, temporadas, episodios y resolución de reproductores. */
final class DoramasFlixProvider implements VideoProvider {
    private static final String BASE="https://doramasflix.co";
    private static final Pattern HREF=Pattern.compile("\"href\":\"(/(?:doramas|peliculas)/[^\"]+)\"");
    private static final Pattern TITLE=Pattern.compile("\"displayName\":\"([^\"]{1,140})\"");
    private static final Pattern TYPE=Pattern.compile("\"type\":\"(dorama|movie)\"");
    private static final Pattern IMAGE=Pattern.compile("\"image\":\"([^\"]*)\"");
    private static final int PAGE_SIZE=24;

    public String name(){return "DoramasFlix";}
    public boolean supportsNovels(){return true;}
    public String homeLabel(){return "K-DRAMAS Y DORAMAS";}
    public String description(){return "Doramas coreanos, japoneses y chinos con fichas, episodios y reproducción.";}

    private static String last(Pattern pattern,String text) {
        Matcher m=pattern.matcher(text);String value="";
        while(m.find())value=m.group(1);
        return value;
    }

    public CatalogData.CatalogPage catalog(String kind,String query,int page) throws Exception {
        if(page<1||page>50)throw new IOException("Pagina fuera de rango.");
        String targetUrl;
        if (!query.trim().isEmpty()) {
            targetUrl = BASE + "/buscar?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8");
        } else if ("movie".equals(kind)) {
            targetUrl = BASE + "/peliculas";
        } else {
            targetUrl = BASE + "/doramas";
        }
        String source=HttpSupport.text(targetUrl,BASE+"/").replace("\\\"", "\"");
        Matcher links=HREF.matcher(source);
        LinkedHashMap<String,JSONObject> found=new LinkedHashMap<>();
        while(links.find()&&found.size()<160){
            String path=links.group(1);
            String prefix=source.substring(Math.max(0,links.start()-600),links.start());
            String name=last(TITLE,prefix),type=last(TYPE,prefix);
            if(name.isEmpty())continue;
            boolean movie=path.startsWith("/peliculas/");
            boolean dorama=path.startsWith("/doramas/");
            if(!movie&&!dorama)continue;
            if(query.trim().isEmpty()) {
                if(movie&&!kind.equals("movie"))continue;
                if(dorama&&!kind.equals("serie"))continue;
            }
            String tail=source.substring(links.end(),Math.min(source.length(),links.end()+210));
            String poster=last(IMAGE,tail);
            JSONObject item=new JSONObject().put("page_url",BASE+path)
                .put("title",name).put("kind",movie?"movie":"serie");
            if(poster.matches("/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)"))item.put("poster_path",poster);
            found.put(BASE+path,item);
        }
        if(found.isEmpty())throw new IOException("No se hallaron titulos de esta categoria en el catalogo publico de portada.");
        JSONArray result=new JSONArray();List<JSONObject> all=new ArrayList<>(found.values());
        int start=(page-1)*PAGE_SIZE;
        for(int i=start;i<Math.min(all.size(),start+PAGE_SIZE);i++)result.put(all.get(i));
        return new CatalogData.CatalogPage(new JSONObject()
            .put("items",result)
            .put("pagination",new JSONObject().put("page",page).put("has_next",all.size()>start+PAGE_SIZE)),page);
    }

    public JSONArray seasons(CatalogData.Entry entry) throws Exception {
        JSONArray choices=new JSONArray();
        try {
            String html = HttpSupport.text(entry.pageUrl, BASE+"/");
            Document doc = Jsoup.parse(html, entry.pageUrl);
            Set<Integer> seasonNums = new TreeSet<>();

            // 1. Check Next.js data
            Element nextData = doc.selectFirst("script#__NEXT_DATA__");
            if (nextData != null) {
                try {
                    JSONObject root = new JSONObject(nextData.data());
                    JSONObject pageProps = root.optJSONObject("props") != null ? root.getJSONObject("props").optJSONObject("pageProps") : null;
                    if (pageProps != null) {
                        JSONObject serie = pageProps.optJSONObject("serie");
                        if (serie == null) serie = pageProps.optJSONObject("dorama");
                        if (serie != null) {
                            JSONArray sArr = serie.optJSONArray("seasons");
                            if (sArr != null) {
                                for (int i = 0; i < sArr.length(); i++) {
                                    int num = sArr.optJSONObject(i) != null ? sArr.getJSONObject(i).optInt("season_number", i + 1) : (i + 1);
                                    seasonNums.add(num);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 2. Check HTML elements
            for (Element el : doc.select("[data-season], a[href*='/temporada/']")) {
                String val = el.hasAttr("data-season") ? el.attr("data-season") : el.attr("href");
                Matcher m = Pattern.compile("temporada[/\\-](\\d+)", Pattern.CASE_INSENSITIVE).matcher(val);
                if (m.find()) {
                    try { seasonNums.add(Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
                }
            }

            if (seasonNums.isEmpty()) seasonNums.add(1);
            for (int s : seasonNums) {
                choices.put(new JSONObject().put("season", s).put("name", "Temporada " + s));
            }
        } catch (Exception e) {
            choices.put(new JSONObject().put("season", 1).put("name", "Temporada 1"));
        }
        return choices;
    }

    public JSONObject episodes(CatalogData.Entry entry, int season, int page) throws Exception {
        JSONArray epArray = new JSONArray();
        boolean hasNext = false;
        try {
            String html = HttpSupport.text(entry.pageUrl, BASE+"/");
            Document doc = Jsoup.parse(html, entry.pageUrl);
            Map<Integer, String> epMap = new TreeMap<>();

            // 1. Check Next.js data
            Element nextData = doc.selectFirst("script#__NEXT_DATA__");
            if (nextData != null) {
                try {
                    JSONObject root = new JSONObject(nextData.data());
                    JSONObject pageProps = root.optJSONObject("props") != null ? root.getJSONObject("props").optJSONObject("pageProps") : null;
                    if (pageProps != null) {
                        JSONObject serie = pageProps.optJSONObject("serie");
                        if (serie == null) serie = pageProps.optJSONObject("dorama");
                        if (serie != null) {
                            JSONArray epList = serie.optJSONArray("episodes");
                            if (epList != null) {
                                for (int i = 0; i < epList.length(); i++) {
                                    JSONObject ep = epList.optJSONObject(i);
                                    if (ep == null) continue;
                                    int s = ep.optInt("season_number", 1);
                                    if (s == season) {
                                        int num = ep.optInt("episode_number", ep.optInt("number", i + 1));
                                        String slug = ep.optString("slug", "");
                                        epMap.put(num, slug);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 2. Fallback to HTML anchor links
            for (Element a : doc.select("a[href*='/capitulo/'], a[href*='/capitulo-'], a[href*='/episodio/']")) {
                String href = a.absUrl("href");
                Matcher m = Pattern.compile("(?:capitulo|episodio)[/\\-](\\d+)", Pattern.CASE_INSENSITIVE).matcher(href);
                if (m.find()) {
                    try {
                        int num = Integer.parseInt(m.group(1));
                        if (!epMap.containsKey(num)) epMap.put(num, href);
                    } catch (Exception ignored) {}
                }
            }

            // If empty, generate standard 1..16 episodes typical for K-dramas
            if (epMap.isEmpty()) {
                for (int i = 1; i <= 16; i++) {
                    epMap.put(i, entry.pageUrl + "/temporada/" + season + "/capitulo/" + i);
                }
            }

            List<Integer> sortedNumbers = new ArrayList<>(epMap.keySet());
            int start = (page - 1) * 30;
            int end = Math.min(sortedNumbers.size(), start + 30);
            for (int i = start; i < end; i++) {
                int num = sortedNumbers.get(i);
                epArray.put(new JSONObject()
                    .put("season", season)
                    .put("episode", num)
                    .put("title", "Episodio " + num)
                    .put("playable", true));
            }
            hasNext = sortedNumbers.size() > end;
        } catch (Exception e) {
            for (int i = 1; i <= 16; i++) {
                epArray.put(new JSONObject().put("season", season).put("episode", i).put("title", "Episodio " + i).put("playable", true));
            }
        }
        return new JSONObject().put("episodes", epArray).put("pagination", new JSONObject().put("has_next", hasNext));
    }

    public VideoLink resolve(CatalogData.Entry entry, int season, int episode) throws Exception {
        String epUrl = entry.pageUrl;
        if (!entry.directPlayback()) {
            epUrl = entry.pageUrl + "/temporada/" + season + "/capitulo/" + episode;
        }
        try {
            return MediaPageResolver.resolve(epUrl, BASE + "/");
        } catch (Exception ignored) {}

        // Fetch episode page and search for iframes or player data
        String html = HttpSupport.text(epUrl, BASE + "/");
        Document doc = Jsoup.parse(html, epUrl);

        for (Element frame : doc.select("iframe[src], iframe[data-src]")) {
            String src = frame.hasAttr("data-src") ? frame.absUrl("data-src") : frame.absUrl("src");
            if (src.startsWith("//")) src = "https:" + src;
            if (MediaPageResolver.supported(src)) {
                try { return MediaPageResolver.resolve(src, epUrl); } catch (Exception ignored) {}
            }
        }

        for (Element el : doc.select("[data-player], [data-server], [data-video]")) {
            String val = el.hasAttr("data-player") ? el.attr("data-player") :
                         el.hasAttr("data-server") ? el.attr("data-server") : el.attr("data-video");
            if (val.startsWith("//")) val = "https:" + val;
            if (val.startsWith("http://") || val.startsWith("https://")) {
                if (MediaPageResolver.supported(val)) {
                    try { return MediaPageResolver.resolve(val, epUrl); } catch (Exception ignored) {}
                }
            } else {
                try {
                    String decoded = new String(android.util.Base64.decode(val, android.util.Base64.DEFAULT), "UTF-8");
                    if (decoded.startsWith("//")) decoded = "https:" + decoded;
                    if (MediaPageResolver.supported(decoded)) {
                        return MediaPageResolver.resolve(decoded, epUrl);
                    }
                } catch (Exception ignored) {}
            }
        }

        throw new IOException("DoramasFlix: No se encontró reproductor compatible para este capítulo. " + MediaPageResolver.failureSummary());
    }
}
