package ar.appvideo.player;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Sigue reproductores publicos conocidos, sin ejecutar sus scripts ni descargar videos completos. */
final class MediaPageResolver {
    private static final String[] HOSTS={"fastream.to","fastream.net","goodstream.one","hlswish.com","streamwish.com",
        "streamwish.to","strwish.com","voe.sx","filemoon.sx","filemoon.to","streamtape.com","streamtape.to",
        "streamtape.net","streamtape.cc","streamtape.xyz","uqload.com","uqload.net","uqload.bz","uqload.io","uqload.to",
        "mixdrop.co","mixdrop.ag","mixdrop.to","mxdrop.to","mixdrop.bz","mp4upload.com",
        "yourupload.com","ok.ru","v.tioanime.com","megacloud.tv","rabbitstream.net","streamruby.com",
        "vidplay.site","vidplay.online","playnixes.com","player.zilla-networks.com","cubeembed.rpmvid.com",
        "moflix.rpmplay.xyz","moflix-stream.xyz","moflix-stream.fans","streamtpcloud.com","streamtp3.com",
        "hgplaycdn.com","anibum.playerp2p.online","doremifasol.ezplayer.me","nupload.my","filelions.to",
        "filelions.online","streamvid.net","luluvdo.com","luluvid.com","luluvdo.net","hexload.com",
        "savefiles.com","dsvplay.com"};
    private static final Pattern PLAYER_URL=Pattern.compile("https://[^\"'\\s<>\\\\]+",Pattern.CASE_INSENSITIVE);
    private static final Pattern HLS=Pattern.compile("https://[^\"'\\s<>\\\\]+/[^\"'\\s<>\\\\]*?\\.m3u8(?:[?#][^\"'\\s<>\\\\]*)?",Pattern.CASE_INSENSITIVE);
    private static final Pattern MP4=Pattern.compile("https://[^\"'\\s<>\\\\]+/[^\"'\\s<>\\\\]*?\\.mp4(?:[?#][^\"'\\s<>\\\\]*)?",Pattern.CASE_INSENSITIVE);
    private MediaPageResolver(){}
    private static final class AttemptStats {
        int http404, timeout, connection, other;
        final Set<String> http404Urls = new HashSet<>();
        String description() {
            List<String> parts=new ArrayList<>();
            if(http404>0)parts.add(http404+" enlace(s) HTTP 404");
            if(timeout>0)parts.add(timeout+" espera(s) agotada(s)");
            if(connection>0)parts.add(connection+" conexión(es) interrumpida(s)");
            if(other>0)parts.add(other+" error(es) de servidor");
            return parts.isEmpty()?"No se encontró un video compatible.":String.join("; ",parts)+".";
        }
    }
    private static final ThreadLocal<AttemptStats> STATS=ThreadLocal.withInitial(AttemptStats::new);
    static String failureSummary(){return STATS.get().description();}
    private static void diag(String stage,String address,Exception error){
        String host="unknown";
        try{host=new URL(address).getHost().toLowerCase(Locale.ROOT);}catch(Exception ignored){}
        String kind="other";
        if(error instanceof SocketTimeoutException)kind="timeout";
        else if(error instanceof java.net.UnknownHostException)kind="dns";
        else if(error instanceof javax.net.ssl.SSLException)kind="tls";
        else if(error instanceof EOFException)kind="connection_closed";
        else if(error instanceof java.net.ConnectException)kind="connect";
        else if(error instanceof java.net.SocketException)kind="socket";
        else if(error instanceof IOException)kind="io";
        String http="";
        String msg=error.getMessage();
        if(msg!=null){
            Matcher code=Pattern.compile("(?:\\bHTTP\\s+|Server returned HTTP response code:\\s*)([1-5][0-9]{2})\\b",Pattern.CASE_INSENSITIVE).matcher(msg);
            if(code.find())http=" http="+code.group(1);
        }
        AttemptStats stats=STATS.get();
        if("404".equals(http.replace(" http=",""))){stats.http404++;stats.http404Urls.add(address);}
        else if("timeout".equals(kind))stats.timeout++;
        else if("connection_closed".equals(kind)||"connect".equals(kind)||"socket".equals(kind))stats.connection++;
        else stats.other++;
        android.util.Log.w("VideoProviderQA",stage+" host="+host+" category="+kind+" exception="+error.getClass().getSimpleName()+http);
    }
    static boolean supported(String address) {
        if(VimeosResolver.supports(address))return true;
        try{
            URL u=new URL(address);if(!u.getProtocol().equals("https"))return false;
            String host=u.getHost().toLowerCase(Locale.ROOT);
            for(String allowed:HOSTS)if(host.equals(allowed)||host.endsWith("."+allowed))return true;
        }catch(Exception ignored){}
        return false;
    }
    static VideoProvider.VideoLink resolve(String address,String referer) throws Exception {
        STATS.set(new AttemptStats());
        return walk(address,referer,new LinkedHashSet<>(),0);
    }
    private static String origin(String address) throws Exception {
        URL u=new URL(address);return u.getProtocol()+"://"+u.getAuthority()+"/";
    }
    private static boolean sameHost(String first,String second) {
        try{return new URL(first).getHost().equalsIgnoreCase(new URL(second).getHost());}catch(Exception ignored){return false;}
    }
    private static boolean publicHttps(String address) {
        try{
            URL u=new URL(address);if(!u.getProtocol().equals("https"))return false;
            for(InetAddress ip:InetAddress.getAllByName(u.getHost())){
                if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())return false;
                byte[] bytes=ip.getAddress();if(bytes.length==16&&(bytes[0]&0xfe)==0xfc)return false;
            }
            return true;
        }catch(Exception ignored){return false;}
    }
    private static VideoProvider.VideoLink link(String address,String referer) {
        Map<String,String> headers=new HashMap<>();headers.put("User-Agent",HttpSupport.USER_AGENT);
        headers.put("Referer",referer);
        try{headers.put("Origin",origin(referer).replaceAll("/$",""));}catch(Exception ignored){}
        return new VideoProvider.VideoLink(address,headers);
    }
    private static boolean mp4(String address,String referer) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
        conn.setRequestProperty("User-Agent",HttpSupport.USER_AGENT);conn.setRequestProperty("Referer",referer);
        conn.setRequestProperty("Range","bytes=0-31");conn.setConnectTimeout(8000);conn.setReadTimeout(10000);
        try{
            int code=conn.getResponseCode();if(code!=200&&code!=206)return false;
            String type=conn.getContentType();
            try(InputStream in=conn.getInputStream()){
                byte[] head=new byte[32];int count=in.read(head);
                return (type!=null&&type.startsWith("video/"))||(count>=8&&new String(head,4,4,"US-ASCII").equals("ftyp"));
            }
        }finally{conn.disconnect();}
    }
    private static VideoProvider.VideoLink walk(String address,String referer,Set<String> visited,int depth) throws Exception {
        // visited impide repetir el mismo enlace (incluidos los que devolvieron 404).
        if(depth>3||visited.size()>=9||!publicHttps(address)||!visited.add(address))
            throw new IOException("No se pudo seguir el reproductor.");
        if(VimeosResolver.supports(address))return VimeosResolver.resolve(address,referer);
        String base=origin(address);
        if(MP4.matcher(address).matches()&&mp4(address,referer))return link(address,referer);
        String decoded=HttpSupport.clean(ScriptDecoder.expand(HttpSupport.text(address,referer)))
            .replace("\\u0026","&").replace("\\u003d","=").replace("\\u002f","/");
        Document document=Jsoup.parse(decoded,address);
        LinkedHashSet<String> players=new LinkedHashSet<>();
        for(Element frame:document.select("iframe[src],iframe[data-src]")){
            String next=frame.hasAttr("data-src")?frame.absUrl("data-src"):frame.absUrl("src");
            if(supported(next)||sameHost(next,address))players.add(next);
        }
        for(Element option:document.select("[data-player]")){
            try{
                String next=new String(android.util.Base64.decode(option.attr("data-player"),android.util.Base64.DEFAULT),"UTF-8");
                if(supported(next))players.add(next);
            }catch(Exception ignored){}
        }
        android.util.Log.i("VideoProviderQA","player_candidates="+players.size()+" data_player_elements="+document.select("[data-player]").size());
        Matcher urls=PLAYER_URL.matcher(decoded);
        while(urls.find()&&players.size()<20){String next=urls.group();if(supported(next)&&!next.equals(address))players.add(next);}
        // Primero seguir servidores declarados: un MP4 publicitario en la pagina no es el episodio.
        for(String next:players){
            try{return walk(next,address,visited,depth+1);}catch(Exception error){diag("player_candidate",next,error);}
        }
        if(!supported(address))throw new IOException("El titulo no ofrece un reproductor compatible.");

        // Extractor directo para servidores de video conocidos
        if(address.contains("mp4upload.com")){
            Matcher m=Pattern.compile("(?:player\\.src\\s*\\(\\s*\\{[^}]*src:\\s*[\"']|src:\\s*[\"']|file:\\s*[\"'])(https://[^\"']+\\.mp4[^\"']*)[\"']",Pattern.CASE_INSENSITIVE).matcher(decoded);
            while(m.find()){
                String cand=m.group(1);
                if(publicHttps(cand)&&mp4(cand,address))return link(cand,address);
            }
        }
        if(address.contains("streamtape.")){
            Matcher m=Pattern.compile("(?:robotlink|videolink)['\"]\\)\\.innerHTML\\s*=\\s*['\"]([^'\"]+)['\"]",Pattern.CASE_INSENSITIVE).matcher(decoded);
            if(m.find()){
                String stream=m.group(1);
                if(stream.startsWith("//"))stream="https:"+stream;
                if(publicHttps(stream))return link(stream,address);
            }
        }
        if(address.contains("voe.sx")){
            Matcher m=Pattern.compile("['\"]hls['\"]\\s*:\\s*['\"](https://[^'\"]+\\.m3u8[^'\"]*)['\"]",Pattern.CASE_INSENSITIVE).matcher(decoded);
            if(m.find()&&publicHttps(m.group(1)))return link(m.group(1),address);
        }
        if(address.contains("uqload.")){
            Matcher m=Pattern.compile("sources:\\s*\\[[\"'](https://[^'\"]+\\.mp4[^'\"]*)[\"']\\]",Pattern.CASE_INSENSITIVE).matcher(decoded);
            if(m.find()&&publicHttps(m.group(1))&&mp4(m.group(1),address))return link(m.group(1),address);
        }

        Matcher hls=HLS.matcher(decoded);int candidates=0;
        while(hls.find()&&candidates++<8){
            String stream=hls.group();
            if(!publicHttps(stream)||stream.endsWith(".js")||stream.endsWith(".css"))continue;
            try{if(HttpSupport.text(stream,base).trim().startsWith("#EXTM3U"))return link(stream,base);}
            catch(IOException error){diag("hls_candidate",stream,error);}
        }
        Matcher files=MP4.matcher(decoded);candidates=0;
        while(files.find()&&candidates++<10){
            String stream=files.group();
            if(!publicHttps(stream)||stream.endsWith(".js")||stream.endsWith(".css")||stream.endsWith(".jpg")||stream.endsWith(".png"))continue;
            try{if(mp4(stream,base))return link(stream,base);}catch(Exception error){diag("mp4_candidate",stream,error);}
        }
        throw new IOException("No se encontro un video compatible en los servidores disponibles.");
    }
}
