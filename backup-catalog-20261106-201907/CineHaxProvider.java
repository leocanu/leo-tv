package ar.appvideo.player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conector propio para la prueba de Matrix. No incorpora codigo de Nuvio. */
public final class CineHaxProvider {
    public static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private CineHaxProvider() {}
    private static String fetch(String address, String referer) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol())) throw new IOException("El servidor no ofrece HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        if (referer != null) connection.setRequestProperty("Referer", referer);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("El servidor respondio HTTP " + code + ".");
            try (InputStream in = connection.getInputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > 524288) throw new IOException("Respuesta demasiado grande.");
                    out.write(buffer, 0, count);
                }
                return out.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }
    private static String clean(String text) {
        return text.replace("&amp;", "&").replace("&#38;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("\\/", "/");
    }
    private static boolean videoHost(String address) {
        try {
            URL url = new URL(address);
            String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equals(url.getProtocol())
                && (host.equals("vimeos.net") || host.endsWith(".vimeos.net")
                    || host.equals("vimeos.zip") || host.endsWith(".vimeos.zip"));
        } catch (Exception ignored) { return false; }
    }
    public static String resolveMatrix() throws IOException {
        String page = fetch("https://videoapp.mov/e/movie/603", "https://cinehax.org/");
        Matcher frame = Pattern.compile("<iframe\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)",
            Pattern.CASE_INSENSITIVE).matcher(page);
        String embed = null;
        while (frame.find()) {
            String candidate = clean(frame.group(1));
            if (videoHost(candidate)) { embed = candidate; break; }
        }
        if (embed == null) throw new IOException("Matrix no tiene un reproductor disponible.");
        String html = clean(fetch(embed, "https://videoapp.mov/"));
        Matcher links = Pattern.compile("https://[^\"'\\s<>\\\\]+\\.m3u8[^\"'\\s<>\\\\]*",
            Pattern.CASE_INSENSITIVE).matcher(html);
        String stream = null;
        while (links.find()) {
            String candidate = links.group();
            if (!videoHost(candidate)) continue;
            if (stream == null) stream = candidate;
            if (candidate.contains("master.m3u8")) { stream = candidate; break; }
        }
        if (stream == null) throw new IOException("No se encontro un enlace de video.");
        if (!fetch(stream, null).trim().startsWith("#EXTM3U"))
            throw new IOException("El servidor no devolvio una lista de video valida.");
        return stream;
    }
}
