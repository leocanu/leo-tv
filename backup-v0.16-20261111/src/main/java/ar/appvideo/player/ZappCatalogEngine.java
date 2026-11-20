package ar.appvideo.player;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Motor unificado de búsqueda global (estilo Lunified) y fichas enriquecidas
 * con capítulos con miniatura y fichas individuales de actores (estilo Zapp TV).
 */
public final class ZappCatalogEngine {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    private static final String PREFS_NAME = "leotv_zapp_prefs";
    private static final String KEY_FAVS = "favorites_json";
    private static final String KEY_HIST = "history_json";

    private ZappCatalogEngine() {}

    public static final class ExploreTile {
        public final String label;
        public final String taxonomyType; // "special", "year", "genre", "country"
        public final String slug;
        public final String kind; // "movie", "serie", "all"
        public final int color;

        public ExploreTile(String l, String type, String s, String k, int c) {
            label = l; taxonomyType = type; slug = s; kind = k; color = c;
        }
    }

    public static final class ActorItem {
        public final String name;
        public final String role;
        public final String photoUrl;
        public final String slug;
        public final long personId;
        public final long tvmazePersonId;
        public String birthday = "";
        public String country = "";
        public String bio = "";

        public ActorItem(String n, String r, String photo, String s, long pid, long tvPid) {
            name = n == null ? "Actor" : n;
            role = r == null ? "" : r;
            photoUrl = photo == null ? "" : photo;
            slug = s == null ? "" : s;
            personId = pid;
            tvmazePersonId = tvPid;
        }
    }

    public static final class ActorProfile {
        public final ActorItem actor;
        public final List<CatalogData.Entry> works = new ArrayList<>();
        public ActorProfile(ActorItem a) { actor = a; }
    }

    public static final class EpisodeCard {
        public final int season;
        public final int episode;
        public final String title;
        public final String overview;
        public final String thumbUrl;
        public final String airDate;
        public final int runtime;
        public final boolean playable;

        public EpisodeCard(int s, int e, String t, String ov, String th, String air, int rt, boolean play) {
            season = s; episode = e;
            title = (t == null || t.isEmpty()) ? ("Episodio " + e) : t;
            overview = ov == null ? "" : ov;
            thumbUrl = th == null ? "" : th;
            airDate = air == null ? "" : air;
            runtime = rt;
            playable = play;
        }
    }

    public static final class GlobalSearchResult {
        public final String query;
        public final String alternateQuery;
        public final List<ActorItem> matchedActors = new ArrayList<>();
        public final Map<String, List<CatalogData.Entry>> byProvider = new LinkedHashMap<>();

        public GlobalSearchResult(String q, String alt) {
            query = q; alternateQuery = alt;
        }
    }

