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
    private String page = "home";
    private String channel = "";
    private String playingChannel = "Demo";
    private JSONArray channels = new JSONArray();
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
        first.requestFocus();
        pageScroll.post(() -> { first.requestFocus(); pageScroll.scrollTo(0, 0); });
    }
    private void openChannel(String name) {
        page = "channel"; channel = name;
        String sub = "Canal independiente · Volvé con ATRÁS.";
        if (!name.equals("Demo") && !name.equals("Blender Open Movies")) sub = descriptionFor(name);
        if (name.equals("Blender Open Movies")) sub = "Cine abierto de Blender Foundation. Comprobamos acceso HTTP; falta confirmar reproducción.";
        if (name.equals("Internet Archive")) sub = "Biblioteca abierta de prueba. Archivo MP4 completo de Big Buck Bunny; reproducibilidad por comprobar.";
        if (name.equals("CineHax")) sub =
            "Matrix con audio latino confirmada en Nuvio. En nuestra app sigue sin integrar.";
        if (name.equals("SoloLatino")) sub =
            "Catálogo investigado; sin reproducción propia confirmada.";
        if (name.equals("Mis nubes")) sub =
            "Futura importación de bibliotecas personales autorizadas.";
        if (name.equals("Estado")) sub =
            channels.length() + " candidatos documentados; 0 conectores externos habilitados.";
        LinearLayout body = newPage(name, name, sub);
        Button back = item("←  Volver a todos los canales", body, this::home);
        if (name.equals("Internet Archive")) {
            item("▶  Big Buck Bunny (2008)  |  Película completa 720p", body,
                () -> playMovie("Big Buck Bunny · Completa", "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4", "Internet Archive"));
        } else if (name.equals("Blender Open Movies")) {
            item("▶  Elephants Dream (2006)  |  Video 480p", body,
                () -> playMovie("Elephants Dream", "https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov", "Blender Open Movies"));
            item("▶  Big Buck Bunny (2008)  |  720p completa", body,
                () -> playMovie("Big Buck Bunny · Completa", "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4", "Blender Open Movies"));
        } else if (name.equals("Demo")) {
            item("🎞  Big Buck Bunny (2008)  |  Reproducir muestra", body, this::playDemo);
            item("ⓘ  Fuente: video de muestra autorizado (MP4)", body,
                () -> showStatus("Demo: video independiente de los proveedores pendientes."));
        } else {
            item("No disponible todavía  ·  Sin acceso a contenido", body,
                () -> showStatus("Este canal no está conectado. No se realizarán consultas externas."));
        }
        status = text("", 17, Color.LTGRAY);
        body.addView(status);
        back.requestFocus();
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
                    if (state == Player.STATE_READY) showStatus("Video preparado. Reproduciendo.");
                    else if (state == Player.STATE_ENDED) showStatus("Película finalizada.");
                    else if (state == Player.STATE_BUFFERING) showStatus("Cargando datos de video...");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    showStatus("Error Media3: " + error.errorCodeName);
                }
            });
            externalPlayer.setMediaItem(MediaItem.fromUri(url));
            externalPlayer.prepare();
            externalPlayer.play();
            view.requestFocus();
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadChannels();
        home();
    }
    @Override public void onBackPressed() {
        if (page.equals("playing")) {
            if (video != null) { video.stopPlayback(); video = null; }
            if (externalPlayer != null) { externalPlayer.release(); externalPlayer = null; }
            openChannel(playingChannel);
        } else if (page.equals("channel")) home();
        else super.onBackPressed();
    }
}
