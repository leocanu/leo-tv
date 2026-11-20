package ar.appvideo.player;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Read-only connector: Internet Archive public metadata API, CC BY whitelisted works. */
public final class InternetArchiveProvider {
    private InternetArchiveProvider() {}
    public static final class Movie {
        public final String id, title, url, license, thumbnail;
        public final int year;
        public Movie(String id, String title, String url, String license, String thumbnail, int year) {
            this.id = id; this.title = title; this.url = url; this.license = license;
            this.thumbnail = thumbnail; this.year = year;
        }
    }
    private static String apiJson(String id) throws Exception {
        URL endpoint = new URL("https://archive.org/metadata/" + id);
        HttpURLConnection c = (HttpURLConnection) endpoint.openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "VideoPropia/0.3 metadata-client");
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int n;
                while ((n = in.read(bytes)) >= 0) {
                    buf.write(bytes,0,n);
                    if (buf.size() > 2000000) throw new Exception("Respuesta de metadatos demasiado grande");
                }
                return buf.toString("UTF-8");
            }
        } finally { c.disconnect(); }
    }
    public static List<Movie> fetchCurated() throws Exception {
        // The identifiers are fixed and vetted, but the metadata, media URLs and titles are live.
        String[] ids = {"BigBuckBunny_124", "Sintel"};
        int[] years = {2008, 2010};
        List<Movie> result = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            JSONObject item = new JSONObject(apiJson(id));
            JSONObject meta = item.optJSONObject("metadata");
            if (meta == null) throw new Exception("Sin metadatos: " + id);
            String license = meta.optString("licenseurl","");
            if (!license.matches("(?i)^https?://creativecommons\\.org/licenses/by/3\\.0/?$"))
                throw new Exception("Licencia no verificada: " + id);
            JSONArray files = item.optJSONArray("files");
            if (files == null) throw new Exception("Sin archivos: " + id);
            JSONObject best = null; long bestSize = Long.MAX_VALUE;
            for (int j=0;j<files.length();j++) {
                JSONObject f=files.optJSONObject(j);
                if(f==null) continue;
                String name=f.optString("name","");
                if(!name.toLowerCase().endsWith(".mp4") || name.contains("..") || name.startsWith("/")) continue;
                long size;
                try { size=Long.parseLong(f.optString("size","0")); }
                catch(NumberFormatException e) {continue;}
                if(size<2000000 || size>180000000)continue;
                if(size<bestSize){best=f;bestSize=size;}
            }
            if(best==null)throw new Exception("MP4 adecuado no encontrado: "+id);
            String file=best.optString("name");
            String[] parts=file.split("/");
            StringBuilder path=new StringBuilder("https://archive.org/download/").append(id);
            for(String part:parts) path.append("/").append(URLEncoder.encode(part,"UTF-8").replace("+","%20"));
            result.add(new Movie(id,meta.optString("title",id),path.toString(),license,
                "https://archive.org/services/img/"+id,years[i]));
        }
        return result;
    }
}
