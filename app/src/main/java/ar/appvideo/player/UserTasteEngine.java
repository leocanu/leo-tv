package ar.appvideo.player;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.*;

/**
 * Motor de personalización, recomendaciones y re-ordenamiento inteligente para Leo TV.
 * Analiza en tiempo real las reproducciones recientes, episodios vistos y favoritos
 * para construir un perfil de afinidad por géneros y temáticas 100% on-device.
 */
public final class UserTasteEngine {

    public static class UserTasteProfile {
        public final Map<String, Float> genreWeights;
        public final boolean prefersSeries;
        public final String topRecentTitle;
        public final String topRecentKind;
        public final long topRecentTmdbId;

        public UserTasteProfile(Map<String, Float> genreWeights, boolean prefersSeries,
                                String topRecentTitle, String topRecentKind, long topRecentTmdbId) {
            this.genreWeights = genreWeights != null ? genreWeights : new HashMap<>();
            this.prefersSeries = prefersSeries;
            this.topRecentTitle = topRecentTitle != null ? topRecentTitle : "";
            this.topRecentKind = topRecentKind != null ? topRecentKind : "movie";
            this.topRecentTmdbId = topRecentTmdbId;
        }

        public float getScoreForTitle(String title, String kind, String overview) {
            float score = 0.0f;
            if (title == null) return score;
            String textToAnalyze = (title + " " + (overview != null ? overview : "")).toLowerCase(Locale.ROOT);

            for (Map.Entry<String, Float> entry : genreWeights.entrySet()) {
                String genre = entry.getKey().toLowerCase(Locale.ROOT);
                if (textToAnalyze.contains(genre)) {
                    score += entry.getValue();
                }
            }

            if (prefersSeries && "serie".equalsIgnoreCase(kind)) {
                score += 1.5f;
            } else if (!prefersSeries && "movie".equalsIgnoreCase(kind)) {
                score += 1.0f;
            }

            return score;
        }
    }

    /**
     * Construye el perfil de gustos del usuario a partir de su historial real
     */
    public static UserTasteProfile buildProfile(Context ctx) {
        if (ctx == null) {
            return new UserTasteProfile(new HashMap<>(), false, "", "movie", 0);
        }

        Map<String, Float> genreScores = new HashMap<>();
        int seriesCount = 0;
        int movieCount = 0;
        String topRecentTitle = "";
        String topRecentKind = "movie";
        long topRecentTmdbId = 0;

        long now = System.currentTimeMillis();

        // 1. Analizar Continuar Viendo (alta relevancia reciente)
        List<PlaybackEngine.PlaybackProgress> cwList = PlaybackEngine.getContinueWatchingList(ctx);
        if (cwList != null && !cwList.isEmpty()) {
            PlaybackEngine.PlaybackProgress first = cwList.get(0);
            topRecentTitle = first.title;
            topRecentKind = first.kind;

            for (int i = 0; i < cwList.size(); i++) {
                PlaybackEngine.PlaybackProgress p = cwList.get(i);
                if (p == null || p.title.isEmpty()) continue;

                if ("serie".equalsIgnoreCase(p.kind)) seriesCount++;
                else movieCount++;

                // Factor de decaimiento temporal
                long ageHours = (now - p.updatedAt) / (1000 * 3600);
                float decay = ageHours < 48 ? 1.5f : (ageHours < 168 ? 1.0f : 0.6f);
                float baseWeight = 2.0f;
                if (p.getPercentage() > 60) baseWeight = 3.5f;

                accumulateGenresFromText(p.title, baseWeight * decay, genreScores);
            }
        }

        // 2. Analizar Favoritos (preferencia explícita máxima)
        List<CatalogData.Entry> favs = ZappCatalogEngine.getFavorites(ctx);
        if (favs != null && !favs.isEmpty()) {
            for (CatalogData.Entry fav : favs) {
                if (fav == null || fav.title == null) continue;
                if (!fav.isMovie()) seriesCount += 2;
                else movieCount += 2;

                accumulateGenresFromText(fav.title + " " + fav.overview, 4.0f, genreScores);
                if (topRecentTmdbId == 0 && fav.tmdb > 0) {
                    topRecentTmdbId = fav.tmdb;
                    if (topRecentTitle.isEmpty()) {
                        topRecentTitle = fav.title;
                        topRecentKind = fav.kind;
                    }
                }
            }
        }

        boolean prefersSeries = seriesCount > movieCount;
        return new UserTasteProfile(genreScores, prefersSeries, topRecentTitle, topRecentKind, topRecentTmdbId);
    }

