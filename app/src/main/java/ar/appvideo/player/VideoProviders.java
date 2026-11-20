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
    private static final VideoProvider TIOANIME=new HtmlCatalogProvider("TioAnime","https://tioanime.com",false,null,true);
    private static final VideoProvider LATANIME=new HtmlCatalogProvider("LatAnime","https://latanime.org",false,null,true);
    private static final VideoProvider CINECALIDAD=new HtmlCatalogProvider("CineCalidad","https://www.cinecalidad.am",true,null);
    private static final VideoProvider PELISPLUS=new HtmlCatalogProvider("Pelisplus","https://pelisplus.to",true,null);
    private static final VideoProvider SERIESFLIX=new HtmlCatalogProvider("Seriesflix","https://pelisflix1.guru",true,null);
    private static final VideoProvider DORAMASFLIX=new DoramasFlixProvider();
    private static final VideoProvider CANELA=new CanelaTVProvider();
    private static final VideoProvider SOLOLATINO=new SoloLatinoProvider();
    private static final VideoProvider JKANIME=new HtmlCatalogProvider("JKAnime","https://jkanime.net",false,null,true);

    // Adaptadores HTML secundarios
    private static final VideoProvider CUEVANA=new HtmlCatalogProvider("Cuevana","https://wv3.cuevana3.eu",false,null);
    private static final VideoProvider HENAOJARA=new HtmlCatalogProvider("HenaoJara","https://ww1.henaojara.net",false,null);
    private static final VideoProvider FLIXLATAM=new HtmlCatalogProvider("FlixLatam","https://flixlatam.com",false,null);
    private static final VideoProvider VERPELIS=new HtmlCatalogProvider("VerPelisTop","https://www1.verpelis.top",false,null);
    private static final VideoProvider VERANIME=new HtmlCatalogProvider("VerAni.me","https://verani.me",false,null,true);
    private static final VideoProvider VERANIMES=new HtmlCatalogProvider("VerAnimes","https://wwv.veranimes.net",false,null,true);
    private static final VideoProvider ANIMEAV1=new HtmlCatalogProvider("AnimeAV1","https://animeav1.com",false,null,true);
    private static final VideoProvider BEATZANIME=new HtmlCatalogProvider("BeatZ Anime","https://www.beatz-anime.net",false,null,true);
    private static final VideoProvider MUNDODONGHUA=new HtmlCatalogProvider("MundoDonghua","https://www.mundodonghua.com",false,null,true);
    private static final VideoProvider ARESHD=new HtmlCatalogProvider("AresHD","https://areshd.com",true,null);
    private static final VideoProvider CINEHAXPLUS=new HtmlCatalogProvider("CineHax+","https://cinehaxplus.com",true,null);
    private static final VideoProvider CINEHDPLUS=new HtmlCatalogProvider("CineHDPlus","https://cinehdplus.org",true,null);
    private static final VideoProvider DETODO=new HtmlCatalogProvider("DeTodo Peliculas","https://detodopeliculas.nu",true,null);
    private static final VideoProvider MONOSCHINOS=new HtmlCatalogProvider("MonosChinos","https://vww.monoschinos2.net",false,null,true);
    private static final VideoProvider CINEPLUS123=new HtmlCatalogProvider("Cineplus123","https://cineplus123.org",true,null);

    static boolean isDead(String name) {
        if (name == null) return true;
        return name.equals("LaMovie") || name.equals("Canela TV") || name.equals("Canela") || name.equals("canela")
            || name.equals("DoramasFlix") || name.equals("Pelisplus")
            || name.equals("DeTodo Peliculas") || name.equals("VerAni.me");
    }

    static boolean provisional(String name) {
        return name.equals("Cuevana")||name.equals("HenaoJara")||
            name.equals("FlixLatam")||name.equals("VerPelisTop")||
            name.equals("VerAnimes")||name.equals("AnimeAV1")||name.equals("BeatZ Anime")||
            name.equals("MundoDonghua")||name.equals("AresHD")||name.equals("CineHax+")||
            name.equals("CineHDPlus")||name.equals("MonosChinos")||
            name.equals("Cineplus123");
    }

    static VideoProvider find(String name) {
        if(isDead(name)) return null;
        if(name.equals("CineHax"))return CINEHAX;
        if(name.equals("LaMovie"))return LAMOVIE;
        if(name.equals("SeriesMetro"))return SERIESMETRO;
        if(name.equals("Pelispedia"))return PELISPEDIA;
        if(name.equals("TioAnime"))return TIOANIME;
        if(name.equals("LatAnime"))return LATANIME;
        if(name.equals("CineCalidad"))return CINECALIDAD;
        if(name.equals("Pelisplus"))return PELISPLUS;
        if(name.equals("Seriesflix"))return SERIESFLIX;
        if(name.equals("DoramasFlix"))return DORAMASFLIX;
        if(name.equals("Canela TV")||name.equals("Canela")||name.equals("canela"))return CANELA;
        if(name.equals("SoloLatino")||name.equals("sololatino"))return SOLOLATINO;
        if(name.equals("JKAnime"))return JKANIME;
        if(name.equals("Cuevana"))return CUEVANA;
        if(name.equals("HenaoJara"))return HENAOJARA;
        if(name.equals("FlixLatam"))return FLIXLATAM;
        if(name.equals("VerPelisTop"))return VERPELIS;
        if(name.equals("VerAni.me"))return VERANIME;
        if(name.equals("VerAnimes"))return VERANIMES;
        if(name.equals("AnimeAV1"))return ANIMEAV1;
        if(name.equals("BeatZ Anime"))return BEATZANIME;
        if(name.equals("MundoDonghua"))return MUNDODONGHUA;
        if(name.equals("AresHD"))return ARESHD;
        if(name.equals("CineHax+"))return CINEHAXPLUS;
        if(name.equals("CineHDPlus"))return CINEHDPLUS;
        if(name.equals("DeTodo Peliculas"))return DETODO;
        if(name.equals("MonosChinos"))return MONOSCHINOS;
        if(name.equals("Cineplus123"))return CINEPLUS123;
        return null;
    }
}
