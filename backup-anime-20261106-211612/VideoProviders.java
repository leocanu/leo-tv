package ar.appvideo.player;

import org.json.*;
import java.util.*;

final class VideoProviders {
    private static final VideoProvider CINEHAX=new VideoProvider() {
        public String name(){return "CineHax";}
        public boolean supportsNovels(){return true;}
        public CatalogData.CatalogPage catalog(String kind,String query,int page) throws Exception {
            return CineHaxProvider.catalog(kind,query,page);
        }
        public JSONArray seasons(CatalogData.Entry entry) throws Exception{return CineHaxProvider.seasons(entry.id);}
        public JSONObject episodes(CatalogData.Entry entry,int season,int page) throws Exception {
            return CineHaxProvider.episodes(entry.id,season,page);
        }
        public VideoLink resolve(CatalogData.Entry entry,int season,int episode) throws Exception {
            String address=CineHaxProvider.resolve(entry.tmdb,season,episode);
            Map<String,String> headers=new HashMap<>();headers.put("User-Agent",HttpSupport.USER_AGENT);
            return new VideoLink(address,headers);
        }
    };
    private static final VideoProvider LAMOVIE=new LaMovieProvider();
    private static final VideoProvider SERIESMETRO=new HtmlCatalogProvider("SeriesMetro","https://www3.seriesmetro.net",true,"action_select_season");
    private static final VideoProvider PELISPEDIA=new HtmlCatalogProvider("Pelispedia","https://pelispedia.is",true,"action_select_season");
    static VideoProvider find(String name) {
        if(name.equals("CineHax"))return CINEHAX;
        if(name.equals("LaMovie"))return LAMOVIE;
        if(name.equals("SeriesMetro"))return SERIESMETRO;
        if(name.equals("Pelispedia"))return PELISPEDIA;
        return null;
    }
}