    private static void accumulateGenresFromText(String text, float weight, Map<String, Float> scores) {
        if (text == null || text.isEmpty()) return;
        String t = text.toLowerCase(Locale.ROOT);

        // Mapeo de palabras clave a géneros principales
        addIfMatch(t, new String[]{"accion", "action", "mision", "fuego", "venganza", "guerra", "batalla", "arma"}, "Acción", weight, scores);
        addIfMatch(t, new String[]{"ciencia ficcion", "sci-fi", "alien", "universo", "espacio", "robot", "futuro", "apocalipsis", "dune", "fallout"}, "Ciencia Ficción", weight, scores);
        addIfMatch(t, new String[]{"aventura", "adventure", "viaje", "isla", "selva", "dragon", "tesoro", "explorador"}, "Aventura", weight, scores);
        addIfMatch(t, new String[]{"comedia", "comedy", "risa", "divertido", "chiste", "humor"}, "Comedia", weight, scores);
        addIfMatch(t, new String[]{"terror", "horror", "miedo", "pesadilla", "fantasma", "demonio", "monstruo", "exorcismo", "zombie"}, "Terror", weight, scores);
        addIfMatch(t, new String[]{"drama", "amor", "vida", "emocion", "familia", "secreto", "romance", "lagrimas"}, "Drama", weight, scores);
        addIfMatch(t, new String[]{"anime", "naruto", "dragon ball", "one piece", "shingeki", "otaku", "manga", "bleach", "jujutsu"}, "Anime", weight * 1.5f, scores);
        addIfMatch(t, new String[]{"crimen", "mafia", "policia", "detective", "asesino", "robo", "carcel", "narco"}, "Crimen", weight, scores);
        addIfMatch(t, new String[]{"animacion", "animated", "infantil", "disney", "pixar", "kids", "muñeco"}, "Animación", weight, scores);
        addIfMatch(t, new String[]{"suspenso", "thriller", "misterio", "intriga", "desaparicion"}, "Suspenso", weight, scores);
    }

