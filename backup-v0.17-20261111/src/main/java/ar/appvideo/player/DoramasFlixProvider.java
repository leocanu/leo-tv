package ar.appvideo.player;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Catalogo Next.js de DoramasFlix: solo metadatos publicos, sin suponer enlaces de video. */
final class DoramasFlixProvider implements VideoProvider {
    private static final String BASE="https://doramasflix.co";
    private static final Pattern HREF=Pattern.compile("\"href\":\"(/(?:doramas|peliculas)/[^\"]+)\"");
    private static final Pattern TITLE=Pattern.compile("\"displayName\":\"([^\"]{1,140})\"");
    private static final Pattern TYPE=Pattern.compile("\"type\":\"(dorama|movie)\"");
    private static final Pattern IMAGE=Pattern.compile("\"image\":\"([^\"]*)\"");
    private static final int PAGE_SIZE=24;
    public String name(){return "DoramasFlix";}
    public boolean supportsNovels(){return false;}
    public String homeLabel(){return "CATALOGO PROVISIONAL";}
    public String description(){return "Catalogo publico parcial; busca solo entre titulos de portada. Episodios y reproduccion aun no integrados.";}
    private static String last(Pattern pattern,String text) {
        Matcher m=pattern.matcher(text);String value="";
        while(m.find())value=m.group(1);
        return value;
    }
    public CatalogData.CatalogPage catalog(String kind,String query,int page) throws Exception {
        if(page<1||page>50)throw new IOException("Pagina fuera de rango.");
        String targetUrl;
        if (!query.trim().isEmpty()) {
            targetUrl = BASE + "/buscar?q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8");
        } else if ("movie".equals(kind)) {
            targetUrl = BASE + "/peliculas";
        } else {
            targetUrl = BASE + "/doramas";
        }
        String source=HttpSupport.text(targetUrl,BASE+"/").replace("\\\"", "\"");
        Matcher links=HREF.matcher(source);
        LinkedHashMap<String,JSONObject> found=new LinkedHashMap<>();
        while(links.find()&&found.size()<160){
            String path=links.group(1);
            String prefix=source.substring(Math.max(0,links.start()-600),links.start());
            String name=last(TITLE,prefix),type=last(TYPE,prefix);
            if(name.isEmpty())continue;
            boolean movie=path.startsWith("/peliculas/");
            boolean dorama=path.startsWith("/doramas/");
            if(!movie&&!dorama)continue;
            if(query.trim().isEmpty()) {
                if(movie&&!kind.equals("movie"))continue;
                if(dorama&&!kind.equals("serie"))continue;
            }
            String tail=source.substring(links.end(),Math.min(source.length(),links.end()+210));
            String poster=last(IMAGE,tail);
            JSONObject item=new JSONObject().put("page_url",BASE+path)
                .put("title",name).put("kind",movie?"movie":"serie");
            if(poster.matches("/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)"))item.put("poster_path",poster);
            found.put(BASE+path,item);
        }
        if(found.isEmpty())throw new IOException("No se hallaron titulos de esta categoria en el catalogo publico de portada.");
        JSONArray result=new JSONArray();List<JSONObject> all=new ArrayList<>(found.values());
        int start=(page-1)*PAGE_SIZE;
        for(int i=start;i<Math.min(all.size(),start+PAGE_SIZE);i++)result.put(all.get(i));
        return new CatalogData.CatalogPage(new JSONObject()
            .put("items",result)
            .put("pagination",new JSONObject().put("page",page).put("has_next",all.size()>start+PAGE_SIZE)),page);
    }
    public JSONArray seasons(CatalogData.Entry entry){return new JSONArray();}
    public JSONObject episodes(CatalogData.Entry entry,int season,int page) throws Exception{
        return new JSONObject().put("episodes",new JSONArray())
            .put("pagination",new JSONObject().put("has_next",false));
    }
    public VideoLink resolve(CatalogData.Entry entry,int season,int episode)throws Exception{
        throw new IOException("DoramasFlix: la reproduccion aun no esta integrada.");
    }
}
