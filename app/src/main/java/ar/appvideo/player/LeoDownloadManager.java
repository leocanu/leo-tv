package ar.appvideo.player;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

public final class LeoDownloadManager {

    private LeoDownloadManager() {}

    public static String sanitizeFilename(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "video_" + System.currentTimeMillis() + ".mp4";
        }
        String clean = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        clean = clean.replaceAll("\\s+", " ");
        if (!clean.toLowerCase(Locale.ROOT).endsWith(".mp4") &&
            !clean.toLowerCase(Locale.ROOT).endsWith(".mkv") &&
            !clean.toLowerCase(Locale.ROOT).endsWith(".ts")) {
            clean = clean + ".mp4";
        }
        return clean;
    }

    public static OutputStream openDownloadOutputStream(Context context, String fileName) throws Exception {
        // 1. En Android 10+ (API 29+), usar MediaStore para guardar en Downloads sin requerir permisos especiales
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                        fileName.endsWith(".ts") ? "video/mp2t" : "video/mp4");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/LeoTV");
                Uri uri = context.getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os != null) return os;
                }
            } catch (Exception ex) {
                android.util.Log.w("LeoDownload", "MediaStore insert failed, falling back to file IO", ex);
            }
        }

        // 2. Intentar carpeta pública de descargas (para Android 9 o anteriores o con legacy storage)
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            File outFile = new File(downloadsDir, fileName);
            return new FileOutputStream(outFile);
        } catch (Exception ex) {
            android.util.Log.w("LeoDownload", "Public downloads folder failed (EPERM), using app external dir", ex);
        }

        // 3. Fallback garantizado sin restricciones de Scoped Storage: getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        File appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (appDownloads == null) {
            appDownloads = context.getExternalFilesDir(null);
        }
        if (appDownloads == null) {
            appDownloads = context.getFilesDir();
        }
        if (!appDownloads.exists()) {
            appDownloads.mkdirs();
        }
        File fallbackFile = new File(appDownloads, fileName);
        return new FileOutputStream(fallbackFile);
    }

    public static void checkPermissions(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= 33) {
            if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        if (Build.VERSION.SDK_INT <= 29) {
            if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, 102);
            }
        }
    }

    public static void download(Activity activity, String title, String videoUrl, Map<String, String> headers) {
        if (activity == null || videoUrl == null || videoUrl.trim().isEmpty()) {
            if (activity != null) {
                Toast.makeText(activity, "Error: Enlace de video no válido para descargar", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        checkPermissions(activity);

        final String fileName = sanitizeFilename(title);

        if (videoUrl.contains(".m3u8")) {
            downloadHlsOrDirect(activity, title, fileName, videoUrl, headers);
            return;
        }

        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Toast.makeText(activity, "No se pudo acceder al administrador de descargas del sistema", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = Uri.parse(videoUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(title);
            request.setDescription("Leo TV · Descarga de contenido");
            request.setMimeType("video/mp4");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    if (h.getKey() != null && h.getValue() != null) {
                        try {
                            request.addRequestHeader(h.getKey(), h.getValue());
                        } catch (Exception ignored) {}
                    }
                }
            }

            dm.enqueue(request);

            Toast.makeText(activity, "⬇ Descarga iniciada: " + title + "\nGuardando en carpeta Descargas (Downloads).", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            android.util.Log.e("LeoDownload", "Error en DownloadManager, usando descarga en segundo plano", e);
            downloadBackgroundDirect(activity, title, fileName, videoUrl, headers);
        }
    }

    private static void downloadHlsOrDirect(Activity activity, String title, String fileName, String videoUrl, Map<String, String> headers) {
        Toast.makeText(activity, "⬇ Iniciando descarga de stream: " + title + "\nGuardando en la carpeta Descargas...", Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                URL url = new URL(videoUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", headers != null && headers.containsKey("User-Agent") ? headers.get("User-Agent") : HttpSupport.USER_AGENT);
                if (headers != null && headers.containsKey("Referer")) {
                    conn.setRequestProperty("Referer", headers.get("Referer"));
                }
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    String line;
                    java.util.List<String> segmentUrls = new java.util.ArrayList<>();
                    String baseUrl = videoUrl.substring(0, videoUrl.lastIndexOf('/') + 1);

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            String segUrl = line.startsWith("http") ? line : baseUrl + line;
                            segmentUrls.add(segUrl);
                        }
                    }
                    reader.close();

                    if (!segmentUrls.isEmpty()) {
                        OutputStream fos = openDownloadOutputStream(activity, fileName);
                        byte[] buf = new byte[8192];
                        for (String seg : segmentUrls) {
                            try {
                                URL sUrl = new URL(seg);
                                HttpURLConnection sConn = (HttpURLConnection) sUrl.openConnection();
                                sConn.setRequestProperty("User-Agent", headers != null && headers.containsKey("User-Agent") ? headers.get("User-Agent") : HttpSupport.USER_AGENT);
                                if (headers != null && headers.containsKey("Referer")) {
                                    sConn.setRequestProperty("Referer", headers.get("Referer"));
                                }
                                sConn.setConnectTimeout(10000);
                                sConn.setReadTimeout(15000);
                                if (sConn.getResponseCode() == 200) {
                                    InputStream is = sConn.getInputStream();
                                    int n;
                                    while ((n = is.read(buf)) != -1) {
                                        fos.write(buf, 0, n);
                                    }
                                    is.close();
                                }
                            } catch (Exception ignored) {}
                        }
                        fos.flush();
                        fos.close();

                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "✓ Descarga completada: " + title + "\nDisponible en tu carpeta Descargas.", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                }

                downloadBackgroundDirect(activity, title, fileName, videoUrl, headers);

            } catch (Exception e) {
                android.util.Log.e("LeoDownload", "Error descargando HLS", e);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "No se pudo completar la descarga del stream: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }, "hls-downloader-" + System.currentTimeMillis()).start();
    }

    private static void downloadBackgroundDirect(Activity activity, String title, String fileName, String videoUrl, Map<String, String> headers) {
        new Thread(() -> {
            try {
                URL url = new URL(videoUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", headers != null && headers.containsKey("User-Agent") ? headers.get("User-Agent") : HttpSupport.USER_AGENT);
                if (headers != null && headers.containsKey("Referer")) {
                    conn.setRequestProperty("Referer", headers.get("Referer"));
                }
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(25000);

                if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                    InputStream in = conn.getInputStream();
                    OutputStream out = openDownloadOutputStream(activity, fileName);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                    out.close();
                    in.close();

                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "✓ Descarga completada: " + title + "\nGuardado en Descargas.", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("LeoDownload", "Error en descarga directa", e);
            }
        }, "direct-downloader-" + System.currentTimeMillis()).start();
    }

    public static void openDownloadsFolder(Activity activity) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
                intent.setDataAndType(uri, "*/*");
                activity.startActivity(Intent.createChooser(intent, "Abrir carpeta de Descargas"));
            } catch (Exception ex) {
                Toast.makeText(activity, "Abre la app 'Archivos' o 'Descargas' de tu teléfono para ver tus videos.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