    public static List<ExploreTile> buildLunifiedTiles() {
        List<ExploreTile> list = new ArrayList<>();
        int[] palette = new int[] {
            android.graphics.Color.rgb(217, 70, 0),    // Naranja Kids
            android.graphics.Color.rgb(107, 78, 255),  // Violeta Anime
            android.graphics.Color.rgb(27, 54, 100),   // Azul marino
            android.graphics.Color.rgb(224, 7, 81),    // Magenta
            android.graphics.Color.rgb(184, 90, 0),    // Ámbar
            android.graphics.Color.rgb(14, 138, 0),    // Verde
            android.graphics.Color.rgb(23, 43, 112),   // Azul intenso
            android.graphics.Color.rgb(219, 0, 114),   // Rosa fuerte
            android.graphics.Color.rgb(125, 91, 166),  // Lavanda
            android.graphics.Color.rgb(220, 60, 0),    // Naranja rojizo
            android.graphics.Color.rgb(220, 10, 34),   // Rojo carmesí
            android.graphics.Color.rgb(120, 124, 130), // Gris acero
            android.graphics.Color.rgb(80, 118, 156),  // Azul pizarra
            android.graphics.Color.rgb(0, 112, 84),    // Verde esmeralda
            android.graphics.Color.rgb(74, 48, 68),    // Ciruela oscuro
            android.graphics.Color.rgb(164, 96, 68),   // Terracota
            android.graphics.Color.rgb(66, 120, 148),  // Azul petróleo
            android.graphics.Color.rgb(0, 118, 236),   // Azul brillante (Turquía)
            android.graphics.Color.rgb(124, 17, 43),   // Bordó (Turquía)
            android.graphics.Color.rgb(166, 30, 138)   // Púrpura (Colombia)
        };
        int c = 0;
        list.add(new ExploreTile("Kids", "genre", "kids", "all", palette[c++ % palette.length]));
        list.add(new ExploreTile("Anime", "special", "anime", "all", palette[c++ % palette.length]));

        // Años 2026 a 2010 (Películas, Series, Todo) igual que en Lunified
        for (int yr = 2026; yr >= 2010; yr--) {
            String ys = String.valueOf(yr);
            list.add(new ExploreTile("Películas · " + ys, "year", ys, "movie", palette[c++ % palette.length]));
            list.add(new ExploreTile("Series · " + ys, "year", ys, "serie", palette[c++ % palette.length]));
            list.add(new ExploreTile("Todo · " + ys, "year", ys, "all", palette[c++ % palette.length]));
        }

        // Países destacados (Turquía, Corea, Japón, Argentina, México, España, Colombia, EE.UU., Brasil)
        String[][] countries = new String[][] {
            {"Turquía", "tr"},
            {"Corea", "kr"},
            {"Japón", "jp"},
            {"Argentina", "ar"},
            {"México", "mx"},
            {"España", "es"},
            {"Colombia", "co"},
            {"Estados Unidos", "us"},
            {"Brasil", "br"}
        };
        for (String[] country : countries) {
            list.add(new ExploreTile("Películas · " + country[0], "country", country[1], "movie", palette[c++ % palette.length]));
            list.add(new ExploreTile("Series · " + country[0], "country", country[1], "serie", palette[c++ % palette.length]));
            list.add(new ExploreTile("Todo · " + country[0], "country", country[1], "all", palette[c++ % palette.length]));
        }

        // Géneros completos igual que en Lunified
        String[][] genres = new String[][] {
            {"Acción", "accion"},
            {"Aventura", "aventura"},
            {"Animación", "animacion"},
            {"Comedia", "comedia"},
            {"Crimen", "crimen"},
            {"Documental", "documental"},
            {"Drama", "drama"},
            {"Familia", "familia"},
            {"Fantasía", "fantasia"},
            {"Guerra", "belica"},
            {"Historia", "historia"},
            {"Terror", "terror"},
            {"Misterio", "misterio"},
            {"Romance", "romance"},
            {"Ciencia Ficción", "ciencia-ficcion"},
            {"Suspenso", "suspense"},
            {"Western", "western"}
        };
        for (String[] g : genres) {
            list.add(new ExploreTile("Películas · " + g[0], "genre", g[1], "movie", palette[c++ % palette.length]));
            list.add(new ExploreTile("Series · " + g[0], "genre", g[1], "serie", palette[c++ % palette.length]));
            list.add(new ExploreTile("Todo · " + g[0], "genre", g[1], "all", palette[c++ % palette.length]));
        }
        return list;
    }

    public static List<CatalogData.Entry> fetchExploreTile(ExploreTile tile, int page) throws Exception {
        if ("special".equals(tile.taxonomyType) && "anime".equals(tile.slug)) {
            VideoProvider lat = VideoProviders.find("LatAnime");
            if (lat != null) {
                CatalogData.CatalogPage cp = lat.catalog("movie", "", page);
                List<CatalogData.Entry> out = new ArrayList<>();
                for (CatalogData.Entry e : cp.entries) {
                    out.add(withProvider(e, "LatAnime"));
                }
                return out;
            }
        }
        StringBuilder route = new StringBuilder("/taxonomies/")
            .append(tile.taxonomyType).append("/").append(tile.slug)
            .append("?page=").append(page);
        if (!"all".equals(tile.kind)) {
            route.append("&kind=").append(tile.kind);
        }
        JSONObject resp = CineHaxProvider.api(route.toString());
        CatalogData.CatalogPage cp = new CatalogData.CatalogPage(resp, page);
        List<CatalogData.Entry> out = new ArrayList<>();
        for (CatalogData.Entry e : cp.entries) {
            out.add(withProvider(e, "CineHax"));
        }
        // Si es Turquía / Series, enriquecemos también con clásicos turcos como Erkenci Kuş (Pájaro Soñador)
        if ("country".equals(tile.taxonomyType) && "tr".equals(tile.slug) && page == 1 && !"movie".equals(tile.kind)) {
            List<CatalogData.Entry> tvTr = searchTvMazeShows("Erkenci Kus");
            for (CatalogData.Entry trEntry : tvTr) {
                out.add(0, trEntry);
            }
        }
        return out;
    }

    public static CatalogData.Entry withProvider(CatalogData.Entry e, String prov) {
        JSONObject j = e.toJson();
        try { j.put("provider_name", prov); } catch (Exception ignored) {}
        return new CatalogData.Entry(j, prov);
    }

