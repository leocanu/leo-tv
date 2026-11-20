package ar.appvideo.player;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.*;

final class VimeosResolver {
    private static final Pattern LINKS=Pattern.compile("https://[^\"'\\s<>\\\\]+\\.m3u8[^\"'\\s<>\\\\]*",Pattern.CASE_INSENSITIVE);
    private VimeosResolver() {}
    static boolean supports(String address) {
        try {
            URL url=new URL(address);String host=url.getHost().toLowerCase(Locale.ROOT);
            return url.getProtocol().equals("https")&&(host.equals("vimeos.net")||host.endsWith(".vimeos.net")
                ||host.equals("vimeos.zip")||host.endsWith(".vimeos.zip"));
        }catch(Exception ignored){return false;}
    }
    static VideoProvider.VideoLink resolve(String embed,String referer) throws IOException {
        if(!supports(embed))throw new IOException("Reproductor no compatible.");
        String html=HttpSupport.clean(ScriptDecoder.expand(HttpSupport.text(embed,referer)));
        Matcher matcher=LINKS.matcher(html);LinkedHashSet<String> candidates=new LinkedHashSet<>();
        while(matcher.find()&&candidates.size()<8)if(supports(matcher.group()))candidates.add(matcher.group());
        if(candidates.isEmpty())throw new IOException("El reproductor no devolvio un enlace de video.");
        for(String stream:candidates) {
            try {
                if(!HttpSupport.text(stream,"https://vimeos.net/").trim().startsWith("#EXTM3U"))continue;
                Map<String,String> headers=new HashMap<>();
                headers.put("User-Agent",HttpSupport.USER_AGENT);headers.put("Referer","https://vimeos.net/");
                return new VideoProvider.VideoLink(stream,headers);
            }catch(IOException ignored){}
        }
        throw new IOException("No se pudo verificar el video disponible.");
    }
}
