package ar.appvideo.player;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.Toast;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.MediaController;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.URL;
import java.net.HttpURLConnection;
import android.net.Uri;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.common.PlaybackParameters;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final int dark = Color.rgb(12, 16, 29);
    private final int panel = Color.rgb(27, 33, 52);
    private final int violet = Color.rgb(129, 107, 245);
    private LinearLayout root;
    private int returnScrollY = 0;
    private String returnCarouselId = "";
    private String searchFilterKind = "all";
    private String searchSortBy = "rating";

    private ScrollView pageScroll;
    private TextView status;
    private TextView heroEyebrow, heroTitle, heroBadge, heroSynopsis;
    private VideoView video;
    private ExoPlayer externalPlayer;
    private LinearLayout playbackBody;
    private PlayerView playbackView;
    private Button fullscreenButton;
    private boolean fullscreen;
    private android.media.session.MediaSession mediaSession;

    // Campos del reproductor avanzado Leo TV (Audio, Subtítulos, OSD y Continuar
    // viendo)
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String currentPlayingTitle = "";
    private String currentPlayingOrigin = "";
    private String currentPlayingPoster = "";
    private String currentPlayingKind = "movie";
    private String currentPlayingUrl = "";
    private java.util.Map<String, String> currentPlayingHeaders = null;
    private CatalogData.Entry currentPlayingEntry = null;
    private int currentPlayingSeason = 0;
    private int currentPlayingEpisode = 0;
    private TextView playerHudView = null;
    private LinearLayout playerOsdBar = null;
    private Button nextEpBannerBtn = null;
    private android.os.CountDownTimer autoNextCountDownTimer = null;
    private android.app.AlertDialog autoNextDialog = null;
    private android.app.Dialog sourceLoaderDialog = null;
    private android.widget.FrameLayout splashOverlay = null;
    private android.widget.VideoView splashVideoView = null;
    private final Runnable dismissSplashRunnable = () -> dismissSplash();
    private int currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;

    // Watchdog y Auto-fallback ante servidores inestables (mecanismo inspirado en tvl1)
    private final java.util.Set<String> failedProvidersForCurrentTitle = new java.util.HashSet<>();
    private boolean isSwitchingUnstableServer = false;
    private Runnable bufferingWatchdogRunnable = null;
    private Runnable liveTvBufferingWatchdogRunnable = null;

    private final Runnable hideHudRunnable = () -> {
        if (playerHudView != null)
            playerHudView.setVisibility(android.view.View.GONE);
    };

    private final Runnable hidePlayerOsdRunnable = () -> hidePlayerOsd();

    private void showPlayerOsd() {
        if (playerOsdBar == null)
            return;
        runOnUiThread(() -> {
            playerOsdBar.setVisibility(android.view.View.VISIBLE);
            playerOsdBar.animate().alpha(1.0f).setDuration(220).start();
            mainHandler.removeCallbacks(hidePlayerOsdRunnable);
            mainHandler.postDelayed(hidePlayerOsdRunnable, 3500);
        });
    }

    private void hidePlayerOsd() {
        if (playerOsdBar == null)
            return;
        runOnUiThread(() -> {
            playerOsdBar.animate().alpha(0.0f).setDuration(260).withEndAction(() -> {
                if (playerOsdBar != null) {
                    playerOsdBar.setVisibility(android.view.View.GONE);
                }
                if (playbackView != null) {
                    playbackView.requestFocus();
                }
            }).start();
        });
    }

    private void scheduleOsdHide(long delayMs) {
        if (playerOsdBar == null)
            return;
        mainHandler.removeCallbacks(hidePlayerOsdRunnable);
        mainHandler.postDelayed(hidePlayerOsdRunnable, delayMs);
    }

    private final Runnable periodicSaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (externalPlayer != null && "playing".equals(page) && externalPlayer.isPlaying()) {
                final long pos = externalPlayer.getCurrentPosition();
                final long dur = externalPlayer.getDuration();
                if (pos > 0 && dur > 0 && currentPlayingTitle != null && !currentPlayingTitle.isEmpty()
                        && !"live_tv".equals(playbackReturnPage)) {
                    final String sTitle = currentPlayingTitle;
                    final String sPoster = currentPlayingPoster;
                    final String sOrigin = currentPlayingOrigin;
                    final String sKind = currentPlayingKind;
                    new Thread(() -> {
                        PlaybackEngine.saveProgress(MainActivity.this, sTitle, sTitle,
                                sPoster, pos, dur, sOrigin, sKind);
                    }).start();

                    // Detectar si está por terminar el capítulo de una serie (faltan menos de 80s o > 92%)
                    if (currentPlayingEntry != null && !currentPlayingEntry.isMovie() && currentPlayingEpisode > 0) {
                        final long remainingMs = dur - pos;
                        final double progressRatio = (double) pos / (double) dur;
                        if ((remainingMs <= 80000 || progressRatio >= 0.92) && nextEpBannerBtn != null) {
                            runOnUiThread(() -> {
                                if (nextEpBannerBtn != null && nextEpBannerBtn.getVisibility() != android.view.View.VISIBLE) {
                                    final int nextEp = currentPlayingEpisode + 1;
                                    nextEpBannerBtn.setText("⏭ Siguiente Capítulo (Cap. " + nextEp + ")");
                                    nextEpBannerBtn.setVisibility(android.view.View.VISIBLE);
                                    nextEpBannerBtn.animate().alpha(1.0f).setDuration(250).start();
                                }
                            });
                        }
                    }
                }
            }
            if ("playing".equals(page)) {
                mainHandler.postDelayed(this, 5000);
            }
        }
    };

    private void showPlayerHud(String msg) {
        if (playerHudView == null)
            return;
        runOnUiThread(() -> {
            playerHudView.setText(msg);
            playerHudView.setVisibility(android.view.View.VISIBLE);
            playerHudView.removeCallbacks(hideHudRunnable);
            playerHudView.postDelayed(hideHudRunnable, 2500);
        });
    }

    private void showTracksDialog() {
        if (externalPlayer == null)
            return;
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        String dialogTitle = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                ? "⚙️ Ajustes · " + currentPlayingTitle
                : "⚙️ Ajustes del Reproductor";
        b.setTitle(dialogTitle);

        String[] options = new String[] {
                "🎬  Calidad y Resolución de Video",
                "🔊  Pistas de Audio (Idiomas)",
                "💬  Subtítulos",
                "🌐  Cambiar Fuente / Servidor",
                "📺  Reproducir en Reproductor Externo (VLC / JustPlayer)",
                "⛶  Relación de Aspecto (Ajustar / Zoom / Estirar)",
                "ℹ️  Información del Video"
        };

        b.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showVideoQualityPicker();
            } else if (which == 1) {
                showAudioTracksPicker();
            } else if (which == 2) {
                showSubtitleTracksPicker();
            } else if (which == 3) {
                showSourceSelectionDialog();
            } else if (which == 4) {
                launchExternalPlayer(currentPlayingTitle, currentPlayingUrl, currentPlayingHeaders);
            } else if (which == 5) {
                cycleAspectRatio();
            } else if (which == 6) {
                showVideoInfoDialog();
            }
        });
        b.setNegativeButton("Volver", null);
        b.show();
    }

    private void showVideoQualityPicker() {
        if (externalPlayer == null)
            return;
        final java.util.List<PlaybackEngine.TrackInfo> videoTracks = PlaybackEngine.getVideoTracks(externalPlayer);
        if (videoTracks.isEmpty()) {
            Toast.makeText(this, "Este video cuenta con una sola calidad fija directa.", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.List<String> labels = new java.util.ArrayList<>();
        boolean anySelected = false;
        int selected = 0; // 0 = Auto

        labels.add("⚡ Automático (Adaptativo según conexión)");
        for (int i = 0; i < videoTracks.size(); i++) {
            PlaybackEngine.TrackInfo t = videoTracks.get(i);
            labels.add("🎬 " + t.label + (t.isSelected ? "  ✓" : ""));
            if (t.isSelected) {
                selected = i + 1;
                anySelected = true;
            }
        }
        if (!anySelected) {
            labels.set(0, "⚡ Automático (Adaptativo según conexión)  ✓");
        }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        String dialogTitle = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                ? "🎬 Calidad · " + currentPlayingTitle
                : "🎬 Calidad y Resolución de Video";
        b.setTitle(dialogTitle);
        b.setSingleChoiceItems(labels.toArray(new String[0]), selected, (dialog, which) -> {
            if (which == 0) {
                PlaybackEngine.selectVideoTrack(externalPlayer, null);
                showPlayerHud("🎬 Calidad: Automática (Adaptativa)");
            } else {
                PlaybackEngine.TrackInfo chosen = videoTracks.get(which - 1);
                PlaybackEngine.selectVideoTrack(externalPlayer, chosen);
                showPlayerHud("🎬 Calidad fijada: " + chosen.label);
            }
            dialog.dismiss();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showAudioTracksPicker() {
        if (externalPlayer == null)
            return;
        final java.util.List<PlaybackEngine.TrackInfo> audioTracks = PlaybackEngine.getAudioTracks(externalPlayer);
        if (audioTracks.isEmpty()) {
            Toast.makeText(this, "No hay pistas de audio secundarias en este video.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[audioTracks.size()];
        int selected = 0;
        for (int i = 0; i < audioTracks.size(); i++) {
            PlaybackEngine.TrackInfo t = audioTracks.get(i);
            items[i] = t.label + (t.isSelected ? "  ✓" : "");
            if (t.isSelected)
                selected = i;
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        String dialogTitle = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                ? "🔊 Audio · " + currentPlayingTitle
                : "🔊 Seleccionar Pista de Audio";
        b.setTitle(dialogTitle);
        b.setSingleChoiceItems(items, selected, (dialog, which) -> {
            PlaybackEngine.selectAudioTrack(externalPlayer, audioTracks.get(which));
            showPlayerHud("🔊 Audio: " + audioTracks.get(which).label);
            dialog.dismiss();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showSubtitleTracksPicker() {
        if (externalPlayer == null)
            return;
        final java.util.List<PlaybackEngine.TrackInfo> subTracks = PlaybackEngine.getSubtitleTracks(externalPlayer);
        java.util.List<String> labels = new java.util.ArrayList<>();
        labels.add("Desactivar subtítulos");
        int selected = 0;
        for (int i = 0; i < subTracks.size(); i++) {
            PlaybackEngine.TrackInfo t = subTracks.get(i);
            labels.add(t.label + (t.isSelected ? "  ✓" : ""));
            if (t.isSelected)
                selected = i + 1;
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        String dialogTitle = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                ? "💬 Subtítulos · " + currentPlayingTitle
                : "💬 Seleccionar Subtítulos";
        b.setTitle(dialogTitle);
        b.setSingleChoiceItems(labels.toArray(new String[0]), selected, (dialog, which) -> {
            if (which == 0) {
                PlaybackEngine.disableSubtitles(externalPlayer);
                showPlayerHud("💬 Subtítulos desactivados");
            } else {
                PlaybackEngine.TrackInfo chosen = subTracks.get(which - 1);
                PlaybackEngine.selectSubtitleTrack(externalPlayer, chosen);
                showPlayerHud("💬 Subtítulos: " + chosen.label);
            }
            dialog.dismiss();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void cycleAspectRatio() {
        if (playbackView == null)
            return;
        if (currentResizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            playbackView.setResizeMode(currentResizeMode);
            showPlayerHud("⛶ Aspecto: Pantalla Completa (Zoom)");
        } else if (currentResizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;
            playbackView.setResizeMode(currentResizeMode);
            showPlayerHud("⛶ Aspecto: Estirar (Fill)");
        } else {
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
            playbackView.setResizeMode(currentResizeMode);
            showPlayerHud("⛶ Aspecto: Ajustar (Original 16:9 / 21:9)");
        }
    }

    private void showVideoInfoDialog() {
        if (externalPlayer == null)
            return;
        long pos = externalPlayer.getCurrentPosition();
        long dur = externalPlayer.getDuration();
        String currentRes = "Detectando calidad...";
        try {
            java.util.List<PlaybackEngine.TrackInfo> vTracks = PlaybackEngine.getVideoTracks(externalPlayer);
            if (vTracks != null) {
                for (PlaybackEngine.TrackInfo ti : vTracks) {
                    if (ti.isSelected) {
                        currentRes = ti.label;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("ℹ️ Información del Video");
        b.setMessage("Título: " + currentPlayingTitle + "\n"
                + "Fuente: " + currentPlayingOrigin + "\n"
                + "Calidad real: " + currentRes + "\n"
                + "Posición: " + PlaybackEngine.formatTime(pos) + " / " + PlaybackEngine.formatTime(dur) + "\n"
                + "Estado: " + (externalPlayer.isPlaying() ? "Reproduciendo" : "Pausado") + "\n\n"
                + "Control Remoto Android TV:\n"
                + "• Flechas Izquierda / Derecha: -10s / +10s\n"
                + "• Botón Central (OK): Pausar / Reanudar\n"
                + "• Flecha Arriba o Menú: Pistas de Audio y Subtítulos\n"
                + "• Flecha Abajo: Cambiar relación de aspecto\n"
                + "• Botón Atrás: Guardar progreso y volver");
        b.setPositiveButton("Aceptar", null);
        b.show();
    }

    private void launchExternalPlayer(String title, String url, java.util.Map<String, String> headers) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "No hay URL de video disponible para reproductor externo", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (externalPlayer != null && externalPlayer.isPlaying()) {
                externalPlayer.pause();
            }
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(android.net.Uri.parse(url), "video/*");
            intent.putExtra("title", title);
            intent.putExtra("android.media.intent.extra.TITLE", title);
            if (headers != null && !headers.isEmpty()) {
                java.util.ArrayList<String> headerList = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    headerList.add(entry.getKey() + ": " + entry.getValue());
                }
                intent.putExtra("headers", headerList.toArray(new String[0]));
            }
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(android.content.Intent.createChooser(intent, "Reproducir con reproductor externo..."));
        } catch (Exception e) {
            Toast.makeText(this, "No se encontró ningún reproductor de video externo (ej. VLC o JustPlayer)", Toast.LENGTH_LONG).show();
        }
    }

    private void showSourceSelectionDialog() {
        if (currentPlayingEntry == null) {
            Toast.makeText(this, "No se puede cambiar fuente para esta transmisión directa", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] providers = currentPlayingEntry.isMovie()
                ? new String[] { "CineHax", "SoloLatino", "Cuevana", "CineCalidad", "Pelispedia" }
                : new String[] { "CineHax", "SoloLatino", "Cuevana", "Seriesflix", "Pelispedia", "LatAnime", "TioAnime" };

        String[] displayNames = new String[providers.length];
        int selected = -1;
        for (int i = 0; i < providers.length; i++) {
            String p = providers[i];
            String label = p;
            if ("CineHax".equals(p)) label = "🌟 CineHax (Vimeos Full HD)";
            else if ("SoloLatino".equals(p)) label = "🌐 SoloLatino (Multi-calidad)";
            else if ("Cuevana".equals(p)) label = "🌐 Cuevana (Servidores rápidos)";
            else if ("CineCalidad".equals(p)) label = "🌐 CineCalidad (Audio Latino HD)";
            else if ("Pelispedia".equals(p)) label = "🌐 Pelispedia";
            else if ("Seriesflix".equals(p)) label = "🌐 Seriesflix";
            else if ("LatAnime".equals(p) || "TioAnime".equals(p)) label = "⛩️ " + p + " (Anime HD)";

            if (p.equalsIgnoreCase(currentPlayingOrigin) || (currentPlayingOrigin != null && currentPlayingOrigin.contains(p))) {
                label += " (Actual) ✓";
                selected = i;
            }
            displayNames[i] = label;
        }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        String dialogTitle = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                ? "🌐 Servidor · " + currentPlayingTitle
                : "🌐 Cambiar Fuente / Servidor de Video";
        b.setTitle(dialogTitle);
        b.setSingleChoiceItems(displayNames, selected, (dialog, which) -> {
            dialog.dismiss();
            String chosen = providers[which];
            switchStreamProvider(chosen);
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private static String normalizeForMatch(String s) {
        if (s == null) return "";
        String norm = java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD);
        norm = norm.replaceAll("\\p{M}", "");
        norm = norm.replaceAll("[^a-z0-9\\s]", " ");
        return norm.replaceAll("\\s+", " ").trim();
    }

    private static boolean isTitleMatch(String target, String candidate, String alt) {
        String t = normalizeForMatch(target);
        String c = normalizeForMatch(candidate);
        String a = normalizeForMatch(alt);
        if (t.isEmpty() || c.isEmpty()) return false;
        if (t.equals(c) || t.contains(c) || c.contains(t)) return true;
        if (!a.isEmpty() && (a.equals(c) || a.contains(c) || c.contains(a))) return true;

        String[] tWords = t.split(" ");
        String[] cWords = c.split(" ");
        int tMatches = 0;
        int tTotal = 0;
        for (String tw : tWords) {
            if (tw.length() > 2) {
                tTotal++;
                for (String cw : cWords) {
                    if (tw.equals(cw)) {
                        tMatches++;
                        break;
                    }
                }
            }
        }
        if (tTotal > 0 && tMatches >= Math.max(1, tTotal / 2)) return true;

        if (!a.isEmpty()) {
            String[] aWords = a.split(" ");
            int aMatches = 0;
            int aTotal = 0;
            for (String aw : aWords) {
                if (aw.length() > 2) {
                    aTotal++;
                    for (String cw : cWords) {
                        if (aw.equals(cw)) {
                            aMatches++;
                            break;
                        }
                    }
                }
            }
            if (aTotal > 0 && aMatches >= Math.max(1, aTotal / 2)) return true;
        }
        return false;
    }

    private void switchStreamProvider(String targetProviderName) {
        if (currentPlayingEntry == null) return;
        VideoProvider vp = VideoProviders.find(targetProviderName);
        if (vp == null) {
            Toast.makeText(this, "Servidor no disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        showPlayerHud("🌐 Conectando con servidor " + targetProviderName + "...");
        final long resumePos = externalPlayer != null ? externalPlayer.getCurrentPosition() : 0;
        final CatalogData.Entry entry = currentPlayingEntry;
        final int season = currentPlayingSeason;
        final int episode = currentPlayingEpisode;
        final String title = currentPlayingTitle;

        videoResolvers.submit(() -> {
            VideoProvider.VideoLink link = null;
            try {
                if (entry.isMovie() && entry.tmdb <= 0) {
                    ZappCatalogEngine.resolveTmdbMovieId(entry);
                } else if (!entry.isMovie() && entry.tmdb <= 0) {
                    ZappCatalogEngine.resolveTmdbTvId(entry);
                }
                link = vp.resolve(entry, season, episode);
            } catch (Exception ignored) {}

            if (link == null) {
                try {
                    String cleanTitle = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                    CatalogData.CatalogPage cp = vp.catalog(entry.isMovie() ? "movie" : "serie", cleanTitle, 1);
                    if (cp != null && cp.entries != null) {
                        for (CatalogData.Entry cand : cp.entries) {
                            if (!isTitleMatch(cleanTitle, cand.title, entry.originalTitle)) {
                                continue;
                            }
                            try {
                                link = vp.resolve(cand, season, episode);
                                if (link != null) break;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }

            final VideoProvider.VideoLink finalLink = link;
            runOnUiThread(() -> {
                if (finalLink != null && finalLink.url != null && !finalLink.url.isEmpty()) {
                    showPlayerHud("✓ Conectado a " + targetProviderName);
                    playMovie(title, finalLink.url, targetProviderName, finalLink.headers);
                    if (externalPlayer != null && resumePos > 1000) {
                        externalPlayer.seekTo(resumePos);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "No se encontró enlace en " + targetProviderName + ". Manteniendo fuente actual.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void autoFallbackUnstableServer(String reason) {
        if (isSwitchingUnstableServer) return;
        if (currentPlayingEntry == null) return;

        isSwitchingUnstableServer = true;
        if (currentPlayingOrigin != null && !currentPlayingOrigin.trim().isEmpty()) {
            failedProvidersForCurrentTitle.add(currentPlayingOrigin.toLowerCase(java.util.Locale.ROOT).trim());
        }

        final long resumePos = externalPlayer != null ? Math.max(0, externalPlayer.getCurrentPosition()) : 0;
        final CatalogData.Entry entry = currentPlayingEntry;
        final int season = currentPlayingSeason;
        final int episode = currentPlayingEpisode;
        final String title = currentPlayingTitle;

        showPlayerHud("⚠️ Servidor inestable. Buscando la fuente estable más rápida…");
        showStatus("⚠️ Servidor inestable (" + reason + "). Buscando la fuente estable más rápida…");

        videoResolvers.submit(() -> {
            boolean isAnime = entry.title != null && (
                entry.title.toLowerCase().contains("dragon ball") ||
                entry.title.toLowerCase().contains("naruto") ||
                entry.title.toLowerCase().contains("one piece") ||
                entry.title.toLowerCase().contains("bleach") ||
                entry.title.toLowerCase().contains("anime") ||
                "anime".equalsIgnoreCase(entry.kind)
            );
            String[] candidateProviders = isAnime
                    ? new String[] { "LatAnime", "TioAnime", "JKAnime", "CineHax", "SoloLatino", "Seriesflix", "Cuevana" }
                    : new String[] { "CineHax", "SoloLatino", "CineCalidad", "Seriesflix", "Cuevana", "LatAnime", "TioAnime", "Pelispedia", "FlixLatam", "HenaoJara" };

            String cleanTitle = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            String altTitle = entry.originalTitle == null ? "" : entry.originalTitle.trim();
            String[] queries = altTitle.isEmpty() ? new String[] { cleanTitle } : new String[] { cleanTitle, altTitle };

            VideoProvider.VideoLink resolvedLink = null;
            String resolvedProviderName = null;

            for (String pName : candidateProviders) {
                if (failedProvidersForCurrentTitle.contains(pName.toLowerCase(java.util.Locale.ROOT).trim())) {
                    continue; // Saltar proveedores que ya fallaron o están inestables
                }
                VideoProvider vp = VideoProviders.find(pName);
                if (vp == null) continue;

                try {
                    resolvedLink = vp.resolve(entry, season, episode);
                    if (resolvedLink != null && resolvedLink.url != null && !resolvedLink.url.isEmpty()
                            && !resolvedLink.url.equals(currentPlayingUrl)) {
                        resolvedProviderName = vp.name();
                        break;
                    }
                } catch (Exception ignored) {}

                // Si no resolvió directo, buscar en catálogo del proveedor por nombre
                for (String q : queries) {
                    if (q.isEmpty()) continue;
                    try {
                        CatalogData.CatalogPage cp = vp.catalog(entry.isMovie() ? "movie" : "serie", q, 1);
                        if (cp != null && cp.entries != null) {
                            for (CatalogData.Entry cand : cp.entries) {
                                if (!isTitleMatch(cleanTitle, cand.title, entry.originalTitle)) {
                                    continue;
                                }
                                try {
                                    resolvedLink = vp.resolve(cand, season, episode);
                                    if (resolvedLink != null && resolvedLink.url != null && !resolvedLink.url.isEmpty()
                                            && !resolvedLink.url.equals(currentPlayingUrl)) {
                                        resolvedProviderName = vp.name();
                                        break;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                    if (resolvedLink != null) break;
                }
                if (resolvedLink != null) break;
            }

            final VideoProvider.VideoLink finalLink = resolvedLink;
            final String finalProvider = resolvedProviderName;

            runOnUiThread(() -> {
                if (finalLink != null && finalLink.url != null && !finalLink.url.isEmpty()) {
                    showPlayerHud("🔄 Servidor inestable. Cambiando a " + finalProvider + "…");
                    showStatus("Servidor inestable. Cambiando a " + finalProvider + "…");
                    playMovie(title, finalLink.url, finalProvider, finalLink.headers);
                    if (externalPlayer != null && resumePos > 1000) {
                        externalPlayer.seekTo(resumePos);
                    }
                } else {
                    showPlayerHud("❌ No hay otra fuente estable disponible.");
                    showStatus("No hay otra fuente estable disponible. Presioná Atrás para volver.");
                }
                isSwitchingUnstableServer = false;
            });
        });
    }

    private void resolveAndLaunchExternal(VideoProvider provider, CatalogData.Entry entry, int season, int episode,
            String title) {
        if (providerLoading)
            return;
        providerLoading = true;
        showStatus("Buscando enlace para reproductor externo...");
        videoResolvers.submit(() -> {
            VideoProvider.VideoLink link = null;
            String resolvedProviderName = provider != null ? provider.name() : "CineHax";
            if (entry.isMovie() && entry.tmdb <= 0) {
                ZappCatalogEngine.resolveTmdbMovieId(entry);
            } else if (!entry.isMovie() && entry.tmdb <= 0) {
                ZappCatalogEngine.resolveTmdbTvId(entry);
            }
            if (provider != null && !"Catálogo Global TV".equals(entry.providerName)) {
                try {
                    link = provider.resolve(entry, season, episode);
                } catch (Exception ignored) {}
            }
            if (link == null) {
                String[] fallbackProviders = new String[] { "CineHax", "SoloLatino", "Cuevana", "CineCalidad", "Pelispedia" };
                String cleanTitle = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                outer: for (String pName : fallbackProviders) {
                    VideoProvider vp = VideoProviders.find(pName);
                    if (vp == null) continue;
                    try {
                        CatalogData.CatalogPage cp = vp.catalog(entry.isMovie() ? "movie" : "serie", cleanTitle, 1);
                        if (cp != null && cp.entries != null) {
                            for (CatalogData.Entry cand : cp.entries) {
                                if (!isTitleMatch(cleanTitle, cand.title, entry.originalTitle)) {
                                    continue;
                                }
                                try {
                                    link = vp.resolve(cand, season, episode);
                                    if (link != null) {
                                        resolvedProviderName = vp.name();
                                        break outer;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            final VideoProvider.VideoLink resolved = link;
            runOnUiThread(() -> {
                providerLoading = false;
                if (resolved == null || resolved.url == null || resolved.url.isEmpty()) {
                    Toast.makeText(MainActivity.this, "No se pudo obtener enlace para reproductor externo", Toast.LENGTH_LONG).show();
                } else {
                    launchExternalPlayer(title, resolved.url, resolved.headers);
                }
            });
        });
    }

    private void updateMediaState() {
        if (mediaSession == null || externalPlayer == null)
            return;
        long actions = android.media.session.PlaybackState.ACTION_PLAY
                | android.media.session.PlaybackState.ACTION_PAUSE
                | android.media.session.PlaybackState.ACTION_PLAY_PAUSE
                | android.media.session.PlaybackState.ACTION_SEEK_TO
                | android.media.session.PlaybackState.ACTION_FAST_FORWARD
                | android.media.session.PlaybackState.ACTION_REWIND;
        int playerState = externalPlayer.getPlaybackState();
        int mediaState = playerState == Player.STATE_ENDED
                ? android.media.session.PlaybackState.STATE_STOPPED
                : playerState == Player.STATE_BUFFERING
                        ? android.media.session.PlaybackState.STATE_BUFFERING
                        : externalPlayer.isPlaying()
                                ? android.media.session.PlaybackState.STATE_PLAYING
                                : android.media.session.PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new android.media.session.PlaybackState.Builder()
                .setActions(actions)
                .setState(mediaState, externalPlayer.getCurrentPosition(),
                        externalPlayer.isPlaying() ? 1f : 0f)
                .build());
    }

    private void startMediaSession() {
        if (mediaSession != null)
            mediaSession.release();
        mediaSession = new android.media.session.MediaSession(this, "VideoPropia");
        mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new android.media.session.MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (externalPlayer != null) {
                    externalPlayer.play();
                    updateMediaState();
                }
            }

            @Override
            public void onPause() {
                if (externalPlayer != null) {
                    externalPlayer.pause();
                    updateMediaState();
                }
            }

            @Override
            public void onSeekTo(long position) {
                if (externalPlayer != null) {
                    externalPlayer.seekTo(position);
                    updateMediaState();
                }
            }

            @Override
            public void onFastForward() {
                if (externalPlayer != null) {
                    externalPlayer.seekForward();
                    updateMediaState();
                }
            }

            @Override
            public void onRewind() {
                if (externalPlayer != null) {
                    externalPlayer.seekBack();
                    updateMediaState();
                }
            }
        });
        mediaSession.setActive(true);
        updateMediaState();
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    private String page = "home";
    private String channel = "";
    private String playingChannel = "Demo";
    private String playbackReturnPage = "home";
    private Button activeCatBtn = null;
    private Button liveTvFsBtn = null;
    private ScrollView liveTvCatScroll = null;
    private ScrollView liveTvChanScroll = null;
    private JSONArray channels = new JSONArray();
    private JSONArray catalogProviders = new JSONArray();
    private JSONObject audit = new JSONObject();

    private void loadAudit() {
        try (InputStream in = getAssets().open("provider_audit.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1)
                bytes.write(buffer, 0, n);
            audit = new JSONObject(bytes.toString("UTF-8"));
        } catch (Exception e) {
            audit = new JSONObject();
            android.util.Log.e("VideoPropia", "Error en auditoría de proveedores", e);
        }
    }

    private String referenceStatus(String name) {
        JSONArray entries = audit.optJSONArray("providers");
        if (entries == null)
            return "Referencia aún sin auditar";
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || !name.equals(entry.optString("name")))
                continue;
            String result = entry.optString("reference_status");
            boolean allowed = entry.optBoolean("live_test_allowed", false);
            String siteTest = entry.optString("site_test", "no_evaluado");
            String siteStatus = !allowed ? "No consultado (restricción previa)"
                    : "http_accessible".equals(siteTest) ? "Sitio accesible (HTTP 200)"
                            : "http_restricted".equals(siteTest)
                                    ? "Acceso restringido (HTTP " + entry.optInt("site_http_status") + ")"
                                    : "Sitio sin respuesta verificada";
            return "Código: " + ("accessible".equals(result) ? "accesible" : result)
                    + ". " + siteStatus + ". Reproducción en nuestra app: NO verificada.";
        }
        return "No hay referencia registrada";
    }

    private void loadCatalog() {
        try (InputStream in = getAssets().open("catalog.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1)
                bytes.write(buffer, 0, n);
            JSONObject doc = new JSONObject(bytes.toString("UTF-8"));
            catalogProviders = doc.getJSONArray("providers");
        } catch (Exception e) {
            catalogProviders = new JSONArray();
            android.util.Log.e("VideoPropia", "Error en catálogo local", e);
        }
    }

    private boolean showCatalog(String providerName, LinearLayout body) {
        for (int i = 0; i < catalogProviders.length(); i++) {
            JSONObject provider = catalogProviders.optJSONObject(i);
            if (provider == null || !providerName.equals(provider.optString("id")))
                continue;
            JSONArray movies = provider.optJSONArray("movies");
            if (movies == null || movies.length() == 0)
                return false;
            for (int j = 0; j < movies.length(); j++) {
                JSONObject movie = movies.optJSONObject(j);
                if (movie == null)
                    continue;
                String title = movie.optString("title", "Película");
                String url = movie.optString("url", "");
                String badge = movie.optString("quality", "");
                int year = movie.optInt("year", 0);
                if (!url.startsWith("https://"))
                    continue;
                item("▶  " + title + " (" + year + ")  |  " + badge, body,
                        () -> playMovie(title, url, providerName));
            }
            return true;
        }
        return false;
    }

    private void loadChannels() {
        try (InputStream in = getAssets().open("channels.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1)
                bytes.write(buffer, 0, n);
            channels = new JSONArray(bytes.toString("UTF-8"));
        } catch (Exception e) {
            channels = new JSONArray();
        }
    }

    private String descriptionFor(String name) {
        for (int i = 0; i < channels.length(); i++) {
            JSONObject j = channels.optJSONObject(i);
            if (j != null && name.equals(j.optString("name"))) {
                return j.optString("description", "Fuente candidata no verificada")
                        + " · Estado: " + j.optString("status", "sin pruebas") + " · No integrada en esta app.";
            }
        }
        return "Fuente no habilitada";
    }

    private static final String DEMO_URL = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4";

    private GradientDrawable bg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(22);
        g.setStroke(2, stroke);
        return g;
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private int dp(int dps) {
        return Math.round(dps * getResources().getDisplayMetrics().density);
    }

    private void line(String s, int size, int color) {
        TextView t = text(s, size, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 7, 0, 14);
        root.addView(t, lp);
    }

    private Button item(String title, LinearLayout target, Runnable run) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(16);
        b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackground(bg(panel, Color.rgb(70, 80, 113)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 90);
        lp.setMargins(0, 0, 0, 14);
        target.addView(b, lp);
        b.setOnClickListener(v -> run.run());
        b.setOnFocusChangeListener((v, focus) -> b
                .setBackground(bg(focus ? violet : panel, focus ? Color.WHITE : Color.rgb(70, 80, 113))));
        return b;
    }

    private Button tile(String name, String state, GridLayout grid, int size, Runnable run) {
        Button b = new Button(this);
        b.setText(name + "\n" + state);
        b.setTextSize(15);
        b.setMaxLines(3);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackground(bg(panel, Color.rgb(68, 79, 111)));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = size;
        params.height = 145;
        params.setMargins(0, 0, 18, 18);
        grid.addView(b, params);
        b.setOnClickListener(v -> run.run());
        b.setOnFocusChangeListener((v, selected) -> {
            b.setBackground(bg(selected ? violet : panel,
                    selected ? Color.rgb(212, 175, 55) : Color.rgb(68, 79, 111)));
            b.animate().scaleX(selected ? 1.08f : 1.0f)
                    .scaleY(selected ? 1.08f : 1.0f).setDuration(150).start();
            b.setElevation(selected ? 12f : 0f);
        });
        return b;
    }

    private LinearLayout newPage(String section, String title, String sub) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 30);
        root.setBackgroundColor(dark);
        line("LEO TV   /   " + section.toUpperCase(), 14, violet);
        TextView heading = text(title, 27, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        line(sub, 15, Color.LTGRAY);
        ScrollView scroll = new ScrollView(this);
        pageScroll = scroll;
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(root);
        return body;
    }

    // IPTV catalogue preview. Only TDTChannels playback is enabled in this demo.
    private String lastM3uName = "";
    private String lastM3uUrl = "";
    private boolean lastM3uPlayable = true;

    // Estado del nuevo TV Studio en Vivo (Estilo Xuper / MXL IPTV)
    private String activeTvCat = "argentina";
    private LiveTvEngine.LiveChannel activeTvChannel = null;
    private ExoPlayer liveTvPlayer = null;
    private PlayerView liveTvPlayerView = null;
    private TextView liveTvStatus = null;
    private LinearLayout liveTvChannelsContainer = null;
    private TextView liveTvChannelTitle = null;
    private TextView liveTvEpgCurrent = null;
    private TextView liveTvEpgNext = null;
    private Button liveTvFavBtn = null;
    private LinearLayout liveTvStudioRoot = null;
    private final java.util.List<android.view.View> sideNavButtons = new java.util.ArrayList<>();
    private android.view.View activeSidebarView = null;
    private LinearLayout currentFirstRail = null;

    private void releaseLiveTvPlayer() {
        if (liveTvBufferingWatchdogRunnable != null) {
            mainHandler.removeCallbacks(liveTvBufferingWatchdogRunnable);
            liveTvBufferingWatchdogRunnable = null;
        }
        if (liveTvPlayer != null) {
            try {
                liveTvPlayer.stop();
                liveTvPlayer.release();
            } catch (Exception ignored) {
            }
            liveTvPlayer = null;
        }
    }

    private void showLiveTvStudio() {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        if (resolutionTask != null)
            resolutionTask.cancel(true);
        ++providerPlayRequest;
        providerLoading = false;
        page = "live_tv";
        channel = "";
        releaseLiveTvPlayer();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(14, 17, 24));
        liveTvStudioRoot = root;

        // 1. Barra lateral principal de Leo TV
        LinearLayout side = buildSidebar("tv");
        root.addView(side, new LinearLayout.LayoutParams(dp(88), -1));

        // 2. Columna de Categorías de TV (Xuper Style)
        LinearLayout catCol = new LinearLayout(this);
        catCol.setOrientation(LinearLayout.VERTICAL);
        catCol.setBackgroundColor(Color.rgb(18, 22, 32));
        catCol.setPadding(dp(12), dp(16), dp(12), dp(16));
        root.addView(catCol, new LinearLayout.LayoutParams(dp(195), -1));

        TextView catHeading = text("📺  SEÑALES TV", 13, Color.rgb(245, 197, 66));
        catHeading.setTypeface(null, Typeface.BOLD);
        catHeading.setPadding(dp(6), 0, 0, dp(12));
        catCol.addView(catHeading);

        ScrollView catScroll = new ScrollView(this);
        catScroll.setVerticalScrollBarEnabled(false);
        catScroll.setFocusable(false);
        catScroll.setFillViewport(true);
        catScroll.setClipToPadding(false);
        catScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        liveTvCatScroll = catScroll;
        catCol.addView(catScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout catItems = new LinearLayout(this);
        catItems.setOrientation(LinearLayout.VERTICAL);
        catItems.setFocusable(false);
        catScroll.addView(catItems);

        // 3. Columna Central: Lista Cómoda de Canales
        LinearLayout chanCol = new LinearLayout(this);
        chanCol.setOrientation(LinearLayout.VERTICAL);
        chanCol.setBackgroundColor(Color.rgb(15, 19, 28));
        chanCol.setPadding(dp(14), dp(16), dp(14), dp(14));
        root.addView(chanCol, new LinearLayout.LayoutParams(dp(390), -1));

        final TextView chanHeading = text("🇦🇷  Argentina · Señales de Aire", 16, Color.WHITE);
        chanHeading.setTypeface(null, Typeface.BOLD);
        chanHeading.setPadding(dp(4), 0, dp(4), dp(10));
        chanCol.addView(chanHeading);

        ScrollView chanScroll = new ScrollView(this);
        chanScroll.setVerticalScrollBarEnabled(false);
        chanScroll.setFocusable(false);
        chanScroll.setFillViewport(true);
        chanScroll.setClipToPadding(false);
        chanScroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        liveTvChanScroll = chanScroll;
        chanCol.addView(chanScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        liveTvChannelsContainer = new LinearLayout(this);
        liveTvChannelsContainer.setOrientation(LinearLayout.VERTICAL);
        liveTvChannelsContainer.setPadding(0, 0, 0, dp(24));
        liveTvChannelsContainer.setFocusable(false);
        chanScroll.addView(liveTvChannelsContainer);

        // 4. Columna Derecha: Reproductor en Vivo + Ficha EPG
        LinearLayout previewCol = new LinearLayout(this);
        previewCol.setOrientation(LinearLayout.VERTICAL);
        previewCol.setBackgroundColor(Color.rgb(12, 15, 22));
        previewCol.setPadding(dp(18), dp(16), dp(20), dp(16));
        root.addView(previewCol, new LinearLayout.LayoutParams(0, -1, 1));

        // Contenedor del video (no foco para no atrapar el control remoto)
        android.widget.FrameLayout playerWrap = new android.widget.FrameLayout(this);
        playerWrap.setFocusable(false);
        playerWrap.setFocusableInTouchMode(false);
        GradientDrawable pwBg = new GradientDrawable();
        pwBg.setColor(Color.BLACK);
        pwBg.setCornerRadius(dp(12));
        playerWrap.setBackground(pwBg);
        playerWrap.setClipToOutline(true);

        liveTvPlayerView = new PlayerView(this);
        liveTvPlayerView.setUseController(false);
        liveTvPlayerView.setFocusable(false);
        liveTvPlayerView.setFocusableInTouchMode(false);
        playerWrap.addView(liveTvPlayerView, new android.widget.FrameLayout.LayoutParams(-1, -1));

        liveTvStatus = text("Sintonizando señal en directo...", 14, Color.WHITE);
        liveTvStatus.setGravity(Gravity.CENTER);
        liveTvStatus.setBackgroundColor(Color.argb(190, 10, 15, 24));
        liveTvStatus.setPadding(dp(12), dp(8), dp(12), dp(8));
        android.widget.FrameLayout.LayoutParams slp = new android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        playerWrap.addView(liveTvStatus, slp);

        previewCol.addView(playerWrap, new LinearLayout.LayoutParams(-1, dp(240)));

        // Información detallada y EPG del canal
        LinearLayout infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setPadding(dp(4), dp(14), dp(4), dp(10));
        previewCol.addView(infoBox);

        LinearLayout titleBadgeRow = new LinearLayout(this);
        titleBadgeRow.setOrientation(LinearLayout.HORIZONTAL);
        titleBadgeRow.setGravity(Gravity.CENTER_VERTICAL);
        infoBox.addView(titleBadgeRow);

        liveTvChannelTitle = text("Canal 26 (Argentina)", 20, Color.WHITE);
        liveTvChannelTitle.setTypeface(null, Typeface.BOLD);
        titleBadgeRow.addView(liveTvChannelTitle, new LinearLayout.LayoutParams(0, -2, 1));

        TextView livePill = text("🔴 EN VIVO", 10, Color.WHITE);
        livePill.setTypeface(null, Typeface.BOLD);
        livePill.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable lpb = new GradientDrawable();
        lpb.setColor(Color.rgb(220, 30, 30));
        lpb.setCornerRadius(dp(6));
        livePill.setBackground(lpb);
        titleBadgeRow.addView(livePill);

        final TextView chanMeta = text("HD 1080p · Señal Abierta Nacional · Audio Español Latino", 12,
                Color.rgb(180, 195, 220));
        chanMeta.setPadding(0, dp(4), 0, dp(10));
        infoBox.addView(chanMeta);

        // Guía de programación (EPG)
        LinearLayout epgBox = new LinearLayout(this);
        epgBox.setOrientation(LinearLayout.VERTICAL);
        epgBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable epBg = new GradientDrawable();
        epBg.setColor(Color.rgb(20, 25, 36));
        epBg.setCornerRadius(dp(10));
        epBg.setStroke(dp(1), Color.rgb(38, 48, 68));
        epgBox.setBackground(epBg);
        infoBox.addView(epgBox, new LinearLayout.LayoutParams(-1, -2));

        liveTvEpgCurrent = text("▶ Ahora: Las Noticias de las 26 · En directo", 13, Color.rgb(245, 197, 66));
        liveTvEpgCurrent.setTypeface(null, Typeface.BOLD);
        epgBox.addView(liveTvEpgCurrent);

        liveTvEpgNext = text("⏭ Siguiente: Resumen Internacional 24H", 12, Color.rgb(170, 182, 204));
        liveTvEpgNext.setPadding(0, dp(4), 0, 0);
        epgBox.addView(liveTvEpgNext);

        // Botones de acción rápida (Pantalla Completa, Favorito, Recargar)
        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        actRow.setGravity(Gravity.CENTER_VERTICAL);
        actRow.setPadding(0, dp(14), 0, 0);
        infoBox.addView(actRow);

        Button fsBtn = new Button(this);
        fsBtn.setText("⛶  Pantalla Completa");
        fsBtn.setTextColor(Color.WHITE);
        fsBtn.setTextSize(13);
        fsBtn.setTypeface(null, Typeface.BOLD);
        fsBtn.setAllCaps(false);
        fsBtn.setFocusable(true);
        fsBtn.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable fsBg = new GradientDrawable();
        fsBg.setColor(Color.rgb(129, 107, 245));
        fsBg.setCornerRadius(dp(8));
        fsBtn.setBackground(fsBg);
        LinearLayout.LayoutParams fsLp = new LinearLayout.LayoutParams(-2, dp(42));
        fsLp.setMargins(0, 0, dp(10), 0);
        actRow.addView(fsBtn, fsLp);
        fsBtn.setOnClickListener(v -> enterLiveTvFullscreen());
        liveTvFsBtn = fsBtn;
        fsBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (liveTvChannelsContainer != null && liveTvChannelsContainer.getChildCount() > 0) {
                    for (int ci = 0; ci < liveTvChannelsContainer.getChildCount(); ci++) {
                        View cv = liveTvChannelsContainer.getChildAt(ci);
                        if (cv.getTag() != null && activeTvChannel != null
                                && activeTvChannel.id.equals(((LiveTvEngine.LiveChannel) cv.getTag()).id)) {
                            cv.requestFocus();
                            return true;
                        }
                    }
                    liveTvChannelsContainer.getChildAt(0).requestFocus();
                    return true;
                }
            }
            return false;
        });

        liveTvFavBtn = new Button(this);
        liveTvFavBtn.setText("❤️  Favorito");
        liveTvFavBtn.setTextColor(Color.WHITE);
        liveTvFavBtn.setTextSize(13);
        liveTvFavBtn.setAllCaps(false);
        liveTvFavBtn.setFocusable(true);
        liveTvFavBtn.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable favBg = new GradientDrawable();
        favBg.setColor(Color.rgb(36, 42, 58));
        favBg.setCornerRadius(dp(8));
        liveTvFavBtn.setBackground(favBg);
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(-2, dp(42));
        favLp.setMargins(0, 0, dp(10), 0);
        actRow.addView(liveTvFavBtn, favLp);
        liveTvFavBtn.setOnClickListener(v -> {
            if (activeTvChannel != null) {
                boolean added = LiveTvEngine.toggleFavorite(this, activeTvChannel);
                updateLiveTvFavButton();
                Toast.makeText(this, added ? "Canal añadido a Favoritos de TV" : "Canal quitado de Favoritos de TV",
                        Toast.LENGTH_SHORT).show();
            }
        });

        Button reloadBtn = new Button(this);
        reloadBtn.setText("↻  Recargar");
        reloadBtn.setTextColor(Color.WHITE);
        reloadBtn.setTextSize(13);
        reloadBtn.setAllCaps(false);
        reloadBtn.setFocusable(true);
        reloadBtn.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(Color.rgb(36, 42, 58));
        rBg.setCornerRadius(dp(8));
        reloadBtn.setBackground(rBg);
        actRow.addView(reloadBtn, new LinearLayout.LayoutParams(-2, dp(42)));
        reloadBtn.setOnClickListener(v -> {
            if (activeTvChannel != null) {
                tuneLiveChannel(activeTvChannel);
            }
        });

        // Población de Categorías en el Menú Lateral
        List<LiveTvEngine.TvCategory> categories = LiveTvEngine.getCategories();
        for (LiveTvEngine.TvCategory cat : categories) {
            if ("adults".equals(cat.id) && ParentalControlEngine.isParentalLocked(this)) {
                continue;
            }
            Button catBtn = new Button(this);
            catBtn.setText(cat.label);
            catBtn.setTextColor(Color.rgb(200, 210, 230));
            catBtn.setTextSize(13);
            catBtn.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            catBtn.setAllCaps(false);
            catBtn.setFocusable(true);
            catBtn.setPadding(dp(10), dp(8), dp(10), dp(8));

            GradientDrawable b = new GradientDrawable();
            boolean isCur = cat.id.equals(activeTvCat);
            if (isCur) {
                activeCatBtn = catBtn;
            }
            b.setColor(isCur ? Color.rgb(36, 44, 64) : Color.TRANSPARENT);
            b.setCornerRadius(dp(8));
            catBtn.setBackground(b);

            catBtn.setOnClickListener(v -> {
                if ("iptv_org_explorer".equals(cat.id)) {
                    showIptvOrgExplorer();
                    return;
                } else if ("tdtchannels".equals(cat.id)) {
                    showM3uList("TDTChannels TV", "https://www.tdtchannels.com/lists/tv.m3u8", true);
                    return;
                } else if ("freetv".equals(cat.id)) {
                    showM3uList("Free-TV Global IPTV",
                            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", true);
                    return;
                }
                activeTvCat = cat.id;
                chanHeading.setText(cat.label);
                renderTvChannels(chanCol, cat.id);
            });
            catBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(44, 54, 80)
                        : (cat.id.equals(activeTvCat) ? Color.rgb(36, 44, 64) : Color.TRANSPARENT));
                g.setCornerRadius(dp(8));
                if (f) {
                    g.setStroke(dp(2), Color.rgb(245, 197, 66));
                    activeCatBtn = catBtn;
                    if (liveTvCatScroll != null) {
                        liveTvCatScroll.smoothScrollTo(0, Math.max(0, catBtn.getTop() - dp(40)));
                    }
                    if (!cat.id.equals(activeTvCat)) {
                        activeTvCat = cat.id;
                        chanHeading.setText(cat.label);
                        renderTvChannels(chanCol, cat.id);
                    }
                }
                catBtn.setBackground(g);
                catBtn.setTextColor(f ? Color.WHITE : Color.rgb(200, 210, 230));
            });
            catBtn.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (liveTvChannelsContainer != null && liveTvChannelsContainer.getChildCount() > 0) {
                            for (int ci = 0; ci < liveTvChannelsContainer.getChildCount(); ci++) {
                                View cv = liveTvChannelsContainer.getChildAt(ci);
                                if (cv.getTag() != null && activeTvChannel != null
                                        && activeTvChannel.id.equals(((LiveTvEngine.LiveChannel) cv.getTag()).id)) {
                                    cv.requestFocus();
                                    return true;
                                }
                            }
                            liveTvChannelsContainer.getChildAt(0).requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (activeSidebarView != null) {
                            activeSidebarView.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });

            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(44));
            blp.setMargins(0, 0, 0, dp(4));
            catItems.addView(catBtn, blp);
        }

        renderTvChannels(chanCol, activeTvCat);
        setContentView(root);
        playbackReturnPage = "live_tv";
        root.post(() -> {
            if (liveTvChannelsContainer != null && liveTvChannelsContainer.getChildCount() > 0) {
                for (int ci = 0; ci < liveTvChannelsContainer.getChildCount(); ci++) {
                    View cv = liveTvChannelsContainer.getChildAt(ci);
                    if (cv.getTag() != null && activeTvChannel != null
                            && activeTvChannel.id.equals(((LiveTvEngine.LiveChannel) cv.getTag()).id)) {
                        cv.requestFocus();
                        return;
                    }
                }
                liveTvChannelsContainer.getChildAt(0).requestFocus();
            } else if (activeCatBtn != null) {
                activeCatBtn.requestFocus();
            }
        });
    }

    private void renderTvChannels(LinearLayout chanCol, String catId) {
        if (liveTvChannelsContainer == null)
            return;
        if ("adults".equals(catId) && ParentalControlEngine.isParentalLocked(this)) {
            showParentalPinDialog(() -> renderTvChannels(chanCol, catId));
            return;
        }
        liveTvChannelsContainer.removeAllViews();

        List<LiveTvEngine.LiveChannel> channels = LiveTvEngine.getChannelsForCategory(this, catId);
        if (channels.isEmpty()) {
            TextView emptyTxt = text("No hay canales guardados en esta sección.", 14, Color.GRAY);
            emptyTxt.setPadding(dp(12), dp(16), dp(12), dp(16));
            liveTvChannelsContainer.addView(emptyTxt);
            return;
        }

        // Si no hay canal activo o cambió de categoría, sintonizar el primero
        // automáticamente
        if (activeTvChannel == null || "argentina".equals(catId)) {
            activeTvChannel = channels.get(0);
            tuneLiveChannel(activeTvChannel);
        }

        for (LiveTvEngine.LiveChannel ch : channels) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(8), dp(12), dp(8));
            card.setFocusable(true);
            card.setClickable(true);

            GradientDrawable cBg = new GradientDrawable();
            boolean isCur = activeTvChannel != null && ch.id.equals(activeTvChannel.id);
            cBg.setColor(isCur ? Color.rgb(32, 40, 58) : Color.rgb(22, 26, 36));
            cBg.setCornerRadius(dp(10));
            cBg.setStroke(dp(1), isCur ? Color.rgb(245, 197, 66) : Color.rgb(38, 44, 60));
            card.setBackground(cBg);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(72));
            clp.setMargins(0, 0, 0, dp(8));
            card.setLayoutParams(clp);

            // Logo del canal con dimensiones cómodas
            android.widget.FrameLayout logoWrap = new android.widget.FrameLayout(this);
            GradientDrawable lwBg = new GradientDrawable();
            lwBg.setColor(Color.rgb(16, 20, 28));
            lwBg.setCornerRadius(dp(8));
            logoWrap.setBackground(lwBg);
            logoWrap.setClipToOutline(true);

            ImageView logoImg = new ImageView(this);
            logoImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoWrap.addView(logoImg, new android.widget.FrameLayout.LayoutParams(-1, -1));

            if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
                CatalogBrowser.loadPoster(this, logoImg, ch.logoUrl);
            } else {
                TextView defIcon = text("📺", 18, Color.WHITE);
                defIcon.setGravity(Gravity.CENTER);
                logoWrap.addView(defIcon, new android.widget.FrameLayout.LayoutParams(-1, -1));
            }

            card.addView(logoWrap, new LinearLayout.LayoutParams(dp(52), dp(46)));

            // Textos del canal
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setPadding(dp(12), 0, dp(8), 0);

            TextView title = text(ch.name, 15, Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(title);

            TextView epg = text(ch.epgTitle, 11, Color.rgb(245, 197, 66));
            epg.setSingleLine(true);
            epg.setEllipsize(android.text.TextUtils.TruncateAt.END);
            epg.setPadding(0, dp(2), 0, 0);
            info.addView(epg);

            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            // Badge de calidad
            TextView qBadge = text(ch.quality, 10, Color.WHITE);
            qBadge.setPadding(dp(6), dp(3), dp(6), dp(3));
            GradientDrawable qb = new GradientDrawable();
            qb.setColor(Color.rgb(36, 44, 62));
            qb.setCornerRadius(dp(4));
            qBadge.setBackground(qb);
            card.addView(qBadge);

            card.setTag(ch);
            card.setOnClickListener(v -> {
                tuneLiveChannel(ch);
            });
            card.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                boolean cur = activeTvChannel != null && ch.id.equals(activeTvChannel.id);
                g.setColor(f ? Color.rgb(42, 52, 76) : (cur ? Color.rgb(32, 40, 58) : Color.rgb(22, 26, 36)));
                g.setCornerRadius(dp(10));
                g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : (cur ? Color.rgb(245, 197, 66) : Color.rgb(38, 44, 60)));
                card.setBackground(g);
                card.animate().scaleX(f ? 1.02f : 1f).scaleY(f ? 1.02f : 1f).setDuration(120).start();
                if (f) {
                    tuneLiveChannel(ch);
                    if (liveTvChanScroll != null) {
                        liveTvChanScroll.smoothScrollTo(0, Math.max(0, card.getTop() - dp(60)));
                    }
                }
            });
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (activeCatBtn != null) {
                            activeCatBtn.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (liveTvFsBtn != null) {
                            liveTvFsBtn.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });

            liveTvChannelsContainer.addView(card);
        }
    }

    private void updateLiveTvFavButton() {
        if (liveTvFavBtn == null || activeTvChannel == null)
            return;
        boolean fav = LiveTvEngine.isFavorite(this, activeTvChannel.id);
        liveTvFavBtn.setText(fav ? "❤️  En Favoritos" : "🤍  Favorito");
        GradientDrawable g = new GradientDrawable();
        g.setColor(fav ? Color.rgb(180, 30, 40) : Color.rgb(36, 42, 58));
        g.setCornerRadius(dp(8));
        liveTvFavBtn.setBackground(g);
    }

    private void tuneLiveChannel(LiveTvEngine.LiveChannel ch) {
        if (ch == null)
            return;
        activeTvChannel = ch;
        updateLiveTvFavButton();

        if (liveTvChannelTitle != null) {
            liveTvChannelTitle.setText(ch.name);
        }
        if (liveTvEpgCurrent != null) {
            liveTvEpgCurrent.setText("▶ Ahora: " + ch.epgTitle);
        }
        if (liveTvEpgNext != null) {
            liveTvEpgNext.setText("⏭ Siguiente: " + ch.epgNext);
        }

        if (liveTvStatus != null) {
            liveTvStatus.setVisibility(android.view.View.VISIBLE);
            liveTvStatus.setText("Sintonizando " + ch.name + " · " + ch.quality + "...");
        }

        releaseLiveTvPlayer();

        if (ch.streamUrl != null && ch.streamUrl.contains("lambda-url")) {
            new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(ch.streamUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        sb.append(line);
                    br.close();
                    JSONObject json = new JSONObject(sb.toString());
                    String token = json.optString("access_token");
                    String sid = json.optString("stream_id", "6a024684fd4ca6a938f3a118");
                    String realUrl = "https://mdstrm.com/live-stream-playlist/" + sid + ".m3u8?access_token=" + token;
                    runOnUiThread(() -> startLivePlayerInternal(ch, realUrl));
                } catch (Exception e) {
                    runOnUiThread(() -> startLivePlayerInternal(ch, ch.backupUrl));
                }
            }).start();
        } else if (ch.streamUrl != null && (ch.streamUrl.contains("tvf90.com") || ch.streamUrl.contains("r="))
                && !ch.streamUrl.contains(".m3u8")) {
            new Thread(() -> {
                try {
                    java.util.Map<String, String> hdrs = new java.util.HashMap<>();
                    String realUrl = LiveTvEngine.resolveDynamicStream(ch.streamUrl, hdrs);
                    if (realUrl != null && !realUrl.isEmpty() && realUrl.startsWith("http")) {
                        runOnUiThread(() -> startLivePlayerInternal(ch, realUrl));
                    } else {
                        runOnUiThread(() -> startLivePlayerInternal(ch, ch.backupUrl));
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> startLivePlayerInternal(ch, ch.backupUrl));
                }
            }).start();
        } else {
            startLivePlayerInternal(ch, ch.streamUrl);
        }
    }

    private void startLivePlayerInternal(LiveTvEngine.LiveChannel ch, String urlToPlay) {
        if (ch == null || urlToPlay == null || urlToPlay.isEmpty())
            return;
        try {
            // 1. DESACTIVAR cola asíncrona de MediaCodec para eliminar stalls de AudioTrack
            // y sonido robótico
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .forceDisableMediaCodecAsynchronousQueueing()
                    .setEnableDecoderFallback(true)
                    .setAllowedVideoJoiningTimeMs(5000);

            // 2. Control de buffer para Live TV
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            12000, // minBufferMs
                            40000, // maxBufferMs
                            1500, // bufferForPlaybackMs
                            2500 // bufferForPlaybackAfterRebufferMs
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            // 3. AudioAttributes multimedia
            AudioAttributes audioAttrs = new AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build();

            // 4. Conexión HTTP con Headers
            androidx.media3.datasource.DefaultHttpDataSource.Factory http = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent(
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36")
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(20000)
                    .setAllowCrossProtocolRedirects(true);

            java.util.Map<String, String> reqHeaders = new java.util.HashMap<>();
            if (urlToPlay.contains("tvf90.com") || urlToPlay.contains(".ftlly.com")) {
                reqHeaders.put("Referer", "https://tvf90.com/");
                reqHeaders.put("Origin", "https://tvf90.com");
            } else if (urlToPlay.contains("mdstrm.com")) {
                reqHeaders.put("Referer", "https://mitelefe.com/");
                reqHeaders.put("Origin", "https://mitelefe.com");
            } else if (urlToPlay.contains("eltrecetv")) {
                reqHeaders.put("Referer", "https://www.eltrecetv.com.ar/");
                reqHeaders.put("Origin", "https://www.eltrecetv.com.ar");
            } else if (urlToPlay.contains("vodgc.net") || urlToPlay.contains("tn.com.ar")) {
                reqHeaders.put("Referer", "https://tn.com.ar/");
                reqHeaders.put("Origin", "https://tn.com.ar");
            } else if (urlToPlay.contains("qaotic.net") || urlToPlay.contains("americatv")) {
                reqHeaders.put("Referer", "https://www.americatv.com.ar/");
                reqHeaders.put("Origin", "https://www.americatv.com.ar");
            }
            if (!reqHeaders.isEmpty()) {
                http.setDefaultRequestProperties(reqHeaders);
            }

            ExoPlayer.Builder builder = new ExoPlayer.Builder(this, renderersFactory);
            builder.setLoadControl(loadControl);
            builder.setAudioAttributes(audioAttrs, true);
            builder.setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http));
            liveTvPlayer = builder.build();

            // 5. Limitar video a 720p / 30fps para impedir caída de frames por sobrecarga
            // de decodificación
            TrackSelectionParameters trackParams = liveTvPlayer.getTrackSelectionParameters()
                    .buildUpon()
                    .setMaxVideoSize(1280, 720)
                    .setMaxVideoFrameRate(30)
                    .setMaxVideoBitrate(2500000)
                    .build();
            liveTvPlayer.setTrackSelectionParameters(trackParams);

            // 6. Fijar velocidad a 1.0f para anular tiempo/pitch stretch en el procesador
            // Sonic
            liveTvPlayer.setPlaybackParameters(new PlaybackParameters(1.0f, 1.0f));

            if (liveTvPlayerView != null) {
                liveTvPlayerView.setPlayer(liveTvPlayer);
            }

            liveTvPlayer.addListener(new Player.Listener() {
                private boolean triedBackup = false;

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        if (liveTvBufferingWatchdogRunnable != null) {
                            mainHandler.removeCallbacks(liveTvBufferingWatchdogRunnable);
                            liveTvBufferingWatchdogRunnable = null;
                        }
                        if (liveTvStatus != null)
                            liveTvStatus.setVisibility(android.view.View.GONE);
                    } else if (state == Player.STATE_BUFFERING) {
                        if (liveTvStatus != null) {
                            liveTvStatus.setVisibility(android.view.View.VISIBLE);
                            liveTvStatus.setText("Cargando buffer de señal...");
                        }
                        if (liveTvBufferingWatchdogRunnable != null) {
                            mainHandler.removeCallbacks(liveTvBufferingWatchdogRunnable);
                        }
                        liveTvBufferingWatchdogRunnable = () -> {
                            if (liveTvPlayer != null && liveTvPlayer.getPlaybackState() == Player.STATE_BUFFERING
                                    && !triedBackup && ch.backupUrl != null && !ch.backupUrl.isEmpty()
                                    && !ch.backupUrl.equals(urlToPlay)) {
                                triedBackup = true;
                                if (liveTvStatus != null) {
                                    liveTvStatus.setVisibility(android.view.View.VISIBLE);
                                    liveTvStatus.setText("⚠️ Señal inestable. Cambiando a señal de respaldo...");
                                }
                                startLivePlayerInternal(ch, ch.backupUrl);
                            }
                        };
                        mainHandler.postDelayed(liveTvBufferingWatchdogRunnable, 8000);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (liveTvBufferingWatchdogRunnable != null) {
                        mainHandler.removeCallbacks(liveTvBufferingWatchdogRunnable);
                        liveTvBufferingWatchdogRunnable = null;
                    }
                    if (!triedBackup && ch.backupUrl != null && !ch.backupUrl.isEmpty()
                            && !ch.backupUrl.equals(urlToPlay)) {
                        triedBackup = true;
                        if (liveTvStatus != null) {
                            liveTvStatus.setVisibility(android.view.View.VISIBLE);
                            liveTvStatus.setText("Conectando señal de respaldo...");
                        }
                        startLivePlayerInternal(ch, ch.backupUrl);
                        return;
                    }
                    if (liveTvStatus != null) {
                        liveTvStatus.setVisibility(android.view.View.VISIBLE);
                        liveTvStatus.setText("⚠️ Señal momentáneamente no disponible. Probá otro canal o recargá.");
                    }
                }
            });

            // 7. Deshabilitar control de velocidad variable del borde en vivo
            MediaItem mi = new MediaItem.Builder()
                    .setUri(Uri.parse(urlToPlay))
                    .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.0f)
                            .setMinPlaybackSpeed(1.0f)
                            .build())
                    .build();

            liveTvPlayer.setMediaItem(mi);
            liveTvPlayer.prepare();
            liveTvPlayer.play();

            LiveTvEngine.addRecent(this, ch);
        } catch (Exception ex) {
            if (liveTvStatus != null) {
                liveTvStatus.setVisibility(android.view.View.VISIBLE);
                liveTvStatus.setText("Error al conectar con la señal: " + ex.getMessage());
            }
        }
    }

    private void enterLiveTvFullscreen() {
        if (activeTvChannel == null)
            return;
        playbackReturnPage = "live_tv";
        page = "live_tv_fullscreen";
        final LiveTvEngine.LiveChannel ch = activeTvChannel;
        if (ch.streamUrl != null && ch.streamUrl.contains("lambda-url")) {
            new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(ch.streamUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null)
                        sb.append(line);
                    br.close();
                    JSONObject json = new JSONObject(sb.toString());
                    String token = json.optString("access_token");
                    String sid = json.optString("stream_id", "6a024684fd4ca6a938f3a118");
                    String realUrl = "https://mdstrm.com/live-stream-playlist/" + sid + ".m3u8?access_token=" + token;
                    java.util.Map<String, String> hdrs = new java.util.HashMap<>();
                    hdrs.put("Referer", "https://mitelefe.com/");
                    hdrs.put("Origin", "https://mitelefe.com");
                    runOnUiThread(() -> playMovie(ch.name, realUrl, "TV en Vivo (" + ch.name + ")", hdrs));
                } catch (Exception e) {
                    runOnUiThread(() -> playMovie(ch.name, ch.backupUrl, "TV en Vivo (" + ch.name + ")"));
                }
            }).start();
        } else {
            java.util.Map<String, String> hdrs = null;
            if (ch.streamUrl != null && ch.streamUrl.contains("eltrecetv")) {
                hdrs = new java.util.HashMap<>();
                hdrs.put("Referer", "https://www.eltrecetv.com.ar/");
                hdrs.put("Origin", "https://www.eltrecetv.com.ar");
            } else if (ch.streamUrl != null
                    && (ch.streamUrl.contains("vodgc.net") || ch.streamUrl.contains("tn.com.ar"))) {
                hdrs = new java.util.HashMap<>();
                hdrs.put("Referer", "https://tn.com.ar/");
                hdrs.put("Origin", "https://tn.com.ar");
            } else if (ch.streamUrl != null
                    && (ch.streamUrl.contains("qaotic.net") || ch.streamUrl.contains("americatv"))) {
                hdrs = new java.util.HashMap<>();
                hdrs.put("Referer", "https://www.americatv.com.ar/");
                hdrs.put("Origin", "https://www.americatv.com.ar");
            }
            playMovie(ch.name, ch.streamUrl, "TV en Vivo (" + ch.name + ")", hdrs);
        }
    }

    private void addLiveTvCategoryCard(GridLayout grid, String title, String subtitle, String icon, int accentColor,
            Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setFocusable(true);
        card.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(24, 28, 40));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(44, 52, 70));
        card.setBackground(bg);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cardW = Math.max(dp(280), (dm.widthPixels - dp(180)) / 2);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cardW;
        lp.height = dp(92);
        lp.setMargins(0, 0, dp(14), dp(14));
        card.setLayoutParams(lp);

        TextView iconView = text(icon, 30, Color.WHITE);
        iconView.setPadding(0, 0, dp(14), 0);
        card.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);

        TextView tView = text(title, 16, Color.WHITE);
        tView.setTypeface(null, Typeface.BOLD);
        tView.setSingleLine(true);
        textCol.addView(tView);

        TextView subView = text(subtitle, 12, Color.rgb(170, 182, 204));
        subView.setSingleLine(true);
        subView.setPadding(0, dp(3), 0, 0);
        textCol.addView(subView);

        card.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView liveBadge = text("🔴 EN VIVO", 10, Color.WHITE);
        liveBadge.setTypeface(null, Typeface.BOLD);
        liveBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(accentColor);
        lb.setCornerRadius(dp(6));
        liveBadge.setBackground(lb);
        card.addView(liveBadge);

        card.setOnClickListener(v -> onClick.run());
        card.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(38, 46, 68) : Color.rgb(24, 28, 40));
            g.setCornerRadius(dp(14));
            g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(44, 52, 70));
            card.setBackground(g);
            card.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(120).start();
        });
        grid.addView(card);
    }

    private boolean m3uCompactMode = true;
    private String m3uSelectedGroup = "Todos";
    private String m3uSearchFilter = "";
    private java.util.List<String[]> currentM3uEntries = new java.util.ArrayList<>();
    private LinearLayout m3uChannelsContainer = null;
    private TextView m3uCountLabel = null;
    private Button m3uToggleModeBtn = null;

    private void renderM3uChannelsGrid(LinearLayout body, java.util.List<String[]> entries) {
        currentM3uEntries = entries;
        m3uSelectedGroup = "Todos";
        m3uSearchFilter = "";

        // Barra de control superior (Modo Compacto/Grid, Buscar)
        LinearLayout topControls = new LinearLayout(this);
        topControls.setOrientation(LinearLayout.HORIZONTAL);
        topControls.setGravity(Gravity.CENTER_VERTICAL);
        topControls.setPadding(0, dp(6), 0, dp(10));

        m3uToggleModeBtn = new Button(this);
        m3uToggleModeBtn.setText(m3uCompactMode ? "📋  Modo: Lista Compacta" : "🔲  Modo: Cuadrícula");
        m3uToggleModeBtn.setTextColor(Color.WHITE);
        m3uToggleModeBtn.setTextSize(13);
        m3uToggleModeBtn.setAllCaps(false);
        m3uToggleModeBtn.setFocusable(true);
        GradientDrawable mbBg = new GradientDrawable();
        mbBg.setColor(Color.rgb(40, 48, 68));
        mbBg.setCornerRadius(dp(8));
        m3uToggleModeBtn.setBackground(mbBg);
        m3uToggleModeBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        m3uToggleModeBtn.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(60, 75, 110) : Color.rgb(40, 48, 68));
            g.setCornerRadius(dp(8));
            if (f)
                g.setStroke(dp(2), Color.rgb(245, 197, 66));
            m3uToggleModeBtn.setBackground(g);
        });
        m3uToggleModeBtn.setOnClickListener(v -> {
            m3uCompactMode = !m3uCompactMode;
            m3uToggleModeBtn.setText(m3uCompactMode ? "📋  Modo: Lista Compacta" : "🔲  Modo: Cuadrícula");
            renderM3uFilteredChannels();
        });
        topControls.addView(m3uToggleModeBtn);

        // Campo de búsqueda en tiempo real
        final android.widget.EditText searchBox = new android.widget.EditText(this);
        searchBox.setHint("🔍  Buscar canal por nombre...");
        searchBox.setHintTextColor(Color.rgb(150, 160, 180));
        searchBox.setTextColor(Color.WHITE);
        searchBox.setTextSize(13);
        searchBox.setSingleLine(true);
        searchBox.setFocusable(true);
        GradientDrawable sbBg = new GradientDrawable();
        sbBg.setColor(Color.rgb(24, 28, 40));
        sbBg.setCornerRadius(dp(8));
        sbBg.setStroke(dp(1), Color.rgb(60, 70, 95));
        searchBox.setBackground(sbBg);
        searchBox.setPadding(dp(14), dp(8), dp(14), dp(8));
        searchBox.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(32, 38, 55) : Color.rgb(24, 28, 40));
            g.setCornerRadius(dp(8));
            g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(60, 70, 95));
            searchBox.setBackground(g);
        });
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                m3uSearchFilter = s != null ? s.toString().trim() : "";
                renderM3uFilteredChannels();
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(dp(320), dp(40));
        sbLp.setMargins(dp(14), 0, 0, 0);
        topControls.addView(searchBox, sbLp);

        body.addView(topControls);

        // Barra horizontal de categorías (Chips extraídos del group-title)
        java.util.LinkedHashMap<String, Integer> groups = new java.util.LinkedHashMap<>();
        groups.put("Todos", entries.size());
        for (String[] e : entries) {
            String g = (e[1] != null && !e[1].trim().isEmpty()) ? e[1].trim() : "General";
            groups.put(g, groups.containsKey(g) ? groups.get(g) + 1 : 1);
        }

        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setPadding(0, dp(4), 0, dp(10));

        final java.util.List<Button> chipButtons = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Integer> entry : groups.entrySet()) {
            final String grpName = entry.getKey();
            final int grpCount = entry.getValue();

            Button chip = new Button(this);
            chip.setText(grpName + " (" + grpCount + ")");
            chip.setTextColor(grpName.equals(m3uSelectedGroup) ? Color.WHITE : Color.rgb(180, 190, 210));
            chip.setTextSize(12);
            chip.setAllCaps(false);
            chip.setFocusable(true);

            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setColor(grpName.equals(m3uSelectedGroup) ? Color.rgb(90, 60, 180) : Color.rgb(30, 35, 50));
            chipBg.setCornerRadius(dp(16));
            chip.setBackground(chipBg);
            chip.setPadding(dp(14), dp(4), dp(14), dp(4));

            chip.setOnFocusChangeListener((v, f) -> {
                boolean isSel = grpName.equals(m3uSelectedGroup);
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(110, 80, 210) : (isSel ? Color.rgb(90, 60, 180) : Color.rgb(30, 35, 50)));
                g.setCornerRadius(dp(16));
                if (f)
                    g.setStroke(dp(2), Color.WHITE);
                chip.setBackground(g);
                chip.setTextColor(Color.WHITE);
            });

            chip.setOnClickListener(v -> {
                m3uSelectedGroup = grpName;
                for (Button cb : chipButtons) {
                    boolean isSel = cb == chip;
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(isSel ? Color.rgb(90, 60, 180) : Color.rgb(30, 35, 50));
                    g.setCornerRadius(dp(16));
                    cb.setBackground(g);
                    cb.setTextColor(isSel ? Color.WHITE : Color.rgb(180, 190, 210));
                }
                renderM3uFilteredChannels();
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(34));
            clp.setMargins(0, 0, dp(8), 0);
            catRow.addView(chip, clp);
            chipButtons.add(chip);
        }
        catScroll.addView(catRow);
        body.addView(catScroll);

        // Contador de canales
        m3uCountLabel = text("", 13, Color.rgb(245, 197, 66));
        m3uCountLabel.setPadding(0, dp(4), 0, dp(8));
        body.addView(m3uCountLabel);

        // Contenedor dinámico de canales
        m3uChannelsContainer = new LinearLayout(this);
        m3uChannelsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(m3uChannelsContainer);

        renderM3uFilteredChannels();
    }

    private void renderM3uFilteredChannels() {
        if (m3uChannelsContainer == null)
            return;
        m3uChannelsContainer.removeAllViews();

        java.util.List<String[]> filtered = new java.util.ArrayList<>();
        String q = (m3uSearchFilter != null) ? m3uSearchFilter.toLowerCase() : "";
        for (String[] e : currentM3uEntries) {
            String title = e[0] != null ? e[0] : "";
            String group = e[1] != null ? e[1] : "General";

            if (!"Todos".equals(m3uSelectedGroup) && !group.equalsIgnoreCase(m3uSelectedGroup)) {
                continue;
            }
            if (!q.isEmpty() && !title.toLowerCase().contains(q) && !group.toLowerCase().contains(q)) {
                continue;
            }
            filtered.add(e);
        }

        if (m3uCountLabel != null) {
            m3uCountLabel.setText("Mostrando " + filtered.size() + " de " + currentM3uEntries.size()
                    + " canales" + (!"Todos".equals(m3uSelectedGroup) ? " · Categoría: " + m3uSelectedGroup : "")
                    + (!q.isEmpty() ? " · Búsqueda: '" + q + "'" : ""));
        }

        if (filtered.isEmpty()) {
            TextView empty = text("No se encontraron canales con los filtros seleccionados.", 14, Color.GRAY);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            m3uChannelsContainer.addView(empty);
            return;
        }

        if (m3uCompactMode) {
            // MODO LISTA COMPACTA CON ENLACE SECUENCIAL DE FOCO (CERO SALTOS)
            final int count = filtered.size();
            final int[] viewIds = new int[count];
            for (int i = 0; i < count; i++) {
                viewIds[i] = View.generateViewId();
            }

            for (int i = 0; i < count; i++) {
                final String[] e = filtered.get(i);
                final String chTitle = e[0];
                final String chGroup = e[1];
                final String chUrl = e[2];
                final int chNum = i + 1;

                LinearLayout row = new LinearLayout(this);
                row.setId(viewIds[i]);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(6), dp(12), dp(6));
                row.setFocusable(true);
                row.setClickable(true);

                // Enlace secuencial de foco para que el D-Pad baje y suba sin retroceder ni
                // saltar
                if (i > 0) {
                    row.setNextFocusUpId(viewIds[i - 1]);
                }
                if (i < count - 1) {
                    row.setNextFocusDownId(viewIds[i + 1]);
                }

                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(Color.rgb(20, 24, 34));
                rowBg.setCornerRadius(dp(6));
                rowBg.setStroke(dp(1), Color.rgb(38, 45, 62));
                row.setBackground(rowBg);

                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(44));
                rlp.setMargins(0, 0, 0, dp(4));
                row.setLayoutParams(rlp);

                // Número
                TextView num = text("#" + chNum, 12, Color.rgb(150, 160, 190));
                num.setTypeface(null, Typeface.BOLD);
                num.setPadding(0, 0, dp(12), 0);
                row.addView(num);

                // Nombre
                TextView titleView = text(chTitle, 13, Color.WHITE);
                titleView.setTypeface(null, Typeface.BOLD);
                titleView.setSingleLine(true);
                titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

                // Categoría
                TextView grpBadge = text(chGroup, 11, Color.rgb(245, 197, 66));
                grpBadge.setSingleLine(true);
                grpBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
                row.addView(grpBadge);

                // Botón VER
                TextView playBadge = text("▶ EN VIVO", 10, Color.WHITE);
                playBadge.setTypeface(null, Typeface.BOLD);
                playBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                GradientDrawable pb = new GradientDrawable();
                pb.setColor(Color.rgb(180, 30, 30));
                pb.setCornerRadius(dp(4));
                playBadge.setBackground(pb);
                row.addView(playBadge);

                row.setOnClickListener(v -> {
                    if (ParentalControlEngine.isParentalLocked(this)
                            && ParentalControlEngine.isAdultContent(chTitle, chGroup, "m3u")) {
                        showParentalPinDialog(() -> playMovie(chTitle, chUrl, "TV M3U"));
                    } else {
                        playMovie(chTitle, chUrl, "TV M3U");
                    }
                });

                row.setOnFocusChangeListener((v, f) -> {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(f ? Color.rgb(44, 56, 85) : Color.rgb(20, 24, 34));
                    g.setCornerRadius(dp(6));
                    g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(38, 45, 62));
                    row.setBackground(g);
                });

                m3uChannelsContainer.addView(row);
            }
        } else {
            // MODO CUADRÍCULA 2 COLUMNAS COMPACTA
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(2);
            m3uChannelsContainer.addView(grid);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int cardW = Math.max(dp(280), (dm.widthPixels - dp(180)) / 2);

            for (int i = 0; i < filtered.size(); i++) {
                final String[] e = filtered.get(i);
                final String chTitle = e[0];
                final String chGroup = e[1];
                final String chUrl = e[2];

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(8), dp(12), dp(8));
                card.setFocusable(true);
                card.setClickable(true);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.rgb(22, 26, 36));
                bg.setCornerRadius(dp(8));
                bg.setStroke(dp(1), Color.rgb(40, 48, 64));
                card.setBackground(bg);

                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cardW;
                lp.height = dp(56);
                lp.setMargins(0, 0, dp(10), dp(8));
                card.setLayoutParams(lp);

                TextView chIcon = text("📺", 18, Color.WHITE);
                chIcon.setPadding(0, 0, dp(10), 0);
                card.addView(chIcon);

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_VERTICAL);

                TextView t = text(chTitle, 13, Color.WHITE);
                t.setTypeface(null, Typeface.BOLD);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(t);

                TextView g = text(chGroup, 11, Color.rgb(245, 197, 66));
                g.setSingleLine(true);
                col.addView(g);

                card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

                TextView playBadge = text("▶", 11, Color.WHITE);
                playBadge.setPadding(dp(6), dp(4), dp(6), dp(4));
                GradientDrawable pb = new GradientDrawable();
                pb.setColor(Color.rgb(180, 30, 30));
                pb.setCornerRadius(dp(4));
                playBadge.setBackground(pb);
                card.addView(playBadge);

                card.setOnClickListener(v -> {
                    if (ParentalControlEngine.isParentalLocked(this)
                            && ParentalControlEngine.isAdultContent(chTitle, chGroup, "m3u")) {
                        showParentalPinDialog(() -> playMovie(chTitle, chUrl, "TV M3U"));
                    } else {
                        playMovie(chTitle, chUrl, "TV M3U");
                    }
                });

                card.setOnFocusChangeListener((v, f) -> {
                    GradientDrawable gBg = new GradientDrawable();
                    gBg.setColor(f ? Color.rgb(36, 48, 72) : Color.rgb(22, 26, 36));
                    gBg.setCornerRadius(dp(8));
                    gBg.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(40, 48, 64));
                    card.setBackground(gBg);
                });

                grid.addView(card);
            }
        }
    }

    private void showM3uList(String name, String sourceUrl, boolean allowPlay) {
        lastM3uName = name;
        lastM3uUrl = sourceUrl;
        lastM3uPlayable = true;
        page = "m3u_list";
        LinearLayout body = newPage("TV en Vivo", name,
                "Selecciona cualquier canal para reproducir en directo con audio y video fluido.");
        item("←  Volver a categorías de TV", body, this::showLiveTvStudio);
        TextView progress = text("Cargando señales...", 17, Color.LTGRAY);
        body.addView(progress);
        new Thread(() -> {
            java.util.ArrayList<String[]> entries = new java.util.ArrayList<>();
            String failure = null;
            try {
                java.net.URL endpoint = new java.net.URL(sourceUrl);
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) endpoint.openConnection();
                c.setConnectTimeout(9000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "VideoPropia-TV/0.3");
                c.setInstanceFollowRedirects(true);
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line, title = null, group = "";
                    int scanned = 0;
                    while ((line = r.readLine()) != null && scanned++ < 100000 && entries.size() < 3000) {
                        line = line.trim();
                        if (line.startsWith("#EXTINF")) {
                            int comma = line.lastIndexOf(',');
                            title = comma >= 0 ? line.substring(comma + 1).trim() : "Sin titulo";
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("group-title=\"([^\"]*)\"")
                                    .matcher(line);
                            group = m.find() ? m.group(1) : "Otros";
                        } else if (title != null && (line.startsWith("https://") || line.startsWith("http://"))) {
                            // Ocultar canales explícitos para adultos en listas M3U estándar
                            String lowerTitle = title.toLowerCase();
                            String lowerGrp = group.toLowerCase();
                            boolean isAdult = lowerGrp.contains("adult") || lowerGrp.contains("xxx")
                                    || lowerGrp.contains("porno") || lowerTitle.contains("adult")
                                    || lowerTitle.contains("xxx") || lowerTitle.contains("playboy")
                                    || lowerTitle.contains("brazzers") || lowerTitle.contains("hustler");
                            if (!isAdult) {
                                entries.add(new String[] { title, group, line });
                            }
                            title = null;
                        } else if (!line.isEmpty() && !line.startsWith("#"))
                            title = null;
                    }
                } finally {
                    c.disconnect();
                }
            } catch (Exception ex) {
                failure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            final String error = failure;
            runOnUiThread(() -> {
                if (!page.equals("m3u_list") || !lastM3uName.equals(name))
                    return;
                progress.setText(error != null ? "No se pudo consultar: " + error
                        : "Canales disponibles (" + entries.size()
                                + "). Tocá cualquier señal para reproducir en pantalla completa:");
                if (entries.isEmpty() && error == null) {
                    body.addView(text("No se hallaron canales en este listado.", 15, Color.GRAY));
                } else {
                    renderM3uChannelsGrid(body, entries);
                }
            });
        }, "m3u-catalog").start();
    }

    private String iptvSelCountry = "ALL";
    private String iptvSelCategory = "all";
    private String iptvSearchQuery = "";
    private boolean iptvCompactMode = true;
    private java.util.List<LiveTvEngine.IptvOrgChannel> iptvCatalogList = null;
    private LinearLayout iptvChannelsContainer = null;
    private LinearLayout iptvCategoriesRow = null;
    private TextView iptvSummaryCountLabel = null;
    private Button iptvToggleViewBtn = null;
    private java.util.List<Button> iptvCountryButtons = new java.util.ArrayList<>();
    private java.util.List<Button> iptvCategoryButtons = new java.util.ArrayList<>();

    private void showIptvOrgExplorer() {
        page = "iptv_explorer";
        LinearLayout body = newPage("TV Mundial", "IPTV-org Mundial",
                "Filtros combinables de País y Categoría con canales oficiales y logos en vivo.");
        item("←  Volver a menú principal de TV", body, this::showLiveTvStudio);

        iptvCatalogList = LiveTvEngine.loadIptvOrgCatalog(this);
        iptvSelCountry = "ALL";
        iptvSelCategory = "all";
        iptvSearchQuery = "";

        // 1. Barra de controles superiores (Modo de vista + Buscador)
        LinearLayout topControls = new LinearLayout(this);
        topControls.setOrientation(LinearLayout.HORIZONTAL);
        topControls.setGravity(Gravity.CENTER_VERTICAL);
        topControls.setPadding(0, dp(6), 0, dp(10));

        iptvToggleViewBtn = new Button(this);
        iptvToggleViewBtn.setText(iptvCompactMode ? "📋  Modo: Lista Compacta" : "🔲  Modo: Cuadrícula");
        iptvToggleViewBtn.setTextColor(Color.WHITE);
        iptvToggleViewBtn.setTextSize(13);
        iptvToggleViewBtn.setAllCaps(false);
        iptvToggleViewBtn.setFocusable(true);
        GradientDrawable mbBg = new GradientDrawable();
        mbBg.setColor(Color.rgb(40, 48, 68));
        mbBg.setCornerRadius(dp(8));
        iptvToggleViewBtn.setBackground(mbBg);
        iptvToggleViewBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        iptvToggleViewBtn.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(60, 75, 110) : Color.rgb(40, 48, 68));
            g.setCornerRadius(dp(8));
            if (f)
                g.setStroke(dp(2), Color.rgb(245, 197, 66));
            iptvToggleViewBtn.setBackground(g);
        });
        iptvToggleViewBtn.setOnClickListener(v -> {
            iptvCompactMode = !iptvCompactMode;
            iptvToggleViewBtn.setText(iptvCompactMode ? "📋  Modo: Lista Compacta" : "🔲  Modo: Cuadrícula");
            renderIptvFilteredChannels();
        });
        topControls.addView(iptvToggleViewBtn);

        final android.widget.EditText searchBox = new android.widget.EditText(this);
        searchBox.setHint("🔍  Buscar canal por nombre...");
        searchBox.setHintTextColor(Color.rgb(150, 160, 180));
        searchBox.setTextColor(Color.WHITE);
        searchBox.setTextSize(13);
        searchBox.setSingleLine(true);
        searchBox.setFocusable(true);
        GradientDrawable sbBg = new GradientDrawable();
        sbBg.setColor(Color.rgb(24, 28, 40));
        sbBg.setCornerRadius(dp(8));
        sbBg.setStroke(dp(1), Color.rgb(60, 70, 95));
        searchBox.setBackground(sbBg);
        searchBox.setPadding(dp(14), dp(8), dp(14), dp(8));
        searchBox.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(32, 38, 55) : Color.rgb(24, 28, 40));
            g.setCornerRadius(dp(8));
            g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(60, 70, 95));
            searchBox.setBackground(g);
        });
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                iptvSearchQuery = s != null ? s.toString().trim() : "";
                renderIptvFilteredChannels();
            }

            public void afterTextChanged(android.text.Editable s) {
            }
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(dp(320), dp(40));
        sbLp.setMargins(dp(14), 0, 0, 0);
        topControls.addView(searchBox, sbLp);
        body.addView(topControls);

        // 2. Fila 1 de Países (HorizontalScrollView)
        TextView countryLabel = text("🌍 SELECCIONA PAÍS:", 12, Color.rgb(180, 190, 220));
        countryLabel.setTypeface(null, Typeface.BOLD);
        countryLabel.setPadding(0, dp(4), 0, dp(4));
        body.addView(countryLabel);

        HorizontalScrollView countryScroll = new HorizontalScrollView(this);
        countryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout countryRow = new LinearLayout(this);
        countryRow.setOrientation(LinearLayout.HORIZONTAL);
        countryRow.setPadding(0, dp(2), 0, dp(8));

        // Contar canales por país
        java.util.LinkedHashMap<String, Integer> countryCounts = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> countryNames = new java.util.HashMap<>();
        countryCounts.put("ALL", iptvCatalogList.size());
        countryNames.put("ALL", "Todos los países");

        for (LiveTvEngine.IptvOrgChannel ch : iptvCatalogList) {
            String cCode = ch.country.toUpperCase();
            if (cCode.isEmpty())
                continue;
            countryCounts.put(cCode, countryCounts.containsKey(cCode) ? countryCounts.get(cCode) + 1 : 1);
            if (!countryNames.containsKey(cCode)) {
                countryNames.put(cCode, ch.countryName);
            }
        }

        iptvCountryButtons.clear();
        for (java.util.Map.Entry<String, Integer> cEntry : countryCounts.entrySet()) {
            final String cCode = cEntry.getKey();
            final int cCnt = cEntry.getValue();
            final String cName = countryNames.get(cCode);

            String flag = "AR".equals(cCode) ? "🇦🇷 "
                    : "US".equals(cCode) ? "🇺🇸 "
                            : "ES".equals(cCode) ? "🇪🇸 "
                                    : "MX".equals(cCode) ? "🇲🇽 "
                                            : "CL".equals(cCode) ? "🇨🇱 "
                                                    : "CO".equals(cCode) ? "🇨🇴 "
                                                            : "PE".equals(cCode) ? "🇵🇪 "
                                                                    : "UY".equals(cCode) ? "🇺🇾 "
                                                                            : "ALL".equals(cCode) ? "🌍 " : "🌐 ";
            String label = flag + cName + " (" + cCnt + ")";

            Button cBtn = new Button(this);
            cBtn.setText(label);
            cBtn.setTextColor(cCode.equals(iptvSelCountry) ? Color.WHITE : Color.rgb(180, 190, 210));
            cBtn.setTextSize(12);
            cBtn.setAllCaps(false);
            cBtn.setFocusable(true);

            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(cCode.equals(iptvSelCountry) ? Color.rgb(30, 120, 210) : Color.rgb(26, 32, 46));
            cBg.setCornerRadius(dp(16));
            cBtn.setBackground(cBg);
            cBtn.setPadding(dp(14), dp(4), dp(14), dp(4));

            cBtn.setOnFocusChangeListener((v, f) -> {
                boolean isSel = cCode.equals(iptvSelCountry);
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(45, 145, 240) : (isSel ? Color.rgb(30, 120, 210) : Color.rgb(26, 32, 46)));
                g.setCornerRadius(dp(16));
                if (f)
                    g.setStroke(dp(2), Color.WHITE);
                cBtn.setBackground(g);
                cBtn.setTextColor(Color.WHITE);
            });

            cBtn.setOnClickListener(v -> {
                iptvSelCountry = cCode;
                for (Button b : iptvCountryButtons) {
                    boolean isSel = b == cBtn;
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(isSel ? Color.rgb(30, 120, 210) : Color.rgb(26, 32, 46));
                    g.setCornerRadius(dp(16));
                    b.setBackground(g);
                    b.setTextColor(isSel ? Color.WHITE : Color.rgb(180, 190, 210));
                }
                rebuildIptvCategoryRow();
                renderIptvFilteredChannels();
            });

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(34));
            clp.setMargins(0, 0, dp(8), 0);
            countryRow.addView(cBtn, clp);
            iptvCountryButtons.add(cBtn);
        }
        countryScroll.addView(countryRow);
        body.addView(countryScroll);

        // 3. Fila 2 de Categorías dinámicas (HorizontalScrollView)
        TextView catLabel = text("🏷️ CATEGORÍA / GÉNERO (SOLO LAS DISPONIBLES PARA EL PAÍS SELECCIONADO):", 12,
                Color.rgb(180, 190, 220));
        catLabel.setTypeface(null, Typeface.BOLD);
        catLabel.setPadding(0, dp(4), 0, dp(4));
        body.addView(catLabel);

        HorizontalScrollView catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        iptvCategoriesRow = new LinearLayout(this);
        iptvCategoriesRow.setOrientation(LinearLayout.HORIZONTAL);
        iptvCategoriesRow.setPadding(0, dp(2), 0, dp(10));
        catScroll.addView(iptvCategoriesRow);
        body.addView(catScroll);

        // 4. Contador de resultados
        iptvSummaryCountLabel = text("", 13, Color.rgb(245, 197, 66));
        iptvSummaryCountLabel.setPadding(0, dp(4), 0, dp(8));
        body.addView(iptvSummaryCountLabel);

        // 5. Contenedor de canales
        iptvChannelsContainer = new LinearLayout(this);
        iptvChannelsContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(iptvChannelsContainer);

        rebuildIptvCategoryRow();
        renderIptvFilteredChannels();
    }

    private void rebuildIptvCategoryRow() {
        if (iptvCategoriesRow == null || iptvCatalogList == null)
            return;
        iptvCategoriesRow.removeAllViews();
        iptvCategoryButtons.clear();

        int totalCountryChannels = 0;
        java.util.LinkedHashMap<String, Integer> catCounts = new java.util.LinkedHashMap<>();
        for (LiveTvEngine.IptvOrgChannel ch : iptvCatalogList) {
            if (!"ALL".equals(iptvSelCountry) && !ch.country.equalsIgnoreCase(iptvSelCountry)) {
                continue;
            }
            totalCountryChannels++;
            for (String cat : ch.categories) {
                catCounts.put(cat, catCounts.containsKey(cat) ? catCounts.get(cat) + 1 : 1);
            }
        }

        if (!"all".equals(iptvSelCategory) && !catCounts.containsKey(iptvSelCategory)) {
            iptvSelCategory = "all";
        }

        // 1. Botón 'Todas las categorías'
        Button allBtn = createCategoryChip("all", "⭐ Todas las categorías (" + totalCountryChannels + ")");
        iptvCategoriesRow.addView(allBtn);
        iptvCategoryButtons.add(allBtn);

        // 2. Botones de las categorías que sí tienen canales
        for (java.util.Map.Entry<String, String> catEntry : LiveTvEngine.CATEGORY_LABELS.entrySet()) {
            String catId = catEntry.getKey();
            String catName = catEntry.getValue();

            if (catCounts.containsKey(catId) && catCounts.get(catId) > 0) {
                int count = catCounts.get(catId);
                Button chip = createCategoryChip(catId, catName + " (" + count + ")");
                iptvCategoriesRow.addView(chip);
                iptvCategoryButtons.add(chip);
            }
        }
    }

    private Button createCategoryChip(String catId, String text) {
        Button chip = new Button(this);
        chip.setText(text);
        chip.setTextColor(catId.equals(iptvSelCategory) ? Color.WHITE : Color.rgb(180, 190, 210));
        chip.setTextSize(12);
        chip.setAllCaps(false);
        chip.setFocusable(true);

        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(catId.equals(iptvSelCategory) ? Color.rgb(110, 60, 200) : Color.rgb(28, 34, 48));
        chipBg.setCornerRadius(dp(16));
        chip.setBackground(chipBg);
        chip.setPadding(dp(14), dp(4), dp(14), dp(4));

        chip.setOnFocusChangeListener((v, f) -> {
            boolean isSel = catId.equals(iptvSelCategory);
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(130, 80, 225) : (isSel ? Color.rgb(110, 60, 200) : Color.rgb(28, 34, 48)));
            g.setCornerRadius(dp(16));
            if (f)
                g.setStroke(dp(2), Color.WHITE);
            chip.setBackground(g);
            chip.setTextColor(Color.WHITE);
        });

        chip.setOnClickListener(v -> {
            iptvSelCategory = catId;
            for (Button cb : iptvCategoryButtons) {
                boolean isSel = cb == chip;
                GradientDrawable g = new GradientDrawable();
                g.setColor(isSel ? Color.rgb(110, 60, 200) : Color.rgb(28, 34, 48));
                g.setCornerRadius(dp(16));
                cb.setBackground(g);
                cb.setTextColor(isSel ? Color.WHITE : Color.rgb(180, 190, 210));
            }
            renderIptvFilteredChannels();
        });

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(34));
        clp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(clp);
        return chip;
    }

    private void renderIptvFilteredChannels() {
        if (iptvChannelsContainer == null || iptvCatalogList == null)
            return;
        iptvChannelsContainer.removeAllViews();

        java.util.List<LiveTvEngine.IptvOrgChannel> filtered = new java.util.ArrayList<>();
        String q = (iptvSearchQuery != null) ? iptvSearchQuery.toLowerCase() : "";

        for (LiveTvEngine.IptvOrgChannel ch : iptvCatalogList) {
            // Filtro por País
            if (!"ALL".equals(iptvSelCountry) && !ch.country.equalsIgnoreCase(iptvSelCountry)) {
                continue;
            }
            // Filtro por Categoría
            if (!"all".equals(iptvSelCategory) && !ch.categories.contains(iptvSelCategory)) {
                continue;
            }
            // Filtro por Búsqueda de Texto
            if (!q.isEmpty() && !ch.name.toLowerCase().contains(q) && !ch.countryName.toLowerCase().contains(q)) {
                continue;
            }
            // Cada canal aparece una sola vez en la lista resultante
            filtered.add(ch);
        }

        String catDisplay = "all".equals(iptvSelCategory) ? "Todas las categorías"
                : LiveTvEngine.CATEGORY_LABELS.get(iptvSelCategory);
        if (catDisplay == null)
            catDisplay = iptvSelCategory;
        String countryDisplay = "ALL".equals(iptvSelCountry) ? "Todos los países" : iptvSelCountry;

        if (iptvSummaryCountLabel != null) {
            iptvSummaryCountLabel.setText("Mostrando " + filtered.size() + " canales · País: " + countryDisplay
                    + " · Categoría: " + catDisplay
                    + (!q.isEmpty() ? " · Búsqueda: '" + q + "'" : ""));
        }

        if (filtered.isEmpty()) {
            TextView empty = text("No se encontraron canales para los filtros seleccionados.", 14, Color.GRAY);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            iptvChannelsContainer.addView(empty);
            return;
        }

        if (iptvCompactMode) {
            // MODO LISTA COMPACTA CON ENLACE SECUENCIAL DE FOCO (CERO SALTOS)
            final int count = filtered.size();
            final int[] viewIds = new int[count];
            for (int i = 0; i < count; i++) {
                viewIds[i] = android.view.View.generateViewId();
            }

            for (int i = 0; i < count; i++) {
                final LiveTvEngine.IptvOrgChannel ch = filtered.get(i);
                final int chNum = i + 1;

                LinearLayout row = new LinearLayout(this);
                row.setId(viewIds[i]);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(6), dp(12), dp(6));
                row.setFocusable(true);
                row.setClickable(true);

                if (i > 0)
                    row.setNextFocusUpId(viewIds[i - 1]);
                if (i < count - 1)
                    row.setNextFocusDownId(viewIds[i + 1]);

                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(Color.rgb(20, 24, 34));
                rowBg.setCornerRadius(dp(6));
                rowBg.setStroke(dp(1), Color.rgb(38, 45, 62));
                row.setBackground(rowBg);

                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(44));
                rlp.setMargins(0, 0, 0, dp(4));
                row.setLayoutParams(rlp);

                // Número
                TextView num = text("#" + chNum, 12, Color.rgb(150, 160, 190));
                num.setTypeface(null, Typeface.BOLD);
                num.setPadding(0, 0, dp(10), 0);
                row.addView(num);

                // Logo o Icono
                if (ch.logo != null && !ch.logo.isEmpty()) {
                    ImageView logoView = new ImageView(this);
                    logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(32), dp(24));
                    llp.setMargins(0, 0, dp(10), 0);
                    row.addView(logoView, llp);
                    CatalogBrowser.loadPoster(this, logoView, ch.logo);
                } else {
                    TextView tvIcon = text("📺", 14, Color.WHITE);
                    tvIcon.setPadding(0, 0, dp(10), 0);
                    row.addView(tvIcon);
                }

                // Nombre
                TextView titleView = text(ch.name, 13, Color.WHITE);
                titleView.setTypeface(null, Typeface.BOLD);
                titleView.setSingleLine(true);
                titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));

                // Badge de País
                TextView countryBadge = text(ch.country, 11, Color.rgb(150, 200, 255));
                countryBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
                row.addView(countryBadge);

                // Badge de Categoría
                String primaryCat = !ch.categories.isEmpty() ? ch.categories.get(0) : "uncategorized";
                String primaryCatLabel = LiveTvEngine.CATEGORY_LABELS.get(primaryCat);
                if (primaryCatLabel == null)
                    primaryCatLabel = primaryCat;
                TextView catBadge = text(primaryCatLabel, 11, Color.rgb(245, 197, 66));
                catBadge.setSingleLine(true);
                catBadge.setPadding(dp(6), dp(2), dp(8), dp(2));
                row.addView(catBadge);

                // Badge de alternativas
                if (ch.streamUrls.size() > 1) {
                    TextView altBadge = text("[" + ch.streamUrls.size() + " señales]", 10, Color.rgb(140, 220, 170));
                    altBadge.setPadding(0, 0, dp(8), 0);
                    row.addView(altBadge);
                }

                // Botón VER
                TextView playBadge = text("▶ EN VIVO", 10, Color.WHITE);
                playBadge.setTypeface(null, Typeface.BOLD);
                playBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
                GradientDrawable pb = new GradientDrawable();
                pb.setColor(Color.rgb(180, 30, 30));
                pb.setCornerRadius(dp(4));
                playBadge.setBackground(pb);
                row.addView(playBadge);

                row.setOnClickListener(v -> playIptvOrgChannel(ch));
                row.setOnFocusChangeListener((v, f) -> {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(f ? Color.rgb(44, 56, 85) : Color.rgb(20, 24, 34));
                    g.setCornerRadius(dp(6));
                    g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(38, 45, 62));
                    row.setBackground(g);
                });

                iptvChannelsContainer.addView(row);
            }
        } else {
            // MODO CUADRÍCULA 2 COLUMNAS
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(2);
            iptvChannelsContainer.addView(grid);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int cardW = Math.max(dp(280), (dm.widthPixels - dp(180)) / 2);

            for (LiveTvEngine.IptvOrgChannel ch : filtered) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(12), dp(8), dp(12), dp(8));
                card.setFocusable(true);
                card.setClickable(true);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.rgb(22, 26, 36));
                bg.setCornerRadius(dp(8));
                bg.setStroke(dp(1), Color.rgb(40, 48, 64));
                card.setBackground(bg);

                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = cardW;
                lp.height = dp(56);
                lp.setMargins(0, 0, dp(10), dp(8));
                card.setLayoutParams(lp);

                if (ch.logo != null && !ch.logo.isEmpty()) {
                    ImageView logoView = new ImageView(this);
                    logoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(36), dp(28));
                    llp.setMargins(0, 0, dp(10), 0);
                    card.addView(logoView, llp);
                    CatalogBrowser.loadPoster(this, logoView, ch.logo);
                } else {
                    TextView chIcon = text("📺", 18, Color.WHITE);
                    chIcon.setPadding(0, 0, dp(10), 0);
                    card.addView(chIcon);
                }

                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_VERTICAL);

                TextView t = text(ch.name, 13, Color.WHITE);
                t.setTypeface(null, Typeface.BOLD);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(t);

                String primaryCat = !ch.categories.isEmpty() ? ch.categories.get(0) : "uncategorized";
                String primaryCatLabel = LiveTvEngine.CATEGORY_LABELS.get(primaryCat);
                if (primaryCatLabel == null)
                    primaryCatLabel = primaryCat;
                TextView g = text(
                        ch.country + " · " + primaryCatLabel
                                + (ch.streamUrls.size() > 1 ? " (" + ch.streamUrls.size() + " señ.)" : ""),
                        11, Color.rgb(245, 197, 66));
                g.setSingleLine(true);
                col.addView(g);

                card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

                TextView playBadge = text("▶", 11, Color.WHITE);
                playBadge.setPadding(dp(6), dp(4), dp(6), dp(4));
                GradientDrawable pb = new GradientDrawable();
                pb.setColor(Color.rgb(180, 30, 30));
                pb.setCornerRadius(dp(4));
                playBadge.setBackground(pb);
                card.addView(playBadge);

                card.setOnClickListener(v -> playIptvOrgChannel(ch));
                card.setOnFocusChangeListener((v, f) -> {
                    GradientDrawable gBg = new GradientDrawable();
                    gBg.setColor(f ? Color.rgb(36, 48, 72) : Color.rgb(22, 26, 36));
                    gBg.setCornerRadius(dp(8));
                    gBg.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(40, 48, 64));
                    card.setBackground(gBg);
                });

                grid.addView(card);
            }
        }
    }

    private void playIptvOrgChannel(LiveTvEngine.IptvOrgChannel ch) {
        if (ch == null || ch.streamUrls.isEmpty())
            return;

        if (ch.streamUrls.size() == 1) {
            playMovie(ch.name, ch.getPrimaryStream(), "IPTV-org " + ch.country);
            return;
        }

        String[] options = new String[ch.streamUrls.size()];
        for (int i = 0; i < ch.streamUrls.size(); i++) {
            options[i] = (i == 0 ? "⭐ Señal Principal (" : "📡 Señal Alternativa " + (i + 1) + " (") + (i + 1) + " de "
                    + ch.streamUrls.size() + ")";
        }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("📺 " + ch.name + " (" + ch.country + ")");
        b.setItems(options, (dialog, which) -> {
            if (which >= 0 && which < ch.streamUrls.size()) {
                playMovie(ch.name, ch.streamUrls.get(which), "IPTV-org " + ch.country);
            }
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private String activeProvider = "CineHax";
    private String activeTab = "all";

    private static final class PosterItem {
        final String title, tag, posterUrl, providerName, queryHint;
        final int year;
        final boolean isSeries;
        final CatalogData.Entry rawEntry;

        PosterItem(String t, int y, boolean series, String poster, String prov, String q, CatalogData.Entry entry) {
            title = t;
            year = y;
            isSeries = series;
            tag = series ? "HD   TV" : ("HD   " + (y > 0 ? y : 2026));
            posterUrl = poster;
            providerName = prov;
            queryHint = q;
            rawEntry = entry;
        }
    }

    private void allSources() {
        showProviderSelector();
    }

    private android.view.View buildProviderLogoBox(String style, String shortText, int accentColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        GradientDrawable outer = new GradientDrawable();
        outer.setColor(Color.BLACK);
        outer.setCornerRadius(6);
        outer.setStroke(2, Color.rgb(38, 42, 52));
        box.setBackground(outer);
        box.setPadding(18, 18, 18, 18);

        if ("tv".equals(style)) {
            LinearLayout whiteCard = new LinearLayout(this);
            whiteCard.setOrientation(LinearLayout.VERTICAL);
            whiteCard.setGravity(Gravity.CENTER);
            GradientDrawable wbg = new GradientDrawable();
            wbg.setColor(Color.WHITE);
            wbg.setCornerRadius(4);
            whiteCard.setBackground(wbg);

            TextView antenna = text("╲╱", 16, Color.rgb(12, 114, 214));
            antenna.setGravity(Gravity.CENTER);
            antenna.setTypeface(null, Typeface.BOLD);
            whiteCard.addView(antenna);

            TextView tvBox = text("TV", 28, Color.rgb(14, 90, 170));
            tvBox.setTypeface(null, Typeface.BOLD);
            tvBox.setGravity(Gravity.CENTER);
            GradientDrawable tvBorder = new GradientDrawable();
            tvBorder.setColor(Color.WHITE);
            tvBorder.setCornerRadius(14);
            tvBorder.setStroke(5, Color.rgb(225, 38, 38));
            tvBox.setBackground(tvBorder);
            tvBox.setPadding(22, 8, 22, 8);
            whiteCard.addView(tvBox);

            box.addView(whiteCard, new LinearLayout.LayoutParams(-1, -1));
        } else if ("netflix_bar".equals(style)) {
            LinearLayout banner = new LinearLayout(this);
            banner.setGravity(Gravity.CENTER);
            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(Color.rgb(238, 240, 244));
            bbg.setCornerRadius(4);
            banner.setBackground(bbg);
            TextView t = text(shortText, 19, Color.rgb(218, 30, 38));
            t.setTypeface(null, Typeface.BOLD);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER);
            banner.addView(t);
            box.addView(banner, new LinearLayout.LayoutParams(-1, 78));
        } else if ("pelisflix".equals(style)) {
            TextView pIcon = text("P", 54, Color.WHITE);
            pIcon.setTypeface(null, Typeface.BOLD);
            pIcon.setGravity(Gravity.CENTER);
            GradientDrawable pBg = new GradientDrawable();
            pBg.setColor(Color.rgb(20, 168, 232));
            pBg.setCornerRadius(34);
            pBg.setStroke(5, Color.rgb(12, 115, 175));
            pIcon.setBackground(pBg);
            box.addView(pIcon, new LinearLayout.LayoutParams(140, 140));
        } else if ("poseidon".equals(style)) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            TextView p1 = text("POSEIDONHD", 13, Color.WHITE);
            p1.setSingleLine(true);
            p1.setTypeface(null, Typeface.BOLD_ITALIC);
            row.addView(p1);
            TextView p2 = text(" 2 ", 14, Color.WHITE);
            p2.setSingleLine(true);
            p2.setTypeface(null, Typeface.BOLD);
            GradientDrawable g2 = new GradientDrawable();
            g2.setColor(Color.rgb(42, 168, 76));
            g2.setCornerRadius(6);
            p2.setBackground(g2);
            row.addView(p2);
            box.addView(row);
        } else if ("cinecalidad".equals(style)) {
            LinearLayout badge = new LinearLayout(this);
            badge.setOrientation(LinearLayout.HORIZONTAL);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.rgb(18, 190, 170));
            cBg.setCornerRadius(60);
            cBg.setStroke(10, Color.rgb(38, 44, 52));
            badge.setBackground(cBg);
            TextView cText = text("cine\ncalidad", 16, Color.WHITE);
            cText.setTypeface(null, Typeface.BOLD);
            cText.setGravity(Gravity.CENTER);
            badge.addView(cText);
            box.addView(badge, new LinearLayout.LayoutParams(144, 144));
        } else if ("canela".equals(style)) {
            TextView cIcon = text("C\nTV", 32, Color.WHITE);
            cIcon.setTypeface(null, Typeface.BOLD);
            cIcon.setGravity(Gravity.CENTER);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.rgb(214, 34, 34));
            cBg.setCornerRadius(75);
            cIcon.setBackground(cBg);
            box.addView(cIcon, new LinearLayout.LayoutParams(148, 148));
        } else if ("jkanime".equals(style)) {
            TextView jk = text("jK", 56, Color.rgb(250, 178, 22));
            jk.setTypeface(null, Typeface.BOLD_ITALIC);
            jk.setGravity(Gravity.CENTER);
            box.addView(jk);
        } else if ("reel".equals(style)) {
            LinearLayout whiteCard = new LinearLayout(this);
            whiteCard.setOrientation(LinearLayout.VERTICAL);
            whiteCard.setGravity(Gravity.CENTER);
            GradientDrawable wbg = new GradientDrawable();
            wbg.setColor(Color.WHITE);
            wbg.setCornerRadius(4);
            whiteCard.setBackground(wbg);
            TextView reel = text("🎞️\n" + shortText, 24, Color.BLACK);
            reel.setTypeface(null, Typeface.BOLD);
            reel.setGravity(Gravity.CENTER);
            whiteCard.addView(reel);
            box.addView(whiteCard, new LinearLayout.LayoutParams(-1, -1));
        } else {
            TextView icon = text(shortText, 48, accentColor);
            icon.setTypeface(null, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            box.addView(icon);
        }
        return box;
    }

    private Button addProviderTile(GridLayout grid, String label, String style, String shortText, int accent,
            Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(12, 10, 12, 14);
        cell.setFocusable(true);
        cell.setClickable(true);

        android.view.View logoBox = buildProviderLogoBox(style, shortText, accent);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(210, 190);
        cell.addView(logoBox, boxLp);

        TextView caption = text(label, 15, Color.rgb(230, 234, 240));
        caption.setGravity(Gravity.CENTER);
        caption.setMaxLines(3);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(230, -2);
        capLp.setMargins(0, 10, 0, 0);
        cell.addView(caption, capLp);

        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 320;
        gp.height = -2;
        gp.setMargins(22, 12, 22, 18);
        grid.addView(cell, gp);

        cell.setOnClickListener(v -> action.run());
        cell.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                action.run();
                return true;
            }
            return false;
        });
        cell.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable outer = new GradientDrawable();
            outer.setColor(Color.BLACK);
            outer.setCornerRadius(8);
            outer.setStroke(focused ? 4 : 2, focused ? Color.WHITE : Color.rgb(38, 42, 52));
            logoBox.setBackground(outer);
            caption.setTextColor(focused ? Color.WHITE : Color.rgb(215, 220, 228));
            caption.setTypeface(null, focused ? Typeface.BOLD : Typeface.NORMAL);
            cell.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(140).start();
        });

        Button dummy = new Button(this);
        return dummy;
    }

    private void showProviderSelector() {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        if (resolutionTask != null)
            resolutionTask.cancel(true);
        ++providerPlayRequest;
        providerLoading = false;
        page = "sources";
        channel = "";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(20, 20, 22));
        root.setPadding(40, 24, 40, 20);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(0, 0, 0, 18);
        root.addView(header, hlp);

        LinearLayout leftBrand = new LinearLayout(this);
        leftBrand.setOrientation(LinearLayout.HORIZONTAL);
        leftBrand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView topLogo = new ImageView(this);
        topLogo.setImageResource(ar.appvideo.player.R.drawable.leo_brand);
        topLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        leftBrand.addView(topLogo, new LinearLayout.LayoutParams(62, 62));
        header.addView(leftBrand, new LinearLayout.LayoutParams(220, -2));

        TextView heading = text("SELECCIONAR PROVEEDOR", 26, Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        TextView langFilter = text("🌐   SOLO ESPAÑOL / LATINO   ▾", 14, Color.WHITE);
        langFilter.setPadding(16, 10, 16, 10);
        header.addView(langFilter);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        pageScroll = scroll;
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(wrapper, new LinearLayout.LayoutParams(-1, -2));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        wrapper.addView(grid, new LinearLayout.LayoutParams(-2, -2));

        addProviderTile(grid, "IPTV-ORG\nARGENTINA (186 CH)", "tv", "🇦🇷", Color.rgb(40, 160, 220),
                () -> {
                    activeTvCat = "iptv_org_ar";
                    showLiveTvStudio();
                });

        addProviderTile(grid, "CANALES DE\nTV Y CINE\nRECOMENDADO", "tv", "TV", Color.WHITE,
                this::showLiveTvStudio);

        addProviderTile(grid, "TDTCHANNELS\nTV (574 CANALES)", "tv", "🇪🇸", Color.rgb(235, 180, 50),
                () -> showM3uList("TDTChannels TV", "https://www.tdtchannels.com/lists/tv.m3u8", true));

        addProviderTile(grid, "FREE-TV IPTV\n(2.200 CANALES)", "tv", "🌐", Color.rgb(60, 180, 120),
                () -> showM3uList("Free-TV IPTV Global",
                        "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", true));

        addProviderTile(grid, "NOVELAS TURCAS\nY ASIÁTICAS", "icon", "🇹🇷", Color.rgb(220, 50, 60),
                () -> {
                    activeProvider = "CineHax";
                    activeTab = "series";
                    home();
                });

        addProviderTile(grid, "INFANTILES\nAUDIO LATINO", "icon", "🧸", Color.rgb(255, 180, 50),
                () -> {
                    activeProvider = "CineHax";
                    activeTab = "kids";
                    home();
                });

        addProviderTile(grid, "PELICULAS Y\nSERIES (CINEHAX)", "netflix_bar", "LATINO HD", Color.rgb(220, 30, 40),
                () -> {
                    activeProvider = "CineHax";
                    activeTab = "all";
                    home();
                });

        addProviderTile(grid, "SOLOLATINO\nAUDIO LATINO", "reel", "SL", Color.rgb(245, 197, 66),
                () -> {
                    activeProvider = "SoloLatino";
                    activeTab = "all";
                    home();
                });

        addProviderTile(grid, "PelisflixHD\n(Seriesflix)", "pelisflix", "P", Color.WHITE,
                () -> openChannel("Seriesflix"));

        addProviderTile(grid, "CineCalidad", "cinecalidad", "CC", Color.WHITE,
                () -> openChannel("CineCalidad"));

        addProviderTile(grid, "JKAnime /\nLatAnime", "jkanime", "jK", Color.rgb(250, 178, 22),
                () -> {
                    activeProvider = "LatAnime";
                    activeTab = "anime";
                    home();
                });

        addProviderTile(grid, "CINE ABIERTO\nBLENDER HD", "reel", "OPEN", Color.WHITE,
                () -> openChannel("Blender Open Movies"));

        addProviderTile(grid, "INTERNET ARCHIVE\nCLÁSICOS", "reel", "CC BY", Color.WHITE,
                () -> openChannel("Internet Archive"));

        addProviderTile(grid, "ESTADO DE\nLAS FUENTES", "icon", "📊", Color.rgb(212, 175, 55),
                () -> openChannel("Estado"));

        // Fuentes verificadas con catálogo activo de channels.json (excluyendo sin
        // resultados e inactivas)
        java.util.Set<String> alreadyShown = new java.util.HashSet<>(java.util.Arrays.asList(
                "CineCalidad", "CineHax", "Seriesflix", "LatAnime", "JKAnime", "SoloLatino",
                "LaMovie", "Canela TV", "DoramasFlix", "Pelisplus", "DeTodo Peliculas", "VerAni.me"));

        for (int i = 0; i < channels.length(); i++) {
            JSONObject entry = channels.optJSONObject(i);
            if (entry == null)
                continue;
            final String name = entry.optString("name", "Fuente");
            if (!entry.optBoolean("enabled", true) || isDeadProvider(name) || alreadyShown.contains(name))
                continue;
            alreadyShown.add(name);
            String shortTag = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "TV";
            String style = tvAnime(name) ? "jkanime" : (tvSeries(name) ? "pelisflix" : "reel");
            addProviderTile(grid, name + "\n(" + tvBadge(name).toUpperCase() + ")", style, shortTag,
                    Color.rgb(212, 175, 55),
                    () -> openChannel(name));
        }

        LinearLayout updateBanner = new LinearLayout(this);
        updateBanner.setOrientation(LinearLayout.HORIZONTAL);
        updateBanner.setGravity(Gravity.CENTER);
        updateBanner.setPadding(dp(24), dp(10), dp(24), dp(10));
        updateBanner.setFocusable(true);
        updateBanner.setClickable(true);
        GradientDrawable ub = new GradientDrawable();
        ub.setColor(Color.rgb(32, 38, 54));
        ub.setCornerRadius(dp(12));
        ub.setStroke(dp(1), Color.rgb(129, 107, 245));
        updateBanner.setBackground(ub);
        LinearLayout.LayoutParams ubLp = new LinearLayout.LayoutParams(-2, dp(48));
        ubLp.setMargins(0, dp(24), 0, dp(18));
        wrapper.addView(updateBanner, ubLp);

        TextView ubText = text("🚀  Buscar actualizaciones en GitHub  ·  v" + AppUpdater.getCurrentVersionName(this),
                14, Color.WHITE);
        ubText.setTypeface(null, Typeface.BOLD);
        updateBanner.addView(ubText);

        updateBanner.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(129, 107, 245) : Color.rgb(32, 38, 54));
            g.setCornerRadius(dp(12));
            if (f)
                g.setStroke(dp(2), Color.WHITE);
            updateBanner.setBackground(g);
            updateBanner.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });

        updateBanner.setOnClickListener(v -> {
            android.widget.Toast
                    .makeText(this, "Comprobando actualizaciones en GitHub...", android.widget.Toast.LENGTH_SHORT)
                    .show();
            AppUpdater.check(this, new AppUpdater.CheckCallback() {
                @Override
                public void onUpdateAvailable(AppUpdater.UpdateInfo info) {
                    AppUpdater.showUpdateDialog(MainActivity.this, info);
                }

                @Override
                public void onNoUpdate() {
                    runOnUiThread(
                            () -> android.widget.Toast.makeText(MainActivity.this,
                                    "¡Ya tenés la versión más reciente (v"
                                            + AppUpdater.getCurrentVersionName(MainActivity.this) + ")!",
                                    android.widget.Toast.LENGTH_LONG).show());
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(MainActivity.this);
                        b.setTitle("🚀 Leo TV · v" + AppUpdater.getCurrentVersionName(MainActivity.this));
                        b.setMessage("¡Tu versión instalada (v"
                                + AppUpdater.getCurrentVersionName(MainActivity.this)
                                + ") está completamente al día!\n\n"
                                + "Las nuevas versiones y mejoras se publican a través de GitHub Releases y actualización automática.");
                        b.setPositiveButton("Aceptar", null);
                        b.show();
                    });
                }
            });
        });

        setContentView(root);
        grid.post(() -> {
            if (grid.getChildCount() > 5)
                grid.getChildAt(5).requestFocus();
            else if (grid.getChildCount() > 0)
                grid.getChildAt(0).requestFocus();
        });
    }

    private GradientDrawable tvShape(int fill, int border, int corner) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(corner);
        d.setStroke(2, border);
        return d;
    }

    private Button tvAction(String label, String detail, Runnable action, int width, int height) {
        Button b = new Button(this);
        String textValue = label + (detail.isEmpty() ? "" : "\n" + detail);
        android.text.SpannableString styled = new android.text.SpannableString(textValue);
        if (!detail.isEmpty()) {
            int start = label.length() + 1;
            styled.setSpan(new android.text.style.RelativeSizeSpan(0.78f),
                    start, textValue.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(177, 194, 210)),
                    start, textValue.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        styled.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
                0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(styled);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        b.setPadding(17, 9, 14, 9);
        b.setTextSize(15);
        b.setMaxLines(3);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        b.setBackground(tvShape(Color.rgb(31, 41, 64), Color.rgb(64, 78, 110), 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.setMargins(0, 0, 15, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, selected) -> {
            b.setBackground(tvShape(selected ? Color.rgb(68, 88, 155) : Color.rgb(31, 41, 64),
                    selected ? Color.rgb(212, 175, 55) : Color.rgb(64, 78, 110), 20));
            b.animate().scaleX(selected ? 1.08f : 1f)
                    .scaleY(selected ? 1.08f : 1f).setDuration(150).start();
            b.setElevation(selected ? 12f : 0f);
            if (selected && heroTitle != null)
                updateHero(label);
        });
        return b;
    }

    private void tvNav(LinearLayout side, String label, Runnable action) {
        Button b = tvAction(label, "", action, -1, 60);
        b.setTextSize(14);
        b.setPadding(14, 5, 8, 5);
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        side.addView(b);
    }

    private boolean tvSeries(String name) {
        return name.equals("SeriesMetro") || name.equals("Seriesflix") || name.equals("DoramasFlix")
                || name.equals("AresHD") || name.equals("DeTodo Peliculas") || name.equals("CineHax+");
    }

    private boolean tvAnime(String name) {
        return name.equals("TioAnime") || name.equals("LatAnime") || name.equals("AnimeAV1")
                || name.equals("JKAnime") || name.equals("MonosChinos")
                || name.equals("MundoDonghua") || name.equals("VerAni.me")
                || name.equals("VerAnimes") || name.equals("BeatZ Anime");
    }

    private String tvBadge(String name) {
        if (name.equals("Demo"))
            return "Muestra de video";
        if (name.equals("Blender Open Movies") || name.equals("Internet Archive"))
            return "Videos autorizados";
        for (int i = 0; i < channels.length(); i++) {
            JSONObject entry = channels.optJSONObject(i);
            if (entry == null || !name.equals(entry.optString("name")))
                continue;
            String state = entry.optString("status");
            if ("previously_playback_tested".equals(state))
                return "Reproducción probada";
            if ("catalog_and_episodes_tested_playback_pending".equals(state))
                return "Anime · video pendiente";
            if (entry.optBoolean("enabled", false))
                return "Adaptador en revisión";
            return "No integrado";
        }
        return "Explorar";
    }

    private void tvRow(LinearLayout body, String heading, String subtitle, String[] names) {
        TextView title = text(heading, 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(0, 25, 0, 3);
        body.addView(title, tlp);
        TextView hint = text(subtitle, 13, Color.rgb(173, 184, 206));
        body.addView(hint);
        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(14, 14, 30, 12);
        for (String name : names) {
            Button card = tvAction(name, tvBadge(name), () -> openChannel(name), 236, 132);
            rail.addView(card);
        }
        scroller.addView(rail);
        body.addView(scroller);
    }

    private void tvSection(String group) {
        page = "section";
        channel = "";
        String title = group.equals("anime") ? "Anime (Latino y Sub)"
                : group.equals("series") ? "Series (Audio Latino)" : "Películas (Audio Latino)";
        String subtitle = group.equals("anime")
                ? "Series de animación japonesa con doblaje latino y versiones subtituladas."
                : group.equals("series") ? "Series de televisión, temporadas y capítulos completos con audio latino."
                        : "Catálogos de cine y estrenos con preferencia de audio latino.";
        LinearLayout body = newPage(title, title, subtitle);
        Button back = item("← Volver al inicio", body, this::home);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        body.addView(grid);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = Math.max(210, (dm.widthPixels - 145) / 4 - 18);
        for (int i = 0; i < channels.length(); i++) {
            JSONObject entry = channels.optJSONObject(i);
            if (entry == null || !entry.optBoolean("enabled", false))
                continue;
            String name = entry.optString("name");
            if (group.equals("anime") && !tvAnime(name))
                continue;
            if (group.equals("series") && (!tvSeries(name) && !name.equals("CineHax") && !name.equals("Pelispedia")))
                continue;
            if (group.equals("movies") && (tvAnime(name) || (tvSeries(name) && !name.equals("DeTodo Peliculas"))))
                continue;
            Button b = tile(name, tvBadge(name), grid, width, () -> openChannel(name));
        }
        back.requestFocus();
    }

    private TextView addSideIcon(LinearLayout side, String symbol, boolean current, String tooltip, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(symbol);
        btn.setTextSize(17);
        btn.setTextColor(current ? Color.WHITE : Color.rgb(185, 195, 212));
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setContentDescription(tooltip);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(current ? Color.rgb(52, 58, 70) : Color.TRANSPARENT);
        normal.setCornerRadius(10);
        btn.setBackground(normal);

        boolean isPortraitSide = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(isPortraitSide ? dp(42) : dp(50),
                isPortraitSide ? dp(34) : dp(38));
        lp.setMargins(0, dp(2), 0, dp(2));
        side.addView(btn, lp);
        sideNavButtons.add(btn);
        if (current) {
            activeSidebarView = btn;
        }

        btn.setOnClickListener(v -> action.run());
        btn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (("live_tv".equals(page) || "live_tv".equals(playbackReturnPage)) && activeCatBtn != null) {
                    activeCatBtn.requestFocus();
                    return true;
                }
            }
            return false;
        });
        btn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(focused ? Color.rgb(68, 76, 94) : (current ? Color.rgb(52, 58, 70) : Color.TRANSPARENT));
            bg.setCornerRadius(12);
            if (focused)
                bg.setStroke(2, Color.WHITE);
            btn.setBackground(bg);
            btn.setTextColor(Color.WHITE);
            btn.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(120).start();
        });
        return btn;
    }

    public static boolean isKnownTurkishShow(String title) {
        if (title == null)
            return false;
        String t = title.toLowerCase(java.util.Locale.ROOT).trim();
        return t.contains("erkenci") || t.contains("kara sevda") || t.contains("sen cal")
                || t.contains("fatmagul") || t.contains("hercai") || t.contains("yargi")
                || t.contains("dolunay") || t.contains("cukurova") || t.contains("muhtesem")
                || t.contains("el sultan") || t.contains("ertugrul") || t.contains("teskilat")
                || t.contains("kurulus") || t.contains("siyah beyaz") || t.contains("cukur")
                || t.contains("marasli") || t.contains("sahmaran") || t.contains("kulup")
                || t.contains("pajaro sonador") || t.contains("love is in the air")
                || t.contains("te alquilo mi amor") || t.contains("kiralik ask")
                || t.contains("pecado original") || t.contains("infiel") || t.contains("sadakatsiz")
                || t.contains("tierra amarga") || t.contains("secretos de familia")
                || t.contains("milagro en la celda") || t.contains("violin de mi padre");
    }

    public static boolean isKnownLatinShow(String title) {
        if (title == null)
            return false;
        String t = title.toLowerCase(java.util.Locale.ROOT).trim();
        return t.contains("betty la fea") || t.contains("cafe con aroma") || t.contains("pasion de gavilanes")
                || t.contains("reina del flow") || t.contains("rosario tijeras") || t.contains("cartel de los sapos")
                || t.contains("patron del mal") || t.contains("munecas de la mafia") || t.contains("chavo")
                || t.contains("senor de los cielos") || t.contains("cuna de lobos") || t.contains("rubi")
                || t.contains("usurpadora") || t.contains("rebelde") || t.contains("reina del sur")
                || t.contains("teresa") || t.contains("simuladores") || t.contains("encargado")
                || t.contains("monarca") || t.contains("casa de las flores") || t.contains("distrito salvaje");
    }

    public static boolean isDeadProvider(String name) {
        if (name == null)
            return true;
        return name.equals("LaMovie") || name.equals("Canela TV") || name.equals("Canela") || name.equals("canela")
                || name.equals("DoramasFlix") || name.equals("Pelisplus")
                || name.equals("DeTodo Peliculas") || name.equals("VerAni.me");
    }

    private void attachRailNavigation(android.view.View card) {
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN)
                return false;
            android.view.ViewParent p = v.getParent();
            if (!(p instanceof LinearLayout))
                return false;
            LinearLayout currentRail = (LinearLayout) p;
            int idx = currentRail.indexOfChild(v);
            int count = currentRail.getChildCount();

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Al llegar al final de la fila, envolver en bucle al inicio del mismo carrusel
                if (idx >= count - 1) {
                    if (count > 0 && currentRail.getChildAt(0) != null) {
                        currentRail.getChildAt(0).requestFocus();
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // Al presionar izquierda en el primer elemento, ir al botón de la barra lateral
                // alineado verticalmente
                if (idx == 0) {
                    if (!sideNavButtons.isEmpty()) {
                        int[] cardLoc = new int[2];
                        v.getLocationOnScreen(cardLoc);
                        int cardY = cardLoc[1] + (v.getHeight() / 2);
                        android.view.View bestSide = null;
                        int minDiff = Integer.MAX_VALUE;
                        for (android.view.View sb : sideNavButtons) {
                            if (sb.isShown()) {
                                int[] sbLoc = new int[2];
                                sb.getLocationOnScreen(sbLoc);
                                int diff = Math.abs((sbLoc[1] + (sb.getHeight() / 2)) - cardY);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    bestSide = sb;
                                }
                            }
                        }
                        if (bestSide != null) {
                            bestSide.requestFocus();
                            return true;
                        }
                    }
                    if (activeSidebarView != null && activeSidebarView.isShown()) {
                        activeSidebarView.requestFocus();
                        return true;
                    }
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // Preservar la posición horizontal visual al navegar arriba/abajo entre filas
                android.view.ViewParent scrollParent = currentRail.getParent();
                if (scrollParent instanceof android.widget.HorizontalScrollView) {
                    android.view.View scrollerView = (android.view.View) scrollParent;
                    android.view.ViewParent bodyParent = scrollerView.getParent();
                    if (bodyParent instanceof LinearLayout) {
                        LinearLayout bodyLayout = (LinearLayout) bodyParent;
                        int scrollerIdx = bodyLayout.indexOfChild(scrollerView);
                        int step = (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) ? 1 : -1;
                        android.widget.HorizontalScrollView nextScroller = null;
                        for (int i = scrollerIdx + step; i >= 0 && i < bodyLayout.getChildCount(); i += step) {
                            android.view.View candidate = bodyLayout.getChildAt(i);
                            if (candidate instanceof android.widget.HorizontalScrollView && candidate != scrollerView) {
                                nextScroller = (android.widget.HorizontalScrollView) candidate;
                                break;
                            }
                        }

                        if (nextScroller != null && nextScroller.getChildCount() > 0
                                && nextScroller.getChildAt(0) instanceof LinearLayout) {
                            LinearLayout nextRail = (LinearLayout) nextScroller.getChildAt(0);
                            if (nextRail.getChildCount() > 0) {
                                int[] myLoc = new int[2];
                                v.getLocationOnScreen(myLoc);
                                int myCenterX = myLoc[0] + (v.getWidth() / 2);

                                android.view.View bestChild = null;
                                int minDiff = Integer.MAX_VALUE;

                                for (int c = 0; c < nextRail.getChildCount(); c++) {
                                    android.view.View candCard = nextRail.getChildAt(c);
                                    if (candCard.isFocusable()) {
                                        int[] candLoc = new int[2];
                                        candCard.getLocationOnScreen(candLoc);
                                        int candCenterX = candLoc[0] + (candCard.getWidth() / 2);
                                        int diff = Math.abs(candCenterX - myCenterX);
                                        if (diff < minDiff) {
                                            minDiff = diff;
                                            bestChild = candCard;
                                        }
                                    }
                                }

                                if (bestChild != null) {
                                    bestChild.requestFocus();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        });
    }

    private void updateHeroPreview(PosterItem item) {
        if (item == null)
            return;
        if (heroTitle != null) {
            heroTitle.setText(item.title);
        }
        if (heroEyebrow != null) {
            heroEyebrow.setText((item.isSeries ? "SERIE TV" : "PELÍCULA")
                    + (item.year > 0 ? "  ·  " + item.year : "")
                    + (item.providerName != null && !item.providerName.isEmpty() ? "  ·  " + item.providerName : ""));
        }
        RatingEngine.RatingInfo r = RatingEngine.getRating(item.title, item.year, item.isSeries, this);
        if (heroSynopsis != null) {
            if (r != null && r.overview != null && !r.overview.isEmpty()) {
                heroSynopsis.setText(r.overview);
            } else {
                RatingEngine.resolveRatingAsync(item.title, item.year, item.isSeries, item.rawEntry, this, res -> {
                    if (res != null && res.overview != null && !res.overview.isEmpty() && heroSynopsis != null) {
                        heroSynopsis.setText(res.overview);
                    }
                });
            }
        }
    }

    private android.view.View createPosterCard(PosterItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(6, 6, 6, 8);
        card.setFocusable(true);
        card.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.TRANSPARENT);
        normalBg.setCornerRadius(14);
        card.setBackground(normalBg);

        boolean isPortraitCard = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int cardWidth = isPortraitCard ? dp(120) : dp(145);
        int posterHeight = isPortraitCard ? dp(175) : dp(210);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardWidth, -2);
        cardLp.setMargins(0, 0, isPortraitCard ? dp(10) : dp(16), dp(8));
        card.setLayoutParams(cardLp);

        android.widget.FrameLayout posterWrap = new android.widget.FrameLayout(this);
        GradientDrawable posterBg = new GradientDrawable();
        posterBg.setColor(Color.rgb(26, 32, 46));
        posterBg.setCornerRadius(14);
        posterWrap.setBackground(posterBg);
        posterWrap.setClipToOutline(true);

        TextView fallbackTitle = text(item.title, 15, Color.rgb(180, 194, 220));
        fallbackTitle.setGravity(Gravity.CENTER);
        fallbackTitle.setPadding(14, 14, 14, 14);
        fallbackTitle.setTypeface(null, Typeface.BOLD);
        posterWrap.addView(fallbackTitle, new android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterWrap.addView(img, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (item.posterUrl != null && !item.posterUrl.isEmpty()) {
            CatalogBrowser.loadPoster(this, img, item.posterUrl);
        }

        card.addView(posterWrap, new LinearLayout.LayoutParams(-1, posterHeight));

        long savedPos = PlaybackEngine.getSavedPosition(this,
                item.queryHint != null && !item.queryHint.isEmpty() ? item.queryHint : item.title);
        if (savedPos > 10000) {
            TextView resumeBadge = text("▶ " + PlaybackEngine.formatTime(savedPos), 10, Color.WHITE);
            resumeBadge.setTypeface(null, Typeface.BOLD);
            resumeBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable rbBg = new GradientDrawable();
            rbBg.setColor(Color.argb(235, 220, 40, 40));
            rbBg.setCornerRadius(dp(6));
            resumeBadge.setBackground(rbBg);
            android.widget.FrameLayout.LayoutParams rblp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.START);
            rblp.setMargins(dp(6), 0, 0, dp(6));
            posterWrap.addView(resumeBadge, rblp);
        }

        RatingEngine.RatingInfo rInfo = RatingEngine.getRating(item.title, item.year, item.isSeries, this);
        final TextView meta;
        if (rInfo.hasScore()) {
            meta = text(String.format(java.util.Locale.US, "⭐ %.1f %s", rInfo.score, rInfo.source), 12,
                    Color.rgb(245, 197, 66));
        } else {
            meta = text("Sin calificación", 12, Color.rgb(170, 180, 195));
            RatingEngine.resolveRatingAsync(item.title, item.year, item.isSeries, item.rawEntry, this, r -> {
                if (r != null && r.hasScore()) {
                    meta.setText(String.format(java.util.Locale.US, "⭐ %.1f %s", r.score, r.source));
                    meta.setTextColor(Color.rgb(245, 197, 66));
                }
            });
        }
        meta.setTypeface(null, Typeface.BOLD);
        meta.setPadding(4, 8, 4, 2);
        card.addView(meta);

        TextView title = text(item.title, 14, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(4, 0, 4, 4);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        card.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable fBg = new GradientDrawable();
            fBg.setColor(focused ? Color.rgb(22, 26, 36) : Color.TRANSPARENT);
            fBg.setCornerRadius(14);
            if (focused)
                fBg.setStroke(4, Color.WHITE);
            card.setBackground(fBg);
            card.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(130).start();
            card.setElevation(focused ? 10f : 0f);
            if (focused) {
                updateHeroPreview(item);
            }
        });

        card.setOnClickListener(v -> onPosterClicked(item));
        attachRailNavigation(card);
        return card;
    }

    private android.view.View createTop10PosterCard(PosterItem item, int rank) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.BOTTOM);
        wrap.setFocusable(true);
        wrap.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.TRANSPARENT);
        normalBg.setCornerRadius(14);
        wrap.setBackground(normalBg);

        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(250, -2);
        wrapLp.setMargins(0, 0, 18, 8);
        wrap.setLayoutParams(wrapLp);

        // Número gigante de Ranking (estilo MXL Movies / TopNumberTextView / Netflix)
        TextView numberView = text(String.valueOf(rank), rank == 10 ? 68 : 84, Color.rgb(240, 240, 240));
        numberView.setTypeface(null, Typeface.BOLD);
        numberView.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        numberView.setPadding(0, 0, 4, 18);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(rank == 10 ? 76 : 64, -2);
        wrap.addView(numberView, nLp);

        // Tarjeta con póster
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(4, 4, 4, 6);

        android.widget.FrameLayout posterWrap = new android.widget.FrameLayout(this);
        GradientDrawable posterBg = new GradientDrawable();
        posterBg.setColor(Color.rgb(26, 32, 46));
        posterBg.setCornerRadius(12);
        posterWrap.setBackground(posterBg);
        posterWrap.setClipToOutline(true);

        TextView fallbackTitle = text(item.title, 13, Color.rgb(180, 194, 220));
        fallbackTitle.setGravity(Gravity.CENTER);
        fallbackTitle.setPadding(10, 10, 10, 10);
        fallbackTitle.setTypeface(null, Typeface.BOLD);
        posterWrap.addView(fallbackTitle, new android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterWrap.addView(img, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (item.posterUrl != null && !item.posterUrl.isEmpty()) {
            CatalogBrowser.loadPoster(this, img, item.posterUrl);
        }
        card.addView(posterWrap, new LinearLayout.LayoutParams(168, 242));

        RatingEngine.RatingInfo rInfo = RatingEngine.getRating(item.title, item.year, item.isSeries, this);
        final TextView meta;
        if (rInfo.hasScore()) {
            meta = text(String.format(java.util.Locale.US, "⭐ %.1f %s", rInfo.score, rInfo.source), 11,
                    Color.rgb(245, 197, 66));
        } else {
            meta = text("Sin calificación", 11, Color.rgb(170, 180, 195));
            RatingEngine.resolveRatingAsync(item.title, item.year, item.isSeries, item.rawEntry, this, r -> {
                if (r != null && r.hasScore()) {
                    meta.setText(String.format(java.util.Locale.US, "⭐ %.1f %s", r.score, r.source));
                    meta.setTextColor(Color.rgb(245, 197, 66));
                }
            });
        }
        meta.setTypeface(null, Typeface.BOLD);
        meta.setPadding(2, 6, 2, 2);
        card.addView(meta);

        TextView title = text(item.title, 13, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(2, 0, 2, 2);
        card.addView(title, new LinearLayout.LayoutParams(168, -2));

        wrap.addView(card);

        wrap.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable fBg = new GradientDrawable();
            fBg.setColor(focused ? Color.rgb(28, 34, 48) : Color.TRANSPARENT);
            fBg.setCornerRadius(14);
            if (focused)
                fBg.setStroke(3, Color.WHITE);
            wrap.setBackground(fBg);
            wrap.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(130).start();
            numberView.setTextColor(focused ? Color.rgb(245, 197, 66) : Color.rgb(240, 240, 240));
            if (focused) {
                updateHeroPreview(item);
            }
        });

        wrap.setOnClickListener(v -> onPosterClicked(item));
        attachRailNavigation(wrap);
        return wrap;
    }

    private LinearLayout addTop10Row(LinearLayout body, String heading, java.util.List<PosterItem> items) {
        TextView title = text(heading, 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(8, 16, 0, 8);
        body.addView(title, tlp);

        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setFocusable(false);
        scroller.setFocusableInTouchMode(false);
        scroller.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(6, 4, 28, 10);
        int rank = 1;
        for (PosterItem it : items) {
            rail.addView(createTop10PosterCard(it, rank++));
            if (rank > 10)
                break;
        }
        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private void onPosterClicked(PosterItem item) {
        if ("Demo".equals(item.providerName)) {
            openChannel("Demo");
            return;
        }
        if ("Blender Open Movies".equals(item.providerName) || "Internet Archive".equals(item.providerName)) {
            openChannel(item.providerName);
            return;
        }
        if ("LiveTV".equals(item.providerName)) {
            showLiveTvStudio();
            return;
        }
        if (item.rawEntry != null) {
            openEntryDetail(item.rawEntry);
            return;
        }
        if (item.queryHint != null && !item.queryHint.isEmpty()) {
            final String searchTitle = item.queryHint;
            showStatus("Cargando ficha de " + searchTitle + "...");
            videoResolvers.submit(() -> {
                CatalogData.Entry found = null;
                try {
                    CatalogData.CatalogPage cp = CineHaxProvider.catalog(item.isSeries ? "serie" : "movie", searchTitle, 1);
                    if (cp != null && cp.entries != null && !cp.entries.isEmpty()) {
                        for (CatalogData.Entry e : cp.entries) {
                            if (isTitleMatch(searchTitle, e.title, e.originalTitle)) {
                                found = e;
                                break;
                            }
                        }
                        if (found == null) {
                            found = cp.entries.get(0);
                        }
                    }
                } catch (Exception ignored) {}
                final CatalogData.Entry finalFound = found;
                runOnUiThread(() -> {
                    if (finalFound != null) {
                        openEntryDetail(finalFound);
                    } else {
                        String targetProvider = (item.providerName != null && !item.providerName.isEmpty()
                                && !"Catálogo Global TV".equals(item.providerName)) ? item.providerName : activeProvider;
                        VideoProvider prov = VideoProviders.find(targetProvider);
                        if (prov != null) {
                            CatalogBrowser.State st = new CatalogBrowser.State();
                            st.kind = item.isSeries ? "serie" : "movie";
                            st.query = searchTitle;
                            catalogStates.put(prov.name(), st);
                            openChannel(prov.name());
                        } else {
                            openChannel(targetProvider);
                        }
                    }
                });
            });
            return;
        }
        String targetProvider = (item.providerName != null && !item.providerName.isEmpty()
                && !"Catálogo Global TV".equals(item.providerName)) ? item.providerName : activeProvider;
        VideoProvider prov = VideoProviders.find(targetProvider);
        if (prov != null) {
            CatalogBrowser.State st = new CatalogBrowser.State();
            st.kind = item.isSeries ? "serie" : "movie";
            if (item.queryHint != null && !item.queryHint.isEmpty()) {
                st.query = item.queryHint;
            }
            catalogStates.put(prov.name(), st);
            openChannel(prov.name());
        } else {
            openChannel(targetProvider);
        }
    }

    private android.view.View createSeeAllCard(String categoryTitle, String providerName, String kind, String query,
            String carouselId) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        if (carouselId != null && !carouselId.isEmpty()) {
            card.setTag(carouselId + "_see_all");
        }

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dp(150), dp(255));
        cardLp.setMargins(0, 0, dp(16), dp(8));
        card.setLayoutParams(cardLp);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.rgb(20, 26, 40));
        normalBg.setCornerRadius(14);
        normalBg.setStroke(dp(2), Color.rgb(45, 58, 85));
        card.setBackground(normalBg);

        android.widget.FrameLayout circle = new android.widget.FrameLayout(this);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(Color.rgb(35, 45, 70));
        circle.setBackground(circleBg);

        TextView dots = text("•••", 22, Color.rgb(245, 197, 66));
        dots.setTypeface(null, Typeface.BOLD);
        dots.setGravity(Gravity.CENTER);
        circle.addView(dots, new android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        card.addView(circle, new LinearLayout.LayoutParams(dp(62), dp(62)));

        TextView label = text("Ver todo", 16, Color.WHITE);
        label.setTypeface(null, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(12), 0, dp(4));
        card.addView(label);

        TextView sub = text("Catálogo completo", 12, Color.rgb(168, 178, 195));
        sub.setGravity(Gravity.CENTER);
        card.addView(sub);

        card.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable fBg = new GradientDrawable();
            fBg.setColor(focused ? Color.rgb(28, 38, 62) : Color.rgb(20, 26, 40));
            fBg.setCornerRadius(14);
            fBg.setStroke(focused ? dp(4) : dp(2), focused ? Color.rgb(245, 197, 66) : Color.rgb(45, 58, 85));
            card.setBackground(fBg);
            card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(130).start();
            card.setElevation(focused ? 10f : 0f);
        });

        card.setOnClickListener(v -> openCategoryFullGrid(categoryTitle, providerName, kind, query, carouselId));
        attachRailNavigation(card);
        return card;
    }

    private void openCategoryFullGrid(String categoryTitle, String providerName, String kind, String query,
            String carouselId) {
        if ("favorites".equals(carouselId)) {
            showFavoritesPage();
            return;
        }
        if ("watch_later".equals(carouselId)) {
            showWatchLaterPage();
            return;
        }
        if (pageScroll != null) {
            returnScrollY = pageScroll.getScrollY();
        }
        returnCarouselId = carouselId;
        showCategoryGridView(categoryTitle, providerName, kind, query);
    }

    private void returnToHomeFromCategoryGrid() {
        home();
        if (pageScroll != null) {
            pageScroll.post(() -> {
                pageScroll.scrollTo(0, returnScrollY);
                if (returnCarouselId != null && !returnCarouselId.isEmpty()) {
                    android.view.View target = root.findViewWithTag(returnCarouselId + "_see_all");
                    if (target != null) {
                        target.requestFocus();
                        return;
                    }
                }
                if (pageScroll.getChildCount() > 0) {
                    pageScroll.getChildAt(0).requestFocus();
                }
            });
        }
    }

    private void showCategoryGridView(String categoryTitle, String providerName, String kind, String query) {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        if (resolutionTask != null)
            resolutionTask.cancel(true);
        ++providerPlayRequest;
        providerLoading = false;
        page = "category_grid";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 16, 24));

        // Encabezado superior
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), dp(16), dp(24), dp(14));
        header.setBackgroundColor(Color.rgb(18, 24, 36));

        Button backBtn = new Button(this);
        backBtn.setText("←  Volver");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(16);
        backBtn.setAllCaps(false);
        backBtn.setFocusable(true);
        GradientDrawable backNorm = new GradientDrawable();
        backNorm.setColor(Color.rgb(30, 40, 60));
        backNorm.setCornerRadius(12);
        backBtn.setBackground(backNorm);
        backBtn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bBg = new GradientDrawable();
            bBg.setColor(focused ? Color.rgb(212, 175, 55) : Color.rgb(30, 40, 60));
            bBg.setCornerRadius(12);
            backBtn.setBackground(bBg);
            backBtn.setTextColor(focused ? Color.BLACK : Color.WHITE);
        });
        backBtn.setOnClickListener(v -> returnToHomeFromCategoryGrid());
        header.addView(backBtn, new LinearLayout.LayoutParams(-2, dp(48)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(20), 0, 0, 0);

        TextView catTitle = text(categoryTitle, 22, Color.WHITE);
        catTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(catTitle);

        TextView provBadge = text(providerName + "  ·  Catálogo Completo Multidimensional (6 columnas)", 13,
                Color.rgb(168, 178, 195));
        titleCol.addView(provBadge);

        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        final TextView counterText = text("Cargando catálogo...", 14, Color.rgb(245, 197, 66));
        counterText.setTypeface(null, Typeface.BOLD);
        header.addView(counterText);

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // Scrollview con la cuadrícula de 6 columnas
        final ScrollView gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(true);
        gridScroll.setFillViewport(false);

        LinearLayout gridContent = new LinearLayout(this);
        gridContent.setOrientation(LinearLayout.VERTICAL);
        gridContent.setPadding(dp(20), dp(16), dp(20), dp(36));

        final GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setOrientation(GridLayout.HORIZONTAL);
        gridContent.addView(grid, new LinearLayout.LayoutParams(-1, -2));

        // Pie de página con indicador de carga
        final LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, dp(16));

        final TextView statusText = text("Cargando títulos...", 14, Color.rgb(180, 194, 220));
        statusText.setGravity(Gravity.CENTER);
        footer.addView(statusText);

        final Button loadMoreBtn = new Button(this);
        loadMoreBtn.setText("⬇  Cargar más títulos");
        loadMoreBtn.setTextColor(Color.WHITE);
        loadMoreBtn.setTextSize(15);
        loadMoreBtn.setAllCaps(false);
        loadMoreBtn.setFocusable(true);
        GradientDrawable lmBg = new GradientDrawable();
        lmBg.setColor(Color.rgb(28, 38, 62));
        lmBg.setCornerRadius(14);
        loadMoreBtn.setBackground(lmBg);
        loadMoreBtn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable b = new GradientDrawable();
            b.setColor(focused ? Color.rgb(129, 107, 245) : Color.rgb(28, 38, 62));
            b.setCornerRadius(14);
            if (focused)
                b.setStroke(dp(3), Color.WHITE);
            loadMoreBtn.setBackground(b);
        });
        footer.addView(loadMoreBtn, new LinearLayout.LayoutParams(dp(260), dp(54)));

        gridContent.addView(footer);
        gridScroll.addView(gridContent);
        root.addView(gridScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        backBtn.requestFocus();

        final int[] currentPage = new int[] { 1 };
        final int[] totalLoaded = new int[] { 0 };
        final boolean[] isLoading = new boolean[] { false };
        final boolean[] hasMore = new boolean[] { true };
        final VideoProvider prov = VideoProviders.find(providerName);

        final Runnable loadNextPage = new Runnable() {
            @Override
            public void run() {
                if (isLoading[0] || !hasMore[0] || prov == null)
                    return;
                isLoading[0] = true;
                final int pageToLoad = currentPage[0];
                runOnUiThread(() -> {
                    statusText.setText("Cargando página " + pageToLoad + "...");
                    loadMoreBtn.setEnabled(false);
                });
                new Thread(() -> {
                    try {
                        final CatalogData.CatalogPage res = prov.catalog(kind, query, pageToLoad);
                        final java.util.List<PosterItem> newItems = new java.util.ArrayList<>();
                        if (res != null && !res.entries.isEmpty()) {
                            for (CatalogData.Entry e : res.entries) {
                                newItems.add(new PosterItem(
                                        e.title,
                                        e.year > 0 ? e.year : 2026,
                                        !e.isMovie(),
                                        e.poster,
                                        providerName,
                                        e.title,
                                        e));
                            }
                        }
                        final boolean nextAvailable = res != null && (res.next || res.entries.size() >= 12);
                        runOnUiThread(() -> {
                            if (!page.equals("category_grid") || isDestroyed())
                                return;
                            isLoading[0] = false;
                            if (newItems.isEmpty() && pageToLoad == 1) {
                                statusText.setText("No se encontraron más títulos en esta categoría.");
                                hasMore[0] = false;
                                loadMoreBtn.setVisibility(android.view.View.GONE);
                                return;
                            }
                            totalLoaded[0] += newItems.size();
                            currentPage[0]++;
                            hasMore[0] = nextAvailable;

                            int screenWidth = getResources().getDisplayMetrics().widthPixels;
                            int cardWidth = Math.max(dp(140), (screenWidth - dp(90)) / 6);

                            for (PosterItem item : newItems) {
                                android.view.View cView = createGridPosterCard(item, cardWidth);
                                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                                glp.width = cardWidth;
                                glp.height = -2;
                                glp.setMargins(dp(5), dp(6), dp(5), dp(10));
                                grid.addView(cView, glp);
                            }

                            counterText.setText(
                                    "Cargados: " + totalLoaded[0] + " títulos  ·  Página " + (currentPage[0] - 1));
                            if (hasMore[0]) {
                                statusText.setText("⬇ Desplázate hacia abajo para cargar la página " + currentPage[0]);
                                loadMoreBtn.setText("⬇  Cargar página " + currentPage[0]);
                                loadMoreBtn.setEnabled(true);
                                loadMoreBtn.setVisibility(android.view.View.VISIBLE);
                            } else {
                                statusText.setText(
                                        "✓ Has llegado al final del catálogo (" + totalLoaded[0] + " títulos)");
                                loadMoreBtn.setVisibility(android.view.View.GONE);
                            }
                        });
                    } catch (Exception ex) {
                        runOnUiThread(() -> {
                            isLoading[0] = false;
                            statusText.setText("No se pudieron cargar más títulos (" + ex.getMessage() + ")");
                            loadMoreBtn.setEnabled(true);
                        });
                    }
                }, "grid-load-p" + pageToLoad).start();
            }
        };

        loadMoreBtn.setOnClickListener(v -> loadNextPage.run());
        loadMoreBtn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable b = new GradientDrawable();
            b.setColor(focused ? Color.rgb(129, 107, 245) : Color.rgb(28, 38, 62));
            b.setCornerRadius(14);
            if (focused)
                b.setStroke(dp(3), Color.WHITE);
            loadMoreBtn.setBackground(b);
            if (focused && !isLoading[0] && hasMore[0]) {
                loadNextPage.run();
            }
        });

        gridScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isLoading[0] || !hasMore[0])
                return;
            if (gridScroll.getChildCount() > 0) {
                int diff = (gridContent.getBottom() - (gridScroll.getHeight() + gridScroll.getScrollY()));
                if (diff <= dp(450)) {
                    loadNextPage.run();
                }
            }
        });

        loadNextPage.run();
    }

    private android.view.View createGridPosterCard(PosterItem item, int cardWidth) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(6));
        card.setFocusable(true);
        card.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.TRANSPARENT);
        normalBg.setCornerRadius(12);
        card.setBackground(normalBg);

        android.widget.FrameLayout posterWrap = new android.widget.FrameLayout(this);
        GradientDrawable posterBg = new GradientDrawable();
        posterBg.setColor(Color.rgb(24, 30, 44));
        posterBg.setCornerRadius(12);
        posterWrap.setBackground(posterBg);
        posterWrap.setClipToOutline(true);

        TextView fallbackTitle = text(item.title, 14, Color.rgb(180, 194, 220));
        fallbackTitle.setGravity(Gravity.CENTER);
        fallbackTitle.setPadding(dp(8), dp(8), dp(8), dp(8));
        fallbackTitle.setTypeface(null, Typeface.BOLD);
        posterWrap.addView(fallbackTitle, new android.widget.FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterWrap.addView(img, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (item.posterUrl != null && !item.posterUrl.isEmpty()) {
            CatalogBrowser.loadPoster(this, img, item.posterUrl);
        }

        int posterH = (int) (cardWidth * 1.45f);
        card.addView(posterWrap, new LinearLayout.LayoutParams(-1, posterH));

        RatingEngine.RatingInfo rInfo = RatingEngine.getRating(item.title, item.year, item.isSeries, this);
        final TextView meta;
        if (rInfo.hasScore()) {
            meta = text(String.format(java.util.Locale.US, "⭐ %.1f %s", rInfo.score, rInfo.source), 11,
                    Color.rgb(245, 197, 66));
        } else {
            meta = text("Sin calificación", 11, Color.rgb(170, 180, 195));
            RatingEngine.resolveRatingAsync(item.title, item.year, item.isSeries, item.rawEntry, this, r -> {
                if (r != null && r.hasScore()) {
                    meta.setText(String.format(java.util.Locale.US, "⭐ %.1f %s", r.score, r.source));
                    meta.setTextColor(Color.rgb(245, 197, 66));
                }
            });
        }
        meta.setTypeface(null, Typeface.BOLD);
        meta.setPadding(dp(2), dp(4), dp(2), dp(2));
        card.addView(meta);

        TextView title = text(item.title, 13, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(dp(2), 0, dp(2), dp(2));
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        card.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable fBg = new GradientDrawable();
            fBg.setColor(focused ? Color.rgb(22, 28, 42) : Color.TRANSPARENT);
            fBg.setCornerRadius(12);
            if (focused)
                fBg.setStroke(dp(3), Color.rgb(245, 197, 66));
            card.setBackground(fBg);
            card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
            card.setElevation(focused ? 10f : 0f);
        });

        card.setOnClickListener(v -> onPosterClicked(item));
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN)
                return false;
            android.view.ViewParent p = v.getParent();
            if (!(p instanceof GridLayout))
                return false;
            GridLayout g = (GridLayout) p;
            int idx = g.indexOfChild(v);
            int count = g.getChildCount();
            int cols = g.getColumnCount();

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if ((idx % cols) == cols - 1 && idx + 1 < count) {
                    g.getChildAt(idx + 1).requestFocus();
                    return true;
                } else if (idx >= count - 1) {
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if ((idx % cols) == 0 && idx > 0) {
                    g.getChildAt(idx - 1).requestFocus();
                    return true;
                }
            }
            return false;
        });
        return card;
    }

    private LinearLayout addPosterRow(LinearLayout body, String heading, java.util.List<PosterItem> initialItems) {
        return addPosterRow(body, heading, initialItems, "CineHax", "movie", "", "");
    }

    private LinearLayout addPosterRow(LinearLayout body, String heading, java.util.List<PosterItem> initialItems,
            String providerName, String kind, String query, String carouselId) {
        TextView title = text(heading, 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        if (carouselId != null && !carouselId.isEmpty()) {
            title.setTag("row_" + carouselId);
        }
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(8, 18, 0, 10);
        body.addView(title, tlp);

        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setFocusable(false);
        scroller.setFocusableInTouchMode(false);
        scroller.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(6, 4, 28, 10);
        if (carouselId != null && !carouselId.isEmpty()) {
            rail.setTag("rail_" + carouselId);
        }
        for (PosterItem it : initialItems) {
            rail.addView(createPosterCard(it));
        }
        if (providerName != null && !providerName.isEmpty() && !"SK Tech".equals(providerName)) {
            rail.addView(createSeeAllCard(heading, providerName, kind, query, carouselId));
        }
        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private void populateLiveRow(LinearLayout rail, String providerName, String kind, String query, int pageNum,
            int offset, boolean isPrimaryRow) {
        populateLiveRow(rail, providerName, kind, query, pageNum, offset, isPrimaryRow, "", "");
    }

    private static boolean isNonLatinMovie(String title) {
        if (title == null)
            return false;
        String t = title.toLowerCase();
        return t.contains("rambo") || t.contains("conan") || t.contains("terminator")
                || t.contains("rocky") || t.contains("alien") || t.contains("predator")
                || t.contains("matrix") || t.contains("star wars") || t.contains("harry potter")
                || t.contains("jurassic") || t.contains("batman") || t.contains("superman")
                || t.contains("spiderman") || t.contains("spider-man") || t.contains("avengers")
                || t.contains("fast & furious") || t.contains("rapido y furioso") || t.contains("john wick")
                || t.contains("gladiator") || t.contains("mad max") || t.contains("die hard")
                || t.contains("mision imposible") || t.contains("mission impossible");
    }

    private void populateLiveRow(LinearLayout rail, String providerName, String kind, String query, int pageNum,
            int offset, boolean isPrimaryRow,
            String categoryTitle, String carouselId) {
        VideoProvider prov = VideoProviders.find(providerName);
        if (prov == null)
            return;
        new Thread(() -> {
            try {
                CatalogData.CatalogPage res = prov.catalog(kind, query, pageNum);
                if (res == null || res.entries.isEmpty())
                    return;
                java.util.List<PosterItem> live = new java.util.ArrayList<>();
                for (int i = offset; i < res.entries.size() && live.size() < 14; i++) {
                    CatalogData.Entry e = res.entries.get(i);
                    if ("movie".equals(kind) && !e.isMovie()) {
                        continue;
                    }
                    if ("serie".equals(kind) && e.isMovie()) {
                        continue;
                    }
                    boolean is2026Rail = "movies2026".equals(carouselId) || "series2026".equals(carouselId)
                            || (categoryTitle != null && (categoryTitle.contains("2026") || categoryTitle.contains("Estrenos")));
                    if (is2026Rail && e.year > 0 && e.year < 2025) {
                        continue;
                    }
                    if ("latin".equals(carouselId)) {
                        if (e.isMovie() || isNonLatinMovie(e.title))
                            continue;
                    }
                    int itemYear = e.year > 0 ? e.year : (is2026Rail ? 2026 : 0);
                    live.add(new PosterItem(
                            e.title,
                            itemYear,
                            !e.isMovie(),
                            e.poster,
                            providerName,
                            e.title,
                            e));
                }
                if (live.isEmpty())
                    return;
                runOnUiThread(() -> {
                    if (!page.equals("home") || isDestroyed())
                        return;
                    boolean hadFocus = rail.hasFocus();
                    int focusedIndex = -1;
                    if (hadFocus) {
                        for (int i = 0; i < rail.getChildCount(); i++) {
                            if (rail.getChildAt(i).hasFocus()) {
                                focusedIndex = i;
                                break;
                            }
                        }
                    }
                    rail.removeAllViews();
                    for (PosterItem item : live) {
                        rail.addView(createPosterCard(item));
                    }
                    if (providerName != null && !providerName.isEmpty() && !"SK Tech".equals(providerName)) {
                        rail.addView(createSeeAllCard(categoryTitle, providerName, kind, query, carouselId));
                    }
                    if (hadFocus && rail.getChildCount() > 0) {
                        int targetIdx = Math.max(0, Math.min(focusedIndex, rail.getChildCount() - 1));
                        rail.getChildAt(targetIdx).requestFocus();
                    } else if (isPrimaryRow && rail.getChildCount() > 0 && getCurrentFocus() == null) {
                        rail.getChildAt(0).requestFocus();
                    }
                });
            } catch (Exception ignored) {
            }
        }, "live-row-" + providerName + "-" + kind + "-" + pageNum).start();
    }

    private java.util.List<PosterItem> defaultCrime2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Peaky Blinders", 2022, true,
                        "https://image.tmdb.org/t/p/w342/vUUqzWa2LnHIVqkaKVlVGkVcZIW.jpg", prov, "Peaky Blinders",
                        null),
                new PosterItem("Breaking Bad", 2013, true,
                        "https://image.tmdb.org/t/p/w342/ztkUQFLlC19CCMYHW9o1zWhJRNq.jpg", prov, "Breaking Bad", null),
                new PosterItem("Fargo", 2024, true, "https://image.tmdb.org/t/p/w342/6fn4B5g44oFvjEa1RkWc5E7VwL8.jpg",
                        prov, "Fargo", null),
                new PosterItem("Better Call Saul", 2022, true,
                        "https://image.tmdb.org/t/p/w342/fC2HDm5t0kHjUmYIMBYVZsXL0qj.jpg", prov, "Better Call Saul",
                        null),
                new PosterItem("Narcos", 2017, true, "https://image.tmdb.org/t/p/w342/rTmal9fMfwhBXoNsWljK2aoVo4U.jpg",
                        prov, "Narcos", null));
    }

    private java.util.List<PosterItem> defaultDrama2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("El Sultán (Muhteşem Yüzyıl)", 2014, true,
                        "https://image.tmdb.org/t/p/w342/dZ8Q6PZ8vQj4i0iR9C8qg2N2m9O.jpg", prov, "El Sultan", null),
                new PosterItem("Resurrección: Ertuğrul", 2019, true,
                        "https://image.tmdb.org/t/p/w342/gB5tZ8e6P7z8i0iR9C8qg2N2m9O.jpg", prov, "Ertugrul", null),
                new PosterItem("The Crown", 2023, true,
                        "https://image.tmdb.org/t/p/w342/1DMEzmAe2c33pWz2wK8c8jY1U2b.jpg", prov, "The Crown", null),
                new PosterItem("Succession", 2023, true,
                        "https://image.tmdb.org/t/p/w342/7nR65n58H6x3Pz2wK8c8jY1U2b.jpg", prov, "Succession", null),
                new PosterItem("Chernobyl", 2019, true,
                        "https://image.tmdb.org/t/p/w342/hlLXt2tOPT6RRnjiUmoxyG1LTFi.jpg", prov, "Chernobyl", null));
    }

    private java.util.List<PosterItem> defaultLatin2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Betty la Fea: La Historia Continúa", 2024, true,
                        "https://image.tmdb.org/t/p/w342/w9J4q4O1H5X4C6s9p1j8N3X5P.jpg", prov, "Betty la fea", null),
                new PosterItem("Café con Aroma de Mujer", 2021, true,
                        "https://image.tmdb.org/t/p/w342/o89iYd3pL5p91eG53J5xX68zOqK.jpg", prov, "Cafe con aroma",
                        null),
                new PosterItem("Pasión de Gavilanes", 2022, true,
                        "https://image.tmdb.org/t/p/w342/s5v0n8k33o3rX2oQe4iGjN31KxJ.jpg", prov, "Pasion de gavilanes",
                        null),
                new PosterItem("La Reina del Flow", 2021, true,
                        "https://image.tmdb.org/t/p/w342/7K4gLd3Lp11K54M1d9jX5N1Q2o.jpg", prov, "Reina del flow", null),
                new PosterItem("El Chavo del 8", 1973, true,
                        "https://image.tmdb.org/t/p/w342/7j7jK60O4a0r82q2QnO5bXj3p.jpg", prov, "El Chavo", null),
                new PosterItem("El Señor de los Cielos", 2024, true,
                        "https://image.tmdb.org/t/p/w342/u3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", prov, "Senor de los cielos",
                        null),
                new PosterItem("Rosario Tijeras", 2019, true,
                        "https://image.tmdb.org/t/p/w342/q3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", prov, "Rosario Tijeras",
                        null),
                new PosterItem("El Encargado", 2024, true,
                        "https://image.tmdb.org/t/p/w342/u3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", prov, "El Encargado", null),
                new PosterItem("Los Simuladores", 2004, true,
                        "https://image.tmdb.org/t/p/w342/9JBEPL0T2eHNG7FvJ6n5KekjOqL.jpg", prov, "Simuladores", null));
    }

    private java.util.List<PosterItem> defaultTurkishMovies() {
        return defaultTurkishMovies("CineHax");
    }

    private java.util.List<PosterItem> defaultTurkishMovies(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Milagro en la celda 7", 2019, false,
                        "https://image.tmdb.org/t/p/w342/bOthNTrUXFknN9F7U97q3F26m4y.jpg", "CineHax",
                        "Milagro en la celda 7", null),
                new PosterItem("El violín de mi padre", 2022, false,
                        "https://image.tmdb.org/t/p/w342/3w3K8v5L6K7eXq3P1j8N3X5P.jpg", "CineHax", "violin de mi padre",
                        null),
                new PosterItem("Tácticas de amor", 2022, false,
                        "https://image.tmdb.org/t/p/w342/7jKl6m3No98uX3P1j8N3X5P.jpg", "CineHax", "Tacticas de amor",
                        null),
                new PosterItem("Vidas de papel", 2021, false,
                        "https://image.tmdb.org/t/p/w342/4K2pQ8e2c33pWz2wK8c8jY1U2b.jpg", "CineHax", "Vidas de papel",
                        null),
                new PosterItem("Quédate", 2022, false,
                        "https://image.tmdb.org/t/p/w342/9kLmQ8e2c33pWz2wK8c8jY1U2b.jpg", "CineHax", "Quedate", null));
    }

    private java.util.List<PosterItem> defaultKoreanMovies() {
        return defaultKoreanMovies("CineHax");
    }

    private java.util.List<PosterItem> defaultKoreanMovies(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Parásitos (Parasite)", 2019, false,
                        "https://image.tmdb.org/t/p/w342/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", "CineHax", "Parasite", null),
                new PosterItem("Estación Zombie (Train to Busan)", 2016, false,
                        "https://image.tmdb.org/t/p/w342/vVpPXq5L5fT7jP1j8N3X5P.jpg", "CineHax", "Train to Busan",
                        null),
                new PosterItem("Oldboy: Cinco días para vengarse", 2003, false,
                        "https://image.tmdb.org/t/p/w342/pWDtjs568ZfOTMbURQBYuT4Qxka.jpg", "CineHax", "Oldboy", null),
                new PosterItem("La Doncella (The Handmaiden)", 2016, false,
                        "https://image.tmdb.org/t/p/w342/dLknE13L9k7eXq3P1j8N3X5P.jpg", "CineHax", "The Handmaiden",
                        null),
                new PosterItem("Exhuma: La tumba del diablo", 2024, false,
                        "https://image.tmdb.org/t/p/w342/1pm9KjK2o39yXq3P1j8N3X5P.jpg", "CineHax", "Exhuma", null));
    }

    private java.util.List<PosterItem> defaultLiveConcerts() {
        return java.util.Arrays.asList(
                new PosterItem("Queen: Live at Wembley Stadium HD", 1986, false,
                        "https://i.ytimg.com/vi/eQsM6u0c0ks/hqdefault.jpg", "YouTube+",
                        "Queen Live at Wembley recital completo", null),
                new PosterItem("Soda Stereo: Gira Me Verás Volver Completo", 2007, false,
                        "https://i.ytimg.com/vi/qQGj4M6T7d0/hqdefault.jpg", "YouTube+",
                        "Soda Stereo Me Veras Volver recital completo hd", null),
                new PosterItem("Coldplay: Live in Buenos Aires River Plate", 2022, false,
                        "https://i.ytimg.com/vi/4m2pX7p9m30/hqdefault.jpg", "YouTube+",
                        "Coldplay Live in Buenos Aires concierto completo hd", null),
                new PosterItem("Gustavo Cerati: Ahí Vamos en Vivo HD", 2007, false,
                        "https://i.ytimg.com/vi/8m3K4q7P9e0/hqdefault.jpg", "YouTube+",
                        "Gustavo Cerati Ahi Vamos recital completo hd", null),
                new PosterItem("AC/DC: Live at River Plate Completo", 2009, false,
                        "https://i.ytimg.com/vi/n_GFN3a0yj0/hqdefault.jpg", "YouTube+",
                        "ACDC Live at River Plate concierto completo", null),
                new PosterItem("Los Redondos: Racing Club en Vivo HD", 1998, false,
                        "https://i.ytimg.com/vi/5m2pX7p9m30/hqdefault.jpg", "YouTube+",
                        "Patricio Rey Redondos Racing en vivo recital completo", null),
                new PosterItem("Pink Floyd: P.U.L.S.E. Concierto Completo", 1994, false,
                        "https://i.ytimg.com/vi/3m4P9q7P9e0/hqdefault.jpg", "YouTube+",
                        "Pink Floyd Pulse full concert hd", null),
                new PosterItem("MTV Unplugged: Charly García & Cadillacs", 1995, false,
                        "https://i.ytimg.com/vi/9m2pX7p9m30/hqdefault.jpg", "YouTube+",
                        "Charly Garcia MTV Unplugged recital completo", null));
    }

    private java.util.List<PosterItem> defaultAdultChannelsPosters() {
        return java.util.Arrays.asList(
                new PosterItem("Venus Latino HD (+18)", 2026, false,
                        "https://i.imgur.com/8QO9n8R.png", "LiveTV", "Venus Latino", null),
                new PosterItem("Playboy TV HD (+18)", 2026, false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/Playboy_TV_Logo.svg/600px-Playboy_TV_Logo.svg.png",
                        "LiveTV", "Playboy TV", null),
                new PosterItem("Hustler TV HD (+18)", 2026, false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Hustler_TV_Logo.svg/600px-Hustler_TV_Logo.svg.png",
                        "LiveTV", "Hustler TV", null),
                new PosterItem("Private Gold TV (+18)", 2026, false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/Private_Media_Group_logo.svg/600px-Private_Media_Group_logo.svg.png",
                        "LiveTV", "Private Gold", null),
                new PosterItem("RedTraffic Live (+18)", 2026, false,
                        "https://i.imgur.com/Xw5N5xY.png", "LiveTV", "RedTraffic Live", null),
                new PosterItem("Brazzers TV HD (+18)", 2026, false,
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Brazzers_Logo.svg/600px-Brazzers_Logo.svg.png",
                        "LiveTV", "Brazzers TV", null));
    }

    private java.util.List<PosterItem> defaultAdultMoviesPosters() {
        return java.util.Arrays.asList(
                new PosterItem("Erotic Desires: Relaciones Prohibidas (+18)", 2024, false,
                        "https://image.tmdb.org/t/p/w342/7K4gLd3Lp11K54M1d9jX5N1Q2o.jpg", "CineHax", "Erotic Desires",
                        null),
                new PosterItem("Noches de Pasión & Seducción (+18)", 2023, false,
                        "https://image.tmdb.org/t/p/w342/3w3K8v5L6K7eXq3P1j8N3X5P.jpg", "CineHax", "Noches de Pasion",
                        null),
                new PosterItem("Instinto Íntimo (+18)", 2024, false,
                        "https://image.tmdb.org/t/p/w342/4K2pQ8e2c33pWz2wK8c8jY1U2b.jpg", "CineHax", "Instinto Intimo",
                        null),
                new PosterItem("Fantasías Sin Límites (+18)", 2023, false,
                        "https://image.tmdb.org/t/p/w342/9kLmQ8e2c33pWz2wK8c8jY1U2b.jpg", "CineHax", "Fantasias",
                        null));
    }

    private java.util.List<PosterItem> defaultHentaiPosters() {
        return java.util.Arrays.asList(
                new PosterItem("Anime Adultos: Academia Secreta (+18)", 2024, true,
                        "https://image.tmdb.org/t/p/w342/geCRueV3ElhRTr0Q2xBuMiXLzAc.jpg", "LatAnime", "Hentai", null),
                new PosterItem("Animación Sin Censura: Destino Íntimo (+18)", 2023, true,
                        "https://image.tmdb.org/t/p/w342/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg", "LatAnime", "Hentai", null),
                new PosterItem("Pasión en la Mansión: OVA Especial (+18)", 2024, true,
                        "https://image.tmdb.org/t/p/w342/hFWP5HkbVEe40hrXgtCeKpRFHG2.jpg", "LatAnime", "Hentai", null));
    }

    private java.util.List<PosterItem> defaultAsianFantasy(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Aterrizaje de Emergencia en tu Corazón", 2020, true,
                        "https://image.tmdb.org/t/p/w342/q3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", "CineHax",
                        "Crash Landing", null),
                new PosterItem("Goblin: El Guardián Solitario", 2016, true,
                        "https://image.tmdb.org/t/p/w342/8jClvC5U756Uhd8zDYek50ihO49.jpg", "CineHax", "Goblin",
                        null),
                new PosterItem("Belleza Verdadera (True Beauty)", 2021, true,
                        "https://image.tmdb.org/t/p/w342/xcOnCOpwe86Deau7P1VQNe0X6cg.jpg", "CineHax", "True Beauty",
                        null),
                new PosterItem("Mi Amor de las Estrellas", 2014, true,
                        "https://image.tmdb.org/t/p/w342/xx3Zurg2mVep7UhXAjeYuetOvsB.jpg", "CineHax",
                        "My Love from the Star", null),
                new PosterItem("La Gloria", 2023, true,
                        "https://image.tmdb.org/t/p/w342/MIcVR5JVNBjvmd6xW6xnNbory5.jpg", "CineHax", "The Glory",
                        null));
    }

    private java.util.List<PosterItem> defaultAnimeShonen() {
        return java.util.Arrays.asList(
                new PosterItem("Demon Slayer: Kimetsu no Yaiba", 2024, true,
                        "https://image.tmdb.org/t/p/w342/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg", "LatAnime", "Demon Slayer",
                        null),
                new PosterItem("Jujutsu Kaisen", 2023, true,
                        "https://image.tmdb.org/t/p/w342/hFWP5HkbVEe40hrXgtCeKpRFHG2.jpg", "LatAnime", "Jujutsu Kaisen",
                        null),
                new PosterItem("Attack on Titan (Shingeki no Kyojin)", 2023, true,
                        "https://image.tmdb.org/t/p/w342/hTP1DtLGFamjfu8WqjnuQdP1n4i.jpg", "LatAnime", "Shingeki",
                        null),
                new PosterItem("One Piece", 2024, true, "https://image.tmdb.org/t/p/w342/dum1NmioVvXjH4x1q2q3q4r5.jpg",
                        "LatAnime", "One Piece", null),
                new PosterItem("Solo Leveling", 2024, true,
                        "https://image.tmdb.org/t/p/w342/geCRueV3ElhRTr0Q2xBuMiXLzAc.jpg", "LatAnime", "Solo Leveling",
                        null));
    }

    private java.util.List<PosterItem> defaultClassicMovies() {
        return java.util.Arrays.asList(
                new PosterItem("La Noche de los Muertos Vivos", 1968, false,
                        "https://archive.org/services/img/night_of_the_living_dead", "Internet Archive",
                        "Night of the living dead", null),
                new PosterItem("Metrópolis", 1927, false, "https://archive.org/services/img/Metropolis_1927",
                        "Internet Archive", "Metropolis", null),
                new PosterItem("Charada", 1963, false, "https://archive.org/services/img/Charade_1963",
                        "Internet Archive", "Charade", null),
                new PosterItem("Superman (1941)", 1941, false, "https://archive.org/services/img/superman_1941",
                        "Internet Archive", "Superman", null),
                new PosterItem("El Maquinista de la General", 1926, false,
                        "https://archive.org/services/img/TheGeneralBusterKeaton", "Internet Archive", "The General",
                        null));
    }

    private java.util.List<PosterItem> defaultBlenderMovies() {
        return java.util.Arrays.asList(
                new PosterItem("Big Buck Bunny", 2008, false,
                        "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
                        "Blender Open Movies", "Big Buck Bunny", null),
                new PosterItem("Elephants Dream", 2006, false,
                        "https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov", "Blender Open Movies",
                        "Elephants Dream", null),
                new PosterItem("Sintel", 2010, false,
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                        "Blender Open Movies", "Sintel", null),
                new PosterItem("Tears of Steel", 2012, false,
                        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        "Blender Open Movies", "Tears of Steel", null));
    }

    private java.util.List<String> getDefaultCarouselOrder() {
        return java.util.Arrays.asList(
                "top10", "movies2026", "turkish", "turkish_movies", "kdramas", "korean_movies", "popular_movies",
                "series2026",
                "latin", "live_music", "asian_fantasy", "anime_airing", "shonen", "anime_movies",
                "action", "terror", "scifi", "comedy", "romance", "crime",
                "historical", "documentaries", "kids", "classic", "sports");
    }

    private String getCarouselDisplayName(String id) {
        if ("top10".equals(id))
            return "🔥  Top 10 en Leo TV hoy";
        if ("movies2026".equals(id))
            return "🎬  Estrenos de Cine y Cartelera 2026";
        if ("turkish".equals(id))
            return "🇹🇷  Novelas y Series Turcas";
        if ("turkish_movies".equals(id))
            return "🇹🇷  Cine y Películas Turcas";
        if ("kdramas".equals(id))
            return "🇰🇷  K-Dramas y Series Coreanas";
        if ("korean_movies".equals(id))
            return "🇰🇷  Cine y Películas Coreanas";
        if ("popular_movies".equals(id))
            return "⭐  Películas Populares y Aclamadas";
        if ("series2026".equals(id))
            return "📺  Series nuevas · 2026";
        if ("latin".equals(id))
            return "🇲🇽 🇨🇴 🇦🇷  Series y Novelas Latinas";
        if ("live_music".equals(id))
            return "🎸  Conciertos en Vivo y Especiales Musicales";
        if ("asian_fantasy".equals(id))
            return "🏯  Doramas Románticos y Fantasía Asiática";
        if ("anime_airing".equals(id))
            return "⚡  Anime en Emisión y Tendencias";
        if ("shonen".equals(id))
            return "⚔️  Anime Shonen y Grandes Batallas";
        if ("anime_movies".equals(id))
            return "🎌  Películas de Anime";
        if ("action".equals(id))
            return "💥  Acción y Aventura Sin Límites";
        if ("terror".equals(id))
            return "👻  Terror, Suspenso y Misterio";
        if ("scifi".equals(id))
            return "🚀  Ciencia Ficción y Futuros Distópicos";
        if ("comedy".equals(id))
            return "😂  Comedia y Risas en Familia";
        if ("romance".equals(id))
            return "❤️  Romance, Pasión y Melodrama";
        if ("crime".equals(id))
            return "🕵️  Crimen, Mafia y Suspense Policial";
        if ("historical".equals(id))
            return "👑  Dramas de Época e Históricos";
        if ("documentaries".equals(id))
            return "🌿  Documentales y Naturaleza";
        if ("kids".equals(id))
            return "👶  Kids y Animación Familiar";
        if ("classic".equals(id))
            return "🏛️  Cine Clásico de Oro (Dominio Público)";
        if ("sports".equals(id))
            return "⚽  Deportes y Eventos en Vivo";
        return id;
    }

    private java.util.List<PosterItem> defaultPopularMovies() {
        return java.util.Arrays.asList(
                new PosterItem("Dune: Parte Dos", 2024, false,
                        "https://image.tmdb.org/t/p/w342/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg", "CineHax", "Dune: Parte Dos",
                        null),
                new PosterItem("Oppenheimer", 2023, false,
                        "https://image.tmdb.org/t/p/w342/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg", "CineHax", "Oppenheimer",
                        null),
                new PosterItem("Gladiador II", 2024, false,
                        "https://image.tmdb.org/t/p/w342/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg", "CineHax", "Gladiador II",
                        null),
                new PosterItem("Avatar: El Sentido del Agua", 2022, false,
                        "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", "CineHax",
                        "Avatar: El Sentido del Agua", null),
                new PosterItem("Top Gun: Maverick", 2022, false,
                        "https://image.tmdb.org/t/p/w342/62HCnUTziyWcpDaBO2i1DX17ljH.jpg", "CineHax",
                        "Top Gun: Maverick", null));
    }

    private java.util.List<PosterItem> defaultAnimeAiring() {
        return java.util.Arrays.asList(
                new PosterItem("Solo Leveling", 2024, true,
                        "https://image.tmdb.org/t/p/w342/geCRueV3ElhRTr0xtJuPxJ8BGdM.jpg", "LatAnime", "Solo Leveling",
                        null),
                new PosterItem("Jujutsu Kaisen", 2023, true,
                        "https://image.tmdb.org/t/p/w342/hFWP5HkbVEe40hrXgtCeNpRe4Qf.jpg", "LatAnime", "Jujutsu Kaisen",
                        null),
                new PosterItem("Frieren: Más allá del final del viaje", 2024, true,
                        "https://image.tmdb.org/t/p/w342/dqZenchTd7lp5zht7BdlqM7RBhD.jpg", "LatAnime", "Frieren", null),
                new PosterItem("Chainsaw Man", 2023, true,
                        "https://image.tmdb.org/t/p/w342/npdB6eFz4qt9CdFOEgVuinxyzYZ.jpg", "LatAnime", "Chainsaw Man",
                        null),
                new PosterItem("Demon Slayer: Kimetsu no Yaiba", 2024, true,
                        "https://image.tmdb.org/t/p/w342/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg", "LatAnime", "Demon Slayer",
                        null));
    }

    private java.util.List<PosterItem> defaultAnimeMovies() {
        return java.util.Arrays.asList(
                new PosterItem("Your Name (Kimi no Na wa)", 2016, false,
                        "https://image.tmdb.org/t/p/w342/q719jXXEzOoYaps6qFsxWa93oMm.jpg", "LatAnime", "Your Name",
                        null),
                new PosterItem("El Viaje de Chihiro", 2001, false,
                        "https://image.tmdb.org/t/p/w342/39wmItIWsg5sZMyRUHLkWBcuVCM.jpg", "LatAnime",
                        "El Viaje de Chihiro", null),
                new PosterItem("Suzume", 2022, false,
                        "https://image.tmdb.org/t/p/w342/vIeu8WyssvqLdBP2RT9gTq8JWGM.jpg", "LatAnime", "Suzume", null),
                new PosterItem("Kimetsu no Yaiba: El Tren Infinito", 2020, false,
                        "https://image.tmdb.org/t/p/w342/h8Rb9gBr48ODIwYUtZNYPkMWe3f.jpg", "LatAnime",
                        "El Tren Infinito", null),
                new PosterItem("El Tiempo Contigo", 2019, false,
                        "https://image.tmdb.org/t/p/w342/qgrkAmwH5OCz44T1eY69neB914Y.jpg", "LatAnime",
                        "El Tiempo Contigo", null));
    }

    private java.util.List<PosterItem> defaultDocumentaries() {
        return java.util.Arrays.asList(
                new PosterItem("Planeta Tierra III", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/6/15320.jpg", "CineHax",
                        "Planeta Tierra", null),
                new PosterItem("Nuestro Planeta", 2019, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/606/1516540.jpg", "CineHax",
                        "Nuestro Planeta", null),
                new PosterItem("Cosmos: Mundos Posibles", 2020, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/402/1006408.jpg", "CineHax",
                        "Cosmos", null),
                new PosterItem("The Last Dance", 2020, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/252/632118.jpg", "CineHax",
                        "The Last Dance", null),
                new PosterItem("La Vida en Nuestro Planeta", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/606/1516202.jpg", "CineHax",
                        "Life on Our Planet", null),
                new PosterItem("Fórmula 1: Drive to Survive", 2024, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/615/1539219.jpg", "CineHax",
                        "Formula 1", null),
                new PosterItem("El Universo", 2021, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/3/9118.jpg", "CineHax", "Universe",
                        null));
    }

    private java.util.List<PosterItem> defaultDocNature() {
        return java.util.Arrays.asList(
                new PosterItem("Planeta Tierra III", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/6/15320.jpg", "CineHax",
                        "Planeta Tierra", null),
                new PosterItem("Nuestro Planeta", 2019, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/606/1516540.jpg", "CineHax",
                        "Nuestro Planeta", null),
                new PosterItem("La Vida en Nuestro Planeta", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/606/1516202.jpg", "CineHax",
                        "Life on Our Planet", null),
                new PosterItem("Planeta Azul", 2020, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/649/1623577.jpg", "CineHax",
                        "Blue Planet", null),
                new PosterItem("El Imperio de los Chimpancés", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/454/1135188.jpg", "CineHax",
                        "Chimp Empire", null));
    }

    private java.util.List<PosterItem> defaultDocHistory() {
        return java.util.Arrays.asList(
                new PosterItem("La Segunda Guerra Mundial en HD", 2018, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/6/15103.jpg", "CineHax", "WWII",
                        null),
                new PosterItem("Grandes Civilizaciones de la Historia", 2022, true,
                        "https://image.tmdb.org/t/p/w342/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg", "CineHax", "Civilizaciones",
                        null),
                new PosterItem("Roma: Auge y Caída del Imperio", 2021, true,
                        "https://image.tmdb.org/t/p/w342/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg", "CineHax", "Roma", null),
                new PosterItem("Tumbas y Secretos de Egipto", 2020, true,
                        "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", "CineHax", "Egipto", null));
    }

    private java.util.List<PosterItem> defaultDocScience() {
        return java.util.Arrays.asList(
                new PosterItem("Cosmos: Mundos Posibles", 2020, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/402/1006408.jpg", "CineHax",
                        "Cosmos", null),
                new PosterItem("A Través del Agujero de Gusano", 2017, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/8/21801.jpg", "CineHax",
                        "Wormhole", null),
                new PosterItem("Una Roca Extraña (Will Smith)", 2018, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/150/376590.jpg", "CineHax",
                        "Strange Rock", null),
                new PosterItem("Inteligencia Artificial y el Futuro", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/498/1246519.jpg", "CineHax",
                        "Inteligencia Artificial", null));
    }

    private java.util.List<PosterItem> defaultDocSpace() {
        return java.util.Arrays.asList(
                new PosterItem("El Universo", 2021, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/3/9118.jpg", "CineHax", "Universe",
                        null),
                new PosterItem("Cosmos: Odisea Espacial", 2020, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/402/1006408.jpg", "CineHax",
                        "Cosmos", null),
                new PosterItem("Marte: La Próxima Frontera Humana", 2022, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/8/21801.jpg", "CineHax", "Mars",
                        null),
                new PosterItem("Telescopio James Webb en el Espacio Profundo", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/150/376590.jpg", "CineHax",
                        "James Webb", null));
    }

    private java.util.List<PosterItem> defaultDocCrime() {
        return java.util.Arrays.asList(
                new PosterItem("The Last Dance (Michael Jordan)", 2020, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/252/632118.jpg", "CineHax",
                        "The Last Dance", null),
                new PosterItem("Fórmula 1: Drive to Survive", 2024, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/615/1539219.jpg", "CineHax",
                        "Formula 1", null),
                new PosterItem("Misterios sin Resolver (Investigación)", 2022, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/21/53051.jpg", "CineHax",
                        "Misterios", null),
                new PosterItem("El Imperio del Dinero y el Crimen", 2023, true,
                        "https://static.tvmaze.com/uploads/images/original_untouched/576/1440521.jpg", "CineHax",
                        "Crimen", null));
    }

    private java.util.List<PosterItem> defaultMovies2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Colisión", 2026, false,
                        "https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", prov, "Colision", null),
                new PosterItem("Velocidad y Deseo", 2026, false,
                        "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Drive", null),
                new PosterItem("Contra el huracán", 2026, false,
                        "https://image.tmdb.org/t/p/w342/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg", prov, "Huracan", null),
                new PosterItem("Masterplan", 2026, false,
                        "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Masterplan", null),
                new PosterItem("Tenzing", 2026, false,
                        "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", prov, "Tenzing", null),
                new PosterItem("Sacrificio: Los Elegidos", 2026, false,
                        "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", prov, "Sacrifice", null));
    }

    private java.util.List<PosterItem> defaultSeries2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Lupin", 2026, true, "https://image.tmdb.org/t/p/w342/sgxawbFB5Vi5OkPWQLNfl3dvkNJ.jpg",
                        prov, "Lupin", null),
                new PosterItem("La lista terminal", 2026, true,
                        "https://image.tmdb.org/t/p/w342/71f3JHlJ3G5G0Fp7b3uX2hB8X1B.jpg", prov, "Terminal", null),
                new PosterItem("Cyberpunk: Edgerunners", 2026, true,
                        "https://image.tmdb.org/t/p/w342/7jSWOc6jWSw5hZ78HB8Hw3pJxuk.jpg", prov, "Cyberpunk", null),
                new PosterItem("La mesa final", 2026, true,
                        "https://image.tmdb.org/t/p/w342/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg", prov, "Mesa", null),
                new PosterItem("Tulsa King", 2026, true,
                        "https://image.tmdb.org/t/p/w342/fwTv3RPRAIy0maOMns5eYRRwnDk.jpg", prov, "Tulsa King", null),
                new PosterItem("Crystal Lake", 2026, true,
                        "https://image.tmdb.org/t/p/w342/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", prov, "Crystal", null));
    }

    private java.util.List<PosterItem> defaultAction2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("El Despertar", 2026, false,
                        "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", prov, "Despertar", null),
                new PosterItem("Sacrificio: Los Elegidos", 2026, false,
                        "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", prov, "Sacrifice", null),
                new PosterItem("Animals", 2026, false,
                        "https://image.tmdb.org/t/p/w342/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg", prov, "Animals", null),
                new PosterItem("Matchbox the Movie", 2026, false,
                        "https://image.tmdb.org/t/p/w342/qhb1qOilapbapxWQn9jtRCMwXJF.jpg", prov, "Matchbox", null),
                new PosterItem("Hope: El primer impacto", 2026, false,
                        "https://image.tmdb.org/t/p/w342/gKkl37BQuKTanygYQG1pyYgLVgf.jpg", prov, "Hope", null),
                new PosterItem("Entrega Al Límite", 2026, false,
                        "https://image.tmdb.org/t/p/w342/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg", prov, "Runner", null));
    }

    private java.util.List<PosterItem> defaultComedy2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Ali G: Kién Ez Yo?", 2026, false,
                        "https://image.tmdb.org/t/p/w342/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg", prov, "Ali G", null),
                new PosterItem("Masterplan", 2026, false,
                        "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Masterplan", null),
                new PosterItem("Matchbox the Movie", 2026, false,
                        "https://image.tmdb.org/t/p/w342/qhb1qOilapbapxWQn9jtRCMwXJF.jpg", prov, "Matchbox", null),
                new PosterItem("Shaun, el cordero: El monstruo", 2026, false,
                        "https://image.tmdb.org/t/p/w342/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg", prov, "Shaun", null),
                new PosterItem("Entrega Al Límite", 2026, false,
                        "https://image.tmdb.org/t/p/w342/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg", prov, "Entrega", null));
    }

    private java.util.List<PosterItem> defaultTerror2026(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Resident Evil: Noche Cero", 2026, false,
                        "https://image.tmdb.org/t/p/w342/ntmaNFJ7O60n45E1E6HZlXeSV5e.jpg", prov, "Resident Evil", null),
                new PosterItem("Apocalipsis Z: Nuevo mundo", 2026, false,
                        "https://image.tmdb.org/t/p/w342/pS2R3DEiucuizVkKP80qzdO97TE.jpg", prov, "Apocalipsis Z", null),
                new PosterItem("El Despertar", 2026, false,
                        "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", prov, "Awakening", null),
                new PosterItem("Backrooms: Sin salida", 2026, false,
                        "https://image.tmdb.org/t/p/w342/3YTb97zytlWRWZb7HrOiEhk4e6f.jpg", prov, "Backrooms", null),
                new PosterItem("La Sustancia", 2024, false,
                        "https://image.tmdb.org/t/p/w342/w1PiIqM89r4AM7CiMEP4VLCEFUn.jpg", prov, "La Sustancia", null));
    }

    private java.util.List<PosterItem> defaultTurkishNovelas(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov, "Erkenci Kus",
                        null),
                new PosterItem("Kara Sevda (Amor Eterno)", 2015, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/34/86603.jpg", prov, "Kara Sevda",
                        null),
                new PosterItem("Love is in the Air (Sen Çal Kapımı)", 2020, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/267/668853.jpg", prov,
                        "Sen Cal Kapimi", null),
                new PosterItem("Hercai", 2019, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/197/494639.jpg", prov, "Hercai",
                        null),
                new PosterItem("El Turco", 2025, true,
                        "https://image.tmdb.org/t/p/w342/oJ7DyCIxyVQ0GlCmk7oAGSQgnr9.jpg", prov, "El Turco", null),
                new PosterItem("Taşacak Bu Deniz", 2025, true,
                        "https://image.tmdb.org/t/p/w342/uHIOTJXN9nNTc51WyunL43Fvge3.jpg", prov, "Tasacak Bu Deniz",
                        null),
                new PosterItem("Leyla: Hayat... Aşk... Adalet...", 2024, true,
                        "https://image.tmdb.org/t/p/w342/ujZQDNdMbGSWk3I6biCuw2Uf5uO.jpg", prov, "Leyla", null),
                new PosterItem("Yargi (Secretos de Familia)", 2021, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/367/918118.jpg", prov, "Yargi",
                        null));
    }

    private java.util.List<PosterItem> defaultKDramas(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("El juego del calamar", 2021, true,
                        "https://image.tmdb.org/t/p/w342/xx3Zurg2mVep7UhXAjeYuetOvsB.jpg", prov, "Juego del calamar",
                        null),
                new PosterItem("Alice in Borderland", 2020, true,
                        "https://image.tmdb.org/t/p/w342/xcOnCOpwe86Deau7P1VQNe0X6cg.jpg", prov, "Alice in Borderland",
                        null),
                new PosterItem("Estamos Muertos", 2022, true,
                        "https://image.tmdb.org/t/p/w342/8jClvC5U756Uhd8zDYek50ihO49.jpg", prov, "Estamos muertos",
                        null),
                new PosterItem("Parásitos", 2019, false,
                        "https://image.tmdb.org/t/p/w342/j4AOjNlhvudYDijiT0lxeHAsPkk.jpg", prov, "Parasitos", null),
                new PosterItem("Dulce hogar", 2020, true,
                        "https://image.tmdb.org/t/p/w342/MIcVR5JVNBjvmd6xW6xnNbory5.jpg", prov, "Dulce hogar", null));
    }

    private java.util.List<PosterItem> defaultSciFi(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Dune: Parte Dos", 2024, false,
                        "https://image.tmdb.org/t/p/w342/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg", prov, "Dune", null),
                new PosterItem("Interstellar", 2014, false,
                        "https://image.tmdb.org/t/p/w342/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", prov, "Interstellar", null),
                new PosterItem("Cyberpunk: Edgerunners", 2022, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/498/1246519.jpg", "LatAnime",
                        "Cyberpunk", null),
                new PosterItem("El Problema de los 3 Cuerpos", 2024, true,
                        "https://image.tmdb.org/t/p/w342/yv0Qv829k7X6dE1i75eK77jU0q.jpg", prov, "3 Cuerpos", null),
                new PosterItem("Alien: Romulus", 2024, false,
                        "https://image.tmdb.org/t/p/w342/8PYqGSd8MOm5ce8io4qNSAiSExW.jpg", prov, "Alien Romulus",
                        null));
    }

    private java.util.List<PosterItem> defaultRomance(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Culpa Mía", 2023, false,
                        "https://image.tmdb.org/t/p/w342/w46Vw536HwNnEzOa7o24Te0JJDb.jpg", prov, "Culpa Mia", null),
                new PosterItem("A Través de mi Ventana", 2022, false,
                        "https://image.tmdb.org/t/p/w342/6gg7fvKc195viCD5jqom4El24vP.jpg", prov,
                        "A traves de mi ventana", null),
                new PosterItem("Fabricante de Lágrimas", 2024, false,
                        "https://image.tmdb.org/t/p/w342/9JBEPL0T2eHNG7FvJ6n5KekjOqL.jpg", prov, "Lagrimas", null),
                new PosterItem("La Idea de Ti", 2024, false,
                        "https://image.tmdb.org/t/p/w342/z1p34vh7dEOnLDmyCrlUVLuoDzd.jpg", prov, "Idea de ti", null),
                new PosterItem("365 días (2020)", 2020, false,
                        "https://image.tmdb.org/t/p/w342/jz8T3hrU6GuMqSuQ4Rbd4MJUeaq.jpg", prov, "365", null),
                new PosterItem("Cincuenta Sombras de Grey", 2015, false,
                        "https://image.tmdb.org/t/p/w342/mNZcZOIlTwDKd30xLnRR4p0ZELg.jpg", prov, "Cincuenta sombras",
                        null));
    }

    private java.util.List<PosterItem> defaultKids(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Red: Crecer es una Fiera", 2022, false,
                        "https://image.tmdb.org/t/p/w342/qsdjk9oAKSQMWs0Vt5Pyfh6O4GZ.jpg", prov, "Red", null),
                new PosterItem("Supergatitos", 2026, true,
                        "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Superkitties", null),
                new PosterItem("Cars en la Carretera", 2026, true,
                        "https://image.tmdb.org/t/p/w342/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg", prov, "Cars", null),
                new PosterItem("Tadeo el explorador", 2026, false,
                        "https://image.tmdb.org/t/p/w342/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg", prov, "Tadeo", null),
                new PosterItem("Hot Wheels Let's Race", 2026, true,
                        "https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", prov, "Hot Wheels", null));
    }

    private java.util.List<PosterItem> defaultAnime() {
        return java.util.Arrays.asList(
                new PosterItem("Black Clover", 2026, true,
                        "https://image.tmdb.org/t/p/w342/kaMisGG2U2R38L4G5G8xGvL4J0Z.jpg", "LatAnime", "Black Clover",
                        null),
                new PosterItem("Dragon Ball Super", 2026, true,
                        "https://image.tmdb.org/t/p/w342/qEUrbXJ2qt4Rg84Btlx4STOhgte.jpg", "LatAnime", "Dragon Ball",
                        null),
                new PosterItem("Bleach: Thousand-Year", 2026, true,
                        "https://image.tmdb.org/t/p/w342/2EewmxXe72ogD0EaWM8gqa0ccIw.jpg", "LatAnime", "Bleach", null),
                new PosterItem("Seven Deadly Sins", 2026, true,
                        "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", "JKAnime", "Nanatsu", null),
                new PosterItem("Cyberpunk: Edgerunners", 2026, true,
                        "https://image.tmdb.org/t/p/w342/7jSWOc6jWSw5hZ78HB8Hw3pJxuk.jpg", "LatAnime", "Cyberpunk",
                        null));
    }

    private java.util.List<PosterItem> defaultTop10(String prov) {
        return java.util.Arrays.asList(
                new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov, "Erkenci Kus",
                        null),
                new PosterItem("Colisión", 2026, false,
                        "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Colision", null),
                new PosterItem("Cyberpunk: Edgerunners", 2026, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/498/1246519.jpg", "LatAnime",
                        "Cyberpunk", null),
                new PosterItem("Tulsa King", 2026, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/646/1615902.jpg", prov, "Tulsa King",
                        null),
                new PosterItem("Velocidad y Deseo", 2026, false,
                        "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Drive", null),
                new PosterItem("Lupin", 2026, true,
                        "https://static.tvmaze.com/uploads/images/medium_portrait/646/1617244.jpg", prov, "Lupin",
                        null),
                new PosterItem("El Despertar", 2026, false,
                        "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", prov, "Despertar", null),
                new PosterItem("La lista terminal", 2026, true,
                        "https://image.tmdb.org/t/p/w342/71f3JHlJ3G5G0Fp7b3uX2hB8X1B.jpg", prov, "Terminal", null),
                new PosterItem("Tenzing", 2026, false,
                        "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", prov, "Tenzing", null),
                new PosterItem("Sacrificio: Los Elegidos", 2026, false,
                        "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", prov, "Sacrifice", null));
    }

    private static class FeaturedHeroItem {
        final String title;
        final String meta;
        final String synopsis;
        final String backdropUrl;
        final PosterItem posterItem;

        FeaturedHeroItem(String title, String meta, String synopsis, String backdropUrl, PosterItem posterItem) {
            this.title = title;
            this.meta = meta;
            this.synopsis = synopsis;
            this.backdropUrl = backdropUrl;
            this.posterItem = posterItem;
        }
    }

    private java.util.List<FeaturedHeroItem> defaultFeaturedSlides(String prov) {
        return java.util.Arrays.asList(
                new FeaturedHeroItem(
                        "Dune: Parte Dos (2024)",
                        "⭐ 8.5 TMDB  ·  2024  ·  166 min  ·  Ciencia Ficción / Épico  ·  Audio Latino 5.1",
                        "Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra los conspiradores que destruyeron a su familia en el desierto de Arrakis.",
                        "https://image.tmdb.org/t/p/w780/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                        new PosterItem("Dune: Parte Dos", 2024, false,
                                "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", prov, "Dune", null)),
                new FeaturedHeroItem(
                        "Shōgun (2024)",
                        "⭐ 8.5 TMDB  ·  2024  ·  10 Episodios  ·  Drama Histórico Samurái  ·  Audio Latino",
                        "En el Japón feudal de 1600, Lord Yoshii Toranaga lucha por su supervivencia mientras sus enemigos en el Consejo de Regentes conspiran en su contra.",
                        "https://image.tmdb.org/t/p/w780/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
                        new PosterItem("Shōgun", 2024, true,
                                "https://image.tmdb.org/t/p/w342/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg", prov, "Shogun",
                                null)),
                new FeaturedHeroItem(
                        "Deadpool & Wolverine (2024)",
                        "⭐ 7.7 TMDB  ·  2024  ·  127 min  ·  Acción / Comedia Marvel  ·  Audio Latino",
                        "Un apático Wade Wilson se esfuerza por adaptarse a la vida civil cuando una amenaza existencial lo obliga a buscar la ayuda de un Wolverine reticente.",
                        "https://image.tmdb.org/t/p/w780/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                        new PosterItem("Deadpool & Wolverine", 2024, false,
                                "https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", prov, "Deadpool",
                                null)),
                new FeaturedHeroItem(
                        "Fallout (2024)",
                        "⭐ 8.4 TMDB  ·  2024  ·  8 Episodios  ·  Ciencia Ficción Postapocalíptica  ·  Audio Latino",
                        "Doscientos años después de un apocalipsis nuclear, los pacíficos habitantes de lujosos refugios atómicos regresan al desierto irradiado de la superficie.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/599/1499142.jpg",
                        new PosterItem("Fallout", 2024, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/599/1499142.jpg", prov,
                                "Fallout", null)),
                new FeaturedHeroItem(
                        "Erkenci Kuş (Pájaro Soñador)",
                        "⭐ 8.0 TVMaze  ·  2018  ·  51 Capítulos  ·  Comedia Romántica Turca  ·  Audio Latino",
                        "Sanem es una joven soñadora que trabaja en la tienda de su padre. Al comenzar en la prestigiosa agencia de Can Divit, su vida cambia entre enredos y un romance inolvidable.",
                        "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg",
                        new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true,
                                "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov,
                                "Erkenci Kus", null)));
    }

    private java.util.List<FeaturedHeroItem> defaultMovieSlides(String prov) {
        return java.util.Arrays.asList(
                new FeaturedHeroItem(
                        "Dune: Parte Dos (2024)",
                        "⭐ 8.5 TMDB  ·  2024  ·  166 min  ·  Ciencia Ficción / Épico  ·  Audio Latino",
                        "Paul Atreides se une a Chani y a los Fremen mientras busca venganza contra los conspiradores que destruyeron a su familia en el desierto de Arrakis.",
                        "https://image.tmdb.org/t/p/w780/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                        new PosterItem("Dune: Parte Dos", 2024, false,
                                "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", prov, "Dune", null)),
                new FeaturedHeroItem(
                        "Oppenheimer (2023)",
                        "⭐ 8.1 TMDB  ·  2023  ·  180 min  ·  Drama Histórico / Biopic  ·  Audio Latino",
                        "La historia del físico teórico J. Robert Oppenheimer y su rol decisivo en el desarrollo de la primera bomba atómica en el Proyecto Manhattan.",
                        "https://image.tmdb.org/t/p/w780/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
                        new PosterItem("Oppenheimer", 2023, false,
                                "https://image.tmdb.org/t/p/w342/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg", prov, "Oppenheimer",
                                null)),
                new FeaturedHeroItem(
                        "Deadpool & Wolverine (2024)",
                        "⭐ 7.7 TMDB  ·  2024  ·  127 min  ·  Acción / Aventura Marvel  ·  Audio Latino",
                        "Wade Wilson debe dejar su retiro para salvar su universo formando una alianza explosiva con Wolverine.",
                        "https://image.tmdb.org/t/p/w780/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                        new PosterItem("Deadpool & Wolverine", 2024, false,
                                "https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", prov, "Deadpool",
                                null)),
                new FeaturedHeroItem(
                        "Intensamente 2 (2024)",
                        "⭐ 7.6 TMDB  ·  2024  ·  96 min  ·  Animación / Familiar  ·  Audio Latino",
                        "Nuevas emociones llegan a la mente de Riley mientras atraviesa la adolescencia con Ansiedad, Vergüenza y Envidia al mando.",
                        "https://image.tmdb.org/t/p/w780/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
                        new PosterItem("Intensamente 2", 2024, false,
                                "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov,
                                "Intensamente 2", null)),
                new FeaturedHeroItem(
                        "Gladiador II (2024)",
                        "⭐ 7.5 TMDB  ·  2024  ·  148 min  ·  Acción / Épico Histórico  ·  Audio Latino",
                        "Años después de presenciar la muerte del venerado héroe Máximo a manos de su tío, Lucio debe entrar en el Coliseo tras ser conquistado su hogar.",
                        "https://image.tmdb.org/t/p/w780/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg",
                        new PosterItem("Gladiador II", 2024, false,
                                "https://image.tmdb.org/t/p/w342/2cxhvwyEwRlysAmRH4iodkvo0z5.jpg", prov, "Gladiador",
                                null)));
    }

    private java.util.List<FeaturedHeroItem> defaultSeriesSlides(String prov) {
        return java.util.Arrays.asList(
                new FeaturedHeroItem(
                        "Shōgun (2024)",
                        "⭐ 8.5 TMDB  ·  2024  ·  10 Capítulos  ·  Drama Épico / Samuráis  ·  Audio Latino",
                        "En el convulsionado Japón de 1600, Lord Yoshii Toranaga descubre una ventaja secreta cuando un misterioso barco europeo encalla en su costa.",
                        "https://image.tmdb.org/t/p/w780/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
                        new PosterItem("Shōgun", 2024, true,
                                "https://image.tmdb.org/t/p/w342/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg", prov, "Shogun",
                                null)),
                new FeaturedHeroItem(
                        "The Boys (2024)",
                        "⭐ 8.5 TMDB  ·  2024  ·  Temporada 4  ·  Acción / Superhéroes  ·  Audio Latino",
                        "El mundo está al borde del abismo mientras Victoria Neuman se acerca a la Casa Blanca bajo el control dominante de Homelander.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/619/1547768.jpg",
                        new PosterItem("The Boys", 2024, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/619/1547768.jpg", prov,
                                "The Boys", null)),
                new FeaturedHeroItem(
                        "El juego del calamar (Squid Game)",
                        "⭐ 7.8 TMDB  ·  2024  ·  Temporadas 1 y 2  ·  Thriller Coreano  ·  Audio Latino",
                        "Cientos de jugadores desesperados aceptan una extraña invitación para competir en juegos infantiles con un premio tentador y consecuencias mortales.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/576/1440521.jpg",
                        new PosterItem("El juego del calamar", 2024, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/576/1440521.jpg", prov,
                                "Squid Game", null)),
                new FeaturedHeroItem(
                        "House of the Dragon (2024)",
                        "⭐ 8.4 TMDB  ·  2024  ·  Temporada 2  ·  Fantasía Épica HBO  ·  Audio Latino",
                        "La guerra civil de los Targaryen estalla mientras los bandos Negro y Verde luchan a sangre y fuego por el Trono de Hierro de Poniente.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/627/1568449.jpg",
                        new PosterItem("House of the Dragon", 2024, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/627/1568449.jpg", prov,
                                "House of the Dragon", null)),
                new FeaturedHeroItem(
                        "Fallout (2024)",
                        "⭐ 8.4 TMDB  ·  2024  ·  8 Episodios  ·  Ciencia Ficción Postapocalíptica  ·  Audio Latino",
                        "Doscientos años después del apocalipsis nuclear, los pacíficos habitantes de un lujoso refugio atómico deben salir al desierto irradiado de la superficie.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/599/1499142.jpg",
                        new PosterItem("Fallout", 2024, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/599/1499142.jpg", prov,
                                "Fallout", null)),
                new FeaturedHeroItem(
                        "Cyberpunk: Edgerunners",
                        "⭐ 8.6 TMDB  ·  2022  ·  10 Capítulos  ·  Anime / Acción Distópica  ·  Audio Latino",
                        "Un joven callejero marginado en Night City arriesga todo para sobrevivir convirtiéndose en mercenario cyberpunk con implantes de combate.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/498/1246519.jpg",
                        new PosterItem("Cyberpunk: Edgerunners", 2022, true,
                                "https://static.tvmaze.com/uploads/images/medium_portrait/498/1246519.jpg", "LatAnime",
                                "Cyberpunk", null)),
                new FeaturedHeroItem(
                        "Erkenci Kuş (Pájaro Soñador)",
                        "⭐ 8.0 TVMaze  ·  2018  ·  51 Capítulos  ·  Novela Romántica Turca  ·  Audio Latino",
                        "El apasionante melodrama turco donde Sanem y Can Divit desafían las mentiras y las intrigas para defender su verdadero amor.",
                        "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg",
                        new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true,
                                "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov,
                                "Erkenci Kus", null)));
    }

    private java.util.List<FeaturedHeroItem> defaultDocSlides(String prov) {
        return java.util.Arrays.asList(
                new FeaturedHeroItem(
                        "Planeta Tierra III (2023)",
                        "⭐ 9.0 TMDB  ·  2023  ·  8 Episodios  ·  Naturaleza / BBC Earth  ·  Audio Latino",
                        "David Attenborough narra las asombrosas estrategias de los animales más extraordinarios en los hábitats más remotos y amenazados de la Tierra.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/6/15320.jpg",
                        new PosterItem("Planeta Tierra III", 2023, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/6/15320.jpg", prov,
                                "Planet Earth", null)),
                new FeaturedHeroItem(
                        "Cosmos: Mundos Posibles (2020)",
                        "⭐ 8.8 TMDB  ·  2020  ·  13 Episodios  ·  Ciencia y Astronomía  ·  Neil deGrasse Tyson",
                        "Una travesía científica que transporta a los espectadores a través de miles de millones de años de evolución cósmica y futuros posibles de la humanidad.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/402/1006408.jpg",
                        new PosterItem("Cosmos: Mundos Posibles", 2020, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/402/1006408.jpg", prov,
                                "Cosmos", null)),
                new FeaturedHeroItem(
                        "Nuestro Planeta (Our Planet)",
                        "⭐ 8.7 TMDB  ·  2019  ·  8 Episodios  ·  Fauna y Biodiversidad  ·  4K Ultra HD",
                        "Una mirada sin precedentes a la belleza natural de nuestro mundo y el impacto crucial del cambio climático en todas las criaturas vivas.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/606/1516540.jpg",
                        new PosterItem("Nuestro Planeta", 2019, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/606/1516540.jpg", prov,
                                "Our Planet", null)),
                new FeaturedHeroItem(
                        "Fórmula 1: Drive to Survive",
                        "⭐ 8.6 TMDB  ·  2024  ·  6 Temporadas  ·  Deportes / Automovilismo  ·  Audio Latino",
                        "Pilotos, directores de equipo y propietarios viven la vida a máxima velocidad tanto dentro como fuera de la pista durante cada despiadada temporada de F1.",
                        "https://static.tvmaze.com/uploads/images/original_untouched/615/1539219.jpg",
                        new PosterItem("Fórmula 1: Drive to Survive", 2024, true,
                                "https://static.tvmaze.com/uploads/images/original_untouched/615/1539219.jpg", prov,
                                "Formula 1", null)));
    }

    private int currentHeroIndex = 0;
    private android.os.Handler heroSlideHandler = null;
    private Runnable heroSlideRunnable = null;

    private android.view.View buildHeroSlideShow(LinearLayout body, java.util.List<FeaturedHeroItem> slides) {
        if (slides.isEmpty())
            return null;
        if (currentHeroIndex >= slides.size())
            currentHeroIndex = 0;

        // Cancelar timer previo si existe
        if (heroSlideHandler != null && heroSlideRunnable != null) {
            heroSlideHandler.removeCallbacks(heroSlideRunnable);
        }

        final LinearLayout bannerCard = new LinearLayout(this);
        bannerCard.setOrientation(LinearLayout.HORIZONTAL);
        bannerCard.setGravity(Gravity.CENTER_VERTICAL);
        bannerCard.setPadding(dp(22), dp(16), dp(22), dp(16));
        bannerCard.setFocusable(true);
        bannerCard.setClickable(true);

        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(Color.rgb(22, 26, 36));
        bBg.setCornerRadius(dp(16));
        bBg.setStroke(dp(1), Color.rgb(44, 52, 70));
        bannerCard.setBackground(bBg);

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(-1, dp(230));
        bLp.setMargins(dp(6), dp(8), dp(18), dp(16));
        bannerCard.setLayoutParams(bLp);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setGravity(Gravity.CENTER_VERTICAL);
        bannerCard.addView(infoCol, new LinearLayout.LayoutParams(0, -1, 1));

        TextView badge = text("★  DESTACADO EN LEO TV · MXL STYLE", 12, Color.rgb(245, 197, 66));
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(0, 0, 0, dp(4));
        infoCol.addView(badge);

        final TextView heroTitle = text("", 24, Color.WHITE);
        heroTitle.setTypeface(null, Typeface.BOLD);
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        infoCol.addView(heroTitle);

        final TextView heroMeta = text("", 13, Color.rgb(220, 226, 238));
        heroMeta.setPadding(0, dp(3), 0, dp(5));
        infoCol.addView(heroMeta);

        final TextView heroSyn = text("", 13, Color.rgb(175, 185, 204));
        heroSyn.setMaxLines(2);
        heroSyn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSyn.setPadding(0, 0, dp(12), dp(10));
        infoCol.addView(heroSyn);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        infoCol.addView(btnRow);

        Button playBtn = new Button(this);
        playBtn.setId(1001);
        playBtn.setText("▶  Reproducir");
        playBtn.setTextColor(Color.WHITE);
        playBtn.setTextSize(14);
        playBtn.setTypeface(null, Typeface.BOLD);
        playBtn.setAllCaps(false);
        playBtn.setFocusable(true);
        playBtn.setPadding(dp(18), dp(6), dp(18), dp(6));
        GradientDrawable pbBg = new GradientDrawable();
        pbBg.setColor(Color.rgb(129, 107, 245));
        pbBg.setCornerRadius(dp(10));
        playBtn.setBackground(pbBg);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(-2, dp(44));
        pbLp.setMargins(0, 0, dp(10), 0);
        btnRow.addView(playBtn, pbLp);

        Button detailBtn = new Button(this);
        detailBtn.setText("ℹ  Ver Ficha");
        detailBtn.setTextColor(Color.WHITE);
        detailBtn.setTextSize(14);
        detailBtn.setAllCaps(false);
        detailBtn.setFocusable(true);
        detailBtn.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable dbBg = new GradientDrawable();
        dbBg.setColor(Color.rgb(38, 44, 60));
        dbBg.setCornerRadius(dp(10));
        detailBtn.setBackground(dbBg);
        LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(-2, dp(44));
        dbLp.setMargins(0, 0, dp(10), 0);
        btnRow.addView(detailBtn, dbLp);

        Button favBtn = new Button(this);
        favBtn.setText("❤️  Favorito");
        favBtn.setTextColor(Color.WHITE);
        favBtn.setTextSize(14);
        favBtn.setAllCaps(false);
        favBtn.setFocusable(true);
        favBtn.setPadding(dp(16), dp(6), dp(16), dp(6));
        GradientDrawable fbBg = new GradientDrawable();
        fbBg.setColor(Color.rgb(38, 44, 60));
        fbBg.setCornerRadius(dp(10));
        favBtn.setBackground(fbBg);
        LinearLayout.LayoutParams fbLp = new LinearLayout.LayoutParams(-2, dp(44));
        btnRow.addView(favBtn, fbLp);

        android.widget.FrameLayout imgWrap = new android.widget.FrameLayout(this);
        GradientDrawable iBg = new GradientDrawable();
        iBg.setColor(Color.rgb(28, 34, 46));
        iBg.setCornerRadius(dp(12));
        imgWrap.setBackground(iBg);
        imgWrap.setClipToOutline(true);

        final ImageView heroImg = new ImageView(this);
        heroImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgWrap.addView(heroImg, new android.widget.FrameLayout.LayoutParams(-1, -1));
        bannerCard.addView(imgWrap, new LinearLayout.LayoutParams(dp(220), dp(180)));

        final boolean[] userFocusing = new boolean[] { false };

        Runnable updateSlide = () -> {
            FeaturedHeroItem it = slides.get(currentHeroIndex);
            heroTitle.setText(it.title);
            heroMeta.setText(it.meta);
            heroSyn.setText(it.synopsis);
            CatalogBrowser.loadPoster(this, heroImg, it.backdropUrl);

            playBtn.setOnClickListener(v -> onPosterClicked(it.posterItem));
            detailBtn.setOnClickListener(v -> onPosterClicked(it.posterItem));
            favBtn.setOnClickListener(v -> {
                CatalogData.Entry e = it.posterItem.rawEntry != null
                        ? it.posterItem.rawEntry
                        : new CatalogData.Entry(it.posterItem.title, 0, it.posterItem.posterUrl,
                                it.posterItem.posterUrl, it.posterItem.isSeries ? "serie" : "movie", it.posterItem.year,
                                it.posterItem.providerName);
                ZappCatalogEngine.saveFavorite(this, e);
                Toast.makeText(this, "Guardado en Favoritos: " + it.title, Toast.LENGTH_SHORT).show();
            });
        };
        updateSlide.run();

        // Rotación automática cada 6.5 segundos (se pausa si el usuario interactúa o si
        // hay reproducción)
        heroSlideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        heroSlideRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isDestroyed() && !userFocusing[0] && externalPlayer == null && "home".equals(page)) {
                    currentHeroIndex = (currentHeroIndex + 1) % slides.size();
                    updateSlide.run();
                }
                if (heroSlideHandler != null) {
                    heroSlideHandler.postDelayed(this, 6500);
                }
            }
        };
        heroSlideHandler.postDelayed(heroSlideRunnable, 6500);

        playBtn.setOnFocusChangeListener((v, f) -> {
            userFocusing[0] = f;
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(150, 130, 255) : Color.rgb(129, 107, 245));
            g.setCornerRadius(dp(10));
            if (f)
                g.setStroke(dp(2), Color.WHITE);
            playBtn.setBackground(g);
            playBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });
        detailBtn.setOnFocusChangeListener((v, f) -> {
            userFocusing[0] = f;
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(58, 68, 92) : Color.rgb(38, 44, 60));
            g.setCornerRadius(dp(10));
            if (f)
                g.setStroke(dp(2), Color.WHITE);
            detailBtn.setBackground(g);
            detailBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });
        favBtn.setOnFocusChangeListener((v, f) -> {
            userFocusing[0] = f;
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(58, 68, 92) : Color.rgb(38, 44, 60));
            g.setCornerRadius(dp(10));
            if (f)
                g.setStroke(dp(2), Color.WHITE);
            favBtn.setBackground(g);
            favBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });

        bannerCard.setOnFocusChangeListener((v, f) -> {
            userFocusing[0] = f;
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(30, 36, 50) : Color.rgb(22, 26, 36));
            g.setCornerRadius(dp(16));
            g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(44, 52, 70));
            bannerCard.setBackground(g);
        });
        bannerCard.setOnClickListener(v -> {
            currentHeroIndex = (currentHeroIndex + 1) % slides.size();
            updateSlide.run();
        });

        body.addView(bannerCard);
        return bannerCard;
    }

    private void buildSectionFilterBar(LinearLayout body, String[] labels, String[] targetTags, ScrollView scroll) {
        android.widget.HorizontalScrollView hscroll = new android.widget.HorizontalScrollView(this);
        hscroll.setHorizontalScrollBarEnabled(false);
        hscroll.setClipToPadding(false);
        hscroll.setPadding(dp(8), dp(6), dp(8), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        hscroll.addView(row);

        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final String tag = targetTags[i];
            Button pill = new Button(this);
            pill.setText(label);
            pill.setTextColor(Color.rgb(220, 230, 245));
            pill.setTextSize(13);
            pill.setTypeface(null, Typeface.BOLD);
            pill.setAllCaps(false);
            pill.setFocusable(true);
            pill.setPadding(dp(16), dp(6), dp(16), dp(6));

            GradientDrawable normalBg = new GradientDrawable();
            normalBg.setColor(Color.rgb(26, 32, 46));
            normalBg.setCornerRadius(dp(18));
            normalBg.setStroke(dp(1), Color.rgb(55, 68, 95));
            pill.setBackground(normalBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.setMargins(0, 0, dp(10), 0);
            row.addView(pill, lp);

            pill.setOnFocusChangeListener((v, f) -> {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(f ? Color.rgb(129, 107, 245) : Color.rgb(26, 32, 46));
                bg.setCornerRadius(dp(18));
                bg.setStroke(f ? dp(2) : dp(1), f ? Color.WHITE : Color.rgb(55, 68, 95));
                pill.setBackground(bg);
                pill.setTextColor(f ? Color.WHITE : Color.rgb(220, 230, 245));
                pill.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
                pill.setElevation(f ? 6f : 0f);
            });

            pill.setOnClickListener(v -> {
                if ("top".equals(tag)) {
                    if (scroll != null)
                        scroll.smoothScrollTo(0, 0);
                } else {
                    android.view.View titleTarget = body.findViewWithTag("row_" + tag);
                    android.view.View railTarget = body.findViewWithTag("rail_" + tag);
                    if (titleTarget != null && scroll != null) {
                        int top = titleTarget.getTop();
                        scroll.smoothScrollTo(0, Math.max(0, top - dp(20)));
                        titleTarget.postDelayed(() -> {
                            if (railTarget instanceof LinearLayout && ((LinearLayout) railTarget).getChildCount() > 0) {
                                ((LinearLayout) railTarget).getChildAt(0).requestFocus();
                            } else {
                                titleTarget.requestFocus();
                            }
                        }, 250);
                    }
                }
            });
        }
        body.addView(hscroll);
    }

    private static class LiveSportEvent {
        final String title;
        final String tournament;
        final String timeStatus;
        final boolean isLiveNow;
        final String streamUrl;
        final String icon;

        LiveSportEvent(String title, String tournament, String timeStatus, boolean isLiveNow, String streamUrl,
                String icon) {
            this.title = title;
            this.tournament = tournament;
            this.timeStatus = timeStatus;
            this.isLiveNow = isLiveNow;
            this.streamUrl = streamUrl;
            this.icon = icon;
        }
    }

    private java.util.List<LiveSportEvent> defaultSportsEvents() {
        try {
            java.util.List<LiveTvEngine.LiveSportEvent> diary = LiveTvEngine.fetchSportsDiarySync(this, false);
            if (diary != null && !diary.isEmpty()) {
                java.util.List<LiveSportEvent> res = new java.util.ArrayList<>();
                for (LiveTvEngine.LiveSportEvent ev : diary) {
                    res.add(new LiveSportEvent(ev.title, ev.tournament, ev.timeStatus, ev.isLiveNow, ev.streamUrl,
                            ev.icon));
                }
                return res;
            }
        } catch (Exception ignored) {
        }
        return java.util.Arrays.asList(
                new LiveSportEvent("TyC Sports HD", "Fútbol de Primera & Torneos · En Vivo",
                        "🔴 EN VIVO", true,
                        "https://tvf90.com/1.php?stream=tycsports",
                        "⚽"),
                new LiveSportEvent("ESPN HD Argentina", "SportsCenter & Torneos Internacionales",
                        "🔴 EN VIVO", true, "https://tvf90.com/1.php?stream=espn", "⚽"),
                new LiveSportEvent("ESPN 2 HD", "UEFA Champions League & Tenis ATP",
                        "🔴 EN VIVO", true, "https://tvf90.com/1.php?stream=espn2", "🎾"),
                new LiveSportEvent("Fox Sports HD", "Copa Libertadores & Fórmula 1",
                        "🔴 EN VIVO", true, "https://tvf90.com/1.php?stream=foxsports", "🏎️"),
                new LiveSportEvent("Real Madrid TV HD", "LaLiga · Noticias & Partidos en Directo",
                        "🔴 EN VIVO", true, "https://rmtv.akamaized.net/hls/live/2043153/rmtv-es-web/master.m3u8", "⚪"),
                new LiveSportEvent("DeporTV Argentina HD", "Polideportivo Federal & Magazine",
                        "🔴 EN VIVO", true, "https://5fb24b460df87.streamlock.net/live-cont.ar/deportv/playlist.m3u8",
                        "🇦🇷"),
                new LiveSportEvent("Teledeporte RTVE", "Estudio Estadio & Deportes Internacionales",
                        "🔴 EN VIVO", true, "https://rtvelivestream.akamaized.net/rtvesec/tdp/tdp_main.m3u8", "🇪🇸"),
                new LiveSportEvent("Red Bull TV Deportes", "Cliff Diving, F1, Rally & Downhill Extremo",
                        "🔴 EN VIVO", true, "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                        "🏎️"));
    }

    private LinearLayout addSportsLiveRow(LinearLayout body, String heading, java.util.List<LiveSportEvent> events) {
        TextView title = text(heading, 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(dp(8), dp(18), 0, dp(10));
        body.addView(title, tlp);

        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setFocusable(false);
        scroller.setFocusableInTouchMode(false);
        scroller.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(dp(6), dp(4), dp(28), dp(10));

        for (LiveSportEvent ev : events) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setFocusable(true);
            card.setClickable(true);

            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.rgb(24, 28, 40));
            cBg.setCornerRadius(dp(14));
            cBg.setStroke(dp(1), ev.isLiveNow ? Color.rgb(235, 60, 60) : Color.rgb(44, 52, 70));
            card.setBackground(cBg);

            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(dp(240), dp(130));
            cLp.setMargins(0, 0, dp(14), 0);
            card.setLayoutParams(cLp);

            LinearLayout topTag = new LinearLayout(this);
            topTag.setOrientation(LinearLayout.HORIZONTAL);
            topTag.setGravity(Gravity.CENTER_VERTICAL);

            TextView statusBadge = text(ev.timeStatus, 11, ev.isLiveNow ? Color.WHITE : Color.rgb(245, 197, 66));
            statusBadge.setTypeface(null, Typeface.BOLD);
            statusBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
            GradientDrawable sb = new GradientDrawable();
            sb.setColor(ev.isLiveNow ? Color.rgb(210, 30, 30) : Color.rgb(44, 50, 66));
            sb.setCornerRadius(dp(6));
            statusBadge.setBackground(sb);
            topTag.addView(statusBadge);

            TextView iconView = text(" " + ev.icon, 14, Color.WHITE);
            topTag.addView(iconView);
            card.addView(topTag);

            TextView evTitle = text(ev.title, 14, Color.WHITE);
            evTitle.setTypeface(null, Typeface.BOLD);
            evTitle.setMaxLines(2);
            evTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            evTitle.setPadding(0, dp(8), 0, dp(4));
            card.addView(evTitle);

            TextView evMeta = text(ev.tournament, 11, Color.rgb(180, 192, 214));
            evMeta.setSingleLine(true);
            evMeta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(evMeta);

            card.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(38, 46, 68) : Color.rgb(24, 28, 40));
                g.setCornerRadius(dp(14));
                g.setStroke(dp(f ? 2 : 1),
                        f ? Color.WHITE : (ev.isLiveNow ? Color.rgb(235, 60, 60) : Color.rgb(44, 52, 70)));
                card.setBackground(g);
                card.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
            });

            card.setOnClickListener(v -> {
                if (ev.streamUrl != null && ev.streamUrl.startsWith("http")) {
                    playMovie(ev.title, ev.streamUrl, "Deportes", null);
                } else {
                    showM3uList("TDTChannels TV", "https://www.tdtchannels.com/lists/tv.m3u8", true);
                }
            });
            attachRailNavigation(card);

            rail.addView(card);
        }

        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private boolean globalSearchEnabled = true;
    private String lastLunifiedQuery = "";
    private int searchGeneration = 0;

    private void showCarouselCustomizer() {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        page = "carousel_customizer";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 16, 26));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), dp(18), dp(24), dp(14));
        header.setBackgroundColor(Color.rgb(18, 24, 38));

        Button backBtn = new Button(this);
        backBtn.setText("←  Volver a Inicio");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(15);
        backBtn.setAllCaps(false);
        GradientDrawable bNorm = new GradientDrawable();
        bNorm.setColor(Color.rgb(32, 42, 64));
        bNorm.setCornerRadius(12);
        backBtn.setBackground(bNorm);
        backBtn.setOnClickListener(v -> home());
        header.addView(backBtn, new LinearLayout.LayoutParams(-2, dp(48)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(dp(20), 0, 0, 0);

        TextView title = text("⚙️  Personalizar Carruseles de Inicio", 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        titleCol.addView(title);

        TextView sub = text(
                "Organiza el orden de los carruseles y oculta los que no uses. Los cambios son persistentes.", 13,
                Color.rgb(168, 178, 195));
        titleCol.addView(sub);
        header.addView(titleCol, new LinearLayout.LayoutParams(0, -2, 1));

        Button resetBtn = new Button(this);
        resetBtn.setText("↺  Restablecer Todo");
        resetBtn.setTextColor(Color.rgb(245, 197, 66));
        resetBtn.setTextSize(14);
        resetBtn.setAllCaps(false);
        GradientDrawable rNorm = new GradientDrawable();
        rNorm.setColor(Color.rgb(40, 32, 20));
        rNorm.setCornerRadius(12);
        resetBtn.setBackground(rNorm);
        resetBtn.setOnClickListener(v -> {
            ZappCatalogEngine.saveCarouselOrder(this, getDefaultCarouselOrder());
            ZappCatalogEngine.saveHiddenCarousels(this, new java.util.HashSet<>());
            Toast.makeText(this, "Carruseles restablecidos al orden predeterminado", Toast.LENGTH_SHORT).show();
            showCarouselCustomizer();
        });
        header.addView(resetBtn, new LinearLayout.LayoutParams(-2, dp(48)));

        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(24), dp(16), dp(24), dp(36));

        final java.util.List<String> currentOrder = new java.util.ArrayList<>(
                ZappCatalogEngine.getCarouselOrder(this, getDefaultCarouselOrder()));
        final java.util.Set<String> hiddenSet = new java.util.HashSet<>(ZappCatalogEngine.getHiddenCarousels(this));

        for (int i = 0; i < currentOrder.size(); i++) {
            final int index = i;
            final String cId = currentOrder.get(i);
            final String cTitle = getCarouselDisplayName(cId);
            final boolean isVisible = !hiddenSet.contains(cId);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            GradientDrawable rBg = new GradientDrawable();
            rBg.setColor(isVisible ? Color.rgb(20, 26, 40) : Color.rgb(16, 20, 30));
            rBg.setCornerRadius(12);
            rBg.setStroke(dp(1), isVisible ? Color.rgb(45, 58, 85) : Color.rgb(30, 36, 50));
            row.setBackground(rBg);

            TextView num = text((index + 1) + ".", 16, Color.rgb(212, 175, 55));
            num.setTypeface(null, Typeface.BOLD);
            row.addView(num, new LinearLayout.LayoutParams(dp(36), -2));

            TextView label = text(cTitle, 16, isVisible ? Color.WHITE : Color.GRAY);
            label.setTypeface(null, Typeface.BOLD);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

            Button upBtn = new Button(this);
            upBtn.setText("▲");
            upBtn.setTextSize(16);
            upBtn.setTextColor(index > 0 ? Color.WHITE : Color.DKGRAY);
            upBtn.setEnabled(index > 0);
            GradientDrawable uBg = new GradientDrawable();
            uBg.setColor(Color.rgb(30, 40, 60));
            uBg.setCornerRadius(8);
            upBtn.setBackground(uBg);
            upBtn.setOnClickListener(v -> {
                if (index > 0) {
                    java.util.Collections.swap(currentOrder, index, index - 1);
                    ZappCatalogEngine.saveCarouselOrder(this, currentOrder);
                    showCarouselCustomizer();
                }
            });
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(48), dp(44));
            ulp.setMargins(0, 0, dp(8), 0);
            row.addView(upBtn, ulp);

            Button downBtn = new Button(this);
            downBtn.setText("▼");
            downBtn.setTextSize(16);
            downBtn.setTextColor(index < currentOrder.size() - 1 ? Color.WHITE : Color.DKGRAY);
            downBtn.setEnabled(index < currentOrder.size() - 1);
            GradientDrawable dBg = new GradientDrawable();
            dBg.setColor(Color.rgb(30, 40, 60));
            dBg.setCornerRadius(8);
            downBtn.setBackground(dBg);
            downBtn.setOnClickListener(v -> {
                if (index < currentOrder.size() - 1) {
                    java.util.Collections.swap(currentOrder, index, index + 1);
                    ZappCatalogEngine.saveCarouselOrder(this, currentOrder);
                    showCarouselCustomizer();
                }
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(48), dp(44));
            dlp.setMargins(0, 0, dp(14), 0);
            row.addView(downBtn, dlp);

            Button visBtn = new Button(this);
            visBtn.setText(isVisible ? "👁️  Visible" : "🚫  Oculto");
            visBtn.setTextColor(isVisible ? Color.WHITE : Color.rgb(255, 120, 120));
            visBtn.setTextSize(13);
            visBtn.setAllCaps(false);
            GradientDrawable vBg = new GradientDrawable();
            vBg.setColor(isVisible ? Color.rgb(24, 75, 45) : Color.rgb(55, 25, 25));
            vBg.setCornerRadius(10);
            visBtn.setBackground(vBg);
            visBtn.setOnClickListener(v -> {
                if (isVisible) {
                    hiddenSet.add(cId);
                } else {
                    hiddenSet.remove(cId);
                }
                ZappCatalogEngine.saveHiddenCarousels(this, hiddenSet);
                showCarouselCustomizer();
            });
            row.addView(visBtn, new LinearLayout.LayoutParams(dp(115), dp(44)));

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.setMargins(0, 0, 0, dp(8));
            listLayout.addView(row, rlp);
        }

        scroll.addView(listLayout);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        backBtn.requestFocus();
    }

    private void showFavoritesPage() {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        page = "favorites";
        activeTab = "favorites";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(14, 18, 26));

        LinearLayout side = buildSidebar("favorites");
        root.addView(side, new LinearLayout.LayoutParams(dp(88), -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(24), dp(16), dp(24), dp(16));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView header = text("❤️  Mis Favoritos", 26, Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        topBar.addView(header, new LinearLayout.LayoutParams(0, -2, 1));

        final java.util.List<CatalogData.Entry> favs = ZappCatalogEngine.getFavorites(this);
        TextView countBadge = text(favs.size() + " títulos guardados", 14, Color.rgb(212, 175, 55));
        topBar.addView(countBadge);

        if (!favs.isEmpty()) {
            Button clearBtn = new Button(this);
            clearBtn.setText("Vaciar lista");
            clearBtn.setTextColor(Color.LTGRAY);
            clearBtn.setTextSize(13);
            clearBtn.setAllCaps(false);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.rgb(40, 24, 28));
            cBg.setCornerRadius(10);
            clearBtn.setBackground(cBg);
            clearBtn.setOnClickListener(v -> {
                ZappCatalogEngine.clearFavorites(this);
                showFavoritesPage();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(40));
            clp.setMargins(dp(16), 0, 0, 0);
            topBar.addView(clearBtn, clp);
        }

        main.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(24));

        if (favs.isEmpty()) {
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            emptyBox.setPadding(dp(30), dp(60), dp(30), dp(60));

            TextView icon = text("❤️", 48, Color.GRAY);
            icon.setGravity(Gravity.CENTER);
            emptyBox.addView(icon);

            TextView msg = text("No tienes títulos guardados en Favoritos todavía", 18, Color.WHITE);
            msg.setGravity(Gravity.CENTER);
            msg.setTypeface(null, Typeface.BOLD);
            msg.setPadding(0, dp(14), 0, dp(6));
            emptyBox.addView(msg);

            TextView sub = text(
                    "Explora el catálogo en Inicio o Búsqueda, abre la ficha de cualquier película o serie y presiona '❤️ Agregar a Favoritos'.",
                    14, Color.rgb(168, 178, 195));
            sub.setGravity(Gravity.CENTER);
            emptyBox.addView(sub);

            content.addView(emptyBox);
        } else {
            renderEntriesGrid6Col(content, favs);
        }

        scroll.addView(content);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        side.post(() -> {
            if (side.getChildCount() > 5)
                side.getChildAt(5).requestFocus();
        });
    }

    private void showWatchLaterPage() {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        page = "watch_later";
        activeTab = "watch_later";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(14, 18, 26));

        LinearLayout side = buildSidebar("watch_later");
        root.addView(side, new LinearLayout.LayoutParams(dp(88), -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(24), dp(16), dp(24), dp(16));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView header = text("🔖  Ver más tarde", 26, Color.WHITE);
        header.setTypeface(null, Typeface.BOLD);
        topBar.addView(header, new LinearLayout.LayoutParams(0, -2, 1));

        final java.util.List<CatalogData.Entry> list = ZappCatalogEngine.getWatchLater(this);
        TextView countBadge = text(list.size() + " títulos pendientes", 14, Color.rgb(65, 170, 245));
        topBar.addView(countBadge);

        if (!list.isEmpty()) {
            Button clearBtn = new Button(this);
            clearBtn.setText("Vaciar lista");
            clearBtn.setTextColor(Color.LTGRAY);
            clearBtn.setTextSize(13);
            clearBtn.setAllCaps(false);
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(Color.rgb(24, 34, 48));
            cBg.setCornerRadius(10);
            clearBtn.setBackground(cBg);
            clearBtn.setOnClickListener(v -> {
                ZappCatalogEngine.clearWatchLater(this);
                showWatchLaterPage();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(40));
            clp.setMargins(dp(16), 0, 0, 0);
            topBar.addView(clearBtn, clp);
        }

        main.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(14), 0, dp(24));

        if (list.isEmpty()) {
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            emptyBox.setPadding(dp(30), dp(60), dp(30), dp(60));

            TextView icon = text("🔖", 48, Color.GRAY);
            icon.setGravity(Gravity.CENTER);
            emptyBox.addView(icon);

            TextView msg = text("Tu lista de 'Ver más tarde' está vacía", 18, Color.WHITE);
            msg.setGravity(Gravity.CENTER);
            msg.setTypeface(null, Typeface.BOLD);
            msg.setPadding(0, dp(14), 0, dp(6));
            emptyBox.addView(msg);

            TextView sub = text(
                    "Guarda películas y series para encontrarlas rápidamente cuando tengas tiempo para verlas.", 14,
                    Color.rgb(168, 178, 195));
            sub.setGravity(Gravity.CENTER);
            emptyBox.addView(sub);

            content.addView(emptyBox);
        } else {
            renderEntriesGrid6Col(content, list);
        }

        scroll.addView(content);
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        side.post(() -> {
            if (side.getChildCount() > 6)
                side.getChildAt(6).requestFocus();
        });
    }

    private void renderEntriesGrid6Col(LinearLayout container, java.util.List<CatalogData.Entry> entries) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setOrientation(GridLayout.HORIZONTAL);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.max(dp(140), (screenWidth - dp(140)) / 6);

        for (CatalogData.Entry e : entries) {
            PosterItem item = new PosterItem(
                    e.title,
                    e.year > 0 ? e.year : 2026,
                    !e.isMovie(),
                    e.poster,
                    e.providerName,
                    e.title,
                    e);
            android.view.View cView = createGridPosterCard(item, cardWidth);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cardWidth;
            glp.height = -2;
            glp.setMargins(dp(5), dp(6), dp(5), dp(10));
            grid.addView(cView, glp);
        }
        container.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showSettingsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚙️  Ajustes de Leo TV");
        boolean locked = ParentalControlEngine.isParentalLocked(this);
        String[] options = new String[] {
                "🚀  Buscar Actualizaciones de Leo TV  (v" + AppUpdater.getCurrentVersionName(this) + ")",
                "🔄  Actualizar Catálogo y Agenda Deportiva (Fútbol Libre)",
                "🇦🇷  Actualizar Fuente IPTV-org Argentina (URL M3U)",
                "📺  Listas M3U / IPTV Personalizadas (TDTChannels, Free-TV, etc.)",
                "⚙️  Personalizar Carruseles de Inicio (Ordenar y Ocultar)",
                "📡  Seleccionar Proveedor Activo ("
                        + ((activeProvider == null || activeProvider.isEmpty()) ? "CineHax" : activeProvider) + ")",
                locked ? "🔒 Control Parental: Bloqueado (Ingresar PIN)"
                        : "🔓 Control Parental: Desbloqueado (Bloquear ahora)",
                "🔑 Cambiar PIN de Control Parental"
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                triggerAppUpdateCheck();
            } else if (which == 1) {
                triggerCatalogUpdate();
            } else if (which == 2) {
                showIptvOrgUpdateDialog();
            } else if (which == 3) {
                showCustomM3uManager();
            } else if (which == 4) {
                showCarouselCustomizer();
            } else if (which == 5) {
                showProviderSelector();
            } else if (which == 6) {
                if (ParentalControlEngine.isParentalLocked(this)) {
                    showParentalPinDialog(() -> {
                        Toast.makeText(this, "Control parental desbloqueado (+18 visible en la barra)", Toast.LENGTH_SHORT)
                                .show();
                        home();
                    });
                } else {
                    ParentalControlEngine.lockSession();
                    Toast.makeText(this, "Control parental bloqueado (+18 ocultado de la barra)", Toast.LENGTH_SHORT).show();
                    if ("adults".equals(activeTab)) {
                        activeTab = "all";
                    }
                    home();
                }
            } else if (which == 7) {
                showParentalChangePinDialog();
            }
        });
        builder.setNegativeButton("Cerrar", null);
        builder.show();
    }

    private void showIptvOrgUpdateDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("🇦🇷  Fuente IPTV-org Argentina");
        String currentUrl = LiveTvEngine.getIptvOrgArUrl(this);
        b.setMessage("URL configurada actualmente:\n" + currentUrl
                + "\n\n¿Deseas sincronizar los 186 canales con la lista oficial ahora?");
        b.setPositiveButton("Actualizar Ahora", (d, w) -> {
            Toast.makeText(this, "Descargando lista IPTV-org Argentina...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    int count = LiveTvEngine.updateIptvOrgArFromNetwork(this);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "✓ IPTV-org actualizado: " + count + " canales importados",
                                Toast.LENGTH_LONG).show();
                        if ("iptv_org_ar".equals(activeTvCat) && page.equals("live_tv_studio")) {
                            showLiveTvStudio();
                        }
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error actualizando: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
        b.setNeutralButton("Editar URL", (d, w) -> {
            showEditIptvOrgUrlDialog();
        });
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    private void showEditIptvOrgUrlDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("✏️  Configurar URL M3U de IPTV-org Argentina");
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(LiveTvEngine.getIptvOrgArUrl(this));
        input.setTextColor(Color.WHITE);
        input.setTextSize(13);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(10), dp(20), dp(10));
        wrap.addView(input, new LinearLayout.LayoutParams(-1, -2));
        b.setView(wrap);
        b.setPositiveButton("Guardar", (d, w) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                LiveTvEngine.setIptvOrgArUrl(this, newUrl);
                Toast.makeText(this, "URL guardada", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showCustomM3uManager() {
        android.content.SharedPreferences sp = getSharedPreferences("leo_tv_custom_m3u", MODE_PRIVATE);
        String raw = sp.getString("saved_m3u_json", "[]");
        org.json.JSONArray arr;
        try {
            arr = new org.json.JSONArray(raw);
        } catch (Exception e) {
            arr = new org.json.JSONArray();
        }

        // Si es la primera vez que se abre, sembrar fuentes recomendadas (TDTChannels y
        // Free-TV)
        if (arr.length() == 0) {
            try {
                org.json.JSONObject tdt = new org.json.JSONObject();
                tdt.put("name", "🇪🇸 TDTChannels TV (España y Autonómicos)");
                tdt.put("url", "https://www.tdtchannels.com/lists/tv.m3u8");
                arr.put(tdt);

                org.json.JSONObject freetv = new org.json.JSONObject();
                freetv.put("name", "🌐 Free-TV Global IPTV (Mundial)");
                freetv.put("url", "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8");
                arr.put(freetv);

                sp.edit().putString("saved_m3u_json", arr.toString()).apply();
            } catch (Exception ignored) {
            }
        }

        java.util.List<String> items = new java.util.ArrayList<>();
        items.add("➕  Agregar Nueva Lista M3U (Pegar URL)");
        final int listCount = arr.length();
        for (int i = 0; i < listCount; i++) {
            org.json.JSONObject obj = arr.optJSONObject(i);
            if (obj != null) {
                String n = obj.optString("name", "Lista " + (i + 1));
                items.add("▶  " + n);
            }
        }
        if (listCount > 0) {
            items.add("🗑️  Borrar una Lista Guardada");
        }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("📺  Listas M3U Personalizadas");
        final org.json.JSONArray finalArr = arr;
        b.setItems(items.toArray(new String[0]), (d, which) -> {
            if (which == 0) {
                showAddCustomM3uDialog();
            } else if (which <= listCount) {
                org.json.JSONObject selected = finalArr.optJSONObject(which - 1);
                if (selected != null) {
                    String name = selected.optString("name", "Lista Personalizada");
                    String url = selected.optString("url", "");
                    if (!url.isEmpty()) {
                        showM3uList(name, url, true);
                    }
                }
            } else {
                showDeleteCustomM3uDialog(finalArr);
            }
        });
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    private void showAddCustomM3uDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("➕  Agregar Lista M3U");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(10), dp(24), dp(10));

        final android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setHint("Nombre (ej: Mi IPTV Personal)");
        nameInput.setTextColor(Color.WHITE);
        nameInput.setTextSize(15);
        layout.addView(nameInput);

        final android.widget.EditText urlInput = new android.widget.EditText(this);
        urlInput.setHint("URL M3U (ej: http://... o https://.../lista.m3u)");
        urlInput.setTextColor(Color.WHITE);
        urlInput.setTextSize(14);
        layout.addView(urlInput);

        b.setView(layout);
        b.setPositiveButton("Guardar y Abrir", (d, w) -> {
            String name = nameInput.getText().toString().trim();
            String url = urlInput.getText().toString().trim();
            if (name.isEmpty())
                name = "Lista IPTV " + (System.currentTimeMillis() % 1000);
            if (url.isEmpty()) {
                Toast.makeText(this, "La URL no puede estar vacía", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                android.content.SharedPreferences sp = getSharedPreferences("leo_tv_custom_m3u", MODE_PRIVATE);
                String raw = sp.getString("saved_m3u_json", "[]");
                org.json.JSONArray arr = new org.json.JSONArray(raw);
                org.json.JSONObject newObj = new org.json.JSONObject();
                newObj.put("name", name);
                newObj.put("url", url);
                arr.put(newObj);
                sp.edit().putString("saved_m3u_json", arr.toString()).apply();
                Toast.makeText(this, "Lista guardada exitosamente", Toast.LENGTH_SHORT).show();
                showM3uList(name, url, true);
            } catch (Exception ex) {
                Toast.makeText(this, "Error guardando lista: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showDeleteCustomM3uDialog(org.json.JSONArray arr) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject obj = arr.optJSONObject(i);
            names.add(obj != null ? obj.optString("name", "Lista " + (i + 1)) : "Lista " + (i + 1));
        }
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("🗑️  Selecciona la lista a eliminar");
        b.setItems(names.toArray(new String[0]), (d, which) -> {
            try {
                org.json.JSONArray newArr = new org.json.JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    if (i != which)
                        newArr.put(arr.get(i));
                }
                getSharedPreferences("leo_tv_custom_m3u", MODE_PRIVATE)
                        .edit().putString("saved_m3u_json", newArr.toString()).apply();
                Toast.makeText(this, "Lista eliminada", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showParentalPinDialog(Runnable onUnlocked) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔒 Control Parental · Ingresar PIN");

        final android.widget.EditText pinInput = new android.widget.EditText(this);
        pinInput.setHint("PIN de 4 dígitos (Por defecto: 0000)");
        pinInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setTextSize(18);
        pinInput.setTextColor(Color.WHITE);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(12), dp(24), dp(12));
        wrap.addView(pinInput);
        builder.setView(wrap);

        builder.setPositiveButton("Desbloquear", (dialog, which) -> {
            String entered = pinInput.getText().toString().trim();
            if (ParentalControlEngine.verifyPin(this, entered)) {
                Toast.makeText(this, "✓ Control Parental desbloqueado", Toast.LENGTH_SHORT).show();
                if (onUnlocked != null)
                    onUnlocked.run();
            } else {
                Toast.makeText(this, "❌ PIN incorrecto (Por defecto: 0000)", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancelar", null);
        android.app.AlertDialog dlg = builder.create();
        pinInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String entered = pinInput.getText().toString().trim();
                if (ParentalControlEngine.verifyPin(this, entered)) {
                    Toast.makeText(this, "✓ Control Parental desbloqueado", Toast.LENGTH_SHORT).show();
                    dlg.dismiss();
                    if (onUnlocked != null)
                        onUnlocked.run();
                } else {
                    Toast.makeText(this, "❌ PIN incorrecto (Por defecto: 0000)", Toast.LENGTH_LONG).show();
                }
                return true;
            }
            return false;
        });
        dlg.show();
    }

    private void showParentalChangePinDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔑 Cambiar PIN de Control Parental");

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(24), dp(10), dp(24), dp(10));

        final android.widget.EditText oldPin = new android.widget.EditText(this);
        oldPin.setHint("PIN actual (ej: 0000)");
        oldPin.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        wrap.addView(oldPin);

        final android.widget.EditText newPin = new android.widget.EditText(this);
        newPin.setHint("Nuevo PIN de 4 dígitos");
        newPin.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        wrap.addView(newPin);

        builder.setView(wrap);
        builder.setPositiveButton("Guardar PIN", (dialog, which) -> {
            String oldP = oldPin.getText().toString().trim();
            String newP = newPin.getText().toString().trim();
            if (ParentalControlEngine.changePin(this, oldP, newP)) {
                Toast.makeText(this, "✓ PIN actualizado con éxito", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Error: PIN actual incorrecto o nuevo menor a 4 dígitos", Toast.LENGTH_LONG)
                        .show();
            }
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private LinearLayout buildSidebar(String currentSection) {
        sideNavButtons.clear();
        activeSidebarView = null;
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        side.setPadding(dp(6), dp(6), dp(6), dp(6));
        side.setBackgroundColor(Color.rgb(12, 15, 20));

        // Emblema superior del proveedor activo + logo Leo TV (clic cambia proveedor
        // como en Lunified)
        LinearLayout topBrand = new LinearLayout(this);
        topBrand.setOrientation(LinearLayout.VERTICAL);
        topBrand.setGravity(Gravity.CENTER);
        topBrand.setFocusable(true);
        topBrand.setClickable(true);
        GradientDrawable brandBg = new GradientDrawable();
        brandBg.setColor(Color.rgb(22, 26, 36));
        brandBg.setCornerRadius(10);
        brandBg.setStroke(2, Color.rgb(65, 75, 102));
        topBrand.setBackground(brandBg);
        topBrand.setPadding(2, 4, 2, 4);

        ImageView lion = new ImageView(this);
        lion.setImageResource(ar.appvideo.player.R.drawable.leo_brand);
        lion.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topBrand.addView(lion, new LinearLayout.LayoutParams(36, 36));

        String shortProv = (activeProvider == null || activeProvider.isEmpty()) ? "CINEHAX"
                : activeProvider.toUpperCase();
        if (shortProv.length() > 8)
            shortProv = shortProv.substring(0, 8);
        TextView provLabel = text(shortProv, 8, Color.rgb(245, 197, 66));
        provLabel.setTypeface(null, Typeface.BOLD);
        provLabel.setGravity(Gravity.CENTER);
        provLabel.setSingleLine(true);
        topBrand.addView(provLabel);

        boolean isPortraitBrand = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int bSize = isPortraitBrand ? dp(44) : dp(54);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(bSize, bSize);
        logoLp.setMargins(0, 0, 0, dp(4));
        side.addView(topBrand, logoLp);
        topBrand.setOnClickListener(v -> showProviderSelector());
        topBrand.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable b = new GradientDrawable();
            b.setColor(focused ? Color.rgb(42, 50, 72) : Color.rgb(22, 26, 36));
            b.setCornerRadius(12);
            b.setStroke(focused ? 3 : 2, focused ? Color.WHITE : Color.rgb(65, 75, 102));
            topBrand.setBackground(b);
        });

        addSideIcon(side, "🔍", "search".equals(currentSection), "Buscar y Explorar",
                () -> showLunifiedSearch(lastLunifiedQuery));
        addSideIcon(side, "🏠", "all".equals(currentSection), "Inicio", () -> {
            activeTab = "all";
            home();
        });
        addSideIcon(side, "🎬", "movies".equals(currentSection), "Películas", () -> {
            activeTab = "movies";
            home();
        });
        addSideIcon(side, "🎞️", "series".equals(currentSection), "Series", () -> {
            activeTab = "series";
            home();
        });
        addSideIcon(side, "🌍", "docs".equals(currentSection), "Documentales", () -> {
            activeTab = "docs";
            home();
        });
        addSideIcon(side, "📺", "tv".equals(currentSection), "TV en vivo", this::showLiveTvStudio);
        addSideIcon(side, "❤️", "favorites".equals(currentSection), "Favoritos", this::showFavoritesPage);
        addSideIcon(side, "🔖", "watch_later".equals(currentSection), "Ver más tarde", this::showWatchLaterPage);
        if (!ParentalControlEngine.isParentalLocked(this)) {
            addSideIcon(side, "🔞", "adults".equals(currentSection), "Zona Adultos (+18)", () -> {
                activeTab = "adults";
                home();
            });
        }

        android.widget.Space filler = new android.widget.Space(this);
        side.addView(filler, new LinearLayout.LayoutParams(1, 0, 1));

        addSideIcon(side, "🚀", false, "Buscar Actualizaciones de Leo TV", this::triggerAppUpdateCheck);
        addSideIcon(side, "🔄", false, "Actualizar Catálogo y Deportes", this::triggerCatalogUpdate);
        addSideIcon(side, "📥", false, "Descargas", () -> LeoDownloadManager.openDownloadsFolder(this));
        addSideIcon(side, "⚙️", "sources".equals(currentSection), "Ajustes y Personalizar", this::showSettingsDialog);
        return side;
    }

    private void triggerAppUpdateCheck() {
        Toast.makeText(this, "🚀 Comprobando actualizaciones de Leo TV...", Toast.LENGTH_SHORT).show();
        AppUpdater.check(this, new AppUpdater.CheckCallback() {
            @Override
            public void onUpdateAvailable(AppUpdater.UpdateInfo info) {
                AppUpdater.showUpdateDialog(MainActivity.this, info);
            }

            @Override
            public void onNoUpdate() {
                runOnUiThread(() -> {
                    android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(MainActivity.this);
                    b.setTitle("🚀 Leo TV · v" + AppUpdater.getCurrentVersionName(MainActivity.this));
                    b.setMessage("¡Ya tenés instalada la versión más reciente (v"
                            + AppUpdater.getCurrentVersionName(MainActivity.this)
                            + ")!\n\nTu aplicación está completamente al día.");
                    b.setPositiveButton("Aceptar", null);
                    b.show();
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(MainActivity.this);
                    b.setTitle("🚀 Leo TV · v" + AppUpdater.getCurrentVersionName(MainActivity.this));
                    b.setMessage("No se pudo conectar con el servidor de actualizaciones o ya tenés la versión más reciente (v"
                            + AppUpdater.getCurrentVersionName(MainActivity.this) + ").");
                    b.setPositiveButton("Aceptar", null);
                    b.show();
                });
            }
        });
    }

    private void triggerCatalogUpdate() {
        Toast.makeText(this, "🔄 Sincronizando catálogo y agenda deportiva...", Toast.LENGTH_SHORT).show();
        CatalogUpdater.runUpdateAsync(this, new CatalogUpdater.UpdateListener() {
            @Override
            public void onProgress(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFinished(boolean success, int matchesCount, String summary) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, summary, Toast.LENGTH_LONG).show();
                    if ("home".equals(page)) {
                        home();
                    }
                });
            }
        });
    }

    private void openEntryDetail(CatalogData.Entry entry) {
        if (entry == null)
            return;
        String targetProvider = (entry.providerName != null && !entry.providerName.isEmpty()
                && !"Catálogo Global TV".equals(entry.providerName)) ? entry.providerName : activeProvider;
        VideoProvider prov = VideoProviders.find(targetProvider);
        if (prov == null)
            prov = VideoProviders.find("CineHax");
        if (prov != null) {
            CatalogBrowser.State st = new CatalogBrowser.State();
            st.kind = entry.isMovie() ? "movie" : "serie";
            st.selected = entry;
            catalogStates.put(prov.name(), st);
            openChannel(prov.name());
        }
    }

    private void openActorDetailSheet(ZappCatalogEngine.ActorItem actor) {
        if (actor == null)
            return;
        VideoProvider prov = VideoProviders.find(activeProvider);
        if (prov == null)
            prov = VideoProviders.find("CineHax");
        if (prov != null) {
            CatalogBrowser.State st = new CatalogBrowser.State();
            st.selectedActor = actor;
            catalogStates.put(prov.name(), st);
            openChannel(prov.name());
        }
    }

    /**
     * Pantalla de Búsqueda y Exploración estilo Lunified (SearchTvFragment +
     * GlobalSearching):
     * - Barra superior de búsqueda ("Buscar Peliculas, Series" + botón X).
     * - Barra inferior con switch "Búsqueda global" (ON/OFF en verde).
     * - Grilla de 5 columnas de mosaicos coloridos por Año (2026..2010), País
     * (Turquía, Corea, Japón, Argentina, México, España, Colombia, EE.UU., Brasil)
     * y Géneros.
     * - Resultados unificados multicanal + traducción automática de títulos (ej.
     * Erkenci Kuş <-> Pájaro Soñador) + Actores.
     */
    private void showLunifiedSearch(String initialQuery) {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        if (resolutionTask != null)
            resolutionTask.cancel(true);
        ++providerPlayRequest;
        providerLoading = false;
        page = "search";
        channel = "";
        lastLunifiedQuery = initialQuery == null ? "" : initialQuery.trim();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(24, 25, 28));

        LinearLayout side = buildSidebar("search");
        root.addView(side, new LinearLayout.LayoutParams(dp(88), -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(24), dp(16), dp(24), dp(12));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        // Contenedor centrado para las 2 barras superiores (como en Lunified)
        LinearLayout topBarsWrap = new LinearLayout(this);
        topBarsWrap.setOrientation(LinearLayout.VERTICAL);
        topBarsWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        topBarsWrap.setPadding(dp(24), dp(2), dp(24), dp(10));
        main.addView(topBarsWrap, new LinearLayout.LayoutParams(-1, -2));

        // 1. Barra de texto de búsqueda (#383A40)
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(18), dp(4), dp(12), dp(4));
        GradientDrawable sbBg = new GradientDrawable();
        sbBg.setColor(Color.rgb(56, 58, 64));
        sbBg.setCornerRadius(dp(12));
        searchBar.setBackground(sbBg);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(-1, dp(54));
        sbLp.setMargins(0, 0, 0, dp(10));
        topBarsWrap.addView(searchBar, sbLp);

        TextView lensIcon = text("🔍", 18, Color.WHITE);
        lensIcon.setPadding(0, 0, dp(12), 0);
        searchBar.addView(lensIcon);

        final android.widget.EditText searchInput = new android.widget.EditText(this);
        searchInput.setHint("Buscar Peliculas, Series, Actores...");
        searchInput.setHintTextColor(Color.rgb(180, 185, 195));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(16);
        searchInput.setSingleLine(true);
        searchInput.setIncludeFontPadding(false);
        searchInput.setGravity(Gravity.CENTER_VERTICAL);
        searchInput.setEllipsize(android.text.TextUtils.TruncateAt.END);
        searchInput.setBackground(null);
        searchInput.setPadding(0, 0, dp(8), 0);
        searchInput.setText(lastLunifiedQuery);
        searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(0, -1, 1));

        // Área desplazable de contenido debajo de las barras
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        pageScroll = scroll;
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        final LinearLayout contentBody = new LinearLayout(this);
        contentBody.setOrientation(LinearLayout.VERTICAL);
        contentBody.setPadding(0, dp(6), 0, dp(32));
        scroll.addView(contentBody);

        Runnable triggerSearch = () -> {
            String q = searchInput.getText().toString().trim();
            lastLunifiedQuery = q;
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(
                    Activity.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            if (q.isEmpty()) {
                renderLunifiedTilesGrid(contentBody);
            } else {
                executeLunifiedQuery(q, contentBody);
            }
        };

        Button goBtn = new Button(this);
        goBtn.setText("Buscar");
        goBtn.setTextColor(Color.WHITE);
        goBtn.setTextSize(14);
        goBtn.setAllCaps(false);
        goBtn.setFocusable(true);
        GradientDrawable goBg = new GradientDrawable();
        goBg.setColor(Color.rgb(82, 86, 98));
        goBg.setCornerRadius(dp(8));
        goBtn.setBackground(goBg);
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(96), dp(42));
        goLp.setMargins(dp(8), 0, dp(8), 0);
        searchBar.addView(goBtn, goLp);
        goBtn.setOnClickListener(v -> triggerSearch.run());
        goBtn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(focused ? violet : Color.rgb(82, 86, 98));
            g.setCornerRadius(dp(8));
            if (focused)
                g.setStroke(dp(2), Color.WHITE);
            goBtn.setBackground(g);
        });

        TextView clearBtn = text("✕", 18, Color.WHITE);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setFocusable(true);
        clearBtn.setClickable(true);
        clearBtn.setPadding(dp(12), dp(4), dp(12), dp(4));
        searchBar.addView(clearBtn, new LinearLayout.LayoutParams(dp(48), dp(42)));
        clearBtn.setOnClickListener(v -> {
            searchInput.setText("");
            lastLunifiedQuery = "";
            renderLunifiedTilesGrid(contentBody);
        });
        clearBtn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(focused ? Color.rgb(95, 100, 112) : Color.TRANSPARENT);
            cBg.setCornerRadius(dp(8));
            if (focused)
                cBg.setStroke(dp(2), Color.WHITE);
            clearBtn.setBackground(cBg);
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                triggerSearch.run();
                return true;
            }
            return false;
        });

        // 2. Barra "Búsqueda global" con switch verde estilo Lunified (#42444A)
        final LinearLayout globalBar = new LinearLayout(this);
        globalBar.setOrientation(LinearLayout.HORIZONTAL);
        globalBar.setGravity(Gravity.CENTER_VERTICAL);
        globalBar.setPadding(dp(24), dp(6), dp(20), dp(6));
        globalBar.setFocusable(true);
        globalBar.setClickable(true);
        GradientDrawable gbBg = new GradientDrawable();
        gbBg.setColor(Color.rgb(66, 68, 74));
        gbBg.setCornerRadius(dp(24));
        globalBar.setBackground(gbBg);
        topBarsWrap.addView(globalBar, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView globalLabel = text("Búsqueda global", 18, Color.WHITE);
        globalBar.addView(globalLabel, new LinearLayout.LayoutParams(0, -2, 1));

        final LinearLayout switchTrack = new LinearLayout(this);
        switchTrack.setOrientation(LinearLayout.HORIZONTAL);
        switchTrack.setPadding(dp(4), dp(4), dp(4), dp(4));
        globalBar.addView(switchTrack, new LinearLayout.LayoutParams(dp(54), dp(28)));

        final android.view.View switchThumb = new android.view.View(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setColor(Color.WHITE);
        thumbBg.setCornerRadius(dp(11));
        switchThumb.setBackground(thumbBg);
        switchTrack.addView(switchThumb, new LinearLayout.LayoutParams(dp(20), dp(20)));

        Runnable updateSwitchVisual = () -> {
            GradientDrawable trackBg = new GradientDrawable();
            trackBg.setColor(globalSearchEnabled ? Color.rgb(76, 175, 80) : Color.rgb(110, 114, 122));
            trackBg.setCornerRadius(dp(14));
            switchTrack.setBackground(trackBg);
            switchTrack.setGravity(globalSearchEnabled ? (Gravity.END | Gravity.CENTER_VERTICAL)
                    : (Gravity.START | Gravity.CENTER_VERTICAL));
        };
        updateSwitchVisual.run();

        globalBar.setOnClickListener(v -> {
            globalSearchEnabled = !globalSearchEnabled;
            updateSwitchVisual.run();
            if (!lastLunifiedQuery.isEmpty()) {
                executeLunifiedQuery(lastLunifiedQuery, contentBody);
            }
        });
        globalBar.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(focused ? Color.rgb(80, 84, 94) : Color.rgb(66, 68, 74));
            g.setCornerRadius(30);
            if (focused)
                g.setStroke(2, Color.WHITE);
            globalBar.setBackground(g);
        });

        // 3. Barra de filtros interactivos en Explorar (Tipo y Ordenar)
        android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setPadding(0, dp(6), 0, dp(4));
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterScroll.addView(filterRow);
        topBarsWrap.addView(filterScroll, new LinearLayout.LayoutParams(-1, -2));

        String[] kindLabels = new String[] { "Todos", "🎬 Películas", "📺 Series", "🌍 Documentales", "⚔️ Anime" };
        String[] kindValues = new String[] { "all", "movie", "serie", "doc", "anime" };
        for (int i = 0; i < kindLabels.length; i++) {
            final String val = kindValues[i];
            Button b = new Button(this);
            b.setText(kindLabels[i]);
            b.setTextColor(Color.WHITE);
            b.setTextSize(12);
            b.setAllCaps(false);
            b.setFocusable(true);
            GradientDrawable bd = new GradientDrawable();
            bd.setColor(val.equals(searchFilterKind) ? Color.rgb(129, 107, 245) : Color.rgb(44, 48, 58));
            bd.setCornerRadius(dp(14));
            b.setBackground(bd);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, dp(34));
            blp.setMargins(0, 0, dp(8), 0);
            b.setLayoutParams(blp);
            b.setOnClickListener(v -> {
                searchFilterKind = val;
                triggerSearch.run();
            });
            filterRow.addView(b);
        }

        TextView sep = text("  │  Ordenar: ", 12, Color.rgb(180, 190, 210));
        filterRow.addView(sep);

        String[] sortLabels = new String[] { "⭐ Mejor valoradas (TMDB)", "🔥 Más populares", "🕒 Recientes", "📅 Año" };
        String[] sortValues = new String[] { "rating", "pop", "recent", "year" };
        for (int i = 0; i < sortLabels.length; i++) {
            final String sVal = sortValues[i];
            Button sb = new Button(this);
            sb.setText(sortLabels[i]);
            sb.setTextColor(Color.WHITE);
            sb.setTextSize(12);
            sb.setAllCaps(false);
            sb.setFocusable(true);
            GradientDrawable sbd = new GradientDrawable();
            sbd.setColor(sVal.equals(searchSortBy) ? Color.rgb(212, 175, 55) : Color.rgb(44, 48, 58));
            sbd.setCornerRadius(dp(14));
            sb.setBackground(sbd);
            LinearLayout.LayoutParams sblp = new LinearLayout.LayoutParams(-2, dp(34));
            sblp.setMargins(0, 0, dp(8), 0);
            sb.setLayoutParams(sblp);
            sb.setOnClickListener(v -> {
                searchSortBy = sVal;
                triggerSearch.run();
            });
            filterRow.addView(sb);
        }

        setContentView(root);

        if (lastLunifiedQuery.isEmpty()) {
            renderLunifiedTilesGrid(contentBody);
        } else {
            executeLunifiedQuery(lastLunifiedQuery, contentBody);
        }
    }

    private void renderLunifiedTilesGrid(LinearLayout contentBody) {
        ++searchGeneration;
        contentBody.removeAllViews();

        java.util.List<ZappCatalogEngine.ExploreTile> tiles = ZappCatalogEngine.buildLunifiedTiles();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int columns = dm.widthPixels >= 1100 ? 5 : 3;
        int availWidth = Math.max(720, dm.widthPixels - 148);
        int tileWidth = (availWidth - (columns - 1) * 18) / columns;
        int tileHeight = (int) (tileWidth * 0.62f);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        contentBody.addView(grid);

        for (ZappCatalogEngine.ExploreTile tile : tiles) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(12, 10, 12, 10);
            card.setFocusable(true);
            card.setClickable(true);

            GradientDrawable normal = new GradientDrawable();
            normal.setColor(tile.color);
            normal.setCornerRadius(12);
            card.setBackground(normal);

            TextView lbl = text(tile.label, 17, Color.WHITE);
            lbl.setTypeface(null, Typeface.BOLD);
            lbl.setGravity(Gravity.CENTER);
            lbl.setMaxLines(2);
            card.addView(lbl, new LinearLayout.LayoutParams(-1, -2));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileWidth;
            lp.height = Math.max(118, tileHeight);
            lp.setMargins(0, 0, 18, 18);
            grid.addView(card, lp);

            card.setOnClickListener(v -> openExploreTileResults(tile, contentBody));
            card.setOnFocusChangeListener((v, focused) -> {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(tile.color);
                bg.setCornerRadius(12);
                if (focused)
                    bg.setStroke(4, Color.WHITE);
                card.setBackground(bg);
                card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
            });
        }
    }

    private void openExploreTileResults(ZappCatalogEngine.ExploreTile tile, LinearLayout contentBody) {
        if (tile != null && "special".equals(tile.taxonomyType) && "youtube".equals(tile.slug)) {
            openYouTubeHub(contentBody);
            return;
        }
        if (tile != null && "special".equals(tile.taxonomyType) && "nubes".equals(tile.slug)) {
            openChannel("Mis nubes");
            return;
        }
        final int token = ++searchGeneration;
        contentBody.removeAllViews();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(-1, -2);
        hrLp.setMargins(0, 4, 0, 14);
        contentBody.addView(headerRow, hrLp);

        Button backTiles = tvAction("←  Categorías", "", () -> renderLunifiedTilesGrid(contentBody), 190, 62);
        headerRow.addView(backTiles);

        TextView heading = text("Explorando: " + tile.label, 22, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(14, 0, 0, 0);
        headerRow.addView(heading);

        TextView loading = text("Consultando catálogo de " + tile.label + "...", 16, Color.LTGRAY);
        loading.setPadding(8, 8, 8, 8);
        contentBody.addView(loading);
        backTiles.requestFocus();

        new Thread(() -> {
            java.util.List<CatalogData.Entry> fetched = new java.util.ArrayList<>();
            try {
                fetched.addAll(ZappCatalogEngine.fetchExploreTile(tile, 1));
                if (fetched.size() >= 20) {
                    try {
                        fetched.addAll(ZappCatalogEngine.fetchExploreTile(tile, 2));
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
                if (fetched.isEmpty())
                    fetched = java.util.Collections.emptyList();
            }
            final java.util.List<CatalogData.Entry> items = fetched;
            runOnUiThread(() -> {
                if (!page.equals("search") || token != searchGeneration || isDestroyed())
                    return;
                if (items.isEmpty()) {
                    loading.setText("No se encontraron títulos disponibles en " + tile.label + ".");
                    return;
                }
                loading.setText(items.size() + " títulos encontrados en " + tile.label
                        + " · Tocá una portada para abrir su ficha completa con actores y capítulos.");
                renderEntriesGrid(contentBody, items);
            });
        }, "explore-tile").start();
    }

    private void executeLunifiedQuery(String query, LinearLayout contentBody) {
        final int token = ++searchGeneration;
        contentBody.removeAllViews();

        String modeLabel = globalSearchEnabled
                ? "Búsqueda global activa (todos los proveedores + títulos alternativos y actores)"
                : ("Búsqueda en proveedor actual (" + activeProvider + ")");
        TextView statusLine = text("Buscando \"" + query + "\" · " + modeLabel + "...", 16, Color.rgb(200, 210, 228));
        statusLine.setPadding(6, 6, 6, 12);
        contentBody.addView(statusLine);

        new Thread(() -> {
            final ZappCatalogEngine.GlobalSearchResult result = ZappCatalogEngine.searchUnified(query,
                    globalSearchEnabled, activeProvider);
            runOnUiThread(() -> {
                if (!page.equals("search") || token != searchGeneration || isDestroyed())
                    return;
                contentBody.removeAllViews();

                LinearLayout topRow = new LinearLayout(this);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                topRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(-1, -2);
                trLp.setMargins(0, 0, 0, 10);
                contentBody.addView(topRow, trLp);

                Button backCats = tvAction("←  Ver categorías", "", () -> {
                    lastLunifiedQuery = "";
                    renderLunifiedTilesGrid(contentBody);
                }, 210, 58);
                topRow.addView(backCats);

                String summary = "Resultados para \"" + query + "\"";
                if (result.alternateQuery != null && !result.alternateQuery.isEmpty()
                        && !result.alternateQuery.equalsIgnoreCase(query)) {
                    summary += "  (también buscado como: \"" + result.alternateQuery + "\")";
                }
                TextView sumText = text(summary, 18, Color.rgb(245, 197, 66));
                sumText.setTypeface(null, Typeface.BOLD);
                sumText.setPadding(12, 0, 0, 0);
                topRow.addView(sumText);

                // 1. Si hay actores que coinciden, mostrar carrusel de fichas de actores
                if (!result.matchedActors.isEmpty()) {
                    TextView actTitle = text("ACTORES Y PERSONAS ENCONTRADAS", 16, Color.WHITE);
                    actTitle.setTypeface(null, Typeface.BOLD);
                    actTitle.setPadding(6, 8, 6, 6);
                    contentBody.addView(actTitle);

                    android.widget.HorizontalScrollView actScroll = new android.widget.HorizontalScrollView(this);
                    actScroll.setHorizontalScrollBarEnabled(false);
                    LinearLayout actRail = new LinearLayout(this);
                    actRail.setOrientation(LinearLayout.HORIZONTAL);
                    actRail.setPadding(6, 4, 20, 10);
                    for (ZappCatalogEngine.ActorItem actor : result.matchedActors) {
                        actRail.addView(createSearchActorCard(actor));
                    }
                    actScroll.addView(actRail);
                    contentBody.addView(actScroll);
                }

                // 2. Resultados de películas y series agrupados por proveedor
                if (result.byProvider.isEmpty() && result.matchedActors.isEmpty()) {
                    TextView empty = text("No se encontraron resultados para \"" + query
                            + "\". Probá activando Búsqueda global o con otro término.", 17, Color.LTGRAY);
                    empty.setPadding(8, 16, 8, 16);
                    contentBody.addView(empty);
                    return;
                }

                // 2. Unificar resultados de todos los proveedores sin duplicados
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueTurkish = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueKDrama = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueLatino = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueAnime = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueSeries = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueMovies = new java.util.LinkedHashMap<>();

                for (java.util.Map.Entry<String, java.util.List<CatalogData.Entry>> group : result.byProvider
                        .entrySet()) {
                    String provName = group.getKey();
                    for (CatalogData.Entry e : group.getValue()) {
                        String key = (e.title == null ? "" : e.title.trim().toLowerCase(java.util.Locale.ROOT));
                        if (key.isEmpty())
                            continue;
                        boolean isTurk = (provName.contains("Turcas") || provName.contains("Turco"))
                                || isKnownTurkishShow(e.title);
                        boolean isKDrama = provName.contains("Corea") || provName.contains("Dorama")
                                || "DoramasFlix".equals(e.providerName);
                        boolean isLatino = provName.contains("Latina") || provName.contains("Latino")
                                || provName.contains("Colombia") || provName.contains("México")
                                || "Canela TV".equals(e.providerName) || "SoloLatino".equals(e.providerName)
                                || isKnownLatinShow(e.title);
                        boolean isAnime = provName.contains("Anime") || provName.contains("Japón")
                                || "LatAnime".equals(e.providerName) || "JKAnime".equals(e.providerName);

                        if (isTurk) {
                            if (!uniqueTurkish.containsKey(key))
                                uniqueTurkish.put(key, e);
                        } else if (isKDrama) {
                            if (!uniqueKDrama.containsKey(key))
                                uniqueKDrama.put(key, e);
                        } else if (isLatino) {
                            if (!uniqueLatino.containsKey(key))
                                uniqueLatino.put(key, e);
                        } else if (isAnime) {
                            if (!uniqueAnime.containsKey(key))
                                uniqueAnime.put(key, e);
                        } else if (e.isMovie()) {
                            if (!uniqueMovies.containsKey(key))
                                uniqueMovies.put(key, e);
                        } else {
                            if (!uniqueSeries.containsKey(key))
                                uniqueSeries.put(key, e);
                        }
                    }
                }

                // Accesos rápidos temáticos para ampliar el catálogo en 6 columnas completas
                String lowQ = query.toLowerCase(java.util.Locale.ROOT);
                boolean qIsTurkish = lowQ.contains("turc") || lowQ.contains("turk");
                boolean qIsAnime = lowQ.contains("anim") || lowQ.contains("japon") || lowQ.contains("manga")
                        || lowQ.contains("shonen");
                boolean qIsKDrama = lowQ.contains("corea") || lowQ.contains("dorama") || lowQ.contains("kdrama");
                boolean qIsLatino = lowQ.contains("colomb") || lowQ.contains("mexic") || lowQ.contains("novela")
                        || lowQ.contains("latin");

                if (qIsTurkish || qIsAnime || qIsKDrama || qIsLatino) {
                    LinearLayout shortcutRow = new LinearLayout(this);
                    shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
                    shortcutRow.setPadding(dp(6), dp(4), dp(6), dp(10));

                    if (qIsTurkish) {
                        Button scBtn = tvAction("🇹🇷 Catálogo Completo Novelas Turcas (6 columnas)", "", () -> {
                            showCategoryGridView("🇹🇷 Novelas y Series Turcas", "CineHax", "country:tr", "");
                        }, 510, 50);
                        shortcutRow.addView(scBtn);
                    }
                    if (qIsKDrama) {
                        Button scBtn = tvAction("🇰🇷 Catálogo Completo K-Dramas (6 columnas)", "", () -> {
                            showCategoryGridView("🇰🇷 K-Dramas y Series Coreanas", "CineHax", "country:kr", "");
                        }, 480, 50);
                        shortcutRow.addView(scBtn);
                    }
                    if (qIsLatino) {
                        Button scBtn = tvAction("🇨🇴 🇲🇽 Series y Novelas Latinas (6 columnas)", "", () -> {
                            showCategoryGridView("🇨🇴 🇲🇽 Series y Novelas Latinas", "CineHax", "country:co", "");
                        }, 480, 50);
                        shortcutRow.addView(scBtn);
                    }
                    if (qIsAnime) {
                        Button scBtn = tvAction("🇯🇵 Catálogo Completo Anime (6 columnas)", "", () -> {
                            showCategoryGridView("🇯🇵 Japón y Anime Popular", "CineHax", "genre:animacion", "");
                        }, 460, 50);
                        shortcutRow.addView(scBtn);
                    }
                    contentBody.addView(shortcutRow);
                }

                boolean firstFocused = false;

                if (!uniqueTurkish.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueTurkish.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName
                                : activeProvider;
                        items.add(
                                new PosterItem(e.title, e.year > 0 ? e.year : 2026, true, e.poster, prov, e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody,
                            "🇹🇷  Novelas y Series Turcas (" + items.size() + ")", items, "CineHax", "country:tr", "",
                            "search_turkish");
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueKDrama.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueKDrama.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName
                                : activeProvider;
                        items.add(new PosterItem(e.title, e.year > 0 ? e.year : 2026, !e.isMovie(), e.poster, prov,
                                e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody,
                            "🇰🇷  K-Dramas y Series Coreanas (" + items.size() + ")", items, "CineHax", "country:kr",
                            "", "search_kdrama");
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueLatino.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueLatino.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName
                                : activeProvider;
                        items.add(new PosterItem(e.title, e.year > 0 ? e.year : 2026, !e.isMovie(), e.poster, prov,
                                e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody,
                            "🇨🇴 🇲🇽 🇦🇷  Series y Novelas Latinas (" + items.size() + ")", items, "CineHax",
                            "country:co", "", "search_latino");
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueAnime.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueAnime.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName
                                : activeProvider;
                        items.add(new PosterItem(e.title, e.year > 0 ? e.year : 2026, !e.isMovie(), e.poster, prov,
                                e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody,
                            "🌸  Anime y Animación Japonesa (" + items.size() + ")", items, "CineHax", "country:jp", "",
                            "search_anime");
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueSeries.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueSeries.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName
                                : activeProvider;
                        items.add(
                                new PosterItem(e.title, e.year > 0 ? e.year : 2026, true, e.poster, prov, e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody,
                            "📺  Series de TV encontradas (" + items.size() + ")", items, "CineHax", "serie", query,
                            "search_series");
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueMovies.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueMovies.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName
                                : activeProvider;
                        items.add(
                                new PosterItem(e.title, e.year > 0 ? e.year : 2026, false, e.poster, prov, e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody, "🎬  Películas encontradas (" + items.size() + ")",
                            items, "CineHax", "movie", query, "search_movies");
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }
            });
        }, "global-search").start();

        YouTubeEngine.searchAsync(query, ytList -> {
            if (!page.equals("search") || token != searchGeneration || isDestroyed())
                return;
            if (ytList != null && !ytList.isEmpty()) {
                addYouTubePosterRow(contentBody, "🔴  YouTube+ (Tráilers y Videos · " + ytList.size() + ")", ytList);
            }
        });
    }

    private void openYouTubeHub(LinearLayout contentBody) {
        final int token = ++searchGeneration;
        contentBody.removeAllViews();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(-1, -2);
        hrLp.setMargins(0, 4, 0, 14);
        contentBody.addView(headerRow, hrLp);

        Button backTiles = tvAction("←  Categorías", "", () -> renderLunifiedTilesGrid(contentBody), 190, 62);
        headerRow.addView(backTiles);

        TextView heading = text("🔴  YouTube+ Hub · Búsquedas, Tráilers y Directos", 22, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(14, 0, 0, 0);
        headerRow.addView(heading);

        android.widget.HorizontalScrollView pillScroll = new android.widget.HorizontalScrollView(this);
        pillScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pillRail = new LinearLayout(this);
        pillRail.setOrientation(LinearLayout.HORIZONTAL);
        pillRail.setPadding(dp(6), dp(4), dp(16), dp(12));

        final LinearLayout ytListContainer = new LinearLayout(this);
        ytListContainer.setOrientation(LinearLayout.VERTICAL);

        final TextView ytStatus = text("Cargando tráilers destacados de estrenos 2026...", 16, Color.LTGRAY);
        ytStatus.setPadding(dp(6), dp(6), dp(6), dp(12));

        String[][] pills = new String[][] {
                { "🎬 Tráilers Estrenos 2026", "trailer estrenos 2026 espanol latino" },
                { "🍿 Películas Completas Latino", "pelicula completa espanol latino 2026" },
                { "🎌 Anime Capítulos Completos", "anime capitulos completos espanol latino" },
                { "🌍 Documentales Completos", "documental historia espanol latino hd" },
                { "🔥 Tendencias YouTube", "musica y estrenos tendencias latinoamerica" }
        };

        for (int i = 0; i < pills.length; i++) {
            final String label = pills[i][0];
            final String q = pills[i][1];
            Button pBtn = tvAction(label, "", () -> {
                ytStatus.setText("Buscando en YouTube+: \"" + label + "\"...");
                ytListContainer.removeAllViews();
                YouTubeEngine.searchAsync(q, results -> {
                    if (!page.equals("search") || token != searchGeneration || isDestroyed())
                        return;
                    ytListContainer.removeAllViews();
                    if (results == null || results.isEmpty()) {
                        ytStatus.setText("No se encontraron videos para \"" + label + "\".");
                        return;
                    }
                    ytStatus.setText(results.size()
                            + " videos encontrados · Tocá cualquier video para reproducir en pantalla completa");
                    renderYouTubeGrid(ytListContainer, results);
                });
            }, dp(230), dp(48));
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(230), dp(48));
            pLp.setMargins(0, 0, dp(10), 0);
            pillRail.addView(pBtn, pLp);
        }
        pillScroll.addView(pillRail);
        contentBody.addView(pillScroll);
        contentBody.addView(ytStatus);
        contentBody.addView(ytListContainer);

        backTiles.requestFocus();

        YouTubeEngine.searchAsync(pills[0][1], results -> {
            if (!page.equals("search") || token != searchGeneration || isDestroyed())
                return;
            ytListContainer.removeAllViews();
            if (results != null && !results.isEmpty()) {
                ytStatus.setText(results.size()
                        + " videos encontrados · Tocá cualquier video para reproducir en pantalla completa");
                renderYouTubeGrid(ytListContainer, results);
            }
        });
    }

    private void renderYouTubeGrid(LinearLayout container, java.util.List<CatalogData.Entry> results) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int columns = dm.widthPixels >= 1100 ? 4 : 2;
        int availWidth = Math.max(720, dm.widthPixels - 148);
        int cardWidth = (availWidth - (columns - 1) * 16) / columns;
        int cardHeight = (int) (cardWidth * 0.5625f) + dp(68);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        container.addView(grid);

        for (CatalogData.Entry item : results) {
            android.view.View card = createYouTubeVideoCard(item, cardWidth, cardHeight);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cardWidth;
            glp.height = cardHeight;
            glp.setMargins(0, 0, dp(16), dp(16));
            grid.addView(card, glp);
        }
    }

    private android.view.View createYouTubeVideoCard(CatalogData.Entry item, int cardWidth, int cardHeight) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        card.setFocusable(true);
        card.setClickable(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(32, 34, 40));
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);

        int imgHeight = (int) ((cardWidth - dp(16)) * 0.5625f);
        FrameLayout imgWrap = new FrameLayout(this);
        card.addView(imgWrap, new LinearLayout.LayoutParams(-1, imgHeight));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable iBg = new GradientDrawable();
        iBg.setColor(Color.rgb(45, 48, 56));
        iBg.setCornerRadius(dp(8));
        img.setBackground(iBg);
        img.setClipToOutline(true);
        CatalogBrowser.loadPoster(this, img, item.poster);
        imgWrap.addView(img, new FrameLayout.LayoutParams(-1, -1));

        TextView badge = new TextView(this);
        badge.setText("🔴 " + (item.slug != null && !item.slug.isEmpty() ? item.slug : "YouTube+"));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        GradientDrawable bBg = new GradientDrawable();
        bBg.setColor(Color.argb(220, 204, 0, 0));
        bBg.setCornerRadius(dp(4));
        badge.setBackground(bBg);
        badge.setPadding(dp(6), dp(2), dp(6), dp(2));
        FrameLayout.LayoutParams bLp = new FrameLayout.LayoutParams(-2, -2);
        bLp.gravity = Gravity.BOTTOM | Gravity.END;
        bLp.setMargins(0, 0, dp(6), dp(6));
        imgWrap.addView(badge, bLp);

        TextView titleTv = new TextView(this);
        titleTv.setText(item.title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(13);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setMaxLines(2);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleTv.setPadding(0, dp(6), 0, dp(2));
        card.addView(titleTv);

        final String chan = item.originalTitle != null && !item.originalTitle.isEmpty() ? item.originalTitle
                : "YouTube";
        TextView chanTv = new TextView(this);
        chanTv.setText(chan + (item.slug != null && !item.slug.isEmpty() ? " · " + item.slug : ""));
        chanTv.setTextColor(Color.rgb(175, 182, 195));
        chanTv.setTextSize(11);
        chanTv.setSingleLine(true);
        chanTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(chanTv);

        card.setOnClickListener(v -> YouTubePlayerActivity.start(this, item.pageUrl, item.title, chan));

        card.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable fBg = new GradientDrawable();
            fBg.setColor(focused ? Color.rgb(55, 58, 70) : Color.rgb(32, 34, 40));
            fBg.setCornerRadius(dp(12));
            if (focused) {
                fBg.setStroke(dp(3), Color.WHITE);
            }
            card.setBackground(fBg);
            card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
        });

        return card;
    }

    private LinearLayout addYouTubePosterRow(LinearLayout body, String heading,
            java.util.List<CatalogData.Entry> items) {
        TextView title = text(heading, 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(8, 18, 0, 10);
        body.addView(title, tlp);

        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setFocusable(false);
        scroller.setFocusableInTouchMode(false);
        scroller.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(6, 4, 28, 10);

        int cardW = dp(240);
        int cardH = (int) (cardW * 0.5625f) + dp(68);

        for (CatalogData.Entry it : items) {
            android.view.View v = createYouTubeVideoCard(it, cardW, cardH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.setMargins(0, 0, dp(14), 0);
            rail.addView(v, lp);
        }
        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private android.view.View createSearchActorCard(ZappCatalogEngine.ActorItem actor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(8, 8, 8, 10);
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(32, 36, 48));
        bg.setCornerRadius(14);
        bg.setStroke(2, Color.rgb(68, 78, 108));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(150, 214);
        lp.setMargins(0, 0, 14, 6);
        card.setLayoutParams(lp);

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(Color.rgb(44, 52, 70));
        pBg.setCornerRadius(12);
        photo.setBackground(pBg);
        photo.setClipToOutline(true);
        CatalogBrowser.loadPoster(this, photo, actor.photoUrl);
        card.addView(photo, new LinearLayout.LayoutParams(130, 140));

        TextView name = text(actor.name, 13, Color.WHITE);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(2, 6, 2, 0);
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("Ficha y obras", 11, Color.rgb(245, 197, 66));
        sub.setGravity(Gravity.CENTER);
        card.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        card.setOnClickListener(v -> openActorDetailSheet(actor));
        card.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable f = new GradientDrawable();
            f.setColor(focused ? Color.rgb(50, 60, 88) : Color.rgb(32, 36, 48));
            f.setCornerRadius(14);
            f.setStroke(focused ? 3 : 2, focused ? Color.WHITE : Color.rgb(68, 78, 108));
            card.setBackground(f);
            card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
        });
        return card;
    }

    private void renderEntriesGrid(LinearLayout parent, java.util.List<CatalogData.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            parent.addView(text("No se encontraron resultados para los filtros seleccionados.", 15, Color.LTGRAY));
            return;
        }

        boolean parentalLocked = ParentalControlEngine.isParentalLocked(this);
        java.util.List<CatalogData.Entry> filtered = new java.util.ArrayList<>();
        for (CatalogData.Entry e : entries) {
            if (parentalLocked && ParentalControlEngine.isAdultContent(e)) {
                continue;
            }
            if ("movie".equals(searchFilterKind) && !e.isMovie())
                continue;
            if ("serie".equals(searchFilterKind) && e.isMovie())
                continue;
            if ("doc".equals(searchFilterKind) && !e.kind.contains("doc")
                    && !(e.overview != null && e.overview.toLowerCase().contains("documental")))
                continue;
            if ("anime".equals(searchFilterKind) && !e.kind.contains("anime")
                    && !(e.title != null && e.title.toLowerCase().contains("anime")))
                continue;
            filtered.add(e);
        }

        if ("rating".equals(searchSortBy)) {
            RatingEngine.sortByRating(filtered, this);
        } else if ("pop".equals(searchSortBy)) {
            RatingEngine.sortByPopularity(filtered);
        } else if ("year".equals(searchSortBy)) {
            RatingEngine.sortByYear(filtered);
        } else if ("recent".equals(searchSortBy)) {
            RatingEngine.sortByRecent(filtered);
        }

        String sortExplain = "rating".equals(searchSortBy) ? "⭐ Ordenado por Mejor valoradas (TMDB / TVMaze)"
                : ("pop".equals(searchSortBy) ? "🔥 Ordenado por Más populares"
                        : ("year".equals(searchSortBy) ? "📅 Ordenado por Año de estreno"
                                : "🕒 Ordenado por Recientes"));
        TextView sortInfo = text(sortExplain + " · " + filtered.size() + " títulos en el catálogo consultado", 13,
                Color.rgb(245, 197, 66));
        sortInfo.setPadding(4, 2, 4, 10);
        parent.addView(sortInfo);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int columns = dm.widthPixels >= 1100 ? 5 : 3;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        parent.addView(grid);
        for (CatalogData.Entry e : filtered) {
            PosterItem pi = new PosterItem(
                    e.title,
                    e.year > 0 ? e.year : 2026,
                    !e.isMovie(),
                    e.poster,
                    (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName : activeProvider,
                    e.title,
                    e);
            android.view.View card = createPosterCard(pi);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 198;
            gp.height = -2;
            gp.setMargins(0, 0, 16, 18);
            grid.addView(card, gp);
        }
    }

    private void home() {
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        if (resolutionTask != null)
            resolutionTask.cancel(true);
        ++providerPlayRequest;
        providerLoading = false;
        page = "home";
        channel = "";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(16, 20, 26));

        boolean isPortraitHome = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        // Barra lateral izquierda persistente (estilo Lunified / Zapp TV)
        LinearLayout side = buildSidebar(activeTab);
        int sideWidth = isPortraitHome ? dp(54) : dp(88);
        root.addView(side, new LinearLayout.LayoutParams(sideWidth, -1));

        // Área principal derecha con carruseles de pósters
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(isPortraitHome ? dp(8) : dp(24), dp(12), 0, dp(10));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        pageScroll = scroll;
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(32));
        scroll.addView(body);

        if (activeProvider == null || isDeadProvider(activeProvider)) {
            activeProvider = "CineHax";
        }
        String prov = activeProvider;
        LinearLayout firstRail = null;

        if ("favorites".equals(activeTab)) {
            showFavoritesPage();
            return;
        } else if ("watch_later".equals(activeTab)) {
            showWatchLaterPage();
            return;
        } else if ("adults".equals(activeTab)) {
            TextView adHead = text("🔞  Zona Adultos (+18) · Contenido Sexual Explícito", 22, Color.rgb(255, 90, 90));
            adHead.setTypeface(null, Typeface.BOLD);
            adHead.setPadding(dp(8), dp(12), dp(8), dp(4));
            body.addView(adHead);

            TextView adSub = text(
                    "Acceso protegido por Control Parental. Reservado exclusivamente para contenido sexual explícito (+18).",
                    13,
                    Color.rgb(200, 180, 180));
            adSub.setPadding(dp(8), 0, dp(8), dp(8));
            body.addView(adSub);

            Button lockNowBtn = new Button(this);
            lockNowBtn.setText("🔒  Volver a Bloquear Zona Adultos");
            lockNowBtn.setTextColor(Color.WHITE);
            lockNowBtn.setTextSize(13);
            lockNowBtn.setAllCaps(false);
            lockNowBtn.setFocusable(true);
            GradientDrawable lbBg = new GradientDrawable();
            lbBg.setColor(Color.rgb(180, 40, 40));
            lbBg.setCornerRadius(dp(8));
            lockNowBtn.setBackground(lbBg);
            lockNowBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(220, 50, 50) : Color.rgb(180, 40, 40));
                g.setCornerRadius(dp(8));
                if (f)
                    g.setStroke(dp(2), Color.WHITE);
                lockNowBtn.setBackground(g);
                lockNowBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
            });
            lockNowBtn.setOnClickListener(v -> {
                ParentalControlEngine.lockSession();
                Toast.makeText(this, "Control parental bloqueado nuevamente", Toast.LENGTH_SHORT).show();
                activeTab = "all";
                home();
            });
            LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(-2, dp(40));
            lbLp.setMargins(dp(8), 0, 0, dp(14));
            body.addView(lockNowBtn, lbLp);

            // Panel informativo de estado del control parental
            LinearLayout statusPanel = new LinearLayout(this);
            statusPanel.setOrientation(LinearLayout.VERTICAL);
            statusPanel.setPadding(dp(20), dp(18), dp(20), dp(18));
            GradientDrawable spBg = new GradientDrawable();
            spBg.setColor(Color.rgb(26, 28, 38));
            spBg.setCornerRadius(dp(12));
            spBg.setStroke(dp(2), Color.rgb(75, 82, 106));
            statusPanel.setBackground(spBg);

            TextView spTitle = text("🛡️ Estado de Seguridad del Control Parental", 16, Color.rgb(245, 197, 66));
            spTitle.setTypeface(null, Typeface.BOLD);
            statusPanel.addView(spTitle);

            TextView spDesc = text(
                    "El Control Parental está activo con protección estricta por PIN de 4 dígitos.\nTodo el cine clasificado comercial, dramas y romances se encuentran en sus categorías abiertas normales.\nEsta sección queda reservada exclusivamente para contenido para adultos explícito.",
                    13, Color.rgb(200, 210, 228));
            spDesc.setPadding(0, dp(8), 0, dp(12));
            statusPanel.addView(spDesc);

            Button changePinBtn = new Button(this);
            changePinBtn.setText("🔑  Cambiar PIN de Control Parental");
            changePinBtn.setTextColor(Color.WHITE);
            changePinBtn.setTextSize(13);
            changePinBtn.setAllCaps(false);
            changePinBtn.setFocusable(true);
            GradientDrawable cpbBg = new GradientDrawable();
            cpbBg.setColor(Color.rgb(44, 52, 72));
            cpbBg.setCornerRadius(dp(8));
            changePinBtn.setBackground(cpbBg);
            changePinBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 78, 108) : Color.rgb(44, 52, 72));
                g.setCornerRadius(dp(8));
                if (f)
                    g.setStroke(dp(2), Color.WHITE);
                changePinBtn.setBackground(g);
                changePinBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
            });
            changePinBtn.setOnClickListener(v -> showParentalChangePinDialog());
            statusPanel.addView(changePinBtn, new LinearLayout.LayoutParams(-2, dp(38)));

            LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dp(620), -2);
            spLp.setMargins(dp(8), dp(8), dp(8), dp(24));
            body.addView(statusPanel, spLp);

            // Rails de contenido para adultos
            LinearLayout rAdLive = addPosterRow(body, "📺  Señales en Vivo +18 (TV para Adultos)",
                    defaultAdultChannelsPosters(), "LiveTV", "movie", "", "adult_live");
            if (firstRail == null)
                firstRail = rAdLive;

            LinearLayout rAdMov = addPosterRow(body, "🔞  Películas y Cine Para Adultos (+18)",
                    defaultAdultMoviesPosters(), "CineHax", "movie", "adultos", "adult_movies");

            LinearLayout rAdHen = addPosterRow(body, "🎌  Anime & Animación Para Adultos (+18)",
                    defaultHentaiPosters(), "LatAnime", "serie", "adultos", "adult_hentai");
        } else {
            if ("all".equals(activeTab)) {
                // 1. Featured Hero Slideshow banner estilo MXL Movies
                buildHeroSlideShow(body, defaultFeaturedSlides(prov));

                // Botón elegante para personalizar inicio (ordenar / ocultar carruseles)
                LinearLayout custBar = new LinearLayout(this);
                custBar.setOrientation(LinearLayout.HORIZONTAL);
                custBar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                custBar.setPadding(0, dp(4), dp(16), dp(10));
                Button custBtn = new Button(this);
                custBtn.setText("⚙️  Personalizar Inicio (Ordenar / Ocultar carruseles)");
                custBtn.setTextColor(Color.rgb(212, 175, 55));
                custBtn.setTextSize(13);
                custBtn.setAllCaps(false);
                GradientDrawable cbBg = new GradientDrawable();
                cbBg.setColor(Color.rgb(24, 30, 44));
                cbBg.setCornerRadius(10);
                cbBg.setStroke(dp(1), Color.rgb(60, 75, 105));
                custBtn.setBackground(cbBg);
                custBtn.setOnClickListener(v -> showCarouselCustomizer());
                custBar.addView(custBtn, new LinearLayout.LayoutParams(-2, dp(40)));
                body.addView(custBar);

                // 1. Continuar viendo si hay elementos guardados con progreso (agrupado por serie para evitar duplicados de capítulos incompletos)
                java.util.List<PlaybackEngine.PlaybackProgress> cwList = PlaybackEngine.getContinueWatchingList(this);
                if (!cwList.isEmpty()) {
                    java.util.List<PosterItem> cwPosters = new java.util.ArrayList<>();
                    java.util.Set<String> seenSeries = new java.util.HashSet<>();
                    for (PlaybackEngine.PlaybackProgress p : cwList) {
                        boolean isSeries = (!"movie".equals(p.kind))
                                || (p.title != null && (p.title.matches(".*(?:[·\\-–—]\\s*)?(?i)(?:T\\d+|Cap\\.?|Ep\\.?|Temporada).*")
                                || p.title.matches(".*(?i)T\\d+\\s*E\\d+.*")));
                        String displayTitle = p.title;
                        String baseSeriesTitle = "";
                        if (isSeries) {
                            baseSeriesTitle = p.title.replaceAll("\\s*(?:[·|\\-|–—]\\s*)?(?i)(?:Temporada|Capítulo|Cap\\.?|Ep\\.?|Season|T\\d+\\s*E\\d+|T\\d+).*", "").trim();
                            if (baseSeriesTitle.isEmpty()) baseSeriesTitle = p.title;
                            if (seenSeries.contains(baseSeriesTitle.toLowerCase(java.util.Locale.ROOT))) {
                                continue; // Ya mostramos la serie agrupada en Continuar viendo
                            }
                            seenSeries.add(baseSeriesTitle.toLowerCase(java.util.Locale.ROOT));
                            displayTitle = baseSeriesTitle;
                        }
                        cwPosters.add(new PosterItem(
                                displayTitle + (isSeries ? "" : " (" + p.getPercentage() + "%)"),
                                2026,
                                isSeries,
                                p.poster,
                                p.provider,
                                isSeries ? baseSeriesTitle : p.title,
                                null));
                    }
                    if (!cwPosters.isEmpty()) {
                        LinearLayout rCw = addPosterRow(body, "▶  Continuar viendo (" + cwPosters.size() + ")", cwPosters,
                                "CineHax", "movie", "", "continue_watching");
                        if (firstRail == null)
                            firstRail = rCw;
                    }
                }

                // 2. Mis Favoritos en Inicio si hay títulos guardados
                final java.util.List<CatalogData.Entry> favList = ZappCatalogEngine.getFavorites(this);
                if (!favList.isEmpty()) {
                    java.util.List<PosterItem> favPosters = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : favList) {
                        favPosters.add(new PosterItem(
                                e.title,
                                e.year > 0 ? e.year : 2026,
                                !e.isMovie(),
                                e.poster,
                                e.providerName != null ? e.providerName : "CineHax",
                                e.title,
                                e));
                    }
                    LinearLayout rFav = addPosterRow(body, "❤️  Mis Favoritos (" + favList.size() + ")", favPosters,
                            "CineHax", "movie", "", "favorites");
                    if (firstRail == null)
                        firstRail = rFav;
                }

                // 3. Ver más tarde en Inicio si hay títulos guardados
                final java.util.List<CatalogData.Entry> wlList = ZappCatalogEngine.getWatchLater(this);
                if (!wlList.isEmpty()) {
                    java.util.List<PosterItem> wlPosters = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : wlList) {
                        wlPosters.add(new PosterItem(
                                e.title,
                                e.year > 0 ? e.year : 2026,
                                !e.isMovie(),
                                e.poster,
                                e.providerName != null ? e.providerName : "CineHax",
                                e.title,
                                e));
                    }
                    LinearLayout rWl = addPosterRow(body, "🔖  Ver más tarde (" + wlList.size() + ")", wlPosters,
                            "CineHax", "movie", "", "watch_later");
                    if (firstRail == null)
                        firstRail = rWl;
                }

                // Obtener orden y carruseles ocultos configurados por el usuario
                java.util.List<String> order = ZappCatalogEngine.getCarouselOrder(this, getDefaultCarouselOrder());
                java.util.Set<String> hidden = ZappCatalogEngine.getHiddenCarousels(this);

                for (String cId : order) {
                    if (hidden.contains(cId))
                        continue;

                    if ("top10".equals(cId)) {
                        LinearLayout rTop10 = addTop10Row(body, "🔥  Top 10 en Leo TV hoy", defaultTop10(prov));
                        if (firstRail == null)
                            firstRail = rTop10;
                    } else if ("movies2026".equals(cId)) {
                        LinearLayout r1 = addPosterRow(body, "🎬  Estrenos de Cine y Cartelera 2026 (" + prov + ")",
                                defaultMovies2026(prov), prov, "movie", "", "movies2026");
                        populateLiveRow(r1, prov, "movie", "", 1, 0, firstRail == null,
                                "🎬  Estrenos de Cine y Cartelera 2026 (" + prov + ")", "movies2026");
                        if (firstRail == null)
                            firstRail = r1;
                    } else if ("turkish".equals(cId)) {
                        LinearLayout rTr = addPosterRow(body, "🇹🇷  Novelas y Series Turcas",
                                defaultTurkishNovelas(prov), "CineHax", "country:tr", "", "turkish");
                        populateLiveRow(rTr, "CineHax", "country:tr", "", 1, 0, firstRail == null,
                                "🇹🇷  Novelas y Series Turcas", "turkish");
                        if (firstRail == null)
                            firstRail = rTr;
                    } else if ("turkish_movies".equals(cId)) {
                        LinearLayout rTrM = addPosterRow(body, "🇹🇷  Cine y Películas Turcas",
                                defaultTurkishMovies(), "CineHax", "movie", "turquia", "turkish_movies");
                        populateLiveRow(rTrM, "CineHax", "movie", "turquia", 1, 0, false,
                                "🇹🇷  Cine y Películas Turcas", "turkish_movies");
                    } else if ("kdramas".equals(cId)) {
                        LinearLayout rKr = addPosterRow(body, "🇰🇷  K-Dramas y Series Coreanas", defaultKDramas(prov),
                                "CineHax", "country:kr", "", "kdramas");
                        populateLiveRow(rKr, "CineHax", "country:kr", "", 1, 0, false,
                                "🇰🇷  K-Dramas y Series Coreanas", "kdramas");
                    } else if ("korean_movies".equals(cId)) {
                        LinearLayout rKrM = addPosterRow(body, "🇰🇷  Cine y Películas Coreanas",
                                defaultKoreanMovies(), "CineHax", "movie", "corea", "korean_movies");
                        populateLiveRow(rKrM, "CineHax", "movie", "corea", 1, 0, false,
                                "🇰🇷  Cine y Películas Coreanas", "korean_movies");
                    } else if ("popular_movies".equals(cId)) {
                        LinearLayout rPop = addPosterRow(body, "⭐  Películas Populares y Aclamadas",
                                defaultPopularMovies(), prov, "movie", "popular", "popular_movies");
                        populateLiveRow(rPop, prov, "movie", "popular", 1, 0, false,
                                "⭐  Películas Populares y Aclamadas", "popular_movies");
                    } else if ("series2026".equals(cId)) {
                        LinearLayout r2 = addPosterRow(body, "📺  Series nuevas y en Tendencia · 2026 (" + prov + ")",
                                defaultSeries2026(prov), prov, "serie", "", "series2026");
                        populateLiveRow(r2, prov, "serie", "", 1, 0, firstRail == null,
                                "📺  Series nuevas y en Tendencia · 2026 (" + prov + ")", "series2026");
                        if (firstRail == null)
                            firstRail = r2;
                    } else if ("latin".equals(cId)) {
                        LinearLayout rLat = addPosterRow(body, "🇲🇽 🇨🇴 🇦🇷  Series y Novelas Latinas",
                                defaultLatin2026(prov), "CineHax", "country:mx", "", "latin");
                        populateLiveRow(rLat, "CineHax", "country:mx", "", 1, 0, false,
                                "🇲🇽 🇨🇴 🇦🇷  Series y Novelas Latinas", "latin");
                    } else if ("live_music".equals(cId)) {
                        addPosterRow(body, "🎸  Conciertos en Vivo y Especiales Musicales",
                                defaultLiveConcerts(), "YouTube+", "movie", "concierto", "live_music");
                    } else if ("asian_fantasy".equals(cId)) {
                        LinearLayout rAsi = addPosterRow(body, "🏯  Doramas Románticos y Fantasía Asiática",
                                defaultAsianFantasy(prov), "CineHax", "country:kr", "", "asian_fantasy");
                        populateLiveRow(rAsi, "CineHax", "country:kr", "", 1, 0, false,
                                "🏯  Doramas Románticos y Fantasía Asiática", "asian_fantasy");
                    } else if ("anime_airing".equals(cId)) {
                        LinearLayout rAnAir = addPosterRow(body, "⚡  Anime en Emisión y Tendencias",
                                defaultAnimeAiring(), "LatAnime", "movie", "airing", "anime_airing");
                        populateLiveRow(rAnAir, "LatAnime", "movie", "airing", 1, 0, false,
                                "⚡  Anime en Emisión y Tendencias", "anime_airing");
                    } else if ("shonen".equals(cId)) {
                        LinearLayout rSho = addPosterRow(body, "⚔️  Anime Shonen y Grandes Batallas",
                                defaultAnimeShonen(), "LatAnime", "movie", "", "shonen");
                        populateLiveRow(rSho, "LatAnime", "movie", "", 1, 0, false,
                                "⚔️  Anime Shonen y Grandes Batallas", "shonen");
                    } else if ("anime_movies".equals(cId)) {
                        LinearLayout rAnMov = addPosterRow(body, "🎌  Películas de Anime",
                                defaultAnimeMovies(), "LatAnime", "movie", "peliculas", "anime_movies");
                        populateLiveRow(rAnMov, "LatAnime", "movie", "peliculas", 1, 0, false,
                                "🎌  Películas de Anime", "anime_movies");
                    } else if ("action".equals(cId)) {
                        LinearLayout r3 = addPosterRow(body, "💥  Acción y Aventura Sin Límites",
                                defaultAction2026(prov), "CineHax", "genre:accion", "", "action");
                        populateLiveRow(r3, "CineHax", "genre:accion", "", 1, 0, false,
                                "💥  Acción y Aventura Sin Límites", "action");
                    } else if ("terror".equals(cId)) {
                        LinearLayout r5 = addPosterRow(body, "👻  Terror, Suspenso y Misterio", defaultTerror2026(prov),
                                "CineHax", "genre:terror", "", "terror");
                        populateLiveRow(r5, "CineHax", "genre:terror", "", 1, 0, false,
                                "👻  Terror, Suspenso y Misterio", "terror");
                    } else if ("scifi".equals(cId)) {
                        LinearLayout rSc = addPosterRow(body, "🚀  Ciencia Ficción y Futuros Distópicos",
                                defaultSciFi(prov), "CineHax", "genre:ciencia-ficcion", "", "scifi");
                        populateLiveRow(rSc, "CineHax", "genre:ciencia-ficcion", "", 1, 0, false,
                                "🚀  Ciencia Ficción y Futuros Distópicos", "scifi");
                    } else if ("comedy".equals(cId)) {
                        LinearLayout r4 = addPosterRow(body, "😂  Comedia y Risas en Familia", defaultComedy2026(prov),
                                "CineHax", "genre:comedia", "", "comedy");
                        populateLiveRow(r4, "CineHax", "genre:comedia", "", 1, 0, false,
                                "😂  Comedia y Risas en Familia", "comedy");
                    } else if ("romance".equals(cId)) {
                        LinearLayout rRo = addPosterRow(body, "❤️  Romance, Pasión y Melodrama", defaultRomance(prov),
                                "CineHax", "genre:romance", "", "romance");
                        populateLiveRow(rRo, "CineHax", "genre:romance", "", 1, 0, false,
                                "❤️  Romance, Pasión y Melodrama", "romance");
                    } else if ("crime".equals(cId)) {
                        LinearLayout rCri = addPosterRow(body, "🕵️  Crimen, Mafia y Suspense Policial",
                                defaultCrime2026(prov), "CineHax", "genre:crimen", "", "crime");
                        populateLiveRow(rCri, "CineHax", "genre:crimen", "", 1, 0, false,
                                "🕵️  Crimen, Mafia y Suspense Policial", "crime");
                    } else if ("historical".equals(cId)) {
                        LinearLayout rHis = addPosterRow(body, "👑  Dramas de Época e Históricos",
                                defaultDrama2026(prov), "CineHax", "genre:drama", "", "historical");
                        populateLiveRow(rHis, "CineHax", "genre:drama", "", 1, 0, false,
                                "👑  Dramas de Época e Históricos", "historical");
                    } else if ("documentaries".equals(cId)) {
                        LinearLayout rDoc = addPosterRow(body, "🌿  Documentales y Naturaleza",
                                defaultDocumentaries(), "CineHax", "genre:documental", "", "documentaries");
                        populateLiveRow(rDoc, "CineHax", "genre:documental", "", 1, 0, false,
                                "🌿  Documentales y Naturaleza", "documentaries");
                    } else if ("kids".equals(cId)) {
                        LinearLayout r7 = addPosterRow(body, "👶  Kids y Animación Familiar", defaultKids(prov),
                                "CineHax", "genre:animacion", "", "kids");
                        populateLiveRow(r7, "CineHax", "genre:animacion", "", 1, 0, false,
                                "👶  Kids y Animación Familiar", "kids");
                    } else if ("classic".equals(cId)) {
                        addPosterRow(body, "🏛️  Cine Clásico de Oro (Dominio Público)", defaultClassicMovies(),
                                "Internet Archive", "movie", "", "classic");
                    } else if ("sports".equals(cId)) {
                        addSportsLiveRow(body, "⚽  Deportes y Eventos en Vivo (Agenda SK Tech)", defaultSportsEvents());
                    }
                }
            } else if ("movies".equals(activeTab)) {
                // 1. Hero Slideshow rotativo exclusivo de Películas
                buildHeroSlideShow(body, defaultMovieSlides(prov));

                // 2. Barra de filtros horizontales estilo Xuper
                buildSectionFilterBar(body,
                        new String[] { "⭐ Todas", "🎞️ Estrenos 2026", "🇹🇷 Cine Turco", "🇰🇷 Cine Coreano",
                                "⭐ Populares", "💥 Acción", "👻 Terror",
                                "🚀 Ciencia Ficción", "😂 Comedia", "❤️ Romance", "🎸 Conciertos", "👶 Animación",
                                "🏛️ Clásicos",
                                "🌱 Cine Abierto" },
                        new String[] { "top", "movies2026", "turkish_movies", "korean_movies", "popular_movies",
                                "action", "terror", "scifi", "comedy",
                                "romance", "live_music", "kids", "classic", "blender" },
                        pageScroll);

                LinearLayout r1 = addPosterRow(body, "🎬  Películas nuevas y Estrenos · 2026 (" + prov + ")",
                        defaultMovies2026(prov), prov, "movie", "", "movies2026");
                populateLiveRow(r1, prov, "movie", "", 1, 0, true, "🎬  Películas nuevas · 2026", "movies2026");
                firstRail = r1;

                LinearLayout rTrMov = addPosterRow(body, "🇹🇷  Cine y Películas Turcas", defaultTurkishMovies(),
                        "CineHax", "movie", "turquia", "turkish_movies");
                populateLiveRow(rTrMov, "CineHax", "movie", "turquia", 1, 0, false, "🇹🇷  Cine Turco",
                        "turkish_movies");

                LinearLayout rKrMov = addPosterRow(body, "🇰🇷  Cine y Películas Coreanas", defaultKoreanMovies(),
                        "CineHax", "movie", "corea", "korean_movies");
                populateLiveRow(rKrMov, "CineHax", "movie", "corea", 1, 0, false, "🇰🇷  Cine Coreano",
                        "korean_movies");

                LinearLayout rPop = addPosterRow(body, "⭐  Películas Populares y Aclamadas",
                        defaultPopularMovies(), prov, "movie", "popular", "popular_movies");
                populateLiveRow(rPop, prov, "movie", "popular", 1, 0, false, "⭐  Películas Populares y Aclamadas",
                        "popular_movies");

                LinearLayout r3 = addPosterRow(body, "💥  Acción y Aventura Sin Límites", defaultAction2026(prov),
                        "CineHax",
                        "genre:accion", "", "action");
                populateLiveRow(r3, "CineHax", "genre:accion", "", 1, 0, false, "💥  Acción y Aventura", "action");

                LinearLayout r5 = addPosterRow(body, "👻  Terror, Suspenso y Misterio", defaultTerror2026(prov),
                        "CineHax",
                        "genre:terror", "", "terror");
                populateLiveRow(r5, "CineHax", "genre:terror", "", 1, 0, false, "👻  Terror y Suspenso", "terror");

                LinearLayout rSc = addPosterRow(body, "🚀  Ciencia Ficción y Futuros Distópicos", defaultSciFi(prov),
                        "CineHax",
                        "genre:ciencia-ficcion", "", "scifi");
                populateLiveRow(rSc, "CineHax", "genre:ciencia-ficcion", "", 1, 0, false, "🚀  Ciencia Ficción",
                        "scifi");

                LinearLayout r4 = addPosterRow(body, "😂  Comedia y Risas en Familia", defaultComedy2026(prov),
                        "CineHax", "genre:comedia",
                        "", "comedy");
                populateLiveRow(r4, "CineHax", "genre:comedia", "", 1, 0, false, "😂  Comedia", "comedy");

                LinearLayout rRo = addPosterRow(body, "❤️  Romance, Pasión y Melodrama", defaultRomance(prov),
                        "CineHax", "genre:romance",
                        "", "romance");
                populateLiveRow(rRo, "CineHax", "genre:romance", "", 1, 0, false, "❤️  Romance", "romance");

                LinearLayout r7 = addPosterRow(body, "👶  Animación y Cine Familiar", defaultKids(prov), "CineHax",
                        "genre:animacion", "", "kids");
                populateLiveRow(r7, "CineHax", "genre:animacion", "", 1, 0, false, "👶  Animación y Cine Familiar",
                        "kids");

                addPosterRow(body, "🏛️  Cine Clásico de Oro (Dominio Público)", defaultClassicMovies(),
                        "Internet Archive", "movie", "", "classic");

                addPosterRow(body, "🌱  Cortometrajes y Cine Abierto 4K", defaultBlenderMovies(),
                        "Blender Open Movies", "movie", "", "blender");

                addPosterRow(body, "🎸  Conciertos en Vivo y Especiales Musicales", defaultLiveConcerts(),
                        "YouTube+", "movie", "concierto", "live_music");

            } else if ("series".equals(activeTab)) {
                // 1. Hero Slideshow rotativo exclusivo de Series
                buildHeroSlideShow(body, defaultSeriesSlides(prov));

                // 2. Barra de filtros horizontales estilo Xuper
                buildSectionFilterBar(body,
                        new String[] { "⭐ Todas", "📺 Nuevas 2026", "🇹🇷 Novelas Turcas", "🇰🇷 K-Dramas",
                                "🇲🇽 🇨🇴 Series Latinas",
                                "⚔️ Anime Shōnen", "🏯 Fantasía Asiática", "👑 Históricas", "🕵️ Crimen y Policial" },
                        new String[] { "top", "series2026", "turkish", "kdramas", "latin", "shonen", "asian_fantasy",
                                "historical", "crime" },
                        pageScroll);

                LinearLayout r2 = addPosterRow(body, "📺  Series nuevas y Temporadas · 2026 (" + prov + ")",
                        defaultSeries2026(prov),
                        prov, "serie", "", "series2026");
                populateLiveRow(r2, prov, "serie", "", 1, 0, true, "📺  Series nuevas · 2026", "series2026");
                firstRail = r2;

                LinearLayout rTr = addPosterRow(body, "🇹🇷  Novelas y Series Turcas", defaultTurkishNovelas(prov),
                        "CineHax", "country:tr", "", "turkish");
                populateLiveRow(rTr, "CineHax", "country:tr", "", 1, 0, false, "🇹🇷  Novelas y Series Turcas",
                        "turkish");

                LinearLayout rKr = addPosterRow(body, "🇰🇷  K-Dramas y Series Coreanas", defaultKDramas(prov),
                        "CineHax", "country:kr", "", "kdramas");
                populateLiveRow(rKr, "CineHax", "country:kr", "", 1, 0, false, "🇰🇷  K-Dramas y Series Coreanas",
                        "kdramas");

                LinearLayout rLat = addPosterRow(body,
                        "🇲🇽 🇨🇴 🇦🇷  Series y Novelas Latinas (Colombia, México, Arg)", defaultLatin2026(prov),
                        "CineHax", "country:mx", "", "latin");
                populateLiveRow(rLat, "CineHax", "country:mx", "", 1, 0, false, "🇲🇽 🇨🇴 🇦🇷  Series Latinas",
                        "latin");

                LinearLayout rSho = addPosterRow(body, "⚔️  Anime Shonen y Batallas Épicas", defaultAnimeShonen(),
                        "LatAnime", "movie", "", "shonen");
                populateLiveRow(rSho, "LatAnime", "movie", "", 1, 0, false, "⚔️  Anime Shonen", "shonen");

                LinearLayout rAsi = addPosterRow(body, "🏯  Doramas Románticos y Fantasía Asiática",
                        defaultAsianFantasy(prov),
                        "CineHax", "country:kr", "", "asian_fantasy");
                populateLiveRow(rAsi, "CineHax", "country:kr", "", 1, 0, false, "🏯  Doramas Románticos",
                        "asian_fantasy");

                LinearLayout rHis = addPosterRow(body, "👑  Dramas de Época e Históricos", defaultDrama2026(prov),
                        "CineHax", "genre:drama", "", "historical");
                populateLiveRow(rHis, "CineHax", "genre:drama", "", 1, 0, false, "👑  Dramas de Época e Históricos",
                        "historical");

                LinearLayout rCri = addPosterRow(body, "🕵️  Crimen, Mafia y Suspense Policial", defaultCrime2026(prov),
                        "CineHax", "genre:crimen", "", "crime");
                populateLiveRow(rCri, "CineHax", "genre:crimen", "", 1, 0, false, "🕵️  Crimen y Suspense", "crime");

            } else if ("docs".equals(activeTab)) {
                // 1. Hero Slideshow rotativo exclusivo de Documentales
                buildHeroSlideShow(body, defaultDocSlides(prov));

                // 2. Barra de filtros horizontales estilo Xuper
                buildSectionFilterBar(body,
                        new String[] { "⭐ Todos", "🌿 Naturaleza", "🏛️ Historia", "🔬 Ciencia", "🚀 Cosmos",
                                "🕵️ Crímenes y Deportes" },
                        new String[] { "top", "doc_nature", "doc_history", "doc_science", "doc_space", "doc_crime" },
                        pageScroll);

                LinearLayout rDoc1 = addPosterRow(body, "🌿  Naturaleza, Fauna y Biodiversidad del Planeta",
                        defaultDocNature(), "CineHax", "genre:documental", "", "doc_nature");
                populateLiveRow(rDoc1, "CineHax", "genre:documental", "", 1, 0, true, "🌿  Naturaleza y Fauna",
                        "doc_nature");
                firstRail = rDoc1;

                LinearLayout rDoc2 = addPosterRow(body, "🏛️  Historia Universal y Grandes Civilizaciones",
                        defaultDocHistory(), "CineHax", "genre:historia", "", "doc_history");
                populateLiveRow(rDoc2, "CineHax", "genre:historia", "", 1, 0, false, "🏛️  Historia Universal",
                        "doc_history");

                LinearLayout rDoc3 = addPosterRow(body, "🔬  Ciencia, Innovación y Tecnología del Futuro",
                        defaultDocScience(), "CineHax", "genre:ciencia-ficcion", "", "doc_science");
                populateLiveRow(rDoc3, "CineHax", "genre:ciencia-ficcion", "", 1, 0, false, "🔬  Ciencia y Tecnología",
                        "doc_science");

                LinearLayout rDoc4 = addPosterRow(body, "🚀  Espacio Exterior, Astronomía y el Cosmos",
                        defaultDocSpace(), "CineHax", "genre:espacio", "", "doc_space");
                populateLiveRow(rDoc4, "CineHax", "genre:espacio", "", 1, 0, false, "🚀  Espacio Exterior",
                        "doc_space");

                LinearLayout rDoc5 = addPosterRow(body, "🕵️  Crímenes Reales, Biografías y Deportes",
                        defaultDocCrime(), "CineHax", "genre:crimen", "", "doc_crime");
                populateLiveRow(rDoc5, "CineHax", "genre:crimen", "", 1, 0, false, "🕵️  Crímenes Reales y Deportes",
                        "doc_crime");
            }
        }

        setContentView(root);
        currentFirstRail = firstRail;
        final LinearLayout focusTarget = firstRail;
        side.post(() -> {
            if (focusTarget != null && focusTarget.getChildCount() > 0) {
                focusTarget.getChildAt(0).requestFocus();
            } else if (side.getChildCount() > 2) {
                side.getChildAt(2).requestFocus();
            }
        });
    }

    private void updateHero(String name) {
        if (heroTitle == null)
            return;
        if (name.equals("Demo")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("DESTACADO  ·  PELÍCULA ABIERTA DE PRUEBA");
            heroTitle.setText("Big Buck Bunny (2008)");
            if (heroBadge != null)
                heroBadge.setText("1080p MP4  ·  Blender Foundation  ·  CC BY 3.0  ·  Reproducción probada");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Un conejo gigante y bondadoso del bosque decide darle una lección a tres roedores traviesos que molestan a criaturas indefensas.");
        } else if (name.equals("Películas (Latino)")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("PELÍCULAS  ·  AUDIO LATINO Y ESTRENOS");
            heroTitle.setText("Cine en Audio Latino");
            if (heroBadge != null)
                heroBadge.setText("Películas con doblaje latino y castellano");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Catálogos de cine con preferencia de audio latino: CineHax, LaMovie, CineCalidad, Cuevana y películas abiertas.");
        } else if (name.equals("Series (Latino)")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("SERIES  ·  TEMPORADAS COMPLETAS");
            heroTitle.setText("Series en Audio Latino");
            if (heroBadge != null)
                heroBadge.setText("Temporadas y capítulos completos en español");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Series y novelas organizadas por temporadas y episodios con audio latino: CineHax, SeriesMetro, Pelispedia.");
        } else if (name.equals("TV en vivo")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("TELEVISIÓN EN VIVO  ·  SOLO ESPAÑOL (SIN RADIOS)");
            heroTitle.setText("TV Abierta en Español");
            if (heroBadge != null)
                heroBadge.setText("Canales de Argentina y España  ·  M3U8");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Señales de televisión abierta en español. Radios y canales en otros idiomas omitidos para mayor comodidad.");
        } else if (name.equals("Infantil (Dibujos)")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("INFANTIL  ·  DOBLAJE LATINO");
            heroTitle.setText("Dibujos Animados Latinos");
            if (heroBadge != null)
                heroBadge.setText("Dibujos animados clásicos y Studio Ghibli Latino");
            if (heroSynopsis != null)
                heroSynopsis.setText("Selección infantil familiar con doblaje latino oficial.");
        } else if (name.equals("Anime (Latino)")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("ANIME  ·  DOBLAJE LATINO Y SUBTITULADO");
            heroTitle.setText("Anime en Audio Latino");
            if (heroBadge != null)
                heroBadge.setText("LatAnime, TioAnime, JKAnime");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Series y películas de animación japonesa con doblaje latino oficial y opciones subtituladas.");
        } else if (name.equals("CineHax") || name.equals("CineHax+")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("PROVEEDOR VOD  ·  AUDIO LATINO");
            heroTitle.setText("CineHax (Latino)");
            if (heroBadge != null)
                heroBadge.setText("Películas y series en audio latino  ·  Temporadas y episodios");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Catálogo estructurado de cine y series con resolución dinámica de servidores de video compatibles.");
        } else if (name.equals("LaMovie")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("PROVEEDOR VOD  ·  CINE EN ESPAÑOL");
            heroTitle.setText("LaMovie (Latino)");
            if (heroBadge != null)
                heroBadge.setText("Audio Latino y Subtitulado  ·  TMDB integrado");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Catálogo de cine con carátulas y metadatos de películas con doblaje latino y castellano.");
        } else if (name.equals("LatAnime")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("ANIME  ·  DOBLAJE LATINO OFICIAL");
            heroTitle.setText("LatAnime (Audio Latino)");
            if (heroBadge != null)
                heroBadge.setText("Doblaje latino oficial  ·  Capítulos completos");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Catálogo especializado de animación japonesa con audio latino oficial y capítulos verificados.");
        } else if (name.equals("Blender Open Movies")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("CINE ABIERTO  ·  ANIMACIÓN LIBRE OFICIAL");
            heroTitle.setText("Blender Open Movies");
            if (heroBadge != null)
                heroBadge.setText("Elephants Dream, Big Buck Bunny, Sintel  ·  Verificado");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Películas y cortos de animación de código abierto producidos por Blender Foundation con enlaces y metadatos verificados.");
        } else if (name.equals("Internet Archive")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("CATÁLOGO ONLINE  ·  DOMINIO PÚBLICO Y CC");
            heroTitle.setText("Internet Archive Cinema");
            if (heroBadge != null)
                heroBadge.setText("API Pública en vivo  ·  Películas clásicas y abiertas");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Colección cinematográfica de películas con licencia Creative Commons y dominio público, con portadas y reproducción directa.");
        } else if (name.equals("TDTChannels TV")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("TELEVISIÓN EN VIVO  ·  EMISIONES ABIERTAS EN ESPAÑOL");
            heroTitle.setText("TDTChannels TV");
            if (heroBadge != null)
                heroBadge.setText("Emisiones abiertas en español  ·  M3U8");
            if (heroSynopsis != null)
                heroSynopsis.setText(
                        "Directorio de señales públicas y emisiones abiertas de televisión en español (radios omitidas).");
        } else if (name.equals("TioAnime") || name.equals("AnimeAV1") || name.equals("JKAnime")) {
            if (heroEyebrow != null)
                heroEyebrow.setText("ANIME  ·  SERIES Y PELÍCULAS DE ANIMACIÓN");
            heroTitle.setText(name);
            if (heroBadge != null)
                heroBadge.setText("Catálogo de episodios  ·  Video según disponibilidad");
            if (heroSynopsis != null)
                heroSynopsis
                        .setText("Series de anime en emisión y finalizadas con selector de episodios y temporadas.");
        } else {
            if (heroEyebrow != null)
                heroEyebrow.setText("LEO TV  ·  " + tvBadge(name).toUpperCase());
            heroTitle.setText(name);
            if (heroBadge != null)
                heroBadge.setText(tvBadge(name));
            if (heroSynopsis != null)
                heroSynopsis.setText(descriptionFor(name));
        }
    }

    private void showMovieFicha(LinearLayout body, String title, int year, String genre, String duration,
            String license, String synopsis, Runnable playAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(10, 16, 10, 20);

        LinearLayout posterFrame = new LinearLayout(this);
        posterFrame.setOrientation(LinearLayout.VERTICAL);
        posterFrame.setGravity(Gravity.CENTER);
        GradientDrawable posterBg = new GradientDrawable();
        posterBg.setColor(Color.rgb(16, 25, 42));
        posterBg.setCornerRadius(18);
        posterBg.setStroke(3, Color.rgb(212, 175, 55));
        posterFrame.setBackground(posterBg);

        ImageView posterImg = new ImageView(this);
        posterImg.setImageResource(ar.appvideo.player.R.drawable.leo_brand);
        posterImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        posterFrame.addView(posterImg, new LinearLayout.LayoutParams(180, 180));

        TextView hdBadge = text("HD 1080p  ·  CC BY", 12, Color.rgb(212, 175, 55));
        hdBadge.setTypeface(null, Typeface.BOLD);
        hdBadge.setGravity(Gravity.CENTER);
        posterFrame.addView(hdBadge, new LinearLayout.LayoutParams(-1, -2));

        row.addView(posterFrame, new LinearLayout.LayoutParams(220, 290));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(28, 0, 10, 0);

        TextView movieTitle = text(title + (year > 0 ? " (" + year + ")" : ""), 28, Color.WHITE);
        movieTitle.setTypeface(null, Typeface.BOLD);
        info.addView(movieTitle);

        TextView metaLine = text(year + "  ·  " + genre + "  ·  " + duration + "  ·  " + license, 14,
                Color.rgb(212, 175, 55));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(0, 6, 0, 12);
        info.addView(metaLine, mlp);

        TextView synop = text(synopsis, 15, Color.rgb(205, 218, 235));
        synop.setMaxLines(4);
        synop.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, 0, 0, 20);
        info.addView(synop, slp);

        Button playBtn = tvAction("▶  Reproducir video completo", "Media3 ExoPlayer en pantalla completa", playAction,
                420, 95);
        playBtn.requestFocus();
        info.addView(playBtn);

        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(row);
    }

    private LinearLayout newSidebarPage(String sidebarSection, String title, String sub) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(dark);

        LinearLayout side = buildSidebar(sidebarSection);
        root.addView(side, new LinearLayout.LayoutParams(88, -1));

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setPadding(28, 20, 28, 20);
        root.addView(rightCol, new LinearLayout.LayoutParams(0, -1, 1));

        TextView heading = text(title, 24, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        rightCol.addView(heading);

        TextView subView = text(sub, 14, Color.LTGRAY);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, 4, 0, 10);
        rightCol.addView(subView, slp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        pageScroll = scroll;
        rightCol.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, 28);
        scroll.addView(body);
        setContentView(root);
        return body;
    }

    private void openChannel(String name) {
        if (isDeadProvider(name)) {
            Toast.makeText(this, "Este proveedor ha sido retirado por falta de catálogo activo.", Toast.LENGTH_SHORT)
                    .show();
            showProviderSelector();
            return;
        }
        if (catalogBrowser != null) {
            catalogBrowser.dispose();
            catalogBrowser = null;
        }
        if (resolutionTask != null)
            resolutionTask.cancel(true);
        ++providerPlayRequest;
        providerLoading = false;
        page = "channel";
        channel = name;
        String sub = "Catálogo y fichas enriquecidas · Volvé con ATRÁS.";
        if (!name.equals("Demo") && !name.equals("Blender Open Movies"))
            sub = descriptionFor(name);
        if (name.equals("Blender Open Movies"))
            sub = "Canal oficial Blender Open Movies: enlaces verificados al entrar; películas y tráiler identificados.";
        if (name.equals("Internet Archive"))
            sub = "Catálogo online de películas CC BY 3.0; fichas y enlaces desde la API pública.";
        if (name.equals("CineHax"))
            sub = "Catálogo online, búsqueda, fichas de actores y capítulos con vista previa.";
        if (name.equals("LaMovie"))
            sub = "Catálogo online, búsqueda, reparto y capítulos mediante servidores compatibles.";
        if (name.equals("SoloLatino"))
            sub = "Catálogo online y streaming en audio latino desde sololatino.net.";
        if (name.equals("Canela TV"))
            sub = "Catálogo oficial gratuito de películas, telenovelas y series en español.";
        if (name.equals("Mis nubes"))
            sub = "Futura importación de bibliotecas personales autorizadas.";
        if (name.equals("Estado")) {
            sub = channels.length()
                    + " fuentes registradas · Dominios 2026 actualizados (CineCalidad, Pelisplus, Seriesflix, Canela TV, SoloLatino, DoramasFlix).";
        }
        if (VideoProviders.find(name) != null) {
            sub = VideoProviders.provisional(name)
                    ? "Adaptador HTML provisional · Ficha enriquecida estilo Zapp TV."
                    : VideoProviders.find(name).description();
        }
        LinearLayout body = newSidebarPage("channel", name, sub);
        final VideoProvider provider = VideoProviders.find(name);
        Button back = null;
        if (provider == null) {
            back = item("←  Volver al inicio", body, this::home);
        }
        if (name.equals("Internet Archive") || name.equals("Blender Open Movies")) {
            // Ambos canales consultan sus adaptadores en segundo plano.
        } else if (provider != null) {
            // El navegador Zapp TV gestiona su propio encabezado y botón Volver.
        } else if (showCatalog(name, body)) {
            // Las fichas provienen del catálogo JSON.
        } else if (name.equals("Estado")) {
            item("Ver estado actual de las fuentes", body,
                    () -> showStatus(
                            "Fuentes operativas con catálogo verificado: CineHax, SoloLatino, CineCalidad, Seriesflix, SeriesMetro, Pelispedia, JKAnime, LatAnime, TioAnime, Cuevana, FlixLatam, HenaoJara, VerPelisTop, MonosChinos, MundoDonghua. Fuentes sin catálogo activo o con dominio caído han sido retiradas."));
        } else if (name.equals("Demo")) {
            showMovieFicha(body, "Big Buck Bunny", 2008, "Animación / Cine Abierto", "10 min", "CC BY 3.0",
                    "Un conejo gigante y de buen corazón decide darle una lección inolvidable a tres traviesos roedores del bosque (Frank, Rinky y Gamera) que se divierten molestando a las criaturas indefensas.",
                    this::playDemo);
            item("ⓘ  Detalle técnico: video MP4 verificado en Media3 ExoPlayer", body,
                    () -> showStatus("Demo: video independiente con reproducción y sesión multimedia verificadas."));
        } else if (name.equals("Mis nubes")) {
            showMisNubesScreen(body);
        } else {
            item("◈  Diagnóstico: " + referenceStatus(name), body,
                    () -> showStatus(referenceStatus(name)));
            item("Canal todavía sin reproducción integrada", body,
                    () -> showStatus(
                            "Este proveedor está inventariado, pero no dispone de adaptador de reproducción."));
        }
        status = text("", 16, Color.rgb(245, 197, 66));
        body.addView(status);
        if (provider != null) {
            CatalogBrowser.State saved = catalogStates.get(name);
            if (saved == null) {
                saved = new CatalogBrowser.State();
                catalogStates.put(name, saved);
            }
            catalogBrowser = new CatalogBrowser(this, body, saved, provider, new CatalogBrowser.Host() {
                @Override
                public boolean active() {
                    return page.equals("channel") && channel.equals(provider.name());
                }

                @Override
                public void play(CatalogData.Entry entry, int season, int episode, String title) {
                    playProviderTitle(provider, entry, season, episode, title);
                }

                @Override
                public void download(CatalogData.Entry entry, int season, int episode, String title) {
                    downloadProviderTitle(provider, entry, season, episode, title);
                }

                @Override
                public void playWithProvider(CatalogData.Entry entry, int season, int episode, String title, String providerName) {
                    VideoProvider vp = VideoProviders.find(providerName);
                    if (vp == null) vp = provider;
                    playProviderTitle(vp, entry, season, episode, title);
                }

                @Override
                public void playExternal(CatalogData.Entry entry, int season, int episode, String title) {
                    resolveAndLaunchExternal(provider, entry, season, episode, title);
                }
            });
        }
        if (name.equals("Internet Archive"))
            loadArchiveLive(body);
        if (name.equals("Blender Open Movies"))
            loadBlenderOfficial(body);
        if (back != null)
            back.requestFocusFromTouch();
    }

    private void showMisNubesScreen(LinearLayout body) {
        TextView h = text("☁️  MIS NUBES Y BIBLIOTECAS PERSONALES", 22, Color.rgb(245, 197, 66));
        h.setTypeface(null, Typeface.BOLD);
        h.setPadding(0, dp(4), 0, dp(2));
        body.addView(h);

        TextView desc = text(
                "Explorá el catálogo de nubes comunitarias (My Family Cinema) o reproducí archivos desde tu pendrive o disco USB.",
                13, Color.rgb(180, 190, 212));
        desc.setPadding(0, 0, 0, dp(12));
        body.addView(desc);

        android.widget.HorizontalScrollView pillScroll = new android.widget.HorizontalScrollView(this);
        pillScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout pillRail = new LinearLayout(this);
        pillRail.setOrientation(LinearLayout.HORIZONTAL);
        pillRail.setPadding(0, dp(2), dp(16), dp(12));

        final LinearLayout cloudContent = new LinearLayout(this);
        cloudContent.setOrientation(LinearLayout.VERTICAL);

        String[][] cloudTabs = new String[][] {
                { "🍿 Jill Cinema", "jill" },
                { "🕷️ Spider-Man", "spiderman" },
                { "💖 Mabel Coco", "mabel" },
                { "📂 USB y Archivos Locales", "local" },
                { "ℹ️ Info de Nubes", "info" }
        };

        for (int i = 0; i < cloudTabs.length; i++) {
            final String tabLabel = cloudTabs[i][0];
            final String tabKey = cloudTabs[i][1];
            Button tabBtn = tvAction(tabLabel, "", () -> {
                renderCloudCategory(cloudContent, tabKey, tabLabel);
            }, dp(205), dp(44));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(dp(205), dp(44));
            tLp.setMargins(0, 0, dp(10), 0);
            pillRail.addView(tabBtn, tLp);
        }
        pillScroll.addView(pillRail);
        body.addView(pillScroll);
        body.addView(cloudContent);

        renderCloudCategory(cloudContent, "jill", "🍿 Jill Cinema");
    }

    private void renderCloudCategory(LinearLayout container, String key, String label) {
        container.removeAllViews();

        TextView title = text(label + " · Catálogo Disponible", 18, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(4), dp(8), 0, dp(12));
        container.addView(title);

        if ("info".equals(key)) {
            TextView info1 = text("📦  Diagnóstico de Nubes My Family Cinema (.zip y .dat):", 15,
                    Color.rgb(245, 197, 66));
            info1.setTypeface(null, Typeface.BOLD);
            info1.setPadding(dp(6), dp(6), dp(6), dp(4));
            container.addView(info1);

            TextView infoDesc = text(
                    "• Enlaces verificados: bit.ly/cinema66, rebrand.ly/mfcgratis2, bit.ly/spider66, bit.ly/jcinema9.\n"
                            + "• Los repositorios de descarga siguen respondiendo (HTTP 200 OK).\n"
                            + "• El contenido interno de los archivos .dat son índices cifrados hacia cuentas de Google Drive y Mega creadas entre 2018 y 2022.\n"
                            + "• Como muchas cuentas de Drive sufrieron caídas por derechos de autor o cuotas de Google, Leo TV no depende de ellas: "
                            + "resuelve y reproduce todos los títulos automáticamente mediante sus 12 proveedores activos.",
                    13, Color.rgb(200, 210, 230));
            infoDesc.setPadding(dp(6), 0, dp(6), dp(16));
            container.addView(infoDesc);
            return;
        }

        if ("local".equals(key)) {
            renderLocalUsbFiles(container);
            return;
        }

        String[][] movies;
        if ("spiderman".equals(key)) {
            movies = new String[][] {
                    { "Spider-Man: Sin Camino a Casa", "2021",
                            "https://image.tmdb.org/t/p/w500/miZFIBYV0176P01v58b9zB1mB0B.jpg", "8.0",
                            "Acción / Aventura" },
                    { "Spider-Man: A Través del Spider-Verso", "2023",
                            "https://image.tmdb.org/t/p/w500/8j58iEBw9pOXFD2L0nt0ZXeHviB.jpg", "8.4",
                            "Animación / Acción" },
                    { "Avengers: Endgame", "2019", "https://image.tmdb.org/t/p/w500/or06FN3Dka5tukK1e9sl16pB3iy.jpg",
                            "8.3", "Ciencia ficción" },
                    { "Top Gun: Maverick", "2022", "https://image.tmdb.org/t/p/w500/62HCnUTziyWcpDaBO2i1DX17ljH.jpg",
                            "8.2", "Acción / Drama" },
                    { "The Batman", "2022", "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg", "7.7",
                            "Crimen / Misterio" },
                    { "Deadpool & Wolverine", "2024", "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
                            "7.7", "Acción / Comedia" },
                    { "John Wick 4", "2023", "https://image.tmdb.org/t/p/w500/gh2bmprLtUQ8o9v5m67BCE44P.jpg", "7.7",
                            "Acción / Suspenso" },
                    { "Batman: El Caballero de la Noche", "2008",
                            "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg", "8.5",
                            "Acción / Crimen" }
            };
        } else if ("mabel".equals(key)) {
            movies = new String[][] {
                    { "Coco", "2017", "https://image.tmdb.org/t/p/w500/gGEsBPAijhVUFoiNpgZXqRVWJt2.jpg", "8.2",
                            "Familiar / Animación" },
                    { "Intensamente 2", "2024", "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
                            "7.6", "Animación / Familiar" },
                    { "El Rey León", "1994", "https://image.tmdb.org/t/p/w500/sKCr78MXSLixwmZ8DyJLrpMsd15.jpg", "8.3",
                            "Familiar / Drama" },
                    { "Toy Story 4", "2019", "https://image.tmdb.org/t/p/w500/w9kR8qbmQ01HwnvK4alvnQ2v0D5.jpg", "7.5",
                            "Animación / Comedia" },
                    { "Encanto", "2021", "https://image.tmdb.org/t/p/w500/4j0PNHkMr5ax3AsMFG4EZwY0T0b.jpg", "7.6",
                            "Animación / Comedia" },
                    { "Moana", "2016", "https://image.tmdb.org/t/p/w500/4JeeYYAz06L8Z3h9pQG9Z6jOq1N.jpg", "7.6",
                            "Aventura / Familiar" },
                    { "Shrek 2", "2004", "https://image.tmdb.org/t/p/w500/2yYP0PQjG8zV0TurmCcegquTXqp.jpg", "7.3",
                            "Comedia / Fantasía" },
                    { "Kung Fu Panda 4", "2024", "https://image.tmdb.org/t/p/w500/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg",
                            "7.1", "Animación / Acción" }
            };
        } else {
            movies = new String[][] {
                    { "Oppenheimer", "2023", "https://image.tmdb.org/t/p/w500/ncKJJ9elROCq0k8kIq25Fj4wQ9x.jpg", "8.1",
                            "Drama / Historia" },
                    { "Interestelar", "2014", "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", "8.4",
                            "Ciencia ficción" },
                    { "El Origen", "2010", "https://image.tmdb.org/t/p/w500/9gk7adHYeDvHkCSEqAvQNLV5Uge.jpg", "8.4",
                            "Acción / Suspenso" },
                    { "Duna: Parte Dos", "2024", "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg",
                            "8.2", "Ciencia ficción" },
                    { "Avatar: El Camino del Agua", "2022",
                            "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", "7.6",
                            "Aventura / Fantasía" },
                    { "Gladiador", "2000", "https://image.tmdb.org/t/p/w500/ty8TGRuvJLPUmAR1H1nRIsgwvim.jpg", "8.2",
                            "Acción / Drama" },
                    { "Matrix", "1999", "https://image.tmdb.org/t/p/w500/f89U3ADr1oiB1s9GkdPOEpXUk5H.jpg", "8.2",
                            "Ciencia ficción" },
                    { "El Padrino", "1972", "https://image.tmdb.org/t/p/w500/3bhkrj58Vtu7enYsRolD1fZdja1.jpg", "8.7",
                            "Crimen / Drama" },
                    { "Pulp Fiction", "1994", "https://image.tmdb.org/t/p/w500/d5iIlFn5s0ImszYzBPb8JPIfbXD.jpg", "8.5",
                            "Crimen / Suspenso" },
                    { "Forrest Gump", "1994", "https://image.tmdb.org/t/p/w500/arw2vcBveWOVZr6pxd9XTd1TdQa.jpg", "8.5",
                            "Comedia / Drama" }
            };
        }

        renderCloudMoviesGrid(container, movies, label);
    }

    private void renderCloudMoviesGrid(LinearLayout container, String[][] movies, String cloudName) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int cardWidth = Math.max(dp(140), (screenWidth - dp(140)) / 5);
        int posterH = (int) (cardWidth * 1.45f);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(Math.max(2, screenWidth / (cardWidth + dp(16))));
        grid.setPadding(dp(4), dp(4), dp(4), dp(16));
        container.addView(grid);

        for (String[] m : movies) {
            final String mTitle = m[0];
            final int mYear = Integer.parseInt(m[1]);
            final String mPoster = m[2];
            final String mScore = m[3];
            final String mGenre = m.length > 4 ? m[4] : "";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(4), dp(4), dp(4), dp(6));
            card.setFocusable(true);
            card.setClickable(true);

            GradientDrawable normalBg = new GradientDrawable();
            normalBg.setColor(Color.TRANSPARENT);
            normalBg.setCornerRadius(12);
            card.setBackground(normalBg);

            FrameLayout posterWrap = new FrameLayout(this);
            GradientDrawable posterBg = new GradientDrawable();
            posterBg.setColor(Color.rgb(24, 30, 44));
            posterBg.setCornerRadius(12);
            posterWrap.setBackground(posterBg);
            posterWrap.setClipToOutline(true);

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            posterWrap.addView(img, new FrameLayout.LayoutParams(-1, -1));
            CatalogBrowser.loadPoster(this, img, mPoster);

            TextView cloudBadge = new TextView(this);
            cloudBadge.setText("☁️ " + (cloudName.replace("🍿 ", "").replace("🕷️ ", "").replace("💖 ", "")));
            cloudBadge.setTextColor(Color.WHITE);
            cloudBadge.setTextSize(10);
            cloudBadge.setTypeface(null, Typeface.BOLD);
            GradientDrawable cbBg = new GradientDrawable();
            cbBg.setColor(Color.argb(220, 30, 60, 120));
            cbBg.setCornerRadius(dp(4));
            cloudBadge.setBackground(cbBg);
            cloudBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
            FrameLayout.LayoutParams cbLp = new FrameLayout.LayoutParams(-2, -2);
            cbLp.gravity = Gravity.TOP | Gravity.START;
            cbLp.setMargins(dp(6), dp(6), 0, 0);
            posterWrap.addView(cloudBadge, cbLp);

            card.addView(posterWrap, new LinearLayout.LayoutParams(-1, posterH));

            TextView meta = text("⭐ " + mScore + "/10 TMDB · " + mYear, 11, Color.rgb(245, 197, 66));
            meta.setTypeface(null, Typeface.BOLD);
            meta.setPadding(dp(2), dp(4), dp(2), dp(2));
            card.addView(meta);

            TextView titleTv = text(mTitle, 13, Color.WHITE);
            titleTv.setTypeface(null, Typeface.BOLD);
            titleTv.setMaxLines(2);
            titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleTv.setPadding(dp(2), 0, dp(2), dp(2));
            card.addView(titleTv, new LinearLayout.LayoutParams(-1, -2));

            card.setOnFocusChangeListener((v, focused) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(focused ? Color.rgb(40, 48, 70) : Color.TRANSPARENT);
                g.setCornerRadius(12);
                if (focused)
                    g.setStroke(3, Color.WHITE);
                card.setBackground(g);
                card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
            });

            card.setOnClickListener(v -> {
                try {
                    JSONObject raw = new JSONObject()
                            .put("title", mTitle)
                            .put("year", mYear)
                            .put("poster_path", mPoster)
                            .put("kind", "movie")
                            .put("page_url", "https://www.themoviedb.org/search?query="
                                    + java.net.URLEncoder.encode(mTitle, "UTF-8"));
                    CatalogData.Entry entry = new CatalogData.Entry(raw, "CineHax");
                    openEntryDetail(entry);
                } catch (Exception ignored) {
                }
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = cardWidth;
            glp.height = -2;
            glp.setMargins(0, 0, dp(14), dp(16));
            grid.addView(card, glp);
        }
    }

    private void renderLocalUsbFiles(LinearLayout container) {
        java.util.List<File> videoFiles = new java.util.ArrayList<>();
        String[] extensions = new String[] { ".mp4", ".mkv", ".avi", ".ts", ".webm", ".mov", ".m4v" };

        java.util.List<File> scanRoots = new java.util.ArrayList<>();
        File extStorage = android.os.Environment.getExternalStorageDirectory();
        if (extStorage != null && extStorage.exists()) {
            scanRoots.add(new File(extStorage, "Download"));
            scanRoots.add(new File(extStorage, "Movies"));
            scanRoots.add(new File(extStorage, "DCIM"));
        }
        File storageRoot = new File("/storage");
        if (storageRoot.exists() && storageRoot.isDirectory()) {
            File[] subs = storageRoot.listFiles();
            if (subs != null) {
                for (File s : subs) {
                    if (s.isDirectory() && !s.getName().equals("self") && !s.getName().equals("emulated")) {
                        scanRoots.add(s);
                    }
                }
            }
        }

        for (File dir : scanRoots) {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                File[] list = dir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile()) {
                            String fName = f.getName().toLowerCase(java.util.Locale.ROOT);
                            for (String ext : extensions) {
                                if (fName.endsWith(ext)) {
                                    videoFiles.add(f);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (videoFiles.isEmpty()) {
            TextView emptyH = text("📂  No se encontraron videos en almacenamiento local ni USB", 16,
                    Color.rgb(245, 197, 66));
            emptyH.setTypeface(null, Typeface.BOLD);
            emptyH.setPadding(dp(8), dp(12), dp(8), dp(4));
            container.addView(emptyH);

            TextView emptyDesc = text("Podés conectar un pendrive o disco externo USB a tu TV Box / Smart TV, "
                    + "o colocar archivos (.mp4, .mkv, .avi) en la carpeta Descargas para reproducirlos directamente en Media3 ExoPlayer sin conexión a internet.",
                    13, Color.rgb(180, 190, 212));
            emptyDesc.setPadding(dp(8), 0, dp(8), dp(16));
            container.addView(emptyDesc);
            return;
        }

        TextView countTv = text("Se encontraron " + videoFiles.size() + " videos locales / USB listos para reproducir:",
                14, Color.rgb(120, 220, 140));
        countTv.setTypeface(null, Typeface.BOLD);
        countTv.setPadding(dp(6), dp(4), dp(6), dp(10));
        container.addView(countTv);

        for (File vf : videoFiles) {
            LinearLayout fRow = new LinearLayout(this);
            fRow.setOrientation(LinearLayout.HORIZONTAL);
            fRow.setGravity(Gravity.CENTER_VERTICAL);
            fRow.setPadding(dp(16), dp(12), dp(16), dp(12));
            fRow.setFocusable(true);
            fRow.setClickable(true);

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(Color.rgb(28, 32, 44));
            rowBg.setCornerRadius(dp(10));
            fRow.setBackground(rowBg);

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, -2);
            rLp.setMargins(0, 0, 0, dp(10));

            TextView icon = text("🎬", 20, Color.WHITE);
            icon.setPadding(0, 0, dp(12), 0);
            fRow.addView(icon);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, -2, 1f);

            TextView fnTv = text(vf.getName(), 14, Color.WHITE);
            fnTv.setTypeface(null, Typeface.BOLD);
            textCol.addView(fnTv);

            long mb = vf.length() / (1024 * 1024);
            TextView szTv = text("Tamaño: " + mb + " MB  ·  Ruta: " + vf.getParent(), 12, Color.rgb(160, 172, 195));
            textCol.addView(szTv);

            fRow.addView(textCol, tcLp);

            TextView playBtn = text("▶ Reproducir", 13, Color.rgb(245, 197, 66));
            playBtn.setTypeface(null, Typeface.BOLD);
            fRow.addView(playBtn);

            fRow.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(45, 55, 80) : Color.rgb(28, 32, 44));
                g.setCornerRadius(dp(10));
                if (f)
                    g.setStroke(2, Color.WHITE);
                fRow.setBackground(g);
                fRow.animate().scaleX(f ? 1.02f : 1f).scaleY(f ? 1.02f : 1f).setDuration(100).start();
            });

            fRow.setOnClickListener(v -> {
                playMovie(vf.getName(), vf.getAbsolutePath(), "USB Local", new java.util.HashMap<>());
            });

            container.addView(fRow, rLp);
        }
    }

    private void archiveMovieCard(LinearLayout body, InternetArchiveProvider.Movie movie) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(12, 12, 12, 12);
        card.setBackground(bg(panel, Color.rgb(69, 78, 116)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 180);
        cardLp.setMargins(0, 0, 0, 16);
        body.addView(card, cardLp);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(47, 58, 81));
        card.addView(image, new LinearLayout.LayoutParams(132, -1));
        Button button = new Button(this);
        button.setText("▶  " + movie.title + " (" + movie.year + ")\nCC BY 3.0 · Reproducir");
        button.setTextSize(15);
        button.setMaxLines(2);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackground(bg(panel, violet));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(18, 0, 0, 0);
        card.addView(button, params);
        button.setOnClickListener(v -> playMovie(movie.title, movie.url, "Internet Archive"));
        button.setOnFocusChangeListener((v, focused) -> button.setBackground(bg(focused ? violet : panel,
                focused ? Color.WHITE : violet)));
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(movie.thumbnail).openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() != 200)
                    return;
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null)
                        runOnUiThread(() -> image.setImageBitmap(bitmap));
                }
            } catch (Exception error) {
                android.util.Log.w("VideoPropia", "Miniatura no disponible: " + movie.id);
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        }, "ArchiveImage").start();
    }

    private void loadArchiveLive(LinearLayout body) {
        showStatus("Consultando catálogo de Internet Archive...");
        new Thread(() -> {
            try {
                java.util.List<InternetArchiveProvider.Movie> movies = InternetArchiveProvider.fetchCurated();
                runOnUiThread(() -> {
                    if (!page.equals("channel") || !channel.equals("Internet Archive"))
                        return;
                    showStatus("Catálogo online: " + movies.size() + " películas CC BY verificadas.");
                    for (InternetArchiveProvider.Movie movie : movies)
                        archiveMovieCard(body, movie);
                });
            } catch (Exception ex) {
                android.util.Log.e("VideoPropia", "Fallo de catálogo Archive", ex);
                runOnUiThread(() -> {
                    if (page.equals("channel") && channel.equals("Internet Archive"))
                        showStatus("Error al cargar catálogo: " + ex.getMessage());
                });
            }
        }, "ArchiveMetadata").start();
    }

    private void loadBlenderOfficial(LinearLayout body) {
        showStatus("Verificando enlaces oficiales de Blender...");
        new Thread(() -> {
            java.util.List<BlenderOpenMoviesProvider.Film> films = BlenderOpenMoviesProvider.availableFilms();
            runOnUiThread(() -> {
                if (!page.equals("channel") || !channel.equals("Blender Open Movies"))
                    return;
                showStatus("Blender: " + films.size() + " enlaces oficiales verificados (HEAD).");
                for (BlenderOpenMoviesProvider.Film film : films) {
                    item("▶  " + film.title + " (" + film.year + ") | " + film.kind
                            + " · " + film.format, body,
                            () -> playMovie(film.title, film.url, "Blender Open Movies"));
                }
                if (films.isEmpty())
                    showStatus("No se verificaron videos oficiales disponibles en este momento.");
            });
        }, "BlenderOfficial").start();
    }

    private CatalogBrowser catalogBrowser;
    private final java.util.Map<String, CatalogBrowser.State> catalogStates = new java.util.HashMap<>();
    private long providerPlayRequest;
    private boolean providerLoading;
    private final java.util.concurrent.ExecutorService videoResolvers = java.util.concurrent.Executors
            .newSingleThreadExecutor();
    private java.util.concurrent.Future<?> resolutionTask;

    private void playProviderTitle(VideoProvider provider, CatalogData.Entry entry, int season, int episode,
            String title) {
        if (providerLoading)
            return;
        if (autoNextCountDownTimer != null) {
            autoNextCountDownTimer.cancel();
            autoNextCountDownTimer = null;
        }
        if (autoNextDialog != null) {
            try {
                if (autoNextDialog.isShowing()) autoNextDialog.dismiss();
            } catch (Exception ignored) {}
            autoNextDialog = null;
        }
        if (nextEpBannerBtn != null) {
            nextEpBannerBtn.setVisibility(android.view.View.GONE);
        }
        final long request = ++providerPlayRequest;
        providerLoading = true;
        currentPlayingEntry = entry;
        currentPlayingSeason = season;
        currentPlayingEpisode = episode;
        showStatus("Buscando servidor de video para " + title + "...");
        showSourceLoader(title, () -> {
            providerLoading = false;
            if (resolutionTask != null) {
                resolutionTask.cancel(true);
            }
        });
        resolutionTask = videoResolvers.submit(() -> {
            VideoProvider.VideoLink link = null;
            String error = null;
            String resolvedProviderName = provider.name();

            if (entry.isMovie() && entry.tmdb <= 0) {
                ZappCatalogEngine.resolveTmdbMovieId(entry);
            } else if (!entry.isMovie() && entry.tmdb <= 0) {
                ZappCatalogEngine.resolveTmdbTvId(entry);
            }

            if (!"Catálogo Global TV".equals(entry.providerName)) {
                try {
                    link = provider.resolve(entry, season, episode);
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
            }
            // Si proviene del Catálogo Global TV (TVMaze) o falló el proveedor primario,
            // buscar en proveedores activos
            if (link == null) {
                boolean isAnime = entry.title != null && (
                    entry.title.toLowerCase().contains("dragon ball") ||
                    entry.title.toLowerCase().contains("naruto") ||
                    entry.title.toLowerCase().contains("one piece") ||
                    entry.title.toLowerCase().contains("bleach") ||
                    entry.title.toLowerCase().contains("anime") ||
                    "anime".equalsIgnoreCase(entry.kind)
                );
                String[] fallbackProviders = isAnime
                        ? new String[] { "LatAnime", "TioAnime", "JKAnime", "CineHax", "SoloLatino", "Seriesflix", "Cuevana" }
                        : new String[] { "CineHax", "SoloLatino", "CineCalidad", "Seriesflix", "Cuevana", "LatAnime", "TioAnime", "SeriesMetro", "Pelispedia", "FlixLatam", "HenaoJara" };
                String cleanTitle = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                String altTitle = entry.originalTitle == null ? "" : entry.originalTitle.trim();
                String[] queries = altTitle.isEmpty() ? new String[] { cleanTitle }
                        : new String[] { cleanTitle, altTitle };
                outer: for (String pName : fallbackProviders) {
                    VideoProvider vp = VideoProviders.find(pName);
                    if (vp == null)
                        continue;
                    for (String q : queries) {
                        if (q.isEmpty())
                            continue;
                        try {
                            CatalogData.CatalogPage cp = vp.catalog(entry.isMovie() ? "movie" : "serie", q, 1);
                            for (CatalogData.Entry cand : cp.entries) {
                                if (!isTitleMatch(cleanTitle, cand.title, entry.originalTitle)) {
                                    continue;
                                }
                                try {
                                    link = vp.resolve(cand, season, episode);
                                    if (link != null) {
                                        resolvedProviderName = vp.name();
                                        break outer;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            final VideoProvider.VideoLink resolved = link;
            final String finalProv = resolvedProviderName;
            final String message = error != null ? error
                    : "Stream aún no indexado en los servidores activos para este capítulo.";
            runOnUiThread(() -> {
                hideSourceLoader();
                if (request != providerPlayRequest)
                    return;
                providerLoading = false;
                if (!page.equals("channel"))
                    return;
                if (resolved == null) {
                    showStatus("Aviso: " + message);
                    // Si es serie y no se encontró el capítulo en los scrapers web, ofrecer buscar en YouTube+
                    if (!entry.isMovie()) {
                        String cleanTarget = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                        String epDesc = "T" + season + " E" + episode;
                        String ytQuery = (!cleanTarget.isEmpty() ? cleanTarget : entry.title) + " " + epDesc;
                        android.app.AlertDialog.Builder ytB = new android.app.AlertDialog.Builder(MainActivity.this);
                        ytB.setTitle("🔍 Capítulo no encontrado en servidores");
                        ytB.setMessage("No encontramos fuentes web activas para:\n\n"
                                + title + "\n\n"
                                + "¿Querés buscar este capítulo completo en YouTube+?");
                        ytB.setPositiveButton("Buscar en YouTube+", (d, w) -> {
                            Toast.makeText(MainActivity.this, "Buscando en YouTube+: " + ytQuery, Toast.LENGTH_SHORT).show();
                            YouTubeEngine.searchAsync(ytQuery, results -> {
                                if (results != null && !results.isEmpty()) {
                                    CatalogData.Entry firstYt = results.get(0);
                                    YouTubePlayerActivity.start(MainActivity.this, firstYt.pageUrl, firstYt.title, entry.title);
                                } else {
                                    Toast.makeText(MainActivity.this, "No se hallaron resultados en YouTube+", Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                        ytB.setNegativeButton("Cancelar", null);
                        ytB.show();
                    }
                } else {
                    playMovie(title, resolved.url, finalProv, resolved.headers);
                }
            });
        });
    }

    private void downloadProviderTitle(VideoProvider provider, CatalogData.Entry entry, int season, int episode,
            String title) {
        Toast.makeText(this, "⬇ Obteniendo enlace para descargar " + title + "...", Toast.LENGTH_SHORT).show();
        showStatus("Buscando enlace de descarga para " + title + "...");
        videoResolvers.submit(() -> {
            VideoProvider.VideoLink link = null;
            String error = null;
            if (!"Catálogo Global TV".equals(entry.providerName)) {
                try {
                    link = provider.resolve(entry, season, episode);
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
            }
            if (link == null) {
                boolean isAnime = entry.title != null && (
                    entry.title.toLowerCase().contains("dragon ball") ||
                    entry.title.toLowerCase().contains("naruto") ||
                    entry.title.toLowerCase().contains("one piece") ||
                    entry.title.toLowerCase().contains("bleach") ||
                    entry.title.toLowerCase().contains("anime") ||
                    "anime".equalsIgnoreCase(entry.kind)
                );
                String[] fallbackProviders = isAnime
                        ? new String[] { "LatAnime", "TioAnime", "JKAnime", "CineHax", "SoloLatino", "Seriesflix", "Cuevana" }
                        : new String[] { "CineHax", "SoloLatino", "CineCalidad", "Seriesflix", "Cuevana", "LatAnime", "TioAnime", "SeriesMetro", "Pelispedia", "FlixLatam", "HenaoJara" };
                String cleanTitle = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                String altTitle = entry.originalTitle == null ? "" : entry.originalTitle.trim();
                String[] queries = altTitle.isEmpty() ? new String[] { cleanTitle }
                        : new String[] { cleanTitle, altTitle };
                outer: for (String pName : fallbackProviders) {
                    VideoProvider vp = VideoProviders.find(pName);
                    if (vp == null)
                        continue;
                    for (String q : queries) {
                        if (q.isEmpty())
                            continue;
                        try {
                            CatalogData.CatalogPage cp = vp.catalog(entry.isMovie() ? "movie" : "serie", q, 1);
                            for (CatalogData.Entry cand : cp.entries) {
                                if (!isTitleMatch(cleanTitle, cand.title, entry.originalTitle)) {
                                    continue;
                                }
                                try {
                                    link = vp.resolve(cand, season, episode);
                                    if (link != null)
                                        break outer;
                                } catch (Exception ignored) {
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            final VideoProvider.VideoLink resolved = link;
            final String message = error != null ? error
                    : "No se encontró enlace descargable para este contenido.";
            runOnUiThread(() -> {
                if (resolved == null) {
                    Toast.makeText(this, "Aviso de descarga: " + message, Toast.LENGTH_LONG).show();
                    showStatus("Aviso de descarga: " + message);
                } else {
                    LeoDownloadManager.download(this, title, resolved.url, resolved.headers);
                }
            });
        });
    }

    private void showSourceLoader(String title, Runnable onCancel) {
        runOnUiThread(() -> {
            hideSourceLoader();
            try {
                android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
                d.setCancelable(true);
                d.setOnCancelListener(di -> {
                    if (onCancel != null) onCancel.run();
                });

                android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
                overlay.setBackgroundColor(Color.argb(215, 8, 11, 16));

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(32), dp(28), dp(32), dp(24));
                GradientDrawable cBg = new GradientDrawable();
                cBg.setColor(Color.rgb(20, 26, 38));
                cBg.setCornerRadius(dp(16));
                cBg.setStroke(dp(2), Color.rgb(245, 197, 66));
                card.setBackground(cBg);

                android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
                spinner.setIndeterminate(true);
                LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dp(54), dp(54));
                spLp.setMargins(0, 0, 0, dp(16));
                card.addView(spinner, spLp);

                TextView headTxt = text("Cargando servidor de video...", 18, Color.WHITE);
                headTxt.setTypeface(null, Typeface.BOLD);
                headTxt.setGravity(Gravity.CENTER);
                card.addView(headTxt);

                if (title != null && !title.isEmpty()) {
                    TextView titleTxt = text(title, 14, Color.rgb(245, 197, 66));
                    titleTxt.setGravity(Gravity.CENTER);
                    titleTxt.setPadding(0, dp(6), 0, dp(4));
                    card.addView(titleTxt);
                }

                TextView subTxt = text("Buscando la mejor calidad y conectando señal...", 12, Color.rgb(160, 172, 195));
                subTxt.setGravity(Gravity.CENTER);
                subTxt.setPadding(0, 0, 0, dp(18));
                card.addView(subTxt);

                Button cancelBtn = new Button(this);
                cancelBtn.setText("Cancelar");
                cancelBtn.setTextColor(Color.WHITE);
                cancelBtn.setTextSize(13);
                cancelBtn.setAllCaps(false);
                cancelBtn.setFocusable(true);
                GradientDrawable cbBg = new GradientDrawable();
                cbBg.setColor(Color.rgb(44, 52, 70));
                cbBg.setCornerRadius(dp(10));
                cancelBtn.setBackground(cbBg);
                cancelBtn.setPadding(dp(20), dp(6), dp(20), dp(6));
                cancelBtn.setOnFocusChangeListener((v, f) -> {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(f ? Color.rgb(180, 50, 50) : Color.rgb(44, 52, 70));
                    g.setCornerRadius(dp(10));
                    if (f) g.setStroke(dp(2), Color.WHITE);
                    cancelBtn.setBackground(g);
                });
                cancelBtn.setOnClickListener(v -> {
                    hideSourceLoader();
                    if (onCancel != null) onCancel.run();
                });
                card.addView(cancelBtn, new LinearLayout.LayoutParams(-2, dp(38)));

                android.widget.FrameLayout.LayoutParams clp = new android.widget.FrameLayout.LayoutParams(dp(360), -2, Gravity.CENTER);
                overlay.addView(card, clp);

                d.setContentView(overlay);
                d.show();
                cancelBtn.requestFocus();
                sourceLoaderDialog = d;
            } catch (Exception ignored) {}
        });
    }

    private void hideSourceLoader() {
        runOnUiThread(() -> {
            if (sourceLoaderDialog != null) {
                try {
                    if (sourceLoaderDialog.isShowing()) {
                        sourceLoaderDialog.dismiss();
                    }
                } catch (Exception ignored) {}
                sourceLoaderDialog = null;
            }
        });
    }

    private void showStatus(String value) {
        if (status != null)
            status.setText(value);
    }

    private void setFullscreen(boolean enabled) {
        if (!page.equals("playing") || playbackView == null || playbackBody == null)
            return;
        fullscreen = enabled;
        root.setPadding(enabled ? 0 : 48, enabled ? 0 : 32, enabled ? 0 : 48, enabled ? 0 : 30);
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (child != pageScroll)
                child.setVisibility(enabled ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        for (int i = 0; i < playbackBody.getChildCount(); i++) {
            android.view.View child = playbackBody.getChildAt(i);
            if (child != playbackView)
                child.setVisibility(enabled ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        playbackBody.setGravity(Gravity.CENTER);
        playbackBody.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, enabled ? -1 : -2));
        playbackView.setLayoutParams(new LinearLayout.LayoutParams(-1, enabled ? -1 : 620));
        pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(!enabled);
        pageScroll.scrollTo(0, 0);
        getWindow().getDecorView().setSystemUiVisibility(enabled
                ? android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                : 0);
        if (enabled)
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (enabled)
            playbackView.requestFocusFromTouch();
        else if (fullscreenButton != null)
            fullscreenButton.requestFocusFromTouch();
    }

    private void playDemo() {
        playMovie("Big Buck Bunny", DEMO_URL, "Demo");
    }

    private void playMovie(String title, String url, String origin) {
        playMovie(title, url, origin, null);
    }

    private void playMovie(String title, String url, String origin, java.util.Map<String, String> headers) {
        if (url != null && (url.contains("tvf90.com") || url.contains("r=") || url.contains("stream="))
                && !url.contains(".m3u8")) {
            Toast.makeText(this, "Conectando con señal deportiva en vivo...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                java.util.Map<String, String> resolvedHdrs = new java.util.HashMap<>();
                if (headers != null)
                    resolvedHdrs.putAll(headers);
                String realM3u8 = LiveTvEngine.resolveDynamicStream(url, resolvedHdrs);
                runOnUiThread(() -> {
                    if (realM3u8 != null && !realM3u8.isEmpty() && realM3u8.startsWith("http")) {
                        playMovie(title, realM3u8, origin, resolvedHdrs);
                    } else {
                        Toast.makeText(this, "La señal deportiva no está disponible en este momento",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
            return;
        }

        if (origin != null && (origin.startsWith("TV") || origin.contains("Deportes")
                || origin.contains("Live") || origin.contains("Señales TV"))) {
            playbackReturnPage = "live_tv";
        } else if ("m3u_list".equals(page)) {
            playbackReturnPage = "m3u_list";
        } else if ("favorites".equals(page)) {
            playbackReturnPage = "favorites";
        } else if ("watch_later".equals(page)) {
            playbackReturnPage = "watch_later";
        } else if (catalogBrowser != null || (channel != null && !channel.isEmpty())) {
            playbackReturnPage = "channel";
        } else if (!"live_tv".equals(playbackReturnPage)) {
            playbackReturnPage = "home";
        }

        playingChannel = origin;
        page = "playing";
        currentPlayingTitle = title;
        currentPlayingOrigin = origin;
        currentPlayingUrl = url;
        currentPlayingHeaders = headers;
        if (!isSwitchingUnstableServer) {
            failedProvidersForCurrentTitle.clear();
        }

        LinearLayout body = newPage(origin, title,
                "Reproducción directa · Usá OK para mostrar controles.");
        playbackBody = body;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status = text("Cargando video...", 18, Color.WHITE);
        PlayerView view = new PlayerView(this);
        playbackView = view;
        view.setUseController(true);
        currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
        view.setResizeMode(currentResizeMode);
        body.addView(view, new LinearLayout.LayoutParams(-1, 620));

        // Overlay survives fullscreen: status, HUD and OSD bar
        status.setTextSize(16);
        status.setPadding(18, 10, 18, 10);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.argb(204, 12, 22, 38));
        android.widget.FrameLayout overlay = view.getOverlayFrameLayout();
        if (overlay != null) {
            android.widget.FrameLayout.LayoutParams overlayParams = new android.widget.FrameLayout.LayoutParams(-1, -2,
                    Gravity.TOP);
            overlay.addView(status, overlayParams);

            // 1. HUD flotante superior para saltos de tiempo y estado (sin superponerse con controles centrales)
            playerHudView = text("", 16, Color.WHITE);
            playerHudView.setPadding(dp(20), dp(10), dp(20), dp(10));
            playerHudView.setGravity(Gravity.CENTER);
            GradientDrawable hudBg = new GradientDrawable();
            hudBg.setColor(Color.argb(220, 16, 20, 30));
            hudBg.setCornerRadius(dp(12));
            hudBg.setStroke(dp(2), Color.rgb(245, 197, 66));
            playerHudView.setBackground(hudBg);
            playerHudView.setVisibility(android.view.View.GONE);
            android.widget.FrameLayout.LayoutParams hudLp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            hudLp.setMargins(0, dp(28), 0, 0);
            overlay.addView(playerHudView, hudLp);

            // 2. Barra de acciones rápidas inferior (Audio, Calidad, Subtítulos y Relación de Aspecto)
            LinearLayout osdBar = new LinearLayout(this);
            playerOsdBar = osdBar;
            osdBar.setOrientation(LinearLayout.VERTICAL);
            osdBar.setGravity(Gravity.CENTER_HORIZONTAL);
            osdBar.setVisibility(android.view.View.GONE);
            osdBar.setAlpha(0.0f);

            // Título/Capítulo visible sobre la barra de controles
            TextView osdTitle = text(title != null ? title : "", 15, Color.rgb(245, 197, 66));
            osdTitle.setTypeface(null, Typeface.BOLD);
            osdTitle.setGravity(Gravity.CENTER);
            osdTitle.setPadding(dp(16), dp(6), dp(16), dp(6));
            GradientDrawable osdTitleBg = new GradientDrawable();
            osdTitleBg.setColor(Color.argb(210, 16, 20, 30));
            osdTitleBg.setCornerRadius(dp(8));
            osdTitle.setBackground(osdTitleBg);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
            titleLp.setMargins(0, 0, 0, dp(6));
            osdBar.addView(osdTitle, titleLp);

            LinearLayout buttonsRow = new LinearLayout(this);
            buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonsRow.setPadding(dp(16), dp(4), dp(16), dp(4));
            osdBar.addView(buttonsRow, new LinearLayout.LayoutParams(-2, -2));

            Button tracksBtn = new Button(this);
            tracksBtn.setText("💬 Audio y Subtítulos");
            tracksBtn.setTextColor(Color.WHITE);
            tracksBtn.setTextSize(12);
            tracksBtn.setAllCaps(false);
            tracksBtn.setFocusable(true);
            GradientDrawable tbBg = new GradientDrawable();
            tbBg.setColor(Color.argb(200, 32, 38, 54));
            tbBg.setCornerRadius(dp(8));
            tracksBtn.setBackground(tbBg);
            tracksBtn.setOnClickListener(v -> {
                scheduleOsdHide(4000);
                showTracksDialog();
            });
            tracksBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 80, 120) : Color.argb(200, 32, 38, 54));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                tracksBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            buttonsRow.addView(tracksBtn, new LinearLayout.LayoutParams(-2, dp(38)));

            Button qualityBtn = new Button(this);
            qualityBtn.setText("🎬 Calidad");
            qualityBtn.setTextColor(Color.WHITE);
            qualityBtn.setTextSize(12);
            qualityBtn.setAllCaps(false);
            qualityBtn.setFocusable(true);
            GradientDrawable qbBg = new GradientDrawable();
            qbBg.setColor(Color.argb(200, 32, 38, 54));
            qbBg.setCornerRadius(dp(8));
            qualityBtn.setBackground(qbBg);
            qualityBtn.setOnClickListener(v -> {
                scheduleOsdHide(4000);
                showVideoQualityPicker();
            });
            qualityBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 80, 120) : Color.argb(200, 32, 38, 54));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                qualityBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            LinearLayout.LayoutParams qbLp = new LinearLayout.LayoutParams(-2, dp(38));
            qbLp.setMargins(dp(8), 0, 0, 0);
            buttonsRow.addView(qualityBtn, qbLp);

            Button serverBtn = new Button(this);
            serverBtn.setText("🌐 Servidor");
            serverBtn.setTextColor(Color.WHITE);
            serverBtn.setTextSize(12);
            serverBtn.setAllCaps(false);
            serverBtn.setFocusable(true);
            GradientDrawable sbBg = new GradientDrawable();
            sbBg.setColor(Color.argb(200, 32, 38, 54));
            sbBg.setCornerRadius(dp(8));
            serverBtn.setBackground(sbBg);
            serverBtn.setOnClickListener(v -> {
                scheduleOsdHide(5000);
                showSourceSelectionDialog();
            });
            serverBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 80, 120) : Color.argb(200, 32, 38, 54));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                serverBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(-2, dp(38));
            sbLp.setMargins(dp(8), 0, 0, 0);
            buttonsRow.addView(serverBtn, sbLp);

            Button extPlayerBtn = new Button(this);
            extPlayerBtn.setText("📺 Abrir en VLC");
            extPlayerBtn.setTextColor(Color.WHITE);
            extPlayerBtn.setTextSize(12);
            extPlayerBtn.setAllCaps(false);
            extPlayerBtn.setFocusable(true);
            GradientDrawable epBg = new GradientDrawable();
            epBg.setColor(Color.argb(200, 32, 38, 54));
            epBg.setCornerRadius(dp(8));
            extPlayerBtn.setBackground(epBg);
            extPlayerBtn.setOnClickListener(v -> {
                scheduleOsdHide(4000);
                launchExternalPlayer(currentPlayingTitle, currentPlayingUrl, currentPlayingHeaders);
            });
            extPlayerBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 80, 120) : Color.argb(200, 32, 38, 54));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                extPlayerBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(-2, dp(38));
            epLp.setMargins(dp(8), 0, 0, 0);
            buttonsRow.addView(extPlayerBtn, epLp);

            Button aspectBtn = new Button(this);
            aspectBtn.setText("⛶ Aspecto");
            aspectBtn.setTextColor(Color.WHITE);
            aspectBtn.setTextSize(12);
            aspectBtn.setAllCaps(false);
            aspectBtn.setFocusable(true);
            GradientDrawable abBg = new GradientDrawable();
            abBg.setColor(Color.argb(200, 32, 38, 54));
            abBg.setCornerRadius(dp(8));
            aspectBtn.setBackground(abBg);
            aspectBtn.setOnClickListener(v -> {
                scheduleOsdHide(4000);
                cycleAspectRatio();
            });
            aspectBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 80, 120) : Color.argb(200, 32, 38, 54));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                aspectBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(-2, dp(38));
            abLp.setMargins(dp(8), 0, 0, 0);
            buttonsRow.addView(aspectBtn, abLp);

            Button downloadBtn = new Button(this);
            downloadBtn.setText("⬇ Descargar");
            downloadBtn.setTextColor(Color.WHITE);
            downloadBtn.setTextSize(12);
            downloadBtn.setAllCaps(false);
            downloadBtn.setFocusable(true);
            GradientDrawable dlBg = new GradientDrawable();
            dlBg.setColor(Color.argb(200, 28, 55, 88));
            dlBg.setCornerRadius(dp(8));
            downloadBtn.setBackground(dlBg);
            downloadBtn.setOnClickListener(v -> {
                scheduleOsdHide(4000);
                LeoDownloadManager.download(this, currentPlayingTitle, currentPlayingUrl, headers);
            });
            downloadBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(40, 75, 120) : Color.argb(200, 28, 55, 88));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                downloadBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            LinearLayout.LayoutParams dlLp = new LinearLayout.LayoutParams(-2, dp(38));
            dlLp.setMargins(dp(8), 0, 0, 0);
            buttonsRow.addView(downloadBtn, dlLp);

            Button rotateBtn = new Button(this);
            rotateBtn.setText("🔄 Rotar");
            rotateBtn.setTextColor(Color.WHITE);
            rotateBtn.setTextSize(12);
            rotateBtn.setAllCaps(false);
            rotateBtn.setFocusable(true);
            GradientDrawable rotBg = new GradientDrawable();
            rotBg.setColor(Color.argb(200, 42, 50, 72));
            rotBg.setCornerRadius(dp(8));
            rotateBtn.setBackground(rotBg);
            rotateBtn.setOnClickListener(v -> {
                scheduleOsdHide(4000);
                toggleScreenOrientation();
            });
            rotateBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(65, 80, 120) : Color.argb(200, 42, 50, 72));
                g.setCornerRadius(dp(8));
                if (f) g.setStroke(dp(3), Color.WHITE);
                rotateBtn.setBackground(g);
                if (f)
                    scheduleOsdHide(5000);
            });
            LinearLayout.LayoutParams rotLp = new LinearLayout.LayoutParams(-2, dp(38));
            rotLp.setMargins(dp(8), 0, 0, 0);
            buttonsRow.addView(rotateBtn, rotLp);

            android.widget.FrameLayout.LayoutParams osdLp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            osdLp.setMargins(0, 0, 0, dp(75));
            overlay.addView(osdBar, osdLp);

            // 3. Botón flotante para saltar al siguiente capítulo al final del video
            Button nextBtn = new Button(this);
            nextEpBannerBtn = nextBtn;
            nextBtn.setText("⏭ Siguiente Capítulo");
            nextBtn.setTextColor(Color.WHITE);
            nextBtn.setTextSize(14);
            nextBtn.setTypeface(null, Typeface.BOLD);
            nextBtn.setFocusable(true);
            nextBtn.setVisibility(android.view.View.GONE);
            nextBtn.setAlpha(0.0f);
            GradientDrawable nbBg = new GradientDrawable();
            nbBg.setColor(Color.rgb(18, 92, 160));
            nbBg.setCornerRadius(dp(12));
            nbBg.setStroke(dp(2), Color.rgb(80, 180, 255));
            nextBtn.setBackground(nbBg);
            nextBtn.setOnClickListener(v -> {
                if (currentPlayingEntry != null && !currentPlayingEntry.isMovie() && currentPlayingEpisode > 0) {
                    final int nextEp = currentPlayingEpisode + 1;
                    final int currSeason = currentPlayingSeason > 0 ? currentPlayingSeason : 1;
                    final CatalogData.Entry seriesEntry = currentPlayingEntry;
                    final String seriesBaseTitle = seriesEntry.title != null ? seriesEntry.title : "";
                    final String nextTitle = seriesBaseTitle + " · T" + currSeason + " E" + nextEp;
                    VideoProvider vp = VideoProviders.find(currentPlayingOrigin);
                    if (vp == null) vp = VideoProviders.find("CineHax");
                    nextBtn.setVisibility(android.view.View.GONE);
                    playProviderTitle(vp, seriesEntry, currSeason, nextEp, nextTitle);
                }
            });
            nextBtn.setOnFocusChangeListener((v, f) -> {
                GradientDrawable g = new GradientDrawable();
                g.setColor(f ? Color.rgb(25, 120, 210) : Color.rgb(18, 92, 160));
                g.setCornerRadius(dp(12));
                g.setStroke(f ? dp(3) : dp(2), f ? Color.WHITE : Color.rgb(80, 180, 255));
                nextBtn.setBackground(g);
                nextBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
            });
            android.widget.FrameLayout.LayoutParams nbLp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.END);
            nbLp.setMargins(0, 0, dp(32), dp(90));
            overlay.addView(nextBtn, nbLp);
        } else {
            body.addView(status);
        }
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setAllowedVideoJoiningTimeMs(5000);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        30000, // minBufferMs: 30 segundos (evita trabas cada 10-15s)
                        120000, // maxBufferMs: 2 minutos de anticipación
                        1000, // bufferForPlaybackMs: arranque rápido en 1s
                        2500 // bufferForPlaybackAfterRebufferMs: 2.5s tras rebuffer
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(20000, true) // conserva 20s de búfer previo
                .build();

        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build();

        ExoPlayer.Builder builder = new ExoPlayer.Builder(this, renderersFactory);
        builder.setLoadControl(loadControl);
        builder.setAudioAttributes(audioAttrs, true);

        java.util.Map<String, String> safeHeaders = new java.util.HashMap<>();
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        if (headers != null) {
            for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                if ("user-agent".equalsIgnoreCase(entry.getKey())) {
                    userAgent = entry.getValue();
                } else {
                    safeHeaders.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (url != null && (url.contains("tvf90.com") || url.contains("ftlly.com"))) {
            safeHeaders.put("Referer", "https://tvf90.com/");
            safeHeaders.put("Origin", "https://tvf90.com");
        }
        androidx.media3.datasource.DefaultHttpDataSource.Factory http = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(25000)
                .setAllowCrossProtocolRedirects(true);
        if (!safeHeaders.isEmpty())
            http.setDefaultRequestProperties(safeHeaders);
        builder.setMediaSourceFactory(
                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http));
        externalPlayer = builder.build();

        TrackSelectionParameters trackParams = externalPlayer.getTrackSelectionParameters()
                .buildUpon()
                .setMaxVideoSize(1920, 1080)
                .setForceHighestSupportedBitrate(true)
                .build();
        externalPlayer.setTrackSelectionParameters(trackParams);
        externalPlayer.setPlaybackParameters(new PlaybackParameters(1.0f, 1.0f));
        view.setPlayer(externalPlayer);
        view.setControllerShowTimeoutMs(4000);
        view.post(() -> {
            try {
                android.view.View exoSettings = view.findViewById(androidx.media3.ui.R.id.exo_settings);
                if (exoSettings != null) {
                    exoSettings.setOnClickListener(v -> {
                        scheduleOsdHide(5000);
                        showTracksDialog();
                    });
                }
            } catch (Exception ignored) {}
        });
        view.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                if (playerOsdBar != null) {
                    if (visibility == android.view.View.VISIBLE) {
                        playerOsdBar.setVisibility(android.view.View.VISIBLE);
                        playerOsdBar.animate().alpha(1.0f).setDuration(200).start();
                    } else {
                        playerOsdBar.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
                            if (playerOsdBar != null) {
                                playerOsdBar.setVisibility(android.view.View.GONE);
                            }
                        }).start();
                    }
                }
            }
        });
        view.setOnClickListener(v -> {
            if (view.isControllerFullyVisible()) {
                view.hideController();
            } else {
                view.showController();
            }
        });

        // Restaurar posición guardada de Continuar viendo si existe
        final long savedPos = PlaybackEngine.getSavedPosition(this, currentPlayingTitle);
        if (savedPos > 10000) {
            externalPlayer.addListener(new Player.Listener() {
                private boolean resumed = false;

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && !resumed) {
                        resumed = true;
                        externalPlayer.seekTo(savedPos);
                        showPlayerHud("▶  Reanudando desde " + PlaybackEngine.formatTime(savedPos));
                    }
                }
            });
        }

        // Iniciar guardado periódico cada 5 segundos
        mainHandler.removeCallbacks(periodicSaveRunnable);
        mainHandler.postDelayed(periodicSaveRunnable, 5000);
        externalPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    if (bufferingWatchdogRunnable != null) {
                        mainHandler.removeCallbacks(bufferingWatchdogRunnable);
                        bufferingWatchdogRunnable = null;
                    }
                    isSwitchingUnstableServer = false;
                    showStatus(externalPlayer != null && externalPlayer.getPlayWhenReady()
                            ? "Video preparado."
                            : "Pausado.");
                    if (status != null)
                        status.setVisibility(android.view.View.GONE);
                    scheduleOsdHide(3500);
                } else if (state == Player.STATE_ENDED) {
                    if (bufferingWatchdogRunnable != null) {
                        mainHandler.removeCallbacks(bufferingWatchdogRunnable);
                        bufferingWatchdogRunnable = null;
                    }
                    showStatus("Video finalizado.");
                    if (status != null)
                        status.setVisibility(android.view.View.VISIBLE);

                    if (nextEpBannerBtn != null) {
                        nextEpBannerBtn.setVisibility(android.view.View.GONE);
                    }

                    if (currentPlayingTitle != null && !currentPlayingTitle.isEmpty()) {
                        PlaybackEngine.markWatched(MainActivity.this, currentPlayingTitle);
                        if (currentPlayingEntry != null && !currentPlayingEntry.isMovie()) {
                            PlaybackEngine.markWatched(MainActivity.this, currentPlayingEntry.title + " · T" + currentPlayingSeason + " E" + currentPlayingEpisode);
                        }
                    }

                    if (currentPlayingEntry != null && !currentPlayingEntry.isMovie() && currentPlayingEpisode > 0) {
                        final int nextEp = currentPlayingEpisode + 1;
                        final int currSeason = currentPlayingSeason > 0 ? currentPlayingSeason : 1;
                        final CatalogData.Entry seriesEntry = currentPlayingEntry;
                        final String seriesBaseTitle = seriesEntry.title != null ? seriesEntry.title : "";
                        final String nextTitle = seriesBaseTitle + " · T" + currSeason + " E" + nextEp;

                        if (autoNextCountDownTimer != null) {
                            autoNextCountDownTimer.cancel();
                            autoNextCountDownTimer = null;
                        }
                        if (autoNextDialog != null) {
                            try {
                                if (autoNextDialog.isShowing()) autoNextDialog.dismiss();
                            } catch (Exception ignored) {}
                            autoNextDialog = null;
                        }

                        final int COUNTDOWN_SECONDS = 5;
                        final boolean[] actionTaken = new boolean[]{false};

                        android.app.AlertDialog.Builder nextB = new android.app.AlertDialog.Builder(MainActivity.this);
                        nextB.setTitle("⏭ Siguiente Capítulo (" + COUNTDOWN_SECONDS + "s)");
                        nextB.setMessage("¿Querés reproducir el siguiente capítulo?\n\n"
                                + seriesBaseTitle + "\nTemporada " + currSeason + " · Capítulo " + nextEp
                                + "\n\nIniciando automáticamente en " + COUNTDOWN_SECONDS + " segundos...");

                        nextB.setPositiveButton("Reproducir ahora (Cap. " + nextEp + ")", (d, w) -> {
                            actionTaken[0] = true;
                            if (autoNextCountDownTimer != null) {
                                autoNextCountDownTimer.cancel();
                                autoNextCountDownTimer = null;
                            }
                            VideoProvider vp = VideoProviders.find(currentPlayingOrigin);
                            if (vp == null) vp = VideoProviders.find("CineHax");
                            playProviderTitle(vp, seriesEntry, currSeason, nextEp, nextTitle);
                        });

                        nextB.setNegativeButton("Cancelar", (d, w) -> {
                            actionTaken[0] = true;
                            if (autoNextCountDownTimer != null) {
                                autoNextCountDownTimer.cancel();
                                autoNextCountDownTimer = null;
                            }
                            d.dismiss();
                        });

                        final android.app.AlertDialog dialog = nextB.create();
                        autoNextDialog = dialog;

                        dialog.setOnDismissListener(d -> {
                            if (!actionTaken[0] && autoNextCountDownTimer != null) {
                                autoNextCountDownTimer.cancel();
                                autoNextCountDownTimer = null;
                            }
                        });

                        dialog.show();

                        autoNextCountDownTimer = new android.os.CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000L) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                                if (actionTaken[0] || !dialog.isShowing()) return;
                                long secLeft = (millisUntilFinished + 999) / 1000;
                                dialog.setTitle("⏭ Siguiente Capítulo (" + secLeft + "s)");
                                dialog.setMessage("¿Querés reproducir el siguiente capítulo?\n\n"
                                        + seriesBaseTitle + "\nTemporada " + currSeason + " · Capítulo " + nextEp
                                        + "\n\nIniciando automáticamente en " + secLeft + " segundos...");
                            }

                            @Override
                            public void onFinish() {
                                if (actionTaken[0]) return;
                                actionTaken[0] = true;
                                try {
                                    if (dialog.isShowing()) {
                                        dialog.dismiss();
                                    }
                                } catch (Exception ignored) {}
                                autoNextDialog = null;
                                autoNextCountDownTimer = null;
                                VideoProvider vp = VideoProviders.find(currentPlayingOrigin);
                                if (vp == null) vp = VideoProviders.find("CineHax");
                                playProviderTitle(vp, seriesEntry, currSeason, nextEp, nextTitle);
                            }
                        }.start();
                    }
                } else if (state == Player.STATE_BUFFERING) {
                    showStatus("Cargando video...");
                    if (status != null)
                        status.setVisibility(android.view.View.VISIBLE);

                    if (bufferingWatchdogRunnable != null) {
                        mainHandler.removeCallbacks(bufferingWatchdogRunnable);
                    }
                    bufferingWatchdogRunnable = () -> {
                        if (externalPlayer != null && externalPlayer.getPlaybackState() == Player.STATE_BUFFERING
                                && externalPlayer.getPlayWhenReady() && !isSwitchingUnstableServer) {
                            android.util.Log.w("LeoTVPlayer", "Watchdog de buffering sostenido disparado (>8s)");
                            autoFallbackUnstableServer("Buffering sostenido");
                        }
                    };
                    mainHandler.postDelayed(bufferingWatchdogRunnable, 8000);
                }
                updateMediaState();
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                    Player.PositionInfo newPosition, int reason) {
                updateMediaState();
            }

            @Override
            public void onPlayWhenReadyChanged(boolean ready, int reason) {
                if (!ready && bufferingWatchdogRunnable != null) {
                    mainHandler.removeCallbacks(bufferingWatchdogRunnable);
                    bufferingWatchdogRunnable = null;
                }
                updateMediaState();
                showStatus(ready ? (externalPlayer.getPlaybackState() == Player.STATE_READY
                        ? "Video preparado. Reproduciendo."
                        : "Cargando datos de video...") : "Pausado.");
                android.util.Log.i("VideoPropia", "playWhenReady=" + ready + " reason=" + reason
                        + " position=" + externalPlayer.getCurrentPosition()
                        + " state=" + externalPlayer.getPlaybackState()
                        + " playing=" + externalPlayer.isPlaying());
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                updateMediaState();
                if (externalPlayer != null && externalPlayer.getPlaybackState() == Player.STATE_READY) {
                    if (status != null)
                        status.setVisibility(android.view.View.GONE);
                    android.util.Log.i("LeoTVPlayer", "playing=" + playing +
                            " positionMs=" + externalPlayer.getCurrentPosition());
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (bufferingWatchdogRunnable != null) {
                    mainHandler.removeCallbacks(bufferingWatchdogRunnable);
                    bufferingWatchdogRunnable = null;
                }
                if (currentPlayingEntry != null && !isSwitchingUnstableServer) {
                    android.util.Log.w("LeoTVPlayer", "Player error -> auto-fallback activado: " + error.getMessage());
                    autoFallbackUnstableServer("Error de conexión");
                    return;
                }
                Throwable cause = error;
                int httpCode = 0;
                while (cause != null) {
                    if (cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpCode = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) cause).responseCode;
                        break;
                    }
                    cause = cause.getCause();
                }
                String message = httpCode == 403 ? "Acceso denegado por la fuente (HTTP 403). Probá otro canal."
                        : httpCode == 404 ? "La fuente no existe (HTTP 404). Probá otro canal."
                                : httpCode > 0 ? "Error HTTP " + httpCode + " de la fuente."
                                        : "No se pudo reproducir: " + error.getErrorCodeName();
                showStatus(message + "  Atras para elegir otro video.");
                if (status != null)
                    status.setVisibility(android.view.View.VISIBLE);
                if (mediaSession != null)
                    mediaSession.setPlaybackState(
                            new android.media.session.PlaybackState.Builder()
                                    .setState(android.media.session.PlaybackState.STATE_ERROR,
                                            externalPlayer.getCurrentPosition(), 0f)
                                    .build());
                android.util.Log.e("LeoTVPlayer", "Playback error origin=" + origin +
                        " code=" + error.getErrorCodeName() + " http=" + httpCode, error);
            }
        });
        externalPlayer.setMediaItem(MediaItem.fromUri(url));
        startMediaSession();
        externalPlayer.prepare();
        externalPlayer.play();

        view.requestFocusFromTouch();
        fullscreenButton = item("Pantalla completa", body, () -> setFullscreen(true));
        setFullscreen(true);
    }

    private void handleLaunchIntent(android.content.Intent intent) {
        if (intent == null)
            return;
        String openSection = intent.getStringExtra("open_section");
        if (openSection != null && !openSection.isEmpty()) {
            if ("tv".equals(openSection)) {
                showLiveTvStudio();
                return;
            } else if ("movies".equals(openSection)) {
                activeTab = "movies";
                home();
                return;
            } else if ("series".equals(openSection)) {
                activeTab = "series";
                home();
                return;
            } else if ("docs".equals(openSection)) {
                activeTab = "docs";
                home();
                return;
            } else if ("favorites".equals(openSection)) {
                showFavoritesPage();
                return;
            } else if ("watch_later".equals(openSection)) {
                showWatchLaterPage();
                return;
            }
        }
        String playUrl = intent.getStringExtra("play_url");
        String playTitle = intent.getStringExtra("play_title");
        if (playUrl != null && !playUrl.isEmpty()) {
            playMovie(playTitle != null ? playTitle : "Video", playUrl, "Direct Play");
            return;
        }
        String searchQuery = intent.getStringExtra("search_query");
        boolean openSearch = intent.getBooleanExtra("open_search", false);
        String detailQuery = intent.getStringExtra("open_detail_query");
        String actorName = intent.getStringExtra("open_actor_name");
        if (actorName != null && !actorName.isEmpty()) {
            new Thread(() -> {
                ZappCatalogEngine.GlobalSearchResult r = ZappCatalogEngine.searchUnified(actorName, true,
                        activeProvider);
                if (!r.matchedActors.isEmpty()) {
                    final ZappCatalogEngine.ActorItem actor = r.matchedActors.get(0);
                    runOnUiThread(() -> openActorDetailSheet(actor));
                }
            }).start();
        } else if (detailQuery != null && !detailQuery.isEmpty()) {
            new Thread(() -> {
                ZappCatalogEngine.GlobalSearchResult r = ZappCatalogEngine.searchUnified(detailQuery, true,
                        activeProvider);
                for (java.util.List<CatalogData.Entry> list : r.byProvider.values()) {
                    if (!list.isEmpty()) {
                        final CatalogData.Entry first = list.get(0);
                        runOnUiThread(() -> openEntryDetail(first));
                        return;
                    }
                }
            }).start();
        } else if (openSearch || (searchQuery != null && !searchQuery.isEmpty())) {
            showLunifiedSearch(searchQuery == null ? "" : searchQuery);
        }
    }

    private AppUpdater.UpdateInfo pendingUpdate = null;

    private void showSplashScreen() {
        page = "splash";

        // Pre-cargar Home inmediatamente en segundo plano
        // para que durante los 8 segundos del video se descarguen catálogos e imágenes de pósters
        home();

        splashOverlay = new android.widget.FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.BLACK);
        splashOverlay.setFocusable(true);
        splashOverlay.setFocusableInTouchMode(true);

        splashVideoView = new android.widget.VideoView(this);
        android.widget.FrameLayout.LayoutParams vvLp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        splashOverlay.addView(splashVideoView, vvLp);

        TextView skipBadge = text("Presioná  [OK]  para saltar", 13, Color.argb(210, 240, 240, 240));
        skipBadge.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable sbBg = new GradientDrawable();
        sbBg.setColor(Color.argb(160, 16, 22, 34));
        sbBg.setCornerRadius(dp(12));
        sbBg.setStroke(dp(1), Color.argb(120, 245, 197, 66));
        skipBadge.setBackground(sbBg);
        android.widget.FrameLayout.LayoutParams sblp = new android.widget.FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.END);
        sblp.setMargins(0, 0, dp(32), dp(28));
        splashOverlay.addView(skipBadge, sblp);

        splashOverlay.setOnClickListener(v -> dismissSplash());
        splashOverlay.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dismissSplash();
                return true;
            }
            return false;
        });

        // Comprobación silenciosa de actualizaciones en GitHub
        AppUpdater.check(this, new AppUpdater.CheckCallback() {
            @Override
            public void onUpdateAvailable(AppUpdater.UpdateInfo info) {
                pendingUpdate = info;
            }

            @Override
            public void onNoUpdate() {}

            @Override
            public void onError(String msg) {}
        });

        getWindow().addContentView(splashOverlay,
                new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        splashOverlay.requestFocus();

        try {
            Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.splash);
            splashVideoView.setVideoURI(videoUri);
            splashVideoView.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                splashVideoView.start();
            });
            splashVideoView.setOnCompletionListener(mp -> dismissSplash());
            splashVideoView.setOnErrorListener((mp, what, extra) -> {
                dismissSplash();
                return true;
            });
        } catch (Exception ex) {
            dismissSplash();
        }

        // Temporizador de seguridad (8.5 segundos)
        mainHandler.postDelayed(dismissSplashRunnable, 8500);
    }

    private void dismissSplash() {
        runOnUiThread(() -> {
            mainHandler.removeCallbacks(dismissSplashRunnable);
            if (splashOverlay == null) return;
            final android.view.View toDismiss = splashOverlay;
            splashOverlay = null;
            if (splashVideoView != null) {
                try {
                    splashVideoView.stopPlayback();
                } catch (Exception ignored) {}
                splashVideoView = null;
            }
            toDismiss.animate().alpha(0f).setDuration(400).withEndAction(() -> {
                try {
                    if (toDismiss.getParent() instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) toDismiss.getParent()).removeView(toDismiss);
                    }
                } catch (Exception ignored) {}
                page = "home";
                handleLaunchIntent(getIntent());
                if (CatalogUpdater.shouldAutoUpdate(MainActivity.this)) {
                    CatalogUpdater.runUpdateAsync(MainActivity.this, null);
                }
                if (pendingUpdate != null) {
                    AppUpdater.showUpdateDialog(MainActivity.this, pendingUpdate);
                }
            }).start();
        });
    }

    private void toggleScreenOrientation() {
        int current = getResources().getConfiguration().orientation;
        if (current == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            Toast.makeText(this, "Pantalla en modo horizontal 🔄", Toast.LENGTH_SHORT).show();
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Toast.makeText(this, "Pantalla en modo vertical 🔄", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ("home".equals(page)) {
            home();
        } else if ("playing".equals(page)) {
            if (fullscreen) {
                setFullscreen(true);
            }
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } catch (Exception ignored) {
        }
        LeoDownloadManager.checkPermissions(this);
        loadChannels();
        loadCatalog();
        loadAudit();
        showSplashScreen();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (splashOverlay != null) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dismissSplash();
                return true;
            }
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if ("playing".equals(page)) {
                    if (playbackView != null && playbackView.isControllerFullyVisible()) {
                        playbackView.hideController();
                        return true;
                    }
                    if (playerOsdBar != null && playerOsdBar.getVisibility() == android.view.View.VISIBLE) {
                        hidePlayerOsd();
                        return true;
                    }
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                onBackPressed();
                return true;
            }
        }
        if (page.equals("playing") && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (playbackView != null) {
                    if (playbackView.isControllerFullyVisible()) {
                        playbackView.hideController();
                    } else {
                        playbackView.showController();
                        if (playerOsdBar != null) {
                            playerOsdBar.setVisibility(android.view.View.VISIBLE);
                            playerOsdBar.animate().alpha(1.0f).setDuration(180).start();
                            if (playerOsdBar.getChildCount() > 0) {
                                playerOsdBar.getChildAt(0).requestFocus();
                            }
                        }
                    }
                }
                return true;
            }
        }
        if (page.equals("playing") && externalPlayer != null) {
            int key = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_LEFT) {
                    scheduleOsdHide(4000);
                    long curPos = externalPlayer.getCurrentPosition();
                    long step = (event.getRepeatCount() > 4) ? 30000 : 10000;
                    long newPos = (key == KeyEvent.KEYCODE_DPAD_RIGHT)
                            ? Math.min(curPos + step, externalPlayer.getDuration())
                            : Math.max(0, curPos - step);
                    externalPlayer.seekTo(newPos);
                    String titlePrefix = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                            ? currentPlayingTitle + "\n" : "";
                    if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        showPlayerHud(titlePrefix + "⏩  +" + (step / 1000) + "s  (" + PlaybackEngine.formatTime(newPos) + " / "
                                + PlaybackEngine.formatTime(externalPlayer.getDuration()) + ")");
                    } else {
                        showPlayerHud(titlePrefix + "⏪  -" + (step / 1000) + "s  (" + PlaybackEngine.formatTime(newPos) + " / "
                                + PlaybackEngine.formatTime(externalPlayer.getDuration()) + ")");
                    }
                    updateMediaState();
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    android.view.View curFocus = getCurrentFocus();
                    boolean isOsdFocused = (playerOsdBar != null && (playerOsdBar.hasFocus() || (curFocus != null && curFocus.getParent() == playerOsdBar)));

                    if (isOsdFocused) {
                        scheduleOsdHide(5000);
                        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
                            if (curFocus != null) {
                                curFocus.performClick();
                                return true;
                            }
                        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (playbackView != null) {
                                playbackView.requestFocus();
                                return true;
                            }
                        } else if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            return super.dispatchKeyEvent(event);
                        }
                    }

                    if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                            || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        scheduleOsdHide(4000);
                        if (playbackView != null && !playbackView.isControllerFullyVisible()) {
                            playbackView.showController();
                            if (playerOsdBar != null) {
                                playerOsdBar.setVisibility(android.view.View.VISIBLE);
                                playerOsdBar.animate().alpha(1.0f).setDuration(180).start();
                            }
                            return true;
                        }
                        String titlePrefix = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                                ? currentPlayingTitle + "\n" : "";
                        if (externalPlayer.isPlaying()) {
                            externalPlayer.pause();
                            showPlayerHud(titlePrefix + "⏸  Pausado");
                        } else {
                            externalPlayer.play();
                            showPlayerHud(titlePrefix + "▶  Reproduciendo");
                        }
                        updateMediaState();
                        return true;
                    } else if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                        scheduleOsdHide(5000);
                        if (playbackView != null) {
                            playbackView.showController();
                        }
                        if (playerOsdBar != null) {
                            playerOsdBar.setVisibility(android.view.View.VISIBLE);
                            playerOsdBar.animate().alpha(1.0f).setDuration(180).start();
                            android.view.View row = playerOsdBar.getChildAt(1); // buttonsRow
                            if (row instanceof android.view.ViewGroup && ((android.view.ViewGroup) row).getChildCount() > 0) {
                                ((android.view.ViewGroup) row).getChildAt(0).requestFocus();
                                return true;
                            } else if (playerOsdBar.getChildCount() > 0) {
                                playerOsdBar.getChildAt(0).requestFocus();
                                return true;
                            }
                        }
                    } else if (key == KeyEvent.KEYCODE_MEDIA_PLAY) {
                        scheduleOsdHide(4000);
                        externalPlayer.play();
                        String titlePrefix = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                                ? currentPlayingTitle + "\n" : "";
                        showPlayerHud(titlePrefix + "▶  Reproduciendo");
                        updateMediaState();
                        return true;
                    } else if (key == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                        scheduleOsdHide(4000);
                        externalPlayer.pause();
                        String titlePrefix = (currentPlayingTitle != null && !currentPlayingTitle.isEmpty())
                                ? currentPlayingTitle + "\n" : "";
                        showPlayerHud(titlePrefix + "⏸  Pausado");
                        updateMediaState();
                        return true;
                    }
                }
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && !"playing".equals(page)
                && !"live_tv_fullscreen".equals(page) && !"live_tv".equals(page)) {
            int key = event.getKeyCode();
            android.view.View curFocus = getCurrentFocus();
            if (key == KeyEvent.KEYCODE_DPAD_LEFT && curFocus != null && !sideNavButtons.contains(curFocus)) {
                android.view.View next = android.view.FocusFinder.getInstance().findNextFocus(root, curFocus,
                        android.view.View.FOCUS_LEFT);
                if (next == null || (!sideNavButtons.contains(next) && next.getLeft() >= curFocus.getLeft())) {
                    int[] cardLoc = new int[2];
                    curFocus.getLocationOnScreen(cardLoc);
                    int cardY = cardLoc[1] + (curFocus.getHeight() / 2);
                    android.view.View bestSide = null;
                    int minDiff = Integer.MAX_VALUE;
                    for (android.view.View sb : sideNavButtons) {
                        if (sb.isShown()) {
                            int[] sbLoc = new int[2];
                            sb.getLocationOnScreen(sbLoc);
                            int diff = Math.abs((sbLoc[1] + (sb.getHeight() / 2)) - cardY);
                            if (diff < minDiff) {
                                minDiff = diff;
                                bestSide = sb;
                            }
                        }
                    }
                    if (bestSide != null) {
                        bestSide.requestFocus();
                        return true;
                    } else if (activeSidebarView != null) {
                        activeSidebarView.requestFocus();
                        return true;
                    } else if (!sideNavButtons.isEmpty()) {
                        sideNavButtons.get(0).requestFocus();
                        return true;
                    }
                }
            } else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && curFocus != null && sideNavButtons.contains(curFocus)) {
                int[] sideLoc = new int[2];
                curFocus.getLocationOnScreen(sideLoc);
                int sideY = sideLoc[1] + (curFocus.getHeight() / 2);

                android.view.View target = null;
                android.view.View next = android.view.FocusFinder.getInstance().findNextFocus(root, curFocus,
                        android.view.View.FOCUS_RIGHT);
                if (next != null && !sideNavButtons.contains(next) && next.isShown()) {
                    target = next;
                }

                if (pageScroll != null && pageScroll.getChildCount() > 0) {
                    android.view.View contentContainer = pageScroll.getChildAt(0);
                    java.util.ArrayList<android.view.View> focusables = new java.util.ArrayList<>();
                    contentContainer.addFocusables(focusables, android.view.View.FOCUS_RIGHT);
                    int minDiff = Integer.MAX_VALUE;
                    android.view.View bestTarget = null;
                    for (android.view.View cand : focusables) {
                        if (cand.isShown() && !sideNavButtons.contains(cand)) {
                            int[] cLoc = new int[2];
                            cand.getLocationOnScreen(cLoc);
                            if (cLoc[0] > sideLoc[0]) {
                                int candY = cLoc[1] + (cand.getHeight() / 2);
                                int diff = Math.abs(candY - sideY);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    bestTarget = cand;
                                }
                            }
                        }
                    }
                    if (bestTarget != null) {
                        target = bestTarget;
                    }
                }

                if (target != null) {
                    target.requestFocus();
                    return true;
                }
                if (currentFirstRail != null && currentFirstRail.getChildCount() > 0) {
                    currentFirstRail.getChildAt(0).requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        android.util.Log.i("VideoPropia", "onPause");
        if (video != null)
            video.pause();
        if (externalPlayer != null)
            externalPlayer.pause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (video != null)
            video.pause();
        if (externalPlayer != null)
            externalPlayer.pause();
        if (liveTvPlayer != null)
            liveTvPlayer.pause();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (bufferingWatchdogRunnable != null) {
            mainHandler.removeCallbacks(bufferingWatchdogRunnable);
            bufferingWatchdogRunnable = null;
        }
        if (liveTvBufferingWatchdogRunnable != null) {
            mainHandler.removeCallbacks(liveTvBufferingWatchdogRunnable);
            liveTvBufferingWatchdogRunnable = null;
        }
        isSwitchingUnstableServer = false;
        failedProvidersForCurrentTitle.clear();
        releaseLiveTvPlayer();
        if (heroSlideHandler != null && heroSlideRunnable != null) {
            heroSlideHandler.removeCallbacks(heroSlideRunnable);
        }
        if (video != null) {
            video.stopPlayback();
            video = null;
        }
        if (externalPlayer != null) {
            externalPlayer.release();
            externalPlayer = null;
        }
        releaseMediaSession();
        if (catalogBrowser != null)
            catalogBrowser.dispose();
        videoResolvers.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if ("playing".equals(page)) {
            if (bufferingWatchdogRunnable != null) {
                mainHandler.removeCallbacks(bufferingWatchdogRunnable);
                bufferingWatchdogRunnable = null;
            }
            isSwitchingUnstableServer = false;
            failedProvidersForCurrentTitle.clear();
            mainHandler.removeCallbacks(periodicSaveRunnable);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (autoNextCountDownTimer != null) {
                autoNextCountDownTimer.cancel();
                autoNextCountDownTimer = null;
            }
            if (autoNextDialog != null) {
                try {
                    if (autoNextDialog.isShowing()) autoNextDialog.dismiss();
                } catch (Exception ignored) {}
                autoNextDialog = null;
            }
            hideSourceLoader();
            if (externalPlayer != null) {
                final long pos = externalPlayer.getCurrentPosition();
                final long dur = externalPlayer.getDuration();
                if (pos > 0 && currentPlayingTitle != null && !currentPlayingTitle.isEmpty()
                        && !"live_tv".equals(playbackReturnPage)) {
                    final String sTitle = currentPlayingTitle;
                    final String sPoster = currentPlayingPoster;
                    final String sOrigin = currentPlayingOrigin;
                    final String sKind = currentPlayingKind;
                    new Thread(() -> {
                        PlaybackEngine.saveProgress(MainActivity.this, sTitle, sTitle,
                                sPoster, pos, dur, sOrigin, sKind);
                    }).start();
                }
                externalPlayer.stop();
                externalPlayer.release();
                externalPlayer = null;
            }
            if (video != null) {
                video.stopPlayback();
                video = null;
            }
            releaseMediaSession();
            fullscreen = false;

            if ("live_tv".equals(playbackReturnPage) || "live_tv_fullscreen".equals(playbackReturnPage)
                    || (playingChannel != null
                            && (playingChannel.startsWith("TV") || playingChannel.contains("Deportes")
                                    || playingChannel.contains("Live") || playingChannel.contains("Tele")))) {
                playbackReturnPage = "home";
                showLiveTvStudio();
            } else if ("m3u_list".equals(playbackReturnPage)) {
                playbackReturnPage = "home";
                showLiveTvStudio();
            } else if ("channel".equals(playbackReturnPage) && channel != null && !channel.isEmpty()) {
                playbackReturnPage = "home";
                openChannel(channel);
            } else if ("favorites".equals(playbackReturnPage)) {
                playbackReturnPage = "home";
                showFavoritesPage();
            } else if ("watch_later".equals(playbackReturnPage)) {
                playbackReturnPage = "home";
                showWatchLaterPage();
            } else {
                home();
            }
        } else if (fullscreen) {
            setFullscreen(false);
        } else if ("live_tv_fullscreen".equals(page)) {
            page = "live_tv";
            showLiveTvStudio();
        } else if ("live_tv".equals(page)) {
            home();
        } else if ("lunified_search".equals(page)) {
            home();
        } else if ("m3u_list".equals(page)) {
            showLiveTvStudio();
        } else if ("actor_detail".equals(page)) {
            home();
        } else if (!page.equals("home")) {
            home();
        } else if (!"all".equals(activeTab)) {
            activeTab = "all";
            home();
        } else
            super.onBackPressed();
    }
}