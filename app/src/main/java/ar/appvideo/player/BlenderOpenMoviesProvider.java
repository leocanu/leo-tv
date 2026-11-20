package ar.appvideo.player;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Official Blender download endpoints. Non-invasive, single HEAD per curated item. */
public final class BlenderOpenMoviesProvider {
    public static final class Film {
        public final String title, url, format, kind;
        public final int year;
        Film(String title, String url, String format, String kind, int year) {
            this.title=title; this.url=url; this.format=format; this.kind=kind; this.year=year;
        }
    }
    private static boolean reachable(String path) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection)new URL(path).openConnection();
            c.setRequestMethod("HEAD");
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            String type = c.getContentType();
            return code == 200 && type != null &&
                (type.toLowerCase().startsWith("video/") || type.toLowerCase().startsWith("application/octet-stream"));
        } catch (Exception error) {
            return false;
        } finally {
            if (c!=null) c.disconnect();
        }
    }
    public static List<Film> availableFilms() {
        Film[] curated = {
            new Film("Elephants Dream", "https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov", "480p MOV", "Película completa", 2006),
            new Film("Sintel", "https://download.blender.org/durian/trailer/sintel_trailer-720p.mp4", "720p MP4", "Tráiler oficial", 2010)
        };
        List<Film> checked = new ArrayList<>();
        for (Film film : curated) if (reachable(film.url)) checked.add(film);
        return checked;
    }
}