    private static String stripHtml(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.replaceAll("<[^>]+>", "").replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").trim();
    }

    private static String resolveBilingualAlias(String q) {
        String norm = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
        if (norm.contains("erkenci") || norm.contains("pajaro sonador") || norm.contains("sonar contigo")) {
            return norm.contains("erkenci") ? "Pájaro soñador" : "Erkenci Kuş";
        }
        if (norm.contains("kara sevda") || norm.contains("amor eterno")) {
            return norm.contains("kara") ? "Amor eterno" : "Kara Sevda";
        }
        if (norm.contains("sen cal kapimi") || norm.contains("love is in the air") || norm.contains("llamas a mi puerta")) {
            return norm.contains("sen cal") ? "Llamas a mi puerta" : "Sen Çal Kapımı";
        }
        if (norm.contains("fatmagul")) {
            return "Qué culpa tiene Fatmagül";
        }
        if (norm.contains("squid game") || norm.contains("juego del calamar")) {
            return norm.contains("squid") ? "El juego del calamar" : "Squid Game";
        }
        return "";
    }

    private static final Map<String, String> SPANISH_SYNOPSES = new LinkedHashMap<>();
    static {
        SPANISH_SYNOPSES.put("37065", "Erkenci Kuş (Pájaro Soñador / Soñar Contigo): Sanem es una joven soñadora que entra a trabajar en una importante agencia de publicidad en Estambul, donde conoce a Can Divit, un espíritu libre y fotógrafo de renombre mundial. Entre enredos y secretos nace una inolvidable historia de amor.");
    }

    public static List<CatalogData.Entry> searchTvMazeShows(String query) {
        List<CatalogData.Entry> results = new ArrayList<>();
        try {
            String url = "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(query.trim(), "UTF-8");
            JSONArray arr = new JSONArray(HttpSupport.text(url, "https://www.tvmaze.com/"));
            for (int i = 0; i < Math.min(arr.length(), 10); i++) {
                JSONObject row = arr.optJSONObject(i);
                if (row == null) continue;
                JSONObject show = row.optJSONObject("show");
                if (show == null) continue;
                CatalogData.Entry entry = parseTvMazeShow(show);
                if (entry != null) results.add(entry);
            }
        } catch (Exception ignored) {}
        return results;
    }

    public static CatalogData.Entry parseTvMazeShow(JSONObject show) {
        try {
            long sid = show.optLong("id");
            String name = show.optString("name", "Serie");
            String premiered = show.optString("premiered", "");
            int year = 0;
            if (premiered.length() >= 4) {
                try { year = Integer.parseInt(premiered.substring(0, 4)); } catch (Exception ignored) {}
            }
            JSONObject img = show.optJSONObject("image");
            String poster = img != null ? img.optString("original", img.optString("medium", "")) : "";
            String backdrop = img != null ? img.optString("medium", poster) : poster;
            JSONObject ratingObj = show.optJSONObject("rating");
            double rating = ratingObj != null ? ratingObj.optDouble("average", 0.0) : 0.0;
            int runtime = show.optInt("runtime", show.optInt("averageRuntime", 45));
            String summary = stripHtml(show.optString("summary", ""));
            if (SPANISH_SYNOPSES.containsKey(String.valueOf(sid))) {
                summary = SPANISH_SYNOPSES.get(String.valueOf(sid));
            }
            String origTitle = "";
            if (sid == 37065 || name.toLowerCase(Locale.ROOT).contains("erkenci")) {
                name = "Erkenci Kuş (Pájaro Soñador)";
                origTitle = "Erkenci Kuş · Telenovela Turca";
            }
            JSONObject item = new JSONObject();
            item.put("tvmaze_id", sid);
            item.put("catalog_item_id", sid);
            item.put("title", name);
            item.put("original_title", origTitle);
            item.put("kind", "serie");
            item.put("year", year);
            item.put("runtime", runtime);
            item.put("vote_average", rating);
            item.put("poster_path", poster);
            item.put("backdrop_path", backdrop);
            item.put("overview", summary);
            item.put("provider_name", "Catálogo Global TV");
            return new CatalogData.Entry(item, "Catálogo Global TV");
        } catch (Exception e) {
            return null;
        }
    }

