package ar.appvideo.player;

import java.net.*;
import java.io.*;

final class HttpSupport {
    static final String USER_AGENT="Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private HttpSupport() {}
    static String text(String address,String referer) throws IOException {
        URL url=new URL(address);
        if(!"https".equals(url.getProtocol()))throw new IOException("El servidor no ofrece HTTPS.");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(10000);conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent",USER_AGENT);
        if(referer!=null)conn.setRequestProperty("Referer",referer);
        return receive(conn);
    }
    static String post(String address,String referer,java.util.Map<String,String> values) throws IOException {
        URL url=new URL(address);
        if(!url.getProtocol().equals("https"))throw new IOException("El servidor no ofrece HTTPS.");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");conn.setDoOutput(true);
        conn.setConnectTimeout(10000);conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent",USER_AGENT);
        conn.setRequestProperty("Referer",referer);
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        StringBuilder form=new StringBuilder();
        for(java.util.Map.Entry<String,String> field:values.entrySet()) {
            if(form.length()>0)form.append('&');
            form.append(URLEncoder.encode(field.getKey(),"UTF-8")).append('=')
                .append(URLEncoder.encode(field.getValue(),"UTF-8"));
        }
        try(OutputStream out=conn.getOutputStream()){out.write(form.toString().getBytes("UTF-8"));}
        return receive(conn);
    }
    private static String receive(HttpURLConnection conn) throws IOException {
        try {
            int status=conn.getResponseCode();
            if(status!=200)throw new IOException("El servidor respondio HTTP "+status+".");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new IOException("Consulta cancelada.");
                    if(out.size()+count>1048576)throw new IOException("Respuesta demasiado grande.");
                    out.write(buffer,0,count);
                }
                return out.toString("UTF-8");
            }
        } finally {conn.disconnect();}
    }
    static String clean(String value) {
        return value.replace("&amp;","&").replace("&#38;","&")
            .replace("&quot;","\"").replace("&#39;","'").replace("\\/","/");
    }
}
