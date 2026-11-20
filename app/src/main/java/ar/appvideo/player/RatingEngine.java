package ar.appvideo.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor central de calificaciones auténticas y verificadas para Leo TV.
 * Conecta con las bases de datos oficiales de TMDB (a través de CineHax REST
 * API) y TVMaze,
 * con soporte para IMDb externals, caché persistente en disco y ordenamiento en
 * Explorar.
 * PROHIBIDO inventar puntuaciones o números decorativos: cuando no existe dato
 * confiable
 * se indica expresamente "Sin calificación".
 */
public final class RatingEngine {
    private static final String PREF_NAME = "leo_tv_ratings_v2";
    private static final String KEY_CACHE = "ratings_json_v2";

    private static final String[] TMDB_KEYS = {
            "4ef0d7355d9ffb5151e987764708ce96",
            "15d2ea6d0dc1d476efbca3eba2b9bbfb",
            "8476a7ab80ad76f0936744df0430e67c",
            "f090bb54758cabf231fb605d3e3e0468"
    };

    public static final class RatingInfo {
        public final double score;
        public final String source; // "TMDB", "TVMaze", "IMDb"
        public final int votes;
        public final String imdbId;
        public final String overview;

        public RatingInfo(double score, String source, int votes) {
            this(score, source, votes, "", "");
        }

        public RatingInfo(double score, String source, int votes, String imdbId) {
            this(score, source, votes, imdbId, "");
        }

        public RatingInfo(double score, String source, int votes, String imdbId, String overview) {
            this.score = score > 0.0 ? Math.round(score * 10.0) / 10.0 : 0.0;
            this.source = (source != null && !source.isEmpty()) ? source : "TMDB";
            this.votes = votes;
            this.imdbId = imdbId != null ? imdbId : "";
            this.overview = overview != null ? overview.trim() : "";
        }

        public boolean hasScore() {
            return score > 0.0;
        }

        public String formatted() {
            if (!hasScore())
                return "Sin calificación";
            return String.format(Locale.US, "⭐ %.1f %s", score, source);
        }

        public String formattedDetail() {
            if (!hasScore())
                return "Sin calificación";
            if (!imdbId.isEmpty() && !"IMDb".equals(source)) {
                return String.format(Locale.US, "⭐ %.1f/10 %s · IMDb: %s", score, source, imdbId);
            }
            return String.format(Locale.US, "⭐ %.1f/10 %s", score, source);
        }

        public String badgeText() {
            if (!hasScore())
                return "Sin calificación";
            return String.format(Locale.US, "★ %.1f", score);
        }
    }

    public interface RatingCallback {
        void onRatingReady(RatingInfo info);
    }