    public static List<ActorItem> searchTvMazeActors(String query) {
        List<ActorItem> actors = new ArrayList<>();
        try {
            String url = "https://api.tvmaze.com/search/people?q=" + URLEncoder.encode(query.trim(), "UTF-8");
            JSONArray arr = new JSONArray(HttpSupport.text(url, "https://www.tvmaze.com/"));
            for (int i = 0; i < Math.min(arr.length(), 8); i++) {
                JSONObject row = arr.optJSONObject(i);
                if (row == null) continue;
                JSONObject person = row.optJSONObject("person");
                if (person == null) continue;
                long pid = person.optLong("id");
                String name = person.optString("name", "");
                JSONObject img = person.optJSONObject("image");
                String photo = img != null ? img.optString("medium", img.optString("original", "")) : "";
                if (name.isEmpty()) continue;
                JSONObject countryObj = person.optJSONObject("country");
                String country = countryObj != null ? countryObj.optString("name", "") : "";
                String bday = person.isNull("birthday") ? "" : person.optString("birthday", "");
                ActorItem ai = new ActorItem(name, country.isEmpty() ? "Actor / Actriz" : country, photo, "", 0, pid);
                ai.birthday = bday;
                ai.country = country;
                actors.add(ai);
            }
        } catch (Exception ignored) {}
        return actors;
    }

