package ar.appvideo.player;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.HashMap;

public interface VideoProvider {
    String name();
    boolean supportsNovels();
    default boolean supportsSeries(){return true;}
    CatalogData.CatalogPage catalog(String kind, String query, int page) throws Exception;
    JSONArray seasons(CatalogData.Entry entry) throws Exception;
    JSONObject episodes(CatalogData.Entry entry, int season, int page) throws Exception;
    VideoLink resolve(CatalogData.Entry entry, int season, int episode) throws Exception;

    final class VideoLink {
        public final String url;
        public final Map<String,String> headers;
        public VideoLink(String address, Map<String,String> requestHeaders) {
            url=address; headers=new HashMap<>(requestHeaders);
        }
    }
}