    private static void addIfMatch(String text, String[] keywords, String genre, float weight, Map<String, Float> scores) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                float curr = scores.containsKey(genre) ? scores.get(genre) : 0.0f;
                scores.put(genre, curr + weight);
                break;
            }
        }
    }

    /**
     * Re-ordena una lista de resultados de búsqueda dando prioridad a los gustos del usuario
     */
    public static List<CatalogData.Entry> rankSearchResults(Context ctx, List<CatalogData.Entry> results) {
        if (results == null || results.size() <= 1) return results;
        UserTasteProfile profile = buildProfile(ctx);
        if (profile.genreWeights.isEmpty()) return results;

        List<CatalogData.Entry> ranked = new ArrayList<>(results);
        Collections.sort(ranked, (a, b) -> {
            float scoreA = profile.getScoreForTitle(a.title, a.kind, a.overview) + (float) a.rating * 0.3f;
            float scoreB = profile.getScoreForTitle(b.title, b.kind, b.overview) + (float) b.rating * 0.3f;
            return Float.compare(scoreB, scoreA); // Mayor puntaje primero
        });

        return ranked;
    }

    /**
     * Genera términos de búsqueda sugeridos antes de que el usuario empiece a escribir
     */
    public static List<String> getZeroQuerySuggestions(Context ctx) {
        List<String> suggestions = new ArrayList<>();
        UserTasteProfile profile = buildProfile(ctx);

        if (!profile.topRecentTitle.isEmpty()) {
            suggestions.add(profile.topRecentTitle);
        }

        // Obtener géneros con mayor puntuación
        List<Map.Entry<String, Float>> sortedGenres = new ArrayList<>(profile.genreWeights.entrySet());
        Collections.sort(sortedGenres, (a, b) -> Float.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < sortedGenres.size() && suggestions.size() < 4; i++) {
            suggestions.add(sortedGenres.get(i).getKey());
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Acción");
            suggestions.add("Ciencia Ficción");
            suggestions.add("Películas de Estreno");
            suggestions.add("Anime HD");
        }

        return suggestions;
    }

    public interface RecommendationCallback {
        void onLoaded(List<CatalogData.Entry> recommendedForYou, String becauseYouWatchedTitle, List<CatalogData.Entry> becauseYouWatched);
    }

    /**
     * Consulta asíncronamente a TMDB para obtener recomendaciones inteligentes "Para ti" y "Porque viste X"
     */
    public static void getRecommendationsAsync(Context ctx, RecommendationCallback callback) {
        if (callback == null) return;
        new Thread(() -> {
            UserTasteProfile profile = buildProfile(ctx);
            List<CatalogData.Entry> forYou = new ArrayList<>();
            List<CatalogData.Entry> becauseYouWatched = new ArrayList<>();
            String becauseTitle = profile.topRecentTitle;

            try {
                String apiKey = "9b646c24388e3678096f272c7fc63321"; // Clave TMDB de Leo TV

                // 1. "Porque viste [Último contenido]"
                if (!profile.topRecentTitle.isEmpty()) {
                    long targetTmdbId = profile.topRecentTmdbId;
                    String isTv = "serie".equalsIgnoreCase(profile.topRecentKind) ? "tv" : "movie";

                    if (targetTmdbId <= 0) {
                        String clean = profile.topRecentTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                        String sUrl = "https://api.themoviedb.org/3/search/" + isTv + "?api_key=" + apiKey + "&query=" + URLEncoder.encode(clean, "UTF-8") + "&language=es-MX";
                        JSONObject sObj = new JSONObject(HttpSupport.text(sUrl, "https://www.themoviedb.org/"));
                        JSONArray sRes = sObj.optJSONArray("results");
                        if (sRes != null && sRes.length() > 0) {
                            targetTmdbId = sRes.getJSONObject(0).optLong("id");
                        }
                    }

                    if (targetTmdbId > 0) {
                        String recUrl = "https://api.themoviedb.org/3/" + isTv + "/" + targetTmdbId + "/recommendations?api_key=" + apiKey + "&language=es-MX&page=1";
                        JSONObject recObj = new JSONObject(HttpSupport.text(recUrl, "https://www.themoviedb.org/"));
                        JSONArray recResults = recObj.optJSONArray("results");
                        if (recResults != null) {
                            for (int i = 0; i < recResults.length() && becauseYouWatched.size() < 12; i++) {
                                JSONObject item = recResults.getJSONObject(i);
                                item.put("kind", isTv.equals("tv") ? "serie" : "movie");
                                item.put("provider_name", "TMDB");
                                becauseYouWatched.add(new CatalogData.Entry(item));
                            }
                        }
                    }
                }

                // 2. "🎯 Recomendado para vos" (basado en género dominante o trending)
                String genreId = "878"; // Sci-Fi por defecto
                if (!profile.genreWeights.isEmpty()) {
                    List<Map.Entry<String, Float>> sorted = new ArrayList<>(profile.genreWeights.entrySet());
                    Collections.sort(sorted, (a, b) -> Float.compare(b.getValue(), a.getValue()));
                    String topGenre = sorted.get(0).getKey();
                    if ("Acción".equals(topGenre)) genreId = "28";
                    else if ("Aventura".equals(topGenre)) genreId = "12";
                    else if ("Animación".equals(topGenre) || "Anime".equals(topGenre)) genreId = "16";
                    else if ("Comedia".equals(topGenre)) genreId = "35";
                    else if ("Terror".equals(topGenre)) genreId = "27";
                    else if ("Drama".equals(topGenre)) genreId = "18";
                    else if ("Crimen".equals(topGenre)) genreId = "80";
                    else if ("Suspenso".equals(topGenre)) genreId = "53";
                }

                String type = profile.prefersSeries ? "tv" : "movie";
                String discUrl = "https://api.themoviedb.org/3/discover/" + type + "?api_key=" + apiKey + "&language=es-MX&sort_by=popularity.desc&with_genres=" + genreId + "&page=1";
                JSONObject discObj = new JSONObject(HttpSupport.text(discUrl, "https://www.themoviedb.org/"));
                JSONArray discResults = discObj.optJSONArray("results");
                if (discResults != null) {
                    for (int i = 0; i < discResults.length() && forYou.size() < 12; i++) {
                        JSONObject item = discResults.getJSONObject(i);
                        item.put("kind", type.equals("tv") ? "serie" : "movie");
                        item.put("provider_name", "TMDB");
                        forYou.add(new CatalogData.Entry(item));
                    }
                }
            } catch (Exception ignored) {}

            callback.onLoaded(forYou, becauseTitle, becauseYouWatched);
        }, "TasteRecommender").start();
    }
}
