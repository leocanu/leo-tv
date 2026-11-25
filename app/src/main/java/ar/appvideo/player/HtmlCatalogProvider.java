package ar.appvideo.player;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Adaptador propio de catalogos HTML publicos con fichas y episodios reconocibles. */
public final class HtmlCatalogProvider implements VideoProvider {
    private final String name,base,rootDomain,seasonAction;
    private final boolean series,anime;
    private String searchTarget,searchParam="s";
    private String movieListing,seriesListing;
    private final Map<String,CatalogData.CatalogPage> pages=new LinkedHashMap<>();
    private final Map<String,Long> pageTimes=new HashMap<>();
    private final Map<String,String> paths=new LinkedHashMap<>();
    private final Map<String,SeriesIndex> shows=new LinkedHashMap<>();
    private static final Pattern YEAR=Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern EPISODE=Pattern.compile("(?:temporada[-\\s]*(\\d+)[-\\s]*(?:capitulo|episodio)[-\\s]*(\\d+))|(\\d+)\\s*[-xX]\\s*(\\d+)",Pattern.CASE_INSENSITIVE);
    private static final class EpisodeRef {
        final int season,number;final String title,url;
        EpisodeRef(int s,int n,String t,String u){season=s;number=n;title=t;url=u;}
    }
    private static final class SeriesIndex {
        long checked=System.currentTimeMillis();String postId="",episodePrefix="";
        final Set<Integer> seasons=new TreeSet<>(),loaded=new HashSet<>();
        final Map<Integer,List<EpisodeRef>> episodes=new HashMap<>();
    }
    public HtmlCatalogProvider(String providerName,String origin,boolean hasSeries,String ajaxSeasonAction) {
        this(providerName,origin,hasSeries,ajaxSeasonAction,false);
    }
    public HtmlCatalogProvider(String providerName,String origin,boolean hasSeries,String ajaxSeasonAction,boolean animeMode) {
        name=providerName;base=origin.replaceAll("/+$","");series=hasSeries;seasonAction=ajaxSeasonAction;anime=animeMode;searchTarget=base+"/";
        String host=URI.create(base).getHost();String[] labels=host.split("\\.");
        rootDomain=labels.length>1?labels[labels.length-2]+"."+labels[labels.length-1]:host;
    }
    @Override public String name(){return name;}
    @Override public boolean supportsNovels(){return false;}
    @Override public boolean supportsSeries(){return series;}
    @Override public String primaryCategoryLabel(){return anime?"Novedades":"Peliculas";}
    @Override public String homeLabel(){return anime?"ANIME Y EPISODIOS":"CATALOGO";}
    @Override public String description(){return anime?"Novedades y busqueda de "+name+". Cada video se comprueba al abrirlo.":VideoProvider.super.description();}
    private boolean own(String url) {
        try{
            URL parsed=new URL(url);String host=parsed.getHost().toLowerCase(Locale.ROOT);
            return parsed.getProtocol().equals("https")&&(host.equals(rootDomain)||host.endsWith("."+rootDomain));
        }catch(Exception ignored){return false;}
    }
    private Document document(String url) throws Exception {
        if(!own(url))throw new IOException("El enlace no pertenece a esta fuente.");
        return Jsoup.parse(HttpSupport.text(url,base+"/"),url);
    }
    private static String normalized(String text) {
        return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").trim().toLowerCase(Locale.ROOT);
    }
    // Alternativa para catálogos cuyos enlaces públicos no usan tarjetas <article>.
    private boolean isProvisionalStructure() {
        return Arrays.asList("pelisplus.to","pelisflix1.link","pelisflix1.guru","cinecalidad.am","pelisplushd.bz",
            "pelisflixhd.love","areshd.com","cinehaxplus.com","cinehdplus.org","detodopeliculas.nu",
            "jkanime.net","monoschinos2.net","sololatino.net","latanime.org","tioanime.com",
            "moflix-stream.xyz","goodstream.one").contains(rootDomain);
    }
    private void discoverListings(Document doc) {
        if(rootDomain.equals("jkanime.net")){searchTarget=base+"/buscar";searchParam="q";}
        else if(rootDomain.equals("latanime.org")){searchTarget=base+"/buscar";searchParam="q";}
        else if(rootDomain.equals("tioanime.com")){searchTarget=base+"/directorio";searchParam="q";}
        else if(rootDomain.equals("pelisplus.to")){searchTarget=base+"/search";searchParam="s";}
        else if(rootDomain.equals("moflix-stream.xyz")){searchTarget=base+"/search";searchParam="s";}
        else if(rootDomain.equals("goodstream.one")){searchTarget=base+"/";searchParam="s";}
        else if(rootDomain.equals("cinecalidad.am")){searchTarget=base+"/";searchParam="s";}
        else if(rootDomain.equals("pelisflix1.link")||rootDomain.equals("pelisflix1.guru")){searchTarget=base+"/";searchParam="s";}

        for(Element form:doc.select("form")){
            if(form.hasAttr("method")&&!form.attr("method").equalsIgnoreCase("get"))continue;
            Element input=form.selectFirst("input[type=search][name],input[placeholder*=Buscar][name],input[placeholder*=buscar][name]");
            if(input==null)continue;
            String target=form.absUrl("action"),param=input.attr("name");
            if(target.isEmpty())target=base+"/";
            if(own(target)&&param.matches("[A-Za-z_]{1,30}")){searchTarget=target;searchParam=param;break;}
        }
        for(Element a:doc.select("a[href]")){
            String text=normalized(a.text()),url=a.absUrl("href");
            if(!own(url)||url.contains("#"))continue;
            if(text.equals("peliculas")||text.equals("movies")||text.equals("peliculas online"))movieListing=url;
            if(text.equals("series")||text.equals("series de tv")||text.equals("series online")||text.equals("ver series"))seriesListing=url;
        }
        if(movieListing==null){
            if(rootDomain.equals("pelisplus.to"))movieListing=base+"/peliculas";
            else if(rootDomain.equals("pelisflix1.link")||rootDomain.equals("pelisflix1.guru"))movieListing=base+"/peliculas-online/";
            else if(rootDomain.equals("cinecalidad.am"))movieListing=base+"/";
        }
        if(seriesListing==null){
            if(rootDomain.equals("pelisplus.to"))seriesListing=base+"/series";
            else if(rootDomain.equals("pelisflix1.link")||rootDomain.equals("pelisflix1.guru"))seriesListing=base+"/series-online/";
            else if(rootDomain.equals("cinecalidad.am"))seriesListing=base+"/ver-serie/";
        }
    }
    @Override public CatalogData.CatalogPage catalog(String kind,String query,int page) throws Exception {
        String key=kind+"|"+query.trim()+"|"+page;
        synchronized(pages){
            if(pages.containsKey(key)&&System.currentTimeMillis()-pageTimes.get(key)<180000)return pages.get(key);
        }
        String url;
        if(page>1){
            synchronized(paths){url=paths.get(key);}
            if(url==null)throw new IOException("No hay un enlace verificado para esa pagina.");
        }else if(!query.trim().isEmpty())url=searchTarget+(searchTarget.contains("?")?"&":"?")+URLEncoder.encode(searchParam,"UTF-8")+"="+URLEncoder.encode(query.trim(),"UTF-8");
        else url=kind.equals("movie")?movieListing:seriesListing;
        if(url==null)url=base+"/";
        Document doc=document(url);
        discoverListings(doc);
        if(page==1&&query.trim().isEmpty()&&!anime){
            String listing=kind.equals("movie")?movieListing:seriesListing;
            if(listing!=null&&!listing.equals(url)){url=listing;doc=document(url);}
        }
        JSONArray items=new JSONArray();Set<String> unique=new HashSet<>();
        org.jsoup.select.Elements cards=doc.select("article");
        if(cards.isEmpty())cards=doc.select(".items .item, .result-item");
        if(cards.isEmpty()&&anime)cards=doc.select("a[href*=/anime/],a[href*=/media/],a[href*=/bd/],a[href*=/donghua/],a[href*=/ver/]");
        if(cards.isEmpty()&&anime&&rootDomain.equals("jkanime.net"))cards=doc.select("a[href]");
        if(cards.isEmpty()&&isProvisionalStructure())cards=doc.select(
            "a[href*=/pelicula/],a[href*=/peliculas/],a[href*=/serie/],a[href*=/anime/],a[href*=/ver/],a[href*=/pelicula-],a[href*=/serie-de-tv/]");

        for(Element card:cards){
            Element anchor=card.tagName().equals("a")?card:card.selectFirst("h3 a[href], h2 a[href], .Title a[href], .title a[href]");
            if(anchor==null)anchor=card.selectFirst("a[href]");
            if(anchor==null)continue;
            String detail=anchor.absUrl("href").split("#")[0];
            if(!own(detail)||unique.contains(detail))continue;
            String path=new URL(detail).getPath().toLowerCase(Locale.ROOT);
            boolean directEpisode=anime&&(path.contains("/ver/")||path.matches("/media/[^/]+/\\d+/?")
                ||(rootDomain.equals("jkanime.net")&&path.matches("/[^/]+/\\d+/?")));
            if(!anime&&(path.contains("/capitulo/")||path.contains("/episodio/")||path.contains("/ver/")))continue;
            if(anime&&!directEpisode&&!path.contains("/anime/")&&!path.contains("/media/")&&!path.contains("/bd/")&&!path.contains("/donghua/"))continue;
            if(isProvisionalStructure()&&!anime){
                boolean moviePath=path.contains("/pelicula/")||path.contains("/peliculas/")||
                    path.matches("/pelicula-[0-9]+/.*");
                boolean seriesPath=path.contains("/serie/")||path.contains("/serie-de-tv/");
                if(!moviePath&&!seriesPath)continue;
            }
            boolean show=path.contains("/serie/")||path.contains("/tvshows/")||path.contains("/serie-de-tv/")
                ||card.className().toLowerCase(Locale.ROOT).contains("tvshows");
            if(!anime&&query.trim().isEmpty()&&show!=kind.equals("serie"))continue;
            Element titleElement=card.selectFirst("h3,h2,.Title,.title");
            Element image=card.selectFirst("img");
            String title=titleElement==null?anchor.attr("title"):titleElement.text();
            if(title.isEmpty()&&image!=null)title=image.attr("alt");
            if(title.isEmpty())title=anchor.text();
            if(title.trim().isEmpty())continue;
            if(directEpisode){
                Matcher number=Pattern.compile("(?:-|/)(\\d+)(?:\\.html)?/?$").matcher(path);
                if(number.find()&&!normalized(title).contains("episodio")&&!normalized(title).contains("capitulo"))
                    title+=" · Episodio "+number.group(1);
            }
            String poster="";
            if(image!=null)for(String attr:new String[]{"data-src","data-original","data-lazy-src","src"}){
                String candidate=image.absUrl(attr);
                if(candidate.startsWith("https://")){poster=candidate;break;}
            }
            int year=0;
            Element yearElement=card.selectFirst(".Year,.year,[data-year]");
            String yearText=yearElement==null?(directEpisode?"":title):yearElement.hasAttr("data-year")?yearElement.attr("data-year"):yearElement.text();
            Matcher yearMatch=YEAR.matcher(yearText);if(yearMatch.find())year=Integer.parseInt(yearMatch.group(1));
            if(year>0)title=title.replaceAll("\\s*\\("+year+"\\)\\s*$","");
            unique.add(detail);
            items.put(new JSONObject().put("page_url",detail).put("title",title).put("year",year)
                .put("poster_path",poster).put("kind",anime?(directEpisode?"episode":"anime"):(show?"serie":"movie")));
            if(items.length()>=24)break;
        }
        if(items.length()==0)throw new IOException("No se reconocieron titulos disponibles en la pagina.");
        String next=null;
        for(Element a:doc.select("a[rel=next],a.next,a.next.page-numbers,a.page-numbers,.pagination a[href],.navigation a[href]")){
            String candidate=a.absUrl("href");
            if(!own(candidate)||candidate.equals(url))continue;
            if(a.attr("rel").equals("next")||a.hasClass("next")||a.text().trim().equals(String.valueOf(page+1))){next=candidate;break;}
        }
        JSONObject pagination=new JSONObject().put("page",page).put("has_next",next!=null);
        CatalogData.CatalogPage result=new CatalogData.CatalogPage(new JSONObject().put("items",items).put("pagination",pagination),page);
        synchronized(paths){
            if(paths.size()>60)paths.clear();
            if(next!=null)paths.put(kind+"|"+query.trim()+"|"+(page+1),next);
        }
        synchronized(pages){
            if(pages.size()>=6&&!pages.containsKey(key)){
                String oldest=pages.keySet().iterator().next();pages.remove(oldest);pageTimes.remove(oldest);
            }
            pages.put(key,result);pageTimes.put(key,System.currentTimeMillis());
        }
        return result;
    }
    private void extractEpisodes(Document doc,SeriesIndex index){
        for(Element anchor:doc.select("a[href]")){
            String address=anchor.absUrl("href");if(!own(address))continue;
            String path;
            try{path=new URL(address).getPath();}catch(Exception ignored){continue;}
            if(anime){
                if(rootDomain.equals("mundodonghua.com")&&path.startsWith("/ver/"+index.episodePrefix+"/")){
                    Matcher parts=Pattern.compile("^/ver/[^/]+/(\\d+)(?:/[^/]+)?/?$").matcher(path);
                    if(parts.matches()){
                        int number=Integer.parseInt(parts.group(1));
                        index.seasons.add(1);
                        List<EpisodeRef> list=index.episodes.get(1);
                        if(list==null){list=new ArrayList<>();index.episodes.put(1,list);}
                        boolean duplicate=false;for(EpisodeRef existing:list)if(existing.number==number)duplicate=true;
                        if(!duplicate)list.add(new EpisodeRef(1,number,anchor.text(),address));
                    }
                    continue;
                }
                String slug=path.replaceAll("/+$","");slug=slug.substring(slug.lastIndexOf('/')+1).replaceAll("\\.html$","");
                Matcher epNumber=Pattern.compile("(?:-episodio-|-capitulo-|-|/)(\\d+)$").matcher(path.replaceAll("/+$","").replaceAll("\\.html$",""));
                if(!epNumber.find())continue;
                int number=Integer.parseInt(epNumber.group(1));
                String stem=slug.replaceAll("(?:-episodio-|-capitulo-|-)(\\d+)$","");
                boolean media=path.contains("/media/")&&path.contains("/"+index.episodePrefix+"/");
                boolean prefixMatch = media || stem.equals(index.episodePrefix) || path.contains("/"+index.episodePrefix+"/") || path.contains("/"+index.episodePrefix+"-");
                if(!prefixMatch)continue;
                if(!path.contains("/ver/")&&!media&&!path.matches(".*/\\d+/?$"))continue;
                index.seasons.add(1);
                List<EpisodeRef> list=index.episodes.get(1);
                if(list==null){list=new ArrayList<>();index.episodes.put(1,list);}
                boolean duplicate=false;for(EpisodeRef existing:list)if(existing.number==number)duplicate=true;
                String epTitle=anchor.text().trim();
                if(epTitle.isEmpty()||epTitle.length()>45)epTitle="Episodio "+number;
                if(!duplicate)list.add(new EpisodeRef(1,number,epTitle,address));
                continue;
            }
            if(!path.contains("/capitulo/")&&!path.contains("/episodio/")&&!path.contains("/episodes/"))continue;
            Element row=anchor.closest("li");String text=path+" "+anchor.text()+" "+(row==null?"":row.text());
            Matcher match=EPISODE.matcher(text);if(!match.find())continue;
            int season=Integer.parseInt(match.group(1)!=null?match.group(1):match.group(3));
            int number=Integer.parseInt(match.group(2)!=null?match.group(2):match.group(4));
            index.seasons.add(season);
            List<EpisodeRef> list=index.episodes.get(season);
            if(list==null){list=new ArrayList<>();index.episodes.put(season,list);}
            boolean duplicate=false;for(EpisodeRef existing:list)if(existing.number==number)duplicate=true;
            if(!duplicate)list.add(new EpisodeRef(season,number,anchor.text(),address));
        }
    }
    private SeriesIndex show(CatalogData.Entry entry)throws Exception{
        synchronized(shows){SeriesIndex cached=shows.get(entry.pageUrl);if(cached!=null&&System.currentTimeMillis()-cached.checked<180000)return cached;}
        Document doc=document(entry.pageUrl);SeriesIndex index=new SeriesIndex();
        String showPath=new URL(entry.pageUrl).getPath().replaceAll("/+$","");
        index.episodePrefix=showPath.substring(showPath.lastIndexOf('/')+1).replaceAll("\\.html$","");
        for(Element option:doc.select("[data-season]")){
            try{index.seasons.add(Integer.parseInt(option.attr("data-season")));}catch(NumberFormatException ignored){}
            if(option.hasAttr("data-post"))index.postId=option.attr("data-post");
        }
        if(index.postId.isEmpty()){Element post=doc.selectFirst("[data-post]");if(post!=null)index.postId=post.attr("data-post");}
        extractEpisodes(doc,index);
        if(doc.html().contains("seasonsJson")){
            Matcher sm=Pattern.compile("\\b(?:const|var|let)\\s+seasonsJson\\s*=\\s*(\\{.*?\\});",Pattern.DOTALL).matcher(doc.html());
            if(sm.find())try{
                JSONObject sObj=new JSONObject(sm.group(1));
                Iterator<String> sKeys=sObj.keys();
                while(sKeys.hasNext()){
                    String sKey=sKeys.next();
                    int sNum=1;
                    try{sNum=Integer.parseInt(sKey);}catch(Exception ignored){}
                    index.seasons.add(sNum);
                    JSONArray epArr=sObj.optJSONArray(sKey);
                    if(epArr!=null){
                        List<EpisodeRef> list=index.episodes.get(sNum);
                        if(list==null){list=new ArrayList<>();index.episodes.put(sNum,list);}
                        for(int epIdx=0;epIdx<epArr.length();epIdx++){
                            JSONObject epObj=epArr.optJSONObject(epIdx);
                            if(epObj==null)continue;
                            int eNum=epObj.optInt("number",epObj.optInt("episode",epIdx+1));
                            String epTitle=epObj.optString("title","Episodio "+eNum);
                            String epUrl=epObj.optString("url",entry.pageUrl+"/season/"+sNum+"/episode/"+eNum);
                            list.add(new EpisodeRef(sNum,eNum,epTitle,epUrl));
                        }
                    }
                }
            }catch(Exception ignored){}
        }
        if(anime&&rootDomain.equals("tioanime.com")){
            Matcher data=Pattern.compile("\\b(?:var\\s+)?episodes\\s*=\\s*(\\[)").matcher(doc.html());
            if(data.find())try{
                Object value=new JSONTokener(doc.html().substring(data.start(1))).nextValue();
                if(value instanceof JSONArray){
                    JSONArray numbers=(JSONArray)value;List<EpisodeRef> list=index.episodes.get(1);
                    if(list==null){list=new ArrayList<>();index.episodes.put(1,list);}
                    for(int i=0;i<numbers.length()&&i<10000;i++){
                        int number=numbers.optInt(i,-1);if(number<1)continue;
                        boolean duplicate=false;for(EpisodeRef existing:list)if(existing.number==number)duplicate=true;
                        if(!duplicate)list.add(new EpisodeRef(1,number,"Episodio "+number,base+"/ver/"+index.episodePrefix+"-"+number));
                    }
                    if(!list.isEmpty())index.seasons.add(1);
                }
            }catch(Exception ignored){}
        }
        synchronized(shows){if(shows.size()>=8)shows.remove(shows.keySet().iterator().next());shows.put(entry.pageUrl,index);}
        return index;
    }
    private List<EpisodeRef> season(CatalogData.Entry entry,int season)throws Exception{
        SeriesIndex index=show(entry);
        synchronized(index){
            if(!index.loaded.contains(season)){
                if(seasonAction!=null&&!index.postId.isEmpty()){
                    Map<String,String> form=new LinkedHashMap<>();
                    form.put("action",seasonAction);form.put("post",index.postId);form.put("season",String.valueOf(season));
                    String response=HttpSupport.post(base+"/wp-admin/admin-ajax.php",entry.pageUrl,form);
                    extractEpisodes(Jsoup.parse(response,base+"/"),index);
                }
                index.loaded.add(season);
            }
            List<EpisodeRef> episodes=index.episodes.get(season);
            if(episodes==null)return new ArrayList<>();
            List<EpisodeRef> sorted=new ArrayList<>(episodes);Collections.sort(sorted,(a,b)->Integer.compare(a.number,b.number));return sorted;
        }
    }
    @Override public JSONArray seasons(CatalogData.Entry entry)throws Exception{
        JSONArray choices=new JSONArray();
        SeriesIndex index=show(entry);
        synchronized(index){for(int number:new TreeSet<>(index.seasons))choices.put(new JSONObject().put("season",number).put("name",anime?"Episodios":"Temporada "+number));}
        return choices;
    }
    @Override public JSONObject episodes(CatalogData.Entry entry,int season,int page)throws Exception{
        List<EpisodeRef> all=season(entry,season);JSONArray episodes=new JSONArray();
        int start=(page-1)*30;
        for(int i=start;i<Math.min(all.size(),start+30);i++){
            EpisodeRef ep=all.get(i);episodes.put(new JSONObject().put("season",season).put("episode",ep.number).put("title",ep.title).put("playable",true));
        }
        return new JSONObject().put("episodes",episodes).put("pagination",new JSONObject().put("has_next",all.size()>start+30));
    }
    @Override public VideoLink resolve(CatalogData.Entry entry,int season,int episode)throws Exception{
        String address=entry.pageUrl;
        if(!entry.directPlayback()){
            address=null;for(EpisodeRef ep:season(entry,season))if(ep.number==episode){address=ep.url;break;}
            if(address==null)throw new IOException("No se encontro el enlace del episodio.");
        }
        try{return MediaPageResolver.resolve(address,base+"/");}
        catch(Exception error){android.util.Log.w("VideoProviderQA","initial_resolver error="+error.getClass().getSimpleName());}
        Document doc=document(address);int tried=0;
        for(Element option:doc.select("[data-post][data-nume]")){
            if(tried++>=3)break;
            Map<String,String> form=new LinkedHashMap<>();
            form.put("action","doo_player_ajax");form.put("post",option.attr("data-post"));
            form.put("nume",option.attr("data-nume"));form.put("type",option.hasAttr("data-type")?option.attr("data-type"):"movie");
            try{
                JSONObject result=new JSONObject(HttpSupport.post(base+"/wp-admin/admin-ajax.php",address,form));
                String embed=HttpSupport.clean(result.optString("embed_url",""));
                if(embed.startsWith("//"))embed="https:"+embed;
                if(embed.startsWith("https://")&&MediaPageResolver.supported(embed))return MediaPageResolver.resolve(embed,address);
                Document wrapper=Jsoup.parse(embed,address);
                for(Element frame:wrapper.select("iframe[src]")){
                    String next=frame.absUrl("src");if(MediaPageResolver.supported(next))return MediaPageResolver.resolve(next,address);
                }
            }catch(Exception ignored){}
        }
        for(Element srv:doc.select("[data-server]")){
            String ds=srv.attr("data-server").trim();
            if(ds.isEmpty())continue;
            if(ds.startsWith("//"))ds="https:"+ds;
            if(ds.startsWith("http://")||ds.startsWith("https://")){
                try{if(MediaPageResolver.supported(ds))return MediaPageResolver.resolve(ds,address);}catch(Exception ignored){}
            } else {
                try{
                    String decoded=new String(android.util.Base64.decode(ds,android.util.Base64.DEFAULT),"UTF-8");
                    if(decoded.startsWith("//"))decoded="https:"+decoded;
                    if(decoded.startsWith("http://")||decoded.startsWith("https://")){
                        if(MediaPageResolver.supported(decoded))return MediaPageResolver.resolve(decoded,address);
                    }
                }catch(Exception ignored){}
            }
        }
        for(Element frame:doc.select("iframe[src],iframe[data-src]")){
            String fUrl=frame.hasAttr("data-src")?frame.absUrl("data-src"):frame.absUrl("src");
            if(fUrl.startsWith("//"))fUrl="https:"+fUrl;
            if(MediaPageResolver.supported(fUrl))try{return MediaPageResolver.resolve(fUrl,address);}catch(Exception ignored){}
        }
        android.util.Log.w("VideoProviderQA","all_servers_failed ajax_options_tried="+tried);
        throw new IOException("Los servidores de este título no pudieron reproducirse. Diagnóstico: "+MediaPageResolver.failureSummary());
    }
}
