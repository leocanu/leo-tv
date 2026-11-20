package ar.appvideo.player;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Sigue reproductores publicos conocidos, sin ejecutar sus scripts ni descargar videos completos. */
final class MediaPageResolver {
    private static final String[] HOSTS={"fastream.to","goodstream.one","hlswish.com","streamwish.com",
        "streamwish.to","strwish.com","voe.sx","filemoon.sx","filemoon.to","streamtape.com","streamtape.to",
        "uqload.com","uqload.net","mixdrop.co","mixdrop.ag"};
    private static final Pattern HLS=Pattern.compile("https://[^\"'\\s<>\\\\]+\\.m3u8[^\"'\\s<>\\\\]*",Pattern.CASE_INSENSITIVE);
    private static final Pattern MP4=Pattern.compile("https://[^\"'\\s<>\\\\]+\\.mp4[^\"'\\s<>\\\\]*",Pattern.CASE_INSENSITIVE);
    private MediaPageResolver(){}
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
        if(depth>3||visited.size()>=9||!publicHttps(address)||!visited.add(address))
            throw new IOException("No se pudo seguir el reproductor.");
        if(VimeosResolver.supports(address))return VimeosResolver.resolve(address,referer);
        String base=origin(address);
        String decoded=HttpSupport.clean(ScriptDecoder.expand(HttpSupport.text(address,referer)))
            .replace("\\u0026","&").replace("\\u003d","=").replace("\\u002f","/");
        Matcher hls=HLS.matcher(decoded);int candidates=0;
        while(hls.find()&&candidates++<5){
            String stream=hls.group();
            if(!publicHttps(stream))continue;
            try{if(HttpSupport.text(stream,base).trim().startsWith("#EXTM3U"))return link(stream,base);}
            catch(IOException ignored){}
        }
        Matcher files=MP4.matcher(decoded);candidates=0;
        while(files.find()&&candidates++<3){
            String stream=files.group();if(!publicHttps(stream))continue;
            try{if(mp4(stream,base))return link(stream,base);}catch(Exception ignored){}
        }
        Document document=Jsoup.parse(decoded,address);
        for(Element frame:document.select("iframe[src],iframe[data-src]")){
            String next=frame.hasAttr("data-src")?frame.absUrl("data-src"):frame.absUrl("src");
            if(!supported(next)&&!sameHost(next,address))continue;
            try{return walk(next,address,visited,depth+1);}catch(Exception ignored){}
        }
        throw new IOException("No se encontro un video compatible en los servidores disponibles.");
    }
}
