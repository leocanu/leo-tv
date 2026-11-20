package ar.appvideo.player;

public final class CatalogData {
    private CatalogData() {}
    public static final class Entry {
        public final long id, tmdb;
        public final String title, kind, poster, overview, slug, pageUrl;
        public final int year;
        Entry(org.json.JSONObject json) {
            id = json.optLong("catalog_item_id");
            tmdb = json.optLong("tmdb_id");
            title = json.optString("title", "Sin titulo");
            kind = json.optString("kind", "movie");
            year = json.optInt("year");
            slug = json.optString("slug", "");
            pageUrl = json.optString("page_url", "");
            String path = json.optString("poster_path", "");
            poster = path.startsWith("/") ? "https://image.tmdb.org/t/p/w342" + path : (path.startsWith("https://") ? path : "");
            overview = json.isNull("overview") ? "" : json.optString("overview", "");
        }
        public boolean isMovie() { return kind.equals("movie"); }
    }
    public static final class CatalogPage {
        public final java.util.List<Entry> entries = new java.util.ArrayList<>();
        public final boolean next, totalKnown;
        public final int page, total;
        CatalogPage(org.json.JSONObject json, int requested) {
            org.json.JSONArray items = json.optJSONArray("items");
            if (items != null) for (int i=0; i<items.length(); i++) {
                org.json.JSONObject item = items.optJSONObject(i);
                if (item != null && (item.optLong("tmdb_id") > 0 || item.optLong("catalog_item_id") > 0 || item.optString("page_url","").startsWith("https://"))) entries.add(new Entry(item));
            }
            org.json.JSONObject pagination = json.optJSONObject("pagination");
            totalKnown = pagination != null && pagination.has("total");
            next = pagination != null && pagination.optBoolean("has_next");
            page = pagination == null ? requested : pagination.optInt("page", requested);
            total = pagination == null ? entries.size() : pagination.optInt("total");
        }
    }

}
