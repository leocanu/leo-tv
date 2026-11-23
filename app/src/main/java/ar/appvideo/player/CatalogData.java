package ar.appvideo.player;

public final class CatalogData {
    private CatalogData() {
    }

    public static final class Entry {
        public long id, tmdb, tvmazeId;
        public final String title, originalTitle, kind, poster, backdrop, slug, pageUrl, castUrl,
                providerName;
        public String overview;
        public final int year, runtime;
        public final double rating;

        public void setOverview(String ov) {
            if (ov != null && !ov.trim().isEmpty()) {
                this.overview = ov.trim();
            }
        }
        public void setTmdb(long tid) {
            if (tid > 0) this.tmdb = tid;
        }
        public void setTvmazeId(long tvid) {
            if (tvid > 0) this.tvmazeId = tvid;
        }

        public Entry(org.json.JSONObject json) {
            this(json, json.optString("provider_name", ""));
        }

        public Entry(String title, long id, String poster, String backdrop, String kind, int year, String provider) {
            this.title = title != null ? title : "Sin titulo";
            this.id = id;
            this.tmdb = 0;
            this.tvmazeId = 0;
            this.originalTitle = "";
            this.kind = kind != null ? kind : "movie";
            this.year = year;
            this.runtime = 0;
            double verifiedRating = RatingEngine.getRating(this.title, this.year, !"movie".equals(this.kind),
                    null).score;
            this.rating = verifiedRating > 0 ? verifiedRating : 0.0;
            this.slug = "";
            this.pageUrl = "";
            this.castUrl = "";
            this.providerName = provider != null ? provider : "";
            this.poster = poster != null ? poster : "";
            this.backdrop = backdrop != null ? backdrop : this.poster;
            this.overview = "";
        }

        public Entry(org.json.JSONObject json, String sourceProvider) {
            id = json.optLong("catalog_item_id");
            tmdb = json.optLong("tmdb_id");
            tvmazeId = json.optLong("tvmaze_id");
            title = json.optString("title", "Sin titulo");
            originalTitle = json.isNull("original_title") ? "" : json.optString("original_title", "");
            kind = json.optString("kind", "movie");
            year = json.optInt("year");
            runtime = json.optInt("runtime", 0);
            rating = json.optDouble("vote_average", json.optDouble("rating_avg", 0.0));
            slug = json.isNull("slug") ? "" : json.optString("slug", "");
            pageUrl = json.isNull("page_url") ? "" : json.optString("page_url", "");
            String rawCast = json.isNull("cast_url") ? "" : json.optString("cast_url", "");
            castUrl = rawCast.isEmpty() && id > 0 ? "/api/theme/cast?item_id=" + id : rawCast;
            providerName = sourceProvider == null ? "" : sourceProvider;
            String path = json.isNull("poster_path") ? "" : json.optString("poster_path", "");
            poster = path.startsWith("/") ? "https://image.tmdb.org/t/p/w342" + path
                    : (path.startsWith("https://") ? path : "");
            String bgPath = json.isNull("backdrop_path") ? "" : json.optString("backdrop_path", "");
            backdrop = bgPath.startsWith("/") ? "https://image.tmdb.org/t/p/w780" + bgPath
                    : (bgPath.startsWith("https://") ? bgPath : poster);
            overview = json.isNull("overview") ? "" : json.optString("overview", "");
        }

        public boolean isMovie() {
            return kind.equals("movie");
        }

        public boolean directPlayback() {
            return isMovie() || kind.equals("episode");
        }

        public org.json.JSONObject toJson() {
            org.json.JSONObject o = new org.json.JSONObject();
            try {
                o.put("catalog_item_id", id);
                o.put("tmdb_id", tmdb);
                o.put("tvmaze_id", tvmazeId);
                o.put("title", title);
                o.put("original_title", originalTitle);
                o.put("kind", kind);
                o.put("year", year);
                o.put("runtime", runtime);
                o.put("vote_average", rating);
                o.put("slug", slug);
                o.put("page_url", pageUrl);
                o.put("cast_url", castUrl);
                o.put("provider_name", providerName);
                o.put("poster_path", poster);
                o.put("backdrop_path", backdrop);
                o.put("overview", overview);
            } catch (Exception ignored) {
            }
            return o;
        }
    }

    public static final class CatalogPage {
        public final java.util.List<Entry> entries = new java.util.ArrayList<>();
        public final boolean next, totalKnown;
        public final int page, total;

        public CatalogPage(org.json.JSONObject json, int requested) {
            org.json.JSONArray items = json.optJSONArray("items");
            if (items != null)
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject item = items.optJSONObject(i);
                    if (item != null && (item.optLong("tmdb_id") > 0 || item.optLong("catalog_item_id") > 0
                            || item.optLong("tvmaze_id") > 0
                            || item.optString("page_url", "").startsWith("https://"))) {
                        entries.add(new Entry(item));
                    }
                }
            org.json.JSONObject pagination = json.optJSONObject("pagination");
            totalKnown = pagination != null && pagination.has("total");
            next = pagination != null && pagination.optBoolean("has_next");
            page = pagination == null ? requested : pagination.optInt("page", requested);
            total = pagination == null ? entries.size() : pagination.optInt("total");
        }
    }
}
