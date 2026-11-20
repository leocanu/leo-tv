package ar.appvideo.player;

import org.json.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.*;

/** Adaptador propio de la API publica de LaMovie. */
public final class LaMovieProvider implements VideoProvider {
    private static final String BASE="https://lamovie.org";
    private static final Map<String,JSONObject> CACHE=new LinkedHashMap<>();
    private static final Map<String,Long> TIMES=new HashMap<>();
    @Override public String name(){return "LaMovie";}
    @Override public boolean supportsNovels(){return false;}

    private JSONObject api(String route) throws Exception {
        boolean cacheable=!route.startsWith("/player");
        synchronized(CACHE) {
            if(cacheable&&CACHE.containsKey(route)&&System.currentTimeMillis()-TIMES.get(route)<120000)
                return CACHE.get(route);
        }
        JSONObject response=new JSONObject(HttpSupport.text(BASE+"/wp-api/v1"+route,BASE+"/"));
        if(response.optBoolean("error"))throw new IOException("La fuente no devolvio datos disponibles.");
        JSONObject data=response.optJSONObject("data");
        if(data==null)throw new IOException("Respuesta de catalogo no reconocida.");
        if(cacheable)synchronized(CACHE) {
            if(!CACHE.containsKey(route)&&CACHE.size()>=24) {
                String oldest=CACHE.keySet().iterator().next();CACHE.remove(oldest);TIMES.remove(oldest);
            }
            CACHE.put(route,data);TIMES.put(route,System.currentTimeMillis());
        }
        return data;
    }
    private JSONObject normalize(JSONObject source) throws Exception {
        JSONObject item=new JSONObject();
        String rawTitle=source.optString("title","Sin titulo");
        String full=(android.os.Build.VERSION.SDK_INT>=24
            ? android.text.Html.fromHtml(rawTitle,android.text.Html.FROM_HTML_MODE_LEGACY)
            : android.text.Html.fromHtml(rawTitle)).toString();
        int year=0;String title=full;
        Matcher match=Pattern.compile("^(.*?)\\s*\\((\\d{4})\\)\\s*$").matcher(full);
        if(match.matches()){title=match.group(1);year=Integer.parseInt(match.group(2));}
        else {
            String date=source.optString("release_date","");
            if(date.length()>=4)try{year=Integer.parseInt(date.substring(0,4));}catch(NumberFormatException ignored){}
        }
        JSONObject images=source.optJSONObject("images");
        String poster=images==null?"":images.optString("poster","");
        if(poster.startsWith("/"))poster=BASE+"/wp-content/uploads"+poster;
        item.put("catalog_item_id",source.optLong("_id"));
        item.put("title",title);item.put("year",year);item.put("slug",source.optString("slug",""));
        item.put("poster_path",poster);item.put("overview",source.optString("overview",""));
        item.put("kind",source.optString("type").equals("movies")?"movie":"serie");
        return item;
    }
    private JSONObject pagination(JSONObject data,int page) throws Exception {
        JSONObject original=data.optJSONObject("pagination");
        JSONObject result=new JSONObject();
        if(original==null)return result.put("page",page).put("has_next",false).put("total",0);
        result.put("page",original.optInt("current_page",page));
        result.put("total",original.optInt("total"));
        result.put("has_next",!original.isNull("next_page_url")
            &&!original.optString("next_page_url","").isEmpty());
        return result;
    }
    @Override public CatalogData.CatalogPage catalog(String kind,String query,int page) throws Exception {
        String type=kind.equals("movie")?"movies":"tvshows";
        String path=query.trim().isEmpty()
            ?"/listing/"+type+"?postType="+type+"&page="+page+"&postsPerPage=24"
            :"/search?postType=any&q="+URLEncoder.encode(query.trim(),"UTF-8")+"&page="+page+"&postsPerPage=24";
        JSONObject data=api(path);JSONArray posts=data.optJSONArray("posts"),items=new JSONArray();
        if(posts!=null)for(int i=0;i<posts.length();i++) {
            JSONObject post=posts.optJSONObject(i);if(post==null)continue;
            String postType=post.optString("type");
            if(!postType.equals("movies")&&!postType.equals("tvshows"))continue;
            items.put(normalize(post));
        }
        return new CatalogData.CatalogPage(new JSONObject().put("items",items).put("pagination",pagination(data,page)),page);
    }
    private JSONObject episodeData(CatalogData.Entry entry,int season,int page,int limit) throws Exception {
        return api("/single/episodes/list?_id="+entry.id+"&season="+season+"&page="+page+"&postsPerPage="+limit);
    }
    @Override public JSONArray seasons(CatalogData.Entry entry) throws Exception {
        JSONArray seasons=episodeData(entry,1,1,30).optJSONArray("seasons");
        List<Integer> numbers=new ArrayList<>();
        if(seasons!=null)for(int i=0;i<seasons.length();i++) {
            int number=seasons.optInt(i,-1);if(number>=0&&!numbers.contains(number))numbers.add(number);
        }
        Collections.sort(numbers);JSONArray result=new JSONArray();
        for(int number:numbers)result.put(new JSONObject().put("season",number).put("name","Temporada "+number));
        return result;
    }
    @Override public JSONObject episodes(CatalogData.Entry entry,int season,int page) throws Exception {
        JSONObject data=episodeData(entry,season,page,30);JSONArray items=data.optJSONArray("posts"),episodes=new JSONArray();
        if(items!=null)for(int i=0;i<items.length();i++) {
            JSONObject ep=items.optJSONObject(i);if(ep==null)continue;
            episodes.put(new JSONObject().put("episode",ep.optInt("episode_number")).put("season",season)
                .put("title",ep.optString("title","Episodio")).put("playable",true));
        }
        return new JSONObject().put("episodes",episodes).put("pagination",pagination(data,page));
    }
    @Override public VideoLink resolve(CatalogData.Entry entry,int season,int episode) throws Exception {
        long postId=entry.id;
        if(!entry.isMovie()) {
            JSONArray posts=episodeData(entry,season,(episode-1)/50+1,50).optJSONArray("posts");postId=0;
            if(posts!=null)for(int i=0;i<posts.length();i++) {
                JSONObject ep=posts.optJSONObject(i);
                if(ep!=null&&ep.optInt("season_number")==season&&ep.optInt("episode_number")==episode) {
                    postId=ep.optLong("_id");break;
                }
            }
            if(postId<=0)throw new IOException("No se encontro el episodio.");
        }
        JSONArray embeds=api("/player?postId="+postId+"&demo=0").optJSONArray("embeds");
        if(embeds==null)throw new IOException("La fuente no ofrece servidores para este titulo.");
        List<JSONObject> ordered=new ArrayList<>();
        for(int pass=0;pass<2;pass++)for(int i=0;i<embeds.length();i++) {
            JSONObject embed=embeds.optJSONObject(i);if(embed==null)continue;
            boolean latin=embed.optString("lang","").toLowerCase(Locale.ROOT).contains("lat");
            if(latin==(pass==0))ordered.add(embed);
        }
        int attempted=0;
        for(JSONObject embed:ordered) {
            String address=embed.optString("url","");
            if(!VimeosResolver.supports(address))continue;
            if(attempted++>=4)break;
            try{return VimeosResolver.resolve(address,"https://vimeos.net/");}catch(Exception ignored){}
        }
        throw new IOException("No hay un servidor compatible disponible para este titulo.");
    }
}