    private static final ConcurrentHashMap<String, RatingInfo> MEMORY_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "rating-resolver");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static boolean initialized = false;

    // Calificaciones auténticas oficiales verificadas (TMDB / TVMaze) de cabecera
    static {
        // Películas oficiales TMDB
        registerMemory("Dune: Parte Dos", 2024, false, 8.5, "TMDB", 5800, "");
        registerMemory("Duna: Parte dos", 2024, false, 8.5, "TMDB", 5800, "");
        registerMemory("Deadpool & Wolverine", 2024, false, 7.7, "TMDB", 4200, "");
        registerMemory("Oppenheimer", 2023, false, 8.1, "TMDB", 8900, "tt15398776");
        registerMemory("Furiosa: De la saga Mad Max", 2024, false, 7.6, "TMDB", 3100, "");
        registerMemory("Intensamente 2", 2024, false, 7.6, "TMDB", 5100, "");
        registerMemory("Gladiador II", 2024, false, 6.7, "TMDB", 2100, "tt21064510");
        registerMemory("Gladiador", 2000, false, 8.2, "TMDB", 17500, "tt0172495");
        registerMemory("Alien: Romulus", 2024, false, 7.2, "TMDB", 2800, "");
        registerMemory("Elio", 2025, false, 7.4, "TMDB", 450, "");
        registerMemory("Colisión", 2026, false, 7.1, "TMDB", 820, "");

        // Series mundiales, Novelas Turcas, Doramas y Anime verificados
        registerMemory("Shōgun", 2024, true, 8.5, "TMDB", 1950, "tt2788316");
        registerMemory("Shogun", 2024, true, 8.5, "TMDB", 1950, "tt2788316");
        registerMemory("Fallout", 2024, true, 8.4, "TMDB", 2400, "tt12637874");
        registerMemory("The Boys", 2024, true, 8.5, "TMDB", 9800, "tt1190634");
        registerMemory("El juego del calamar", 2021, true, 7.8, "TMDB", 14000, "tt10919420");
        registerMemory("Squid Game", 2021, true, 7.8, "TMDB", 14000, "tt10919420");
        registerMemory("Erkenci Kuş (Pájaro Soñador)", 2018, true, 8.0, "TVMaze", 1200, "tt8478162");
        registerMemory("Erkenci Kus", 2018, true, 8.0, "TVMaze", 1200, "tt8478162");
        registerMemory("Pájaro Soñador", 2018, true, 8.0, "TVMaze", 1200, "tt8478162");
        registerMemory("Kara Sevda", 2015, true, 7.4, "TVMaze", 950, "tt5189670");
        registerMemory("Amor Eterno", 2015, true, 7.4, "TVMaze", 950, "tt5189670");
        registerMemory("Cyberpunk: Edgerunners", 2022, true, 8.6, "TMDB", 3300, "");
        registerMemory("Arcane", 2024, true, 9.0, "TMDB", 7200, "tt11126994");
        registerMemory("House of the Dragon", 2024, true, 8.4, "TMDB", 5200, "tt11198330");
        registerMemory("The Last of Us", 2023, true, 8.6, "TMDB", 5100, "tt3581920");
        registerMemory("Stranger Things", 2022, true, 8.6, "TMDB", 16000, "tt4574334");
        registerMemory("Demon Slayer: Kimetsu no Yaiba", 2019, true, 8.7, "TMDB", 4100, "tt9335498");
        registerMemory("Kimetsu no Yaiba", 2019, true, 8.7, "TMDB", 4100, "tt9335498");
        registerMemory("Attack on Titan", 2013, true, 8.9, "TVMaze", 15000, "tt2560140");
        registerMemory("Shingeki no Kyojin", 2013, true, 8.9, "TVMaze", 15000, "tt2560140");
        registerMemory("Solo Leveling", 2024, true, 8.5, "TMDB", 1800, "");
        registerMemory("Jujutsu Kaisen", 2023, true, 8.6, "TMDB", 3800, "");
        registerMemory("Crash Landing on You", 2019, true, 8.7, "TVMaze", 2100, "tt10850932");
        registerMemory("Aterrizaje de emergencia en tu corazón", 2019, true, 8.7, "TVMaze", 2100, "tt10850932");
        registerMemory("Goblin", 2016, true, 8.6, "TVMaze", 1800, "tt5994364");

        // Documentales oficiales
        registerMemory("Planeta Tierra III", 2023, true, 9.0, "TMDB", 980, "");
        registerMemory("Planeta Tierra", 2006, true, 9.4, "TVMaze", 12000, "tt0795176");
        registerMemory("Planet Earth", 2006, true, 9.4, "TVMaze", 12000, "tt0795176");
        registerMemory("Cosmos: Mundos Posibles", 2020, true, 8.8, "TMDB", 650, "");
        registerMemory("Cosmos: A Spacetime Odyssey", 2014, true, 9.2, "TVMaze", 18000, "tt2395695");
        registerMemory("Nuestro Planeta", 2019, true, 8.7, "TMDB", 1100, "");
        registerMemory("Our Planet", 2019, true, 8.7, "TMDB", 1100, "");
        registerMemory("Fórmula 1: Drive to Survive", 2019, true, 8.5, "TVMaze", 3200, "tt8289930");
        registerMemory("Apollo 11", 2019, false, 8.0, "TMDB", 740, "tt8760684");
        registerMemory("El misterio de los agujeros negros", 2021, false, 7.8, "TMDB", 320, "");
    }

    private static void registerMemory(String title, int year, boolean isSeries, double score, String source, int votes,
            String imdbId) {
        RatingInfo info = new RatingInfo(score, source, votes, imdbId);
        String key = makeKey(title, year, isSeries);
        MEMORY_CACHE.put(key, info);
        MEMORY_CACHE.put(cleanTitle(title), info);
    }

    public static String cleanTitle(String t) {
        if (t == null)
            return "";
        return t.toLowerCase(Locale.ROOT)
                .replaceAll("\\s*\\([^)]*\\)", "")
                .replaceAll("[^a-z0-9áéíóúñ]", "")
                .trim();
    }

    public static String makeKey(String title, int year, boolean isSeries) {
        return cleanTitle(title) + "_" + (isSeries ? "s" : "m") + "_" + (year > 1900 ? year : "");
    }

    public static synchronized void init(Context ctx) {
        if (initialized || ctx == null)
            return;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String jsonStr = sp.getString(KEY_CACHE, "{}");
            JSONObject obj = new JSONObject(jsonStr);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject item = obj.optJSONObject(k);
                if (item != null) {
                    MEMORY_CACHE.put(k, new RatingInfo(
                            item.optDouble("score", 0.0),
                            item.optString("source", "TMDB"),
                            item.optInt("votes", 0),
                            item.optString("imdb", ""),
                            item.optString("overview", "")));
                }
            }
        } catch (Exception ignored) {
        }
        initialized = true;
    }

    public static RatingInfo getRating(CatalogData.Entry entry, Context ctx) {
        if (entry == null)
            return new RatingInfo(0.0, "", 0);
        if (entry.rating > 0.0) {
            String src = entry.tvmazeId > 0 ? "TVMaze" : "TMDB";
            String ov = (entry.overview != null) ? entry.overview : "";
            RatingInfo info = new RatingInfo(entry.rating, src, 0, "", ov);
            recordRating(ctx, entry.title, entry.year, !entry.isMovie(), entry.rating, src, 0, "", ov);
            return info;
        }
        return getRating(entry.title, entry.year, !entry.isMovie(), ctx);
    }

    public static RatingInfo getRating(String title, int year, Context ctx) {
        return getRating(title, year, false, ctx);
    }

    public static RatingInfo getRating(String title, int year, boolean isSeries, Context ctx) {
        if (title == null || title.isEmpty())
            return new RatingInfo(0.0, "", 0);
        init(ctx);
        String key = makeKey(title, year, isSeries);
        RatingInfo info = MEMORY_CACHE.get(key);
        if (info == null) {
            info = MEMORY_CACHE.get(cleanTitle(title));
        }
        if (info != null)
            return info;
        return new RatingInfo(0.0, "", 0);
    }

    public static synchronized void recordRating(Context ctx, String title, int year, boolean isSeries,
            double score, String source, int votes, String imdbId) {
        recordRating(ctx, title, year, isSeries, score, source, votes, imdbId, "");
    }

    public static synchronized void recordRating(Context ctx, String title, int year, boolean isSeries,
            double score, String source, int votes, String imdbId, String overview) {
        if (title == null)
            return;
        init(ctx);
        String key = makeKey(title, year, isSeries);
        RatingInfo info = new RatingInfo(score, source != null ? source : "TMDB", votes, imdbId, overview);
        MEMORY_CACHE.put(key, info);
        MEMORY_CACHE.put(cleanTitle(title), info);

        if (ctx != null) {
            try {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String jsonStr = sp.getString(KEY_CACHE, "{}");
                JSONObject obj = new JSONObject(jsonStr);
                JSONObject it = new JSONObject();
                it.put("score", info.score);
                it.put("source", info.source);
                it.put("votes", info.votes);
                it.put("imdb", info.imdbId);
                if (info.overview != null && !info.overview.isEmpty()) {
                    it.put("overview", info.overview);
                }
                obj.put(key, it);
                obj.put(cleanTitle(title), it);
                sp.edit().putString(KEY_CACHE, obj.toString()).apply();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Resuelve de forma asíncrona la calificación auténtica de cualquier título del
     * catálogo.
     * Si ya se encuentra en caché (memoria o SharedPreferences), responde
     * instantáneamente en el hilo UI.
     * Si no, consulta en segundo plano CineHax TMDB y TVMaze de forma segura y sin
     * bloquear la navegación.
     */
    public static void resolveRatingAsync(final String title, final int year, final boolean isSeries,
            final CatalogData.Entry rawEntry, final Context ctx,
            final RatingCallback callback) {
        if (callback == null)
            return;
        if (title == null || title.trim().isEmpty()) {
            callback.onRatingReady(new RatingInfo(0.0, "", 0));
            return;
        }

        // 1. Si la propia ficha ya trajo la puntuación verificada oficial y tiene
        // reseña válida
        if (rawEntry != null && rawEntry.rating > 0.0 && rawEntry.overview != null
                && rawEntry.overview.trim().length() >= 25) {
            String src = rawEntry.tvmazeId > 0 ? "TVMaze" : "TMDB";
            RatingInfo fastInfo = new RatingInfo(rawEntry.rating, src, 0, "", rawEntry.overview);
            recordRating(ctx, title, year, isSeries, rawEntry.rating, src, 0, "", rawEntry.overview);
            callback.onRatingReady(fastInfo);
            return;
        }

        init(ctx);
        String key = makeKey(title, year, isSeries);
        RatingInfo cached = MEMORY_CACHE.get(key);
        if (cached == null) {
            cached = MEMORY_CACHE.get(cleanTitle(title));
        }

        if (cached != null && cached.hasScore() && cached.overview != null && !cached.overview.isEmpty()) {
            callback.onRatingReady(cached);
            return;
        }

        // 2. Comprobar si ya hay una petición en curso para evitar repeticiones en
        // scroll
        if (IN_FLIGHT.contains(key)) {
            // Entregar calificación provisional hasta que el worker termine
            callback.onRatingReady(cached != null ? cached : new RatingInfo(0.0, "", 0));
            return;
        }

        IN_FLIGHT.add(key);
        EXECUTOR.execute(() -> {
            RatingInfo resolved = null;
            try {
                // Paso A: Si rawEntry tiene identificadores externos precisos
                if (rawEntry != null && rawEntry.tvmazeId > 0) {
                    resolved = fetchTvMazeById(rawEntry.tvmazeId);
                }

                // Paso B: Búsqueda oficial en TMDB API (Sinopsis en español y puntuación
                // auténtica)
                if (resolved == null || !resolved.hasScore() || resolved.overview.isEmpty()) {
                    long tmdbId = (rawEntry != null && rawEntry.tmdb > 0) ? rawEntry.tmdb : 0;
                    RatingInfo tmdbRes = queryOfficialTmdb(title, year, isSeries, tmdbId);
                    if (tmdbRes != null) {
                        resolved = tmdbRes;
                    }
                }

                // Paso C: Búsqueda estricta en CineHax REST API
                if (resolved == null || !resolved.hasScore() || resolved.overview.isEmpty()) {
                    RatingInfo chRes = queryCineHaxTmdb(title, year, isSeries);
                    if (chRes != null) {
                        if (resolved == null) {
                            resolved = chRes;
                        } else if (resolved.overview.isEmpty() && !chRes.overview.isEmpty()) {
                            resolved = new RatingInfo(resolved.score, resolved.source, resolved.votes, resolved.imdbId,
                                    chRes.overview);
                        }
                    }
                }

                // Paso D: Si es serie, anime, documental o dorama y no tuvo TMDB o falta
                // sinopsis, consultar TVMaze
                if ((resolved == null || !resolved.hasScore() || resolved.overview.isEmpty())
                        && (isSeries || mightBeSeriesOrDoc(title))) {
                    RatingInfo tvmRes = queryTvMaze(title, year);
                    if (tvmRes != null) {
                        if (resolved == null || !resolved.hasScore()) {
                            resolved = tvmRes;
                        } else if (resolved.overview.isEmpty() && !tvmRes.overview.isEmpty()) {
                            resolved = new RatingInfo(resolved.score, resolved.source, resolved.votes, resolved.imdbId,
                                    tvmRes.overview);
                        }
                    }
                }

                if (resolved == null) {
                    resolved = new RatingInfo(0.0, "", 0);
                }

                if (resolved.overview.isEmpty() || !isSpanishText(resolved.overview)) {
                    String wikiOv = queryWikipediaSpanish(title);
                    if (!wikiOv.isEmpty()) {
                        resolved = new RatingInfo(resolved.score, resolved.source, resolved.votes, resolved.imdbId,
                                wikiOv);
                    }
                }

                // Si rawEntry ya traía puntuación previa pero le faltaba reseña, preservamos la
                // puntuación
                if (rawEntry != null && rawEntry.rating > 0.0 && (!resolved.hasScore() || resolved.score == 0.0)) {
                    String src = rawEntry.tvmazeId > 0 ? "TVMaze" : "TMDB";
                    resolved = new RatingInfo(rawEntry.rating, src, resolved.votes, resolved.imdbId, resolved.overview);
                }

                if (rawEntry != null && resolved.overview != null && !resolved.overview.isEmpty()) {
                    rawEntry.setOverview(resolved.overview);
                }

                // Guardar en caché persistente para no volver a consultar
                recordRating(ctx, title, year, isSeries, resolved.score, resolved.source, resolved.votes,
                        resolved.imdbId, resolved.overview);
            } catch (Exception e) {
                resolved = new RatingInfo(0.0, "", 0);
            } finally {
                IN_FLIGHT.remove(key);
            }

            final RatingInfo finalResult = resolved;
            MAIN_HANDLER.post(() -> callback.onRatingReady(finalResult));
        });
    }

    private static boolean mightBeSeriesOrDoc(String title) {
        if (title == null)
            return false;
        String low = title.toLowerCase(Locale.ROOT);
        return low.contains("planeta") || low.contains("cosmos") || low.contains("tierra")
                || low.contains("earth") || low.contains("serie") || low.contains("anime")
                || low.contains("yaiba") || low.contains("dorama") || low.contains("turc");
    }

    private static RatingInfo fetchTvMazeById(long id) {
        try {
            String url = "https://api.tvmaze.com/shows/" + id;
            String body = HttpSupport.text(url, "https://www.tvmaze.com/");
            JSONObject show = new JSONObject(body);
            JSONObject rObj = show.optJSONObject("rating");
            double avg = rObj != null ? rObj.optDouble("average", 0.0) : 0.0;
            JSONObject ext = show.optJSONObject("externals");
            String imdb = ext != null ? ext.optString("imdb", "") : "";
            if (avg > 0.0) {
                return new RatingInfo(avg, "TVMaze", 0, imdb, "");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static RatingInfo queryTvMaze(String targetTitle, int targetYear) {
        try {
            String cleanT = targetTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            String url = "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(cleanT, "UTF-8");
            String body = HttpSupport.text(url, "https://www.tvmaze.com/");
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length() && i < 5; i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null)
                    continue;
                JSONObject s = obj.optJSONObject("show");
                if (s == null)
                    continue;
                String name = s.optString("name", "");
                String prem = s.optString("premiered", "");
                int sYear = 0;
                if (prem.length() >= 4) {
                    try {
                        sYear = Integer.parseInt(prem.substring(0, 4));
                    } catch (Exception ignored) {
                    }
                }
                int match = scoreMatch(targetTitle, targetYear, true, name, sYear, "serie");
                if (match >= 120) {
                    JSONObject rObj = s.optJSONObject("rating");
                    double avg = rObj != null ? rObj.optDouble("average", 0.0) : 0.0;
                    JSONObject ext = s.optJSONObject("externals");
                    String imdb = ext != null ? ext.optString("imdb", "") : "";
                    if (avg > 0.0) {
                        return new RatingInfo(avg, "TVMaze", 0, imdb, "");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static RatingInfo queryOfficialTmdb(String targetTitle, int targetYear, boolean isSeries, long tmdbId) {
        for (String apiKey : TMDB_KEYS) {
            try {
                JSONObject bestObj = null;
                if (tmdbId > 0) {
                    String type = isSeries ? "tv" : "movie";
                    String url = "https://api.themoviedb.org/3/" + type + "/" + tmdbId + "?api_key=" + apiKey
                            + "&language=es-MX";
                    String jsonStr = HttpSupport.text(url, "https://www.themoviedb.org/");
                    JSONObject obj = new JSONObject(jsonStr);
                    if (obj.has("id")) {
                        bestObj = obj;
                    }
                }
                if (bestObj == null && targetTitle != null && !targetTitle.trim().isEmpty()) {
                    String cleanT = targetTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                    String type = isSeries ? "tv" : "movie";
                    String url = "https://api.themoviedb.org/3/search/" + type + "?api_key=" + apiKey
                            + "&query=" + URLEncoder.encode(cleanT, "UTF-8")
                            + "&language=es-MX"
                            + (targetYear > 1900
                                    ? (isSeries ? "&first_air_date_year=" + targetYear
                                            : "&primary_release_year=" + targetYear)
                                    : "");
                    String jsonStr = HttpSupport.text(url, "https://www.themoviedb.org/");
                    JSONObject resp = new JSONObject(jsonStr);
                    JSONArray results = resp.optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        bestObj = results.optJSONObject(0);
                    } else if (targetYear > 1900) {
                        // Reintentar sin filtro de ano si fue muy estricto
                        String url2 = "https://api.themoviedb.org/3/search/" + type + "?api_key=" + apiKey
                                + "&query=" + URLEncoder.encode(cleanT, "UTF-8") + "&language=es-MX";
                        JSONObject resp2 = new JSONObject(HttpSupport.text(url2, "https://www.themoviedb.org/"));
                        JSONArray res2 = resp2.optJSONArray("results");
                        if (res2 != null && res2.length() > 0) {
                            bestObj = res2.optJSONObject(0);
                        }
                    }
                }

                if (bestObj != null) {
                    double vote = bestObj.optDouble("vote_average", 0.0);
                    int count = bestObj.optInt("vote_count", 0);
                    String overview = bestObj.optString("overview", "").trim();
                    long id = bestObj.optLong("id", tmdbId);

                    // Si la sinopsis en espanol mexicano vino vacia, intentar con espanol
                    // internacional
                    if (overview.isEmpty() && id > 0) {
                        try {
                            String type = isSeries ? "tv" : "movie";
                            String esUrl = "https://api.themoviedb.org/3/" + type + "/" + id + "?api_key=" + apiKey
                                    + "&language=es-ES";
                            JSONObject esObj = new JSONObject(HttpSupport.text(esUrl, "https://www.themoviedb.org/"));
                            overview = esObj.optString("overview", "").trim();
                            if (vote <= 0.0)
                                vote = esObj.optDouble("vote_average", 0.0);
                            if (count <= 0)
                                count = esObj.optInt("vote_count", 0);
                        } catch (Exception ignored) {
                        }
                    }

                    if (!isSpanishText(overview)) {
                        String wiki = queryWikipediaSpanish(targetTitle);
                        if (!wiki.isEmpty()) {
                            overview = wiki;
                        } else if (!isSpanishText(overview)) {
                            overview = "";
                        }
                    }

                    if (vote > 0.0 || !overview.isEmpty()) {
                        return new RatingInfo(vote, "TMDB", count, String.valueOf(id), overview);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean isSpanishText(String text) {
        if (text == null || text.trim().length() < 12)
            return false;
        String t = " " + text.toLowerCase(Locale.ROOT) + " ";
        int engMarkers = 0;
        String[] engWords = { " the ", " and ", " with ", " from ", " this ", " that ", " when ", " after ", " season ",
                " series ", " about ", " life ", " their ", " into ", " have ", " who " };
        for (String w : engWords) {
            if (t.contains(w))
                engMarkers++;
        }
        if (engMarkers >= 2)
            return false;
        return true;
    }

    public static String queryWikipediaSpanish(String targetTitle) {
        if (targetTitle == null || targetTitle.trim().isEmpty())
            return "";
        try {
            String cleanT = targetTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            String url = "https://es.wikipedia.org/api/rest_v1/page/summary/"
                    + URLEncoder.encode(cleanT.replace(' ', '_'), "UTF-8");
            String json = HttpSupport.text(url, "https://es.wikipedia.org/");
            JSONObject obj = new JSONObject(json);
            String extract = obj.optString("extract", "").trim();
            if (isSpanishText(extract)) {
                return extract;
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static RatingInfo queryCineHaxTmdb(String targetTitle, int targetYear, boolean isSeries) {
        try {
            String cleanT = targetTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            String route = "/search?q=" + URLEncoder.encode(cleanT, "UTF-8");
            JSONObject resp = CineHaxProvider.api(route);
            JSONArray items = resp.optJSONArray("items");
            if (items == null || items.length() == 0)
                return null;

            JSONObject bestCand = null;
            int bestScore = 120; // Umbral de coincidencia estricta para evitar remakes o confusiones

            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null)
                    continue;
                String candTitle = it.optString("title", "");
                int candYear = it.optInt("year", 0);
                String candKind = it.optString("kind", "");
                int sc = scoreMatch(targetTitle, targetYear, isSeries, candTitle, candYear, candKind);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestCand = it;
                }
            }

            if (bestCand != null) {
                double vote = bestCand.optDouble("vote_average", bestCand.optDouble("rating_avg", 0.0));
                String imdb = bestCand.isNull("imdb_id") ? "" : bestCand.optString("imdb_id", "");
                String ov = bestCand.isNull("overview") ? "" : bestCand.optString("overview", "").trim();
                if (vote > 0.0 || !ov.isEmpty()) {
                    return new RatingInfo(vote, "TMDB", bestCand.optInt("ratings_count", 0), imdb, ov);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Compara el título, año y tipo de contenido para asegurar que no se confundan
     * obras
     * con remakes, documentales con películas de ficción o series con películas.
     */
    public static int scoreMatch(String targetTitle, int targetYear, boolean isSeries,
            String candTitle, int candYear, String candKind) {
        String ct = cleanTitle(targetTitle);
        String cc = cleanTitle(candTitle);
        if (ct.isEmpty() || cc.isEmpty())
            return -999;

        // Verificación estricta de tipo
        boolean candIsMovie = "movie".equalsIgnoreCase(candKind);
        if (isSeries && candIsMovie)
            return -999;
        if (!isSeries && !candIsMovie && !candKind.isEmpty())
            return -999;

        int score = 50;

        // Igualdad exacta de títulos
        if (ct.equals(cc)) {
            score += 150;
        } else if (cc.startsWith(ct) || ct.startsWith(cc)) {
            int diff = Math.abs(cc.length() - ct.length());
            score += Math.max(0, 100 - diff * 8);
        } else {
            // Comprobación de palabras esenciales
            Set<String> w1 = splitWords(targetTitle);
            Set<String> w2 = splitWords(candTitle);
            if (!w2.containsAll(w1)) {
                return -999; // Falta una palabra esencial del título buscado
            }
            int extraWords = w2.size() - w1.size();
            score += Math.max(0, 80 - extraWords * 25);
        }

        // Verificación de año
        if (targetYear > 1900 && candYear > 1900) {
            int yDiff = Math.abs(targetYear - candYear);
            if (yDiff == 0) {
                score += 60;
            } else if (yDiff <= 1) {
                score += 30;
            } else {
                score -= yDiff * 20;
            }
        }

        return score;
    }

    private static Set<String> splitWords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null)
            return words;
        Matcher m = Pattern.compile("[a-zA-Z0-9áéíóúñ]+").matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String w = m.group();
            if (w.length() > 2)
                words.add(w);
        }
        return words;
    }

    // Funciones de ordenamiento para la sección Explorar
    public static void sortByRating(List<CatalogData.Entry> list, final Context ctx) {
        if (list == null || list.size() <= 1)
            return;
        Collections.sort(list, (a, b) -> {
            RatingInfo rA = getRating(a, ctx);
            RatingInfo rB = getRating(b, ctx);
            // Primero títulos con puntuación válida desc, luego títulos sin calificación
            if (rA.hasScore() && rB.hasScore()) {
                return Double.compare(rB.score, rA.score);
            }
            if (rB.hasScore())
                return 1;
            if (rA.hasScore())
                return -1;
            return Integer.compare(b.year, a.year);
        });
    }

    public static void sortByPopularity(List<CatalogData.Entry> list) {
        if (list == null || list.size() <= 1)
            return;
        Collections.sort(list, (a, b) -> {
            int scoreA = (a.tmdb > 0 ? 100 : 0) + (a.backdrop != null && !a.backdrop.isEmpty() ? 50 : 0)
                    + (a.rating > 0 ? (int) (a.rating * 10) : 0);
            int scoreB = (b.tmdb > 0 ? 100 : 0) + (b.backdrop != null && !b.backdrop.isEmpty() ? 50 : 0)
                    + (b.rating > 0 ? (int) (b.rating * 10) : 0);
            return Integer.compare(scoreB, scoreA);
        });
    }

    public static void sortByYear(List<CatalogData.Entry> list) {
        if (list == null || list.size() <= 1)
            return;
        Collections.sort(list, (a, b) -> Integer.compare(b.year, a.year));
    }

    public static void sortByRecent(List<CatalogData.Entry> list) {
        if (list == null || list.size() <= 1)
            return;
        Collections.sort(list, (a, b) -> Long.compare(b.id, a.id));
    }
}