    public static GlobalSearchResult searchUnified(final String rawQuery, boolean globalMode, String activeProvider) {
        final String q = rawQuery == null ? "" : rawQuery.trim();
        final String alt = resolveBilingualAlias(q);
        final GlobalSearchResult out = new GlobalSearchResult(q, alt);
        if (q.isEmpty()) return out;

        List<Future<?>> futures = new ArrayList<>();

        String normQ = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
        boolean isTurkishIntent = normQ.contains("turca") || normQ.contains("turco") || normQ.contains("turquia") || normQ.contains("turk");
        boolean isJapaneseIntent = normQ.contains("japon") || normQ.contains("anime") || normQ.contains("manga");
        boolean isKoreanIntent = normQ.contains("corea") || normQ.contains("dorama") || normQ.contains("kdrama");
        boolean isAdultIntent = normQ.contains("adult") || normQ.contains("+18") || normQ.contains("erotic") || normQ.contains("365") || normQ.contains("cincuenta");
        boolean isHorrorIntent = normQ.contains("terror") || normQ.contains("miedo") || normQ.contains("horror");
        boolean isActionIntent = normQ.contains("accion") || normQ.contains("action");
        boolean isComedyIntent = normQ.contains("comedia") || normQ.contains("humor");
        boolean isSciFiIntent = normQ.contains("ciencia") || normQ.contains("ficcion") || normQ.contains("sci-fi");

        // 1. Buscar actores coincidentes
        futures.add(POOL.submit(() -> {
            List<ActorItem> people = searchTvMazeActors(q);
            synchronized (out) { out.matchedActors.addAll(people); }
        }));

        // 2. Búsqueda por intención temática en taxonomías reales de CineHax
        if (isTurkishIntent) {
            futures.add(POOL.submit(() -> {
                try {
                    VideoProvider ch = VideoProviders.find("CineHax");
                    if (ch != null) {
                        CatalogData.CatalogPage p = ch.catalog("country:tr", "", 1);
                        if (!p.entries.isEmpty()) {
                            List<CatalogData.Entry> list = new ArrayList<>();
                            for (CatalogData.Entry e : p.entries) list.add(withProvider(e, "CineHax"));
                            synchronized (out) { out.byProvider.put("CineHax · Novelas Turcas", list); }
                        }
                    }
                } catch (Exception ignored) {}
            }));
            futures.add(POOL.submit(() -> {
                List<CatalogData.Entry> list = new ArrayList<>();
                for (String tShow : new String[]{"Erkenci Kus", "Kara Sevda", "Sen Cal Kapimi", "Fatmagul", "Hercai", "Yargi"}) {
                    list.addAll(searchTvMazeShows(tShow));
                }
                if (!list.isEmpty()) {
                    synchronized (out) { out.byProvider.put("Novelas Turcas Populares", list); }
                }
            }));
        }

        if (isJapaneseIntent) {
            futures.add(POOL.submit(() -> {
                try {
                    VideoProvider ch = VideoProviders.find("CineHax");
                    if (ch != null) {
                        CatalogData.CatalogPage p = ch.catalog("country:jp", "", 1);
                        if (!p.entries.isEmpty()) {
                            List<CatalogData.Entry> list = new ArrayList<>();
                            for (CatalogData.Entry e : p.entries) list.add(withProvider(e, "CineHax"));
                            synchronized (out) { out.byProvider.put("CineHax · Japón y Anime", list); }
                        }
                    }
                } catch (Exception ignored) {}
            }));
        }

        if (isKoreanIntent) {
            futures.add(POOL.submit(() -> {
                try {
                    VideoProvider ch = VideoProviders.find("CineHax");
                    if (ch != null) {
                        CatalogData.CatalogPage p = ch.catalog("country:kr", "", 1);
                        if (!p.entries.isEmpty()) {
                            List<CatalogData.Entry> list = new ArrayList<>();
                            for (CatalogData.Entry e : p.entries) list.add(withProvider(e, "CineHax"));
                            synchronized (out) { out.byProvider.put("CineHax · K-Dramas Corea", list); }
                        }
                    }
                } catch (Exception ignored) {}
            }));
        }

        if (isHorrorIntent) {
            futures.add(POOL.submit(() -> {
                try {
                    VideoProvider ch = VideoProviders.find("CineHax");
                    if (ch != null) {
                        CatalogData.CatalogPage p = ch.catalog("genre:terror", "", 1);
                        if (!p.entries.isEmpty()) {
                            List<CatalogData.Entry> list = new ArrayList<>();
                            for (CatalogData.Entry e : p.entries) list.add(withProvider(e, "CineHax"));
                            synchronized (out) { out.byProvider.put("CineHax · Terror", list); }
                        }
                    }
                } catch (Exception ignored) {}
            }));
        }

        if (isActionIntent) {
            futures.add(POOL.submit(() -> {
                try {
                    VideoProvider ch = VideoProviders.find("CineHax");
                    if (ch != null) {
                        CatalogData.CatalogPage p = ch.catalog("genre:accion", "", 1);
                        if (!p.entries.isEmpty()) {
                            List<CatalogData.Entry> list = new ArrayList<>();
                            for (CatalogData.Entry e : p.entries) list.add(withProvider(e, "CineHax"));
                            synchronized (out) { out.byProvider.put("CineHax · Acción", list); }
                        }
                    }
                } catch (Exception ignored) {}
            }));
        }

        if (isAdultIntent) {
            futures.add(POOL.submit(() -> {
                try {
                    VideoProvider ch = VideoProviders.find("CineHax");
                    if (ch != null) {
                        CatalogData.CatalogPage p = ch.catalog("adults", "", 1);
                        if (!p.entries.isEmpty()) {
                            List<CatalogData.Entry> list = new ArrayList<>();
                            for (CatalogData.Entry e : p.entries) list.add(withProvider(e, "CineHax"));
                            synchronized (out) { out.byProvider.put("CineHax · Zona Adultos (+18)", list); }
                        }
                    }
                } catch (Exception ignored) {}
            }));
        }

        // 3. Buscar en Catálogo Global Bilingüe (TVMaze + AKAs como Erkenci Kuş / Pájaro Soñador)
        futures.add(POOL.submit(() -> {
            List<CatalogData.Entry> globalShows = searchTvMazeShows(q);
            if (globalShows.isEmpty() && !alt.isEmpty()) {
                globalShows = searchTvMazeShows(alt);
            }
            if (!globalShows.isEmpty()) {
                synchronized (out) { out.byProvider.put("Catálogo Global (Bilingüe / Actores)", globalShows); }
            }
        }));

        // 4. Proveedores a consultar (todos en paralelo si Búsqueda global está activa)
        final String[] providersToQuery = globalMode
            ? new String[] { "CineHax", "LaMovie", "SeriesMetro", "Pelispedia", "FlixLatam", "Seriesflix", "DoramasFlix", "LatAnime" }
            : new String[] { (activeProvider == null || activeProvider.isEmpty()) ? "CineHax" : activeProvider };

        for (final String provName : providersToQuery) {
            futures.add(POOL.submit(() -> {
                VideoProvider vp = VideoProviders.find(provName);
                if (vp == null) return;
                List<CatalogData.Entry> found = new ArrayList<>();
                try {
                    CatalogData.CatalogPage p1 = vp.catalog("movie", q, 1);
                    for (CatalogData.Entry e : p1.entries) found.add(withProvider(e, provName));
                } catch (Exception ignored) {}
                if (vp.supportsSeries()) {
                    try {
                        CatalogData.CatalogPage ps = vp.catalog("serie", q, 1);
                        for (CatalogData.Entry e : ps.entries) found.add(withProvider(e, provName));
                    } catch (Exception ignored) {}
                }
                if (found.isEmpty() && !alt.isEmpty()) {
                    try {
                        CatalogData.CatalogPage pAlt = vp.catalog("movie", alt, 1);
                        for (CatalogData.Entry e : pAlt.entries) found.add(withProvider(e, provName));
                    } catch (Exception ignored) {}
                    if (vp.supportsSeries()) {
                        try {
                            CatalogData.CatalogPage pAltS = vp.catalog("serie", alt, 1);
                            for (CatalogData.Entry e : pAltS.entries) found.add(withProvider(e, provName));
                        } catch (Exception ignored) {}
                    }
                }
                if (!found.isEmpty()) {
                    synchronized (out) { out.byProvider.put(provName, found); }
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(7, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
        return out;
    }

    public static List<ActorItem> fetchCast(CatalogData.Entry entry) {
        List<ActorItem> cast = new ArrayList<>();
        // 1. Intentar desde CineHax si tiene catalog_item_id y pertenece a CineHax
        if (entry.id > 0 && ("CineHax".equals(entry.providerName) || entry.tvmazeId == 0)) {
            try {
                String route = (entry.castUrl != null && !entry.castUrl.isEmpty())
                    ? entry.castUrl : ("/api/theme/cast?item_id=" + entry.id);
                String url = route.startsWith("http") ? route : ("https://cinehax.org" + route);
                JSONObject obj = new JSONObject(HttpSupport.text(url, "https://cinehax.org/"));
                JSONArray arr = obj.optJSONArray("cast");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.optJSONObject(i);
                        if (c == null) continue;
                        String name = c.optString("name", "");
                        String role = c.optString("role", "");
                        String profile = c.optString("profile_url", "");
                        String slug = c.optString("slug", "");
                        long pid = c.optLong("person_id", 0);
                        if (!name.isEmpty()) {
                            cast.add(new ActorItem(name, role, profile, slug, pid, 0));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Si es de TVMaze o si no trajo actores, consultar TVMaze Cast
        if (cast.isEmpty()) {
            long tvId = entry.tvmazeId;
            if (tvId <= 0) {
                String cleanTitle = entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                List<CatalogData.Entry> matches = searchTvMazeShows(cleanTitle);
                if (!matches.isEmpty()) tvId = matches.get(0).tvmazeId;
            }
            if (tvId > 0) {
                try {
                    String url = "https://api.tvmaze.com/shows/" + tvId + "/cast";
                    JSONArray arr = new JSONArray(HttpSupport.text(url, "https://www.tvmaze.com/"));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.optJSONObject(i);
                        if (row == null) continue;
                        JSONObject person = row.optJSONObject("person");
                        JSONObject character = row.optJSONObject("character");
                        if (person == null) continue;
                        long pid = person.optLong("id");
                        String name = person.optString("name", "");
                        String role = character != null ? character.optString("name", "") : "";
                        JSONObject img = person.optJSONObject("image");
                        String photo = img != null ? img.optString("medium", img.optString("original", "")) : "";
                        if (!name.isEmpty()) {
                            ActorItem ai = new ActorItem(name, role, photo, "", 0, pid);
                            ai.birthday = person.isNull("birthday") ? "" : person.optString("birthday", "");
                            JSONObject countryObj = person.optJSONObject("country");
                            ai.country = countryObj != null ? countryObj.optString("name", "") : "";
                            cast.add(ai);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return cast;
    }

    public static ActorProfile fetchActorProfile(ActorItem actor) {
        ActorProfile profile = new ActorProfile(actor);
        Map<String, CatalogData.Entry> uniqueWorks = new LinkedHashMap<>();

        // 1. Si tiene slug en CineHax, traer sus películas y series en español desde CineHax
        String slug = actor.slug;
        if (slug == null || slug.isEmpty()) {
            slug = java.text.Normalizer.normalize(actor.name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        }
        if (!slug.isEmpty()) {
            try {
                JSONObject resp = CineHaxProvider.api("/taxonomies/person/" + slug);
                CatalogData.CatalogPage cp = new CatalogData.CatalogPage(resp, 1);
                for (CatalogData.Entry e : cp.entries) {
                    uniqueWorks.put(e.title.toLowerCase(Locale.ROOT), withProvider(e, "CineHax"));
                }
            } catch (Exception ignored) {}
        }

        // 2. Consultar también TVMaze para biografía, fecha de nacimiento, país y series internacionales
        long tvPid = actor.tvmazePersonId;
        if (tvPid <= 0) {
            List<ActorItem> found = searchTvMazeActors(actor.name);
            if (!found.isEmpty()) {
                ActorItem f = found.get(0);
                tvPid = f.tvmazePersonId;
                if (actor.birthday.isEmpty()) actor.birthday = f.birthday;
                if (actor.country.isEmpty()) actor.country = f.country;
            }
        }
        if (tvPid > 0) {
            try {
                JSONObject pObj = new JSONObject(HttpSupport.text("https://api.tvmaze.com/people/" + tvPid, "https://www.tvmaze.com/"));
                if (actor.birthday.isEmpty() && !pObj.isNull("birthday")) actor.birthday = pObj.optString("birthday", "");
                JSONObject cObj = pObj.optJSONObject("country");
                if (actor.country.isEmpty() && cObj != null) actor.country = cObj.optString("name", "");
            } catch (Exception ignored) {}

            try {
                JSONArray credits = new JSONArray(HttpSupport.text("https://api.tvmaze.com/people/" + tvPid + "/castcredits?embed=show", "https://www.tvmaze.com/"));
                for (int i = 0; i < credits.length(); i++) {
                    JSONObject cr = credits.optJSONObject(i);
                    if (cr == null) continue;
                    JSONObject emb = cr.optJSONObject("_embedded");
                    if (emb == null) continue;
                    JSONObject show = emb.optJSONObject("show");
                    if (show == null) continue;
                    CatalogData.Entry se = parseTvMazeShow(show);
                    if (se != null) {
                        String key = se.title.toLowerCase(Locale.ROOT);
                        if (!uniqueWorks.containsKey(key)) uniqueWorks.put(key, se);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 3. Buscar también por nombre del actor en CineHax Search por si tiene más títulos doblados
        try {
            CatalogData.CatalogPage searchCp = CineHaxProvider.catalog("movie", actor.name, 1);
            for (CatalogData.Entry e : searchCp.entries) {
                String key = e.title.toLowerCase(Locale.ROOT);
                if (!uniqueWorks.containsKey(key)) uniqueWorks.put(key, withProvider(e, "CineHax"));
            }
        } catch (Exception ignored) {}

        profile.works.addAll(uniqueWorks.values());
        return profile;
    }

    public static List<EpisodeCard> fetchRichEpisodes(CatalogData.Entry entry, VideoProvider provider, int season, int page) {
        List<EpisodeCard> list = new ArrayList<>();
        // 1. Si proviene de un VideoProvider con episodios propios (CineHax, LaMovie, SeriesMetro, LatAnime...)
        if (provider != null && !"Catálogo Global TV".equals(entry.providerName)) {
            try {
                JSONObject resp = provider.episodes(entry, season, page);
                JSONArray arr = resp.optJSONArray("episodes");
                if (arr != null && arr.length() > 0) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject ep = arr.optJSONObject(i);
                        if (ep == null) continue;
                        int num = ep.optInt("episode", i + 1);
                        String title = ep.optString("title", "Episodio " + num);
                        String ov = ep.isNull("overview") ? "" : ep.optString("overview", "");
                        String still = ep.isNull("still_path") ? "" : ep.optString("still_path", "");
                        String thumb = still.startsWith("/") ? ("https://image.tmdb.org/t/p/w300" + still)
                            : (still.startsWith("https://") ? still : entry.backdrop);
                        String air = ep.isNull("air_date") ? "" : ep.optString("air_date", "");
                        int rt = ep.optInt("runtime", entry.runtime);
                        boolean play = ep.optBoolean("playable", true);
                        list.add(new EpisodeCard(season, num, title, ov, thumb, air, rt, play));
                    }
                    return list;
                }
            } catch (Exception ignored) {}
        }

        // 2. Respaldo o serie de TVMaze (como Erkenci Kuş / Pájaro Soñador)
        long tvId = entry.tvmazeId;
        if (tvId <= 0) {
            String clean = entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            List<CatalogData.Entry> m = searchTvMazeShows(clean);
            if (!m.isEmpty()) tvId = m.get(0).tvmazeId;
        }
        if (tvId > 0) {
            try {
                JSONArray arr = new JSONArray(HttpSupport.text("https://api.tvmaze.com/shows/" + tvId + "/episodes", "https://www.tvmaze.com/"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ep = arr.optJSONObject(i);
                    if (ep == null) continue;
                    int s = ep.optInt("season", 1);
                    if (s != season) continue;
                    int num = ep.optInt("number", i + 1);
                    String title = ep.optString("name", "Capítulo " + num);
                    if (title.startsWith("Episode ")) title = "Capítulo " + num;
                    String rawSummary = ep.isNull("summary") ? "" : ep.optString("summary", "");
                    if ("null".equalsIgnoreCase(rawSummary)) rawSummary = "";
                    String ov = stripHtml(rawSummary);
                    if (ov.isEmpty() || "null".equalsIgnoreCase(ov)) {
                        ov = "Capítulo " + num + " de la temporada " + season + " de " + entry.title + ".";
                    }
                    JSONObject img = ep.optJSONObject("image");
                    String thumb = img != null ? img.optString("medium", img.optString("original", entry.poster)) : entry.poster;
                    String air = ep.optString("airdate", "");
                    int rt = ep.optInt("runtime", 45);
                    list.add(new EpisodeCard(s, num, title, ov, thumb, air, rt, true));
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public static JSONArray fetchSeasonsWithFallback(CatalogData.Entry entry, VideoProvider provider) {
        if (provider != null && !"Catálogo Global TV".equals(entry.providerName)) {
            try {
                JSONArray s = provider.seasons(entry);
                if (s != null && s.length() > 0) return s;
            } catch (Exception ignored) {}
        }
        long tvId = entry.tvmazeId;
        if (tvId <= 0) {
            String clean = entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            List<CatalogData.Entry> m = searchTvMazeShows(clean);
            if (!m.isEmpty()) tvId = m.get(0).tvmazeId;
        }
        JSONArray out = new JSONArray();
        if (tvId > 0) {
            try {
                JSONArray arr = new JSONArray(HttpSupport.text("https://api.tvmaze.com/shows/" + tvId + "/seasons", "https://www.tvmaze.com/"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.optJSONObject(i);
                    if (row == null) continue;
                    int num = row.optInt("number", i + 1);
                    int count = row.optInt("episodeOrder", 0);
                    JSONObject sObj = new JSONObject().put("season", num).put("name", "Temporada " + num);
                    if (count > 0) sObj.put("count", count);
                    out.put(sObj);
                }
            } catch (Exception ignored) {}
        }
        if (out.length() == 0) {
            try { out.put(new JSONObject().put("season", 1).put("name", "Temporada 1")); } catch (Exception ignored) {}
        }
        return out;
    }

    // Persistencia local de Favoritos e Historial (se mantiene entre proveedores como en Lunified)
    private static String entryKey(CatalogData.Entry e) {
        return (e.title == null ? "" : e.title.toLowerCase(Locale.ROOT).trim()) + "|" + e.year;
    }

    public static boolean isFavorite(Context ctx, CatalogData.Entry entry) {
        String key = entryKey(entry);
        for (CatalogData.Entry e : getSavedList(ctx, KEY_FAVS)) {
            if (entryKey(e).equals(key)) return true;
        }
        return false;
    }

    public static boolean toggleFavorite(Context ctx, CatalogData.Entry entry) {
        List<CatalogData.Entry> current = getSavedList(ctx, KEY_FAVS);
        String key = entryKey(entry);
        boolean removed = false;
        List<CatalogData.Entry> updated = new ArrayList<>();
        for (CatalogData.Entry e : current) {
            if (entryKey(e).equals(key)) removed = true;
            else updated.add(e);
        }
        if (!removed) updated.add(0, entry);
        saveList(ctx, KEY_FAVS, updated, 80);
        return !removed;
    }

    public static void saveFavorite(Context ctx, CatalogData.Entry entry) {
        if (!isFavorite(ctx, entry)) {
            toggleFavorite(ctx, entry);
        }
    }

    public static List<CatalogData.Entry> getFavorites(Context ctx) {
        return getSavedList(ctx, KEY_FAVS);
    }

    public static void addHistory(Context ctx, CatalogData.Entry entry) {
        List<CatalogData.Entry> current = getSavedList(ctx, KEY_HIST);
        String key = entryKey(entry);
        List<CatalogData.Entry> updated = new ArrayList<>();
        updated.add(entry);
        for (CatalogData.Entry e : current) {
            if (!entryKey(e).equals(key)) updated.add(e);
        }
        saveList(ctx, KEY_HIST, updated, 60);
    }

    public static List<CatalogData.Entry> getHistory(Context ctx) {
        return getSavedList(ctx, KEY_HIST);
    }

    private static List<CatalogData.Entry> getSavedList(Context ctx, String prefKey) {
        List<CatalogData.Entry> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String raw = sp.getString(prefKey, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(new CatalogData.Entry(o));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void saveList(Context ctx, String prefKey, List<CatalogData.Entry> list, int max) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < Math.min(list.size(), max); i++) {
                arr.put(list.get(i).toJson());
            }
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(prefKey, arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}

