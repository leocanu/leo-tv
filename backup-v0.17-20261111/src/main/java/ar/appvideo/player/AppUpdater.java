package ar.appvideo.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppUpdater {

    public static final String GITHUB_VERSION_URL =
        "https://raw.githubusercontent.com/leocanu/leo-tv/main/version.json";

    public static class UpdateInfo {
        public final int versionCode;
        public final String versionName;
        public final String apkUrl;
        public final String changelog;
        public final boolean mandatory;

        public UpdateInfo(int versionCode, String versionName, String apkUrl, String changelog, boolean mandatory) {
            this.versionCode = versionCode;
            this.versionName = versionName != null ? versionName : "";
            this.apkUrl = apkUrl != null ? apkUrl : "";
            this.changelog = changelog != null ? changelog : "";
            this.mandatory = mandatory;
        }
    }

    public interface CheckCallback {
        void onUpdateAvailable(UpdateInfo info);
        void onNoUpdate();
        void onError(String message);
    }

    public static int getCurrentVersionCode(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 15;
        }
    }

    public static String getCurrentVersionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.15.0";
        }
    }

    public static void check(final Context ctx, final CheckCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(GITHUB_VERSION_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "LeoTV-Android/" + getCurrentVersionName(ctx));

                if (conn.getResponseCode() != 200) {
                    if (callback != null) callback.onError("HTTP " + conn.getResponseCode());
                    return;
                }

                InputStream in = conn.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                in.close();

                String jsonStr = new String(out.toByteArray(), "UTF-8");
                JSONObject obj = new JSONObject(jsonStr);

                int remoteCode = obj.optInt("versionCode", 0);
                String remoteName = obj.optString("versionName", "");
                String apkUrl = obj.optString("apkUrl", "");
                String changelog = obj.optString("changelog", "Mejoras de rendimiento y estabilidad.");
                boolean mandatory = obj.optBoolean("mandatory", false);

                int currentCode = getCurrentVersionCode(ctx);

                if (remoteCode > currentCode && !apkUrl.isEmpty()) {
                    UpdateInfo info = new UpdateInfo(remoteCode, remoteName, apkUrl, changelog, mandatory);
                    if (callback != null) callback.onUpdateAvailable(info);
                } else {
                    if (callback != null) callback.onNoUpdate();
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        }, "LeoTV-UpdateCheck").start();
    }

    public static void showUpdateDialog(final Activity act, final UpdateInfo info) {
        act.runOnUiThread(() -> {
            if (act.isFinishing() || act.isDestroyed()) return;

            final android.app.Dialog dialog = new android.app.Dialog(act, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
            dialog.setCancelable(!info.mandatory);

            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setBackgroundColor(Color.argb(200, 10, 12, 18));

            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            float d = act.getResources().getDisplayMetrics().density;
            int p24 = Math.round(24 * d);
            int p16 = Math.round(16 * d);
            box.setPadding(p24, p24, p24, p24);

            GradientDrawable boxBg = new GradientDrawable();
            boxBg.setColor(Color.rgb(24, 28, 40));
            boxBg.setCornerRadius(Math.round(16 * d));
            boxBg.setStroke(Math.round(2 * d), Color.rgb(129, 107, 245));
            box.setBackground(boxBg);
            box.setLayoutParams(new LinearLayout.LayoutParams(Math.round(520 * d), -2));

            // Título
            TextView title = new TextView(act);
            title.setText("🚀  Nueva actualización: Leo TV " + info.versionName);
            title.setTextSize(20);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            box.addView(title);

            // Subtítulo
            TextView sub = new TextView(act);
            sub.setText("Hay una nueva versión disponible en GitHub (" + info.versionName + ").");
            sub.setTextSize(13);
            sub.setTextColor(Color.rgb(180, 190, 212));
            sub.setPadding(0, Math.round(4 * d), 0, Math.round(12 * d));
            box.addView(sub);

            // Novedades (Changelog)
            TextView clTitle = new TextView(act);
            clTitle.setText("Novedades:");
            clTitle.setTextSize(14);
            clTitle.setTextColor(Color.rgb(245, 197, 66));
            clTitle.setTypeface(null, Typeface.BOLD);
            box.addView(clTitle);

            TextView clText = new TextView(act);
            clText.setText(info.changelog);
            clText.setTextSize(13);
            clText.setTextColor(Color.WHITE);
            clText.setPadding(0, Math.round(4 * d), 0, Math.round(16 * d));
            box.addView(clText);

            // Barra de progreso de descarga (inicialmente oculta)
            final ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(false);
            bar.setMax(100);
            bar.setProgress(0);
            bar.setVisibility(View.GONE);
            box.addView(bar, new LinearLayout.LayoutParams(-1, Math.round(18 * d)));

            final TextView progressText = new TextView(act);
            progressText.setTextSize(12);
            progressText.setTextColor(Color.LTGRAY);
            progressText.setVisibility(View.GONE);
            progressText.setPadding(0, Math.round(4 * d), 0, Math.round(12 * d));
            box.addView(progressText);

            // Botones de acción
            final LinearLayout btnRow = new LinearLayout(act);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.END);
            box.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));

            final Button btnUpdate = new Button(act);
            btnUpdate.setText("▶  Actualizar ahora");
            btnUpdate.setTextColor(Color.WHITE);
            btnUpdate.setTextSize(14);
            btnUpdate.setTypeface(null, Typeface.BOLD);
            btnUpdate.setAllCaps(false);
            btnUpdate.setFocusable(true);
            btnUpdate.setPadding(p16, Math.round(8 * d), p16, Math.round(8 * d));
            GradientDrawable ub = new GradientDrawable();
            ub.setColor(Color.rgb(129, 107, 245));
            ub.setCornerRadius(Math.round(10 * d));
            btnUpdate.setBackground(ub);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-2, Math.round(44 * d));
            ulp.setMargins(0, 0, Math.round(10 * d), 0);
            btnRow.addView(btnUpdate, ulp);

            final Button btnLater = new Button(act);
            btnLater.setText("Más tarde");
            btnLater.setTextColor(Color.LTGRAY);
            btnLater.setTextSize(14);
            btnLater.setAllCaps(false);
            btnLater.setFocusable(true);
            btnLater.setPadding(p16, Math.round(8 * d), p16, Math.round(8 * d));
            GradientDrawable lb = new GradientDrawable();
            lb.setColor(Color.rgb(44, 50, 68));
            lb.setCornerRadius(Math.round(10 * d));
            btnLater.setBackground(lb);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, Math.round(44 * d));
            if (!info.mandatory) {
                btnRow.addView(btnLater, llp);
            }

            btnUpdate.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(150, 130, 255) : Color.rgb(129, 107, 245));
                g.setCornerRadius(Math.round(10 * d));
                if (f) g.setStroke(Math.round(2 * d), Color.WHITE);
                btnUpdate.setBackground(g);
            });

            btnLater.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(64, 72, 96) : Color.rgb(44, 50, 68));
                g.setCornerRadius(Math.round(10 * d));
                if (f) g.setStroke(Math.round(2 * d), Color.WHITE);
                btnLater.setBackground(g);
            });

            btnLater.setOnClickListener(v -> dialog.dismiss());

            btnUpdate.setOnClickListener(v -> {
                btnUpdate.setEnabled(false);
                btnLater.setEnabled(false);
                bar.setVisibility(View.VISIBLE);
                progressText.setVisibility(View.VISIBLE);
                progressText.setText("Descargando actualización desde GitHub...");

                new Thread(() -> {
                    try {
                        URL dUrl = new URL(info.apkUrl);
                        HttpURLConnection dConn = (HttpURLConnection) dUrl.openConnection();
                        dConn.setConnectTimeout(10000);
                        dConn.setReadTimeout(30000);
                        dConn.setRequestProperty("User-Agent", "LeoTV-Downloader");

                        int length = dConn.getContentLength();
                        InputStream in = dConn.getInputStream();

                        File cacheDir = act.getExternalCacheDir();
                        if (cacheDir == null) cacheDir = act.getCacheDir();
                        File apkFile = new File(cacheDir, "leotv-update.apk");
                        if (apkFile.exists()) apkFile.delete();

                        FileOutputStream fos = new FileOutputStream(apkFile);
                        byte[] buffer = new byte[8192];
                        long total = 0;
                        int count;
                        while ((count = in.read(buffer)) != -1) {
                            total += count;
                            fos.write(buffer, 0, count);
                            if (length > 0) {
                                final int pct = (int) ((total * 100) / length);
                                final long currentTotal = total;
                                act.runOnUiThread(() -> {
                                    bar.setProgress(pct);
                                    progressText.setText("Descargando: " + pct + "% (" + (currentTotal / (1024 * 1024)) + " MB / " + (length / (1024 * 1024)) + " MB)");
                                });
                            }
                        }
                        fos.flush();
                        fos.close();
                        in.close();

                        act.runOnUiThread(() -> {
                            bar.setProgress(100);
                            progressText.setText("¡Descarga completa! Abriendo instalador...");
                            dialog.dismiss();

                            // Lanzar instalador del sistema con FileProvider
                            try {
                                Uri apkUri = FileProvider.getUriForFile(
                                    act,
                                    act.getPackageName() + ".fileprovider",
                                    apkFile
                                );
                                Intent installIntent = new Intent(Intent.ACTION_VIEW);
                                installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                act.startActivity(installIntent);
                            } catch (Exception ex) {
                                Toast.makeText(act, "Error al iniciar instalador: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception ex) {
                        act.runOnUiThread(() -> {
                            progressText.setText("Error en la descarga: " + ex.getMessage());
                            btnUpdate.setEnabled(true);
                            btnLater.setEnabled(true);
                        });
                    }
                }, "LeoTV-ApkDownload").start();
            });

            root.addView(box);
            dialog.setContentView(root);
            dialog.show();
            btnUpdate.requestFocus();
        });
    }
}
