package ar.appvideo.player;

import java.net.*;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;
import org.json.JSONArray;
import org.json.JSONObject;

final class HttpSupport {
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final ConcurrentHashMap<String, String> DOH_CACHE = new ConcurrentHashMap<>();

    private HttpSupport() {}

    /**
     * Resuelve nombres de dominio bloqueados por ISPs locales mediante DNS over HTTPS (Cloudflare 1.1.1.1 / Google).
     */
    static String resolveDnsOverHttps(String hostname) {
        if (hostname == null || hostname.isEmpty() || hostname.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return hostname;
        }
        String cached = DOH_CACHE.get(hostname);
        if (cached != null) return cached;

        try {
            // 1. Probar Cloudflare 1.1.1.1 DoH
            String cfUrl = "https://1.1.1.1/dns-query?name=" + URLEncoder.encode(hostname, "UTF-8") + "&type=A";
            HttpURLConnection conn = (HttpURLConnection) new URL(cfUrl).openConnection();
            conn.setRequestProperty("Accept", "application/dns-json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() == 200) {
                String body = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(body);
                JSONArray answers = json.optJSONArray("Answer");
                if (answers != null && answers.length() > 0) {
                    for (int i = 0; i < answers.length(); i++) {
                        JSONObject ans = answers.getJSONObject(i);
                        if (ans.optInt("type") == 1) { // Type A (IPv4)
                            String ip = ans.optString("data");
                            if (ip != null && !ip.isEmpty()) {
                                DOH_CACHE.put(hostname, ip);
                                return ip;
                            }
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Throwable ignored) {}

        try {
            // 2. Fallback a Google DNS DoH
            String gUrl = "https://dns.google/resolve?name=" + URLEncoder.encode(hostname, "UTF-8") + "&type=A";
            HttpURLConnection conn = (HttpURLConnection) new URL(gUrl).openConnection();
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() == 200) {
                String body = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(body);
                JSONArray answers = json.optJSONArray("Answer");
                if (answers != null && answers.length() > 0) {
                    for (int i = 0; i < answers.length(); i++) {
                        JSONObject ans = answers.getJSONObject(i);
                        if (ans.optInt("type") == 1) {
                            String ip = ans.optString("data");
                            if (ip != null && !ip.isEmpty()) {
                                DOH_CACHE.put(hostname, ip);
                                return ip;
                            }
                        }
                    }
                }
            }
            conn.disconnect();
        } catch (Throwable ignored) {}

        return hostname;
    }

    static String text(String address, String referer) throws IOException {
        String current = address;
        int redirects = 0;
        while (redirects < 4) {
            URL url = new URL(current);
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) url.openConnection();
            } catch (UnknownHostException uhe) {
                String ip = resolveDnsOverHttps(url.getHost());
                if (!ip.equals(url.getHost())) {
                    String ipUrl = url.getProtocol() + "://" + ip + (url.getPort() > 0 ? ":" + url.getPort() : "") + url.getFile();
                    conn = (HttpURLConnection) new URL(ipUrl).openConnection();
                    conn.setRequestProperty("Host", url.getHost());
                } else {
                    throw uhe;
                }
            }

            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json,*/*;q=0.8");
            if (referer != null && !referer.isEmpty()) {
                conn.setRequestProperty("Referer", referer);
            }
            conn.setInstanceFollowRedirects(true);

            int status;
            try {
                status = conn.getResponseCode();
            } catch (UnknownHostException uhe) {
                conn.disconnect();
                String ip = resolveDnsOverHttps(url.getHost());
                if (!ip.equals(url.getHost())) {
                    String ipUrl = url.getProtocol() + "://" + ip + (url.getPort() > 0 ? ":" + url.getPort() : "") + url.getFile();
                    conn = (HttpURLConnection) new URL(ipUrl).openConnection();
                    conn.setRequestProperty("Host", url.getHost());
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    if (referer != null) conn.setRequestProperty("Referer", referer);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    status = conn.getResponseCode();
                } else {
                    throw uhe;
                }
            }

            if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == 307 || status == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc != null && !loc.isEmpty()) {
                    current = new URL(url, loc).toString();
                    redirects++;
                    continue;
                }
            }
            return receive(conn);
        }
        throw new IOException("Demasiadas redirecciones HTTP.");
    }

    static String post(String address, String referer, java.util.Map<String, String> values) throws IOException {
        URL url = new URL(address);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        if (referer != null) conn.setRequestProperty("Referer", referer);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        StringBuilder form = new StringBuilder();
        for (java.util.Map.Entry<String, String> field : values.entrySet()) {
            if (form.length() > 0) form.append('&');
            form.append(URLEncoder.encode(field.getKey(), "UTF-8")).append('=')
                .append(URLEncoder.encode(field.getValue(), "UTF-8"));
        }
        try (OutputStream out = conn.getOutputStream()) {
            out.write(form.toString().getBytes("UTF-8"));
        }
        return receive(conn);
    }

    private static String receive(HttpURLConnection conn) throws IOException {
        try {
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("El servidor respondio HTTP " + status + ".");
            return readStream(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static String readStream(InputStream in) throws IOException {
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = is.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Consulta cancelada.");
                if (out.size() + count > 3 * 1024 * 1024) throw new IOException("Respuesta demasiado grande.");
                out.write(buffer, 0, count);
            }
            return out.toString("UTF-8");
        }
    }

    static String clean(String value) {
        if (value == null) return "";
        return value.replace("&amp;", "&").replace("&#38;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("\\/", "/");
    }
}
