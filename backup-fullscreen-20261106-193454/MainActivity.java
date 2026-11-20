package ar.appvideo.player;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.GridLayout;
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
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private final int dark = Color.rgb(12, 16, 29);
    private final int panel = Color.rgb(27, 33, 52);
    private final int violet = Color.rgb(129, 107, 245);
    private LinearLayout root;
    private ScrollView pageScroll;
    private TextView status;
    private VideoView video;
    private ExoPlayer externalPlayer;
    private android.media.session.MediaSession mediaSession;
    private void updateMediaState() {
        if (mediaSession == null || externalPlayer == null) return;
        long actions = android.media.session.PlaybackState.ACTION_PLAY
            | android.media.session.PlaybackState.ACTION_PAUSE
            | android.media.session.PlaybackState.ACTION_PLAY_PAUSE
            | android.media.session.PlaybackState.ACTION_SEEK_TO
            | android.media.session.PlaybackState.ACTION_FAST_FORWARD
            | android.media.session.PlaybackState.ACTION_REWIND;
        mediaSession.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setActions(actions)
            .setState(externalPlayer.getPlayWhenReady() ? android.media.session.PlaybackState.STATE_PLAYING
                : android.media.session.PlaybackState.STATE_PAUSED,
                externalPlayer.getCurrentPosition(), externalPlayer.isPlaying() ? 1f : 0f)
            .build());
    }
    private void startMediaSession() {
        if (mediaSession != null) mediaSession.release();
        mediaSession = new android.media.session.MediaSession(this, "VideoPropia");
        mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
            | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new android.media.session.MediaSession.Callback() {
            @Override public void onPlay() { if (externalPlayer != null) { externalPlayer.play(); updateMediaState(); } }
            @Override public void onPause() { if (externalPlayer != null) { externalPlayer.pause(); updateMediaState(); } }
            @Override public void onSeekTo(long position) { if (externalPlayer != null) { externalPlayer.seekTo(position); updateMediaState(); } }
            @Override public void onFastForward() { if (externalPlayer != null) { externalPlayer.seekForward(); updateMediaState(); } }
            @Override public void onRewind() { if (externalPlayer != null) { externalPlayer.seekBack(); updateMediaState(); } }
        });
        mediaSession.setActive(true);
        updateMediaState();
    }
    private void releaseMediaSession() {
        if (mediaSession != null) { mediaSession.release(); mediaSession = null; }
    }
    private String page = "home";
    private String channel = "";
    private String playingChannel = "Demo";
    private JSONArray channels = new JSONArray();
    private JSONArray catalogProviders = new JSONArray();
    private JSONObject audit = new JSONObject();
    private void loadAudit() {
        try (InputStream in = getAssets().open("provider_audit.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer,0,n);
            audit = new JSONObject(bytes.toString("UTF-8"));
        } catch (Exception e) {
            audit = new JSONObject();
            android.util.Log.e("VideoPropia", "Error en auditoría de proveedores", e);
        }
    }
    private String referenceStatus(String name) {
        JSONArray entries = audit.optJSONArray("providers");
        if (entries == null) return "Referencia aún sin auditar";
        for (int i=0; i<entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null || !name.equals(entry.optString("name"))) continue;
            String result = entry.optString("reference_status");
            boolean allowed = entry.optBoolean("live_test_allowed",false);
            String siteTest = entry.optString("site_test","no_evaluado");
            String siteStatus = !allowed ? "No consultado (restricción previa)"
                : "http_accessible".equals(siteTest) ? "Sitio accesible (HTTP 200)"
                : "http_restricted".equals(siteTest) ? "Acceso restringido (HTTP " + entry.optInt("site_http_status") + ")"
                : "Sitio sin respuesta verificada";
            return "Código: " + ("accessible".equals(result) ? "accesible" : result)
                + ". " + siteStatus + ". Reproducción en nuestra app: NO verificada.";
        }
        return "No hay referencia registrada";
    }
    private void loadCatalog() {
        try (InputStream in = getAssets().open("catalog.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer,0,n);
            JSONObject doc = new JSONObject(bytes.toString("UTF-8"));
            catalogProviders = doc.getJSONArray("providers");
        } catch (Exception e) {
            catalogProviders = new JSONArray();
            android.util.Log.e("VideoPropia", "Error en catálogo local", e);
        }
    }
    private boolean showCatalog(String providerName, LinearLayout body) {
        for (int i=0; i<catalogProviders.length(); i++) {
            JSONObject provider = catalogProviders.optJSONObject(i);
            if (provider == null || !providerName.equals(provider.optString("id"))) continue;
            JSONArray movies = provider.optJSONArray("movies");
            if (movies == null || movies.length() == 0) return false;
            for (int j=0; j<movies.length(); j++) {
                JSONObject movie = movies.optJSONObject(j);
                if (movie == null) continue;
                String title = movie.optString("title","Película");
                String url = movie.optString("url","");
                String badge = movie.optString("quality","");
                int year = movie.optInt("year",0);
                if (!url.startsWith("https://")) continue;
                item("▶  " + title + " (" + year + ")  |  " + badge, body,
                    () -> playMovie(title,url,providerName));
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
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            channels = new JSONArray(bytes.toString("UTF-8"));
        } catch (Exception e) { channels = new JSONArray(); }
    }
    private String descriptionFor(String name) {
        for (int i=0; i<channels.length(); i++) {
            JSONObject j = channels.optJSONObject(i);
            if (j != null && name.equals(j.optString("name"))) {
                return j.optString("description", "Fuente candidata no verificada")
                    + " · Estado: " + j.optString("status", "sin pruebas") + " · No integrada en esta app.";
            }
        }
        return "Fuente no habilitada";
    }
    private static final String DEMO_URL =
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4";

    private GradientDrawable bg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(22); g.setStroke(2, stroke);
        return g;
    }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private void line(String s, int size, int color) {
        TextView t = text(s, size, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 7, 0, 14);
        root.addView(t, lp);
    }
    private Button item(String title, LinearLayout target, Runnable run) {
        Button b = new Button(this);
        b.setText(title); b.setTextSize(19); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setFocusable(true);
        b.setBackground(bg(panel, Color.rgb(70,80,113)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 90);
        lp.setMargins(0, 0, 0, 14);
        target.addView(b, lp);
        b.setOnClickListener(v -> run.run());
        b.setOnFocusChangeListener((v, focus) ->
            b.setBackground(bg(focus ? violet : panel, focus ? Color.WHITE : Color.rgb(70,80,113))));
        return b;
    }
    private Button tile(String name, String state, GridLayout grid, int size, Runnable run) {
        Button b = new Button(this);
        b.setText(name + "\n\n" + state);
        b.setTextSize(20);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackground(bg(panel, Color.rgb(68, 79, 111)));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = size;
        params.height = size;
        params.setMargins(0, 0, 18, 18);
        grid.addView(b, params);
        b.setOnClickListener(v -> run.run());
        b.setOnFocusChangeListener((v, selected) -> {
            b.setBackground(bg(selected ? violet : panel,
                selected ? Color.WHITE : Color.rgb(68, 79, 111)));
            b.setScaleX(selected ? 1.025f : 1.0f);
            b.setScaleY(selected ? 1.025f : 1.0f);
        });
        return b;
    }

    private LinearLayout newPage(String section, String title, String sub) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 32, 48, 30);
        root.setBackgroundColor(dark);
        line("VIDEO PROPIA   /   " + section.toUpperCase(), 15, violet);
        TextView heading = text(title, 31, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        root.addView(heading);
        line(sub, 17, Color.LTGRAY);
        ScrollView scroll = new ScrollView(this);
        pageScroll = scroll;
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(root);
        return body;
    }
    private void home() {
        page = "home"; channel = "";
        LinearLayout body = newPage("inicio", "Tus canales, en un solo lugar",
            "Elegí un canal. Los cuadrados marcados 'Pendiente' aún no reproducen.");
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int columns = dm.widthPixels >= 1200 ? 3 : 2;
        int size = (dm.widthPixels - 96 - columns * 18) / columns;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        Button first = tile("▶  DEMO", "REPRODUCIR", grid, size, () -> openChannel("Demo"));
        tile("BLENDER OPEN MOVIES", "PRUEBAS", grid, size,
            () -> openChannel("Blender Open Movies"));
        tile("INTERNET ARCHIVE", "PELÍCULA COMPLETA", grid, size,
            () -> openChannel("Internet Archive"));
        tile("MIS NUBES", "SIN CONFIGURAR", grid, size,
            () -> openChannel("Mis nubes"));
        tile("ESTADO", channels.length() + " FUENTES", grid, size,
            () -> openChannel("Estado"));
        for (int i = 0; i < channels.length(); i++) {
            JSONObject entry = channels.optJSONObject(i);
            if (entry == null) continue;
            final String name = entry.optString("name", "Fuente");
            tile(name, "PENDIENTE", grid, size, () -> openChannel(name));
        }
        first.requestFocusFromTouch();
        pageScroll.post(() -> { first.requestFocusFromTouch(); pageScroll.scrollTo(0, 0); });
    }
    private void openChannel(String name) {
        page = "channel"; channel = name;
        String sub = "Canal independiente · Volvé con ATRÁS.";
        if (!name.equals("Demo") && !name.equals("Blender Open Movies")) sub = descriptionFor(name);
        if (name.equals("Blender Open Movies")) sub = "Canal oficial Blender Open Movies: enlaces verificados al entrar; películas y tráiler identificados.";
        if (name.equals("Internet Archive")) sub = "Catálogo online de películas CC BY 3.0; fichas y enlaces desde la API pública.";
        if (name.equals("CineHax")) sub =
            "Matrix con audio latino confirmada en Nuvio. En nuestra app sigue sin integrar.";
        if (name.equals("SoloLatino")) sub =
            "Catálogo investigado; sin reproducción propia confirmada.";
        if (name.equals("Mis nubes")) sub =
            "Futura importación de bibliotecas personales autorizadas.";
        if (name.equals("Estado")) {
            JSONObject counts = audit.optJSONObject("site_counts");
            int accessible = counts == null ? 0 : counts.optInt("http_accessible",0);
            int restricted = counts == null ? 0 : counts.optInt("http_restricted",0);
            int skipped = counts == null ? 0 : counts.optInt("skipped_no_permission",0);
            sub = channels.length() + " candidatos. Sitios: " + accessible + " accesibles, "
                + restricted + " restringido, " + skipped + " excluidos. 0 conectores externos activos.";
        }
        LinearLayout body = newPage(name, name, sub);
        Button back = item("←  Volver a todos los canales", body, this::home);
        if (name.equals("Internet Archive") || name.equals("Blender Open Movies")) {
            // Ambos canales consultan sus adaptadores en segundo plano.
        } else if (showCatalog(name, body)) {
            // Las fichas provienen del catálogo JSON, no de URLs incrustadas en la UI.
        } else if (name.equals("Estado")) {
            item("Ver diagnóstico de los 32 candidatos", body,
                () -> showStatus("32 códigos de referencia accesibles. 17 sitios con HTTP 200, 1 con 403 y 14 excluidos. Ningún conector externo de reproducción instalado."));
        } else if (name.equals("Demo")) {
            item("🎞  Big Buck Bunny (2008)  |  Reproducir muestra", body, this::playDemo);
            item("ⓘ  Fuente: video de muestra autorizado (MP4)", body,
                () -> showStatus("Demo: video independiente de los proveedores pendientes."));
        } else {
            item("◈  Diagnóstico: " + referenceStatus(name), body,
                () -> showStatus(referenceStatus(name)));
            item("Canal todavía sin reproducción integrada", body,
                () -> showStatus("Este proveedor está inventariado, pero no dispone de adaptador de reproducción."));
        }
        status = text("", 17, Color.LTGRAY);
        body.addView(status);
        if (name.equals("Internet Archive")) loadArchiveLive(body);
        if (name.equals("Blender Open Movies")) loadBlenderOfficial(body);
        back.requestFocusFromTouch();
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
        card.addView(image, new LinearLayout.LayoutParams(170, -1));
        Button button = new Button(this);
        button.setText("▶  " + movie.title + " (" + movie.year + ")\nCC BY 3.0 · Reproducir");
        button.setTextSize(18); button.setTextColor(Color.WHITE);
        button.setAllCaps(false); button.setFocusable(true);
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
                conn = (HttpURLConnection)new URL(movie.thumbnail).openConnection();
                conn.setConnectTimeout(7000); conn.setReadTimeout(10000);
                if (conn.getResponseCode() != 200) return;
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) runOnUiThread(() -> image.setImageBitmap(bitmap));
                }
            } catch (Exception error) {
                android.util.Log.w("VideoPropia", "Miniatura no disponible: " + movie.id);
            } finally { if (conn != null) conn.disconnect(); }
        }, "ArchiveImage").start();
    }
    private void loadArchiveLive(LinearLayout body) {
        showStatus("Consultando catálogo de Internet Archive...");
        new Thread(() -> {
            try {
                java.util.List<InternetArchiveProvider.Movie> movies = InternetArchiveProvider.fetchCurated();
                runOnUiThread(() -> {
                    if (!page.equals("channel") || !channel.equals("Internet Archive")) return;
                    showStatus("Catálogo online: " + movies.size() + " películas CC BY verificadas.");
                    for (InternetArchiveProvider.Movie movie : movies) archiveMovieCard(body, movie);
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
                if (!page.equals("channel") || !channel.equals("Blender Open Movies")) return;
                showStatus("Blender: " + films.size() + " enlaces oficiales verificados (HEAD).");
                for (BlenderOpenMoviesProvider.Film film : films) {
                    item("▶  " + film.title + " (" + film.year + ") | " + film.kind
                        + " · " + film.format, body,
                        () -> playMovie(film.title, film.url, "Blender Open Movies"));
                }
                if (films.isEmpty()) showStatus("No se verificaron videos oficiales disponibles en este momento.");
            });
        }, "BlenderOfficial").start();
    }
    private void showStatus(String value) {
        if (status != null) status.setText(value);
    }
    private void playDemo() { playMovie("Big Buck Bunny", DEMO_URL, "Demo"); }
    private void playMovie(String title, String url, String origin) {
        playingChannel = origin;
        page = "playing";
        LinearLayout body = newPage(origin, title,
            "Muestra autorizada de reproducción · Usá OK para mostrar controles.");
        status = text("Cargando video...", 18, Color.WHITE);
        if (origin.equals("Demo")) {
            video = new VideoView(this);
            body.addView(video, new LinearLayout.LayoutParams(-1, 620));
            MediaController controls = new MediaController(this);
            controls.setAnchorView(video);
            video.setMediaController(controls);
            body.addView(status);
            video.setOnPreparedListener(mp -> showStatus("Reproduciendo demostración."));
            video.setOnErrorListener((mp, what, extra) -> {
                showStatus("Error de reproducción: " + what); return true;
            });
            video.setOnCompletionListener(mp -> showStatus("Demostración finalizada."));
            video.setVideoURI(Uri.parse(url));
            video.start();
        } else {
            PlayerView view = new PlayerView(this);
            view.setUseController(true);
            body.addView(view, new LinearLayout.LayoutParams(-1, 620));
            body.addView(status);
            externalPlayer = new ExoPlayer.Builder(this).build();
            view.setPlayer(externalPlayer);
            externalPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) showStatus(externalPlayer != null && externalPlayer.getPlayWhenReady() ? "Video preparado. Reproduciendo." : "Pausado.");
                    else if (state == Player.STATE_ENDED) showStatus("Película finalizada.");
                    else if (state == Player.STATE_BUFFERING) showStatus("Cargando datos de video...");
                }
                @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                        Player.PositionInfo newPosition, int reason) {
                    updateMediaState();
                }
                @Override public void onPlayWhenReadyChanged(boolean ready, int reason) {
                    updateMediaState();
                    showStatus(ready ? (externalPlayer.getPlaybackState() == Player.STATE_READY
                        ? "Video preparado. Reproduciendo." : "Cargando datos de video...") : "Pausado.");
                    android.util.Log.i("VideoPropia", "playWhenReady=" + ready + " reason=" + reason
                        + " position=" + externalPlayer.getCurrentPosition()
                        + " state=" + externalPlayer.getPlaybackState()
                        + " playing=" + externalPlayer.isPlaying());
                }
                @Override public void onIsPlayingChanged(boolean playing) {
                    updateMediaState();
                    if (externalPlayer != null && externalPlayer.getPlaybackState() == Player.STATE_READY)
                        showStatus(playing ? "Video preparado. Reproduciendo." : "Pausado.");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    showStatus("Error Media3: " + error.getErrorCodeName());
                }
            });
            externalPlayer.setMediaItem(MediaItem.fromUri(url));
            startMediaSession();
            externalPlayer.prepare();
            externalPlayer.play();

            view.requestFocusFromTouch();
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadChannels();
        loadCatalog();
        loadAudit();
        home();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (externalPlayer != null && page.equals("playing")) {
            int key = event.getKeyCode();
            boolean media = key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PAUSE
                || key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key == KeyEvent.KEYCODE_MEDIA_REWIND;
            if (media) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                        if (externalPlayer.getPlayWhenReady()) externalPlayer.pause();
                        else externalPlayer.play();
                    } else if (key == KeyEvent.KEYCODE_MEDIA_PLAY) externalPlayer.play();
                    else if (key == KeyEvent.KEYCODE_MEDIA_PAUSE) externalPlayer.pause();
                    else if (key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) externalPlayer.seekForward();
                    else externalPlayer.seekBack();
                    updateMediaState();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onPause() {
        android.util.Log.i("VideoPropia", "onPause");
        if (video != null) video.pause();
        if (externalPlayer != null) externalPlayer.pause();
        super.onPause();
    }
    @Override protected void onStop() {
        if (video != null) video.pause();
        if (externalPlayer != null) externalPlayer.pause();
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (video != null) { video.stopPlayback(); video = null; }
        if (externalPlayer != null) { externalPlayer.release(); externalPlayer = null; }
        releaseMediaSession();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (page.equals("playing")) {
            if (video != null) { video.stopPlayback(); video = null; }
            if (externalPlayer != null) { externalPlayer.release(); externalPlayer = null; }
            releaseMediaSession();
            openChannel(playingChannel);
        } else if (page.equals("channel")) home();
        else super.onBackPressed();
    }
}
