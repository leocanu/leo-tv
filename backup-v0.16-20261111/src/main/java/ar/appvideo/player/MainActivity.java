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
    private TextView heroEyebrow, heroTitle, heroBadge, heroSynopsis;
    private VideoView video;
    private ExoPlayer externalPlayer;
    private LinearLayout playbackBody;
    private android.view.View playbackView;
    private Button fullscreenButton;
    private boolean fullscreen;
    private android.media.session.MediaSession mediaSession;
    private void updateMediaState() {
        if (mediaSession == null || externalPlayer == null) return;
        long actions = android.media.session.PlaybackState.ACTION_PLAY
            | android.media.session.PlaybackState.ACTION_PAUSE
            | android.media.session.PlaybackState.ACTION_PLAY_PAUSE
            | android.media.session.PlaybackState.ACTION_SEEK_TO
            | android.media.session.PlaybackState.ACTION_FAST_FORWARD
            | android.media.session.PlaybackState.ACTION_REWIND;
        int playerState=externalPlayer.getPlaybackState();
        int mediaState=playerState==Player.STATE_ENDED
            ? android.media.session.PlaybackState.STATE_STOPPED
            : playerState==Player.STATE_BUFFERING
                ? android.media.session.PlaybackState.STATE_BUFFERING
                : externalPlayer.isPlaying()
                    ? android.media.session.PlaybackState.STATE_PLAYING
                    : android.media.session.PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setActions(actions)
            .setState(mediaState,externalPlayer.getCurrentPosition(),
                externalPlayer.isPlaying() ? 1f : 0f)
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
    private int dp(int dps) {
        return Math.round(dps * getResources().getDisplayMetrics().density);
    }
    private void line(String s, int size, int color) {
        TextView t = text(s, size, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 7, 0, 14);
        root.addView(t, lp);
    }
    private Button item(String title, LinearLayout target, Runnable run) {
        Button b = new Button(this);
        b.setText(title); b.setTextSize(16); b.setMaxLines(2);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END); b.setTextColor(Color.WHITE);
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
        b.setText(name + "\n" + state);
        b.setTextSize(15);
        b.setMaxLines(3); b.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
                selected ? Color.rgb(212,175,55) : Color.rgb(68, 79, 111)));
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
    private void showM3uSources() {
        page = "m3u_sources";
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(16, 20, 26));

        LinearLayout side = buildSidebar("tv");
        root.addView(side, new LinearLayout.LayoutParams(dp(88), -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(24), dp(18), dp(24), dp(14));
        root.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        TextView heading = text("📺  TV en Vivo y Deportes en Directo", 24, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(4));
        main.addView(heading);

        TextView sub = text("Señales abiertas de Argentina, transmisiones deportivas, noticias 24/7 y series infantiles.", 14, Color.rgb(190, 200, 218));
        sub.setPadding(0, 0, 0, dp(14));
        main.addView(sub);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        pageScroll = scroll;
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(32));
        scroll.addView(body);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        body.addView(grid);

        addLiveTvCategoryCard(grid, "⚽  Deportes y Eventos en Vivo", "Agenda de transmisiones y partidos en directo", "⚽", Color.rgb(210, 30, 30),
            () -> showM3uList("TDTChannels Deportes", "https://www.tdtchannels.com/lists/tv.m3u8", true));

        addLiveTvCategoryCard(grid, "🇦🇷  Argentina · Canales de Aire", "Telefe, El Trece, América, TV Pública, TN, C5N", "🇦🇷", Color.rgb(46, 117, 230),
            () -> showM3uList("Mametchi Argentina", "https://www.m3u.cl/lista/AR.m3u", true));

        addLiveTvCategoryCard(grid, "📺  TDTChannels TV en Español", "Emisiones abiertas con calidad HD y sonido 5.1", "📺", Color.rgb(130, 90, 240),
            () -> showM3uList("TDTChannels TV", "https://www.tdtchannels.com/lists/tv.m3u8", true));

        addLiveTvCategoryCard(grid, "👶  Mametchi · Dibujos Animados", "Series animadas con doblaje latino oficial", "👶", Color.rgb(255, 160, 30),
            () -> showM3uList("Mametchi Dibujos", "https://mametchikitty.github.io/Listas-IPTV/dibujos-animados.m3u", true));

        addLiveTvCategoryCard(grid, "✨  Studio Ghibli · Colección", "Películas y clásicos de Ghibli en audio latino", "✨", Color.rgb(30, 180, 120),
            () -> showM3uList("Mametchi Studio Ghibli", "https://mametchikitty.github.io/Listas-IPTV/studio-ghibli-latino.m3u", true));

        addLiveTvCategoryCard(grid, "🌐  Directorio Hispano Abierto", "Cientos de señales abiertas de toda Latinoamérica", "🌐", Color.rgb(70, 130, 180),
            () -> showM3uList("IPTV-org Español", "https://iptv-org.github.io/iptv/languages/spa.m3u", true));

        setContentView(root);
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private void addLiveTvCategoryCard(GridLayout grid, String title, String subtitle, String icon, int accentColor, Runnable onClick) {
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

    private void renderM3uChannelsGrid(LinearLayout body, java.util.List<String[]> entries) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        body.addView(grid);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cardW = Math.max(dp(280), (dm.widthPixels - dp(180)) / 2);

        for (String[] e : entries) {
            final String chTitle = e[0];
            final String chGroup = e[1];
            final String chUrl = e[2];

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(12), dp(16), dp(12));
            card.setFocusable(true);
            card.setClickable(true);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(22, 26, 36));
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.rgb(40, 48, 64));
            card.setBackground(bg);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardW;
            lp.height = dp(76);
            lp.setMargins(0, 0, dp(12), dp(10));
            card.setLayoutParams(lp);

            TextView chIcon = text("📺", 22, Color.WHITE);
            chIcon.setPadding(0, 0, dp(12), 0);
            card.addView(chIcon);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_VERTICAL);

            TextView t = text(chTitle, 15, Color.WHITE);
            t.setTypeface(null, Typeface.BOLD);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(t);

            TextView g = text(chGroup, 12, Color.rgb(245, 197, 66));
            g.setSingleLine(true);
            col.addView(g);

            card.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

            TextView playBadge = text("▶ VER", 10, Color.WHITE);
            playBadge.setTypeface(null, Typeface.BOLD);
            playBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
            GradientDrawable pb = new GradientDrawable();
            pb.setColor(Color.rgb(210, 30, 30));
            pb.setCornerRadius(dp(6));
            playBadge.setBackground(pb);
            card.addView(playBadge);

            card.setOnClickListener(v -> playMovie(chTitle, chUrl, "TV M3U"));
            card.setOnFocusChangeListener((v, f) -> {
                GradientDrawable gBg = new GradientDrawable();
                gBg.setColor(f ? Color.rgb(36, 44, 64) : Color.rgb(22, 26, 36));
                gBg.setCornerRadius(dp(12));
                gBg.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : Color.rgb(40, 48, 64));
                card.setBackground(gBg);
                card.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(120).start();
            });
            grid.addView(card);
        }
    }

    private void showM3uList(String name, String sourceUrl, boolean allowPlay) {
        lastM3uName=name; lastM3uUrl=sourceUrl; lastM3uPlayable=true;
        page="m3u_list";
        LinearLayout body = newPage("TV en Vivo", name,
            "Selecciona cualquier canal para reproducir en directo con audio y video fluido.");
        item("←  Volver a categorías de TV", body, this::showM3uSources);
        TextView progress=text("Cargando señales...",17,Color.LTGRAY);
        body.addView(progress);
        new Thread(() -> {
            java.util.ArrayList<String[]> entries=new java.util.ArrayList<>();
            String failure=null;
            try {
                java.net.URL endpoint=new java.net.URL(sourceUrl);
                java.net.HttpURLConnection c=(java.net.HttpURLConnection)endpoint.openConnection();
                c.setConnectTimeout(9000); c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent","VideoPropia-TV/0.3");
                c.setInstanceFollowRedirects(true);
                try (java.io.BufferedReader r=new java.io.BufferedReader(
                    new java.io.InputStreamReader(c.getInputStream(),"UTF-8"))) {
                    String line,title=null,group="";int scanned=0;
                    while((line=r.readLine())!=null && scanned++<24000 && entries.size()<90) {
                        line=line.trim();
                        if(line.startsWith("#EXTINF")) {
                            int comma=line.lastIndexOf(',');
                            title=comma>=0?line.substring(comma+1).trim():"Sin titulo";
                            java.util.regex.Matcher m=java.util.regex.Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                            group=m.find()?m.group(1):"Otros";
                        } else if (title!=null && line.startsWith("https://")) {
                            entries.add(new String[]{title,group,line});
                            title=null;
                        } else if (!line.isEmpty() && !line.startsWith("#")) title=null;
                    }
                } finally { c.disconnect(); }
            } catch(Exception ex) {failure=ex.getClass().getSimpleName()+": "+ex.getMessage();}
            final String error=failure;
            runOnUiThread(() -> {
                if(!page.equals("m3u_list") || !lastM3uName.equals(name)) return;
                progress.setText(error!=null?"No se pudo consultar: "+error:
                    "Canales disponibles (" + entries.size() + "). Tocá cualquier señal para reproducir en pantalla completa:");
                if (entries.isEmpty() && error == null) {
                    body.addView(text("No se hallaron canales en este listado.", 15, Color.GRAY));
                } else {
                    renderM3uChannelsGrid(body, entries);
                }
            });
        },"m3u-catalog").start();
    }

    private String activeProvider = "CineHax";
    private String activeTab = "all";

    private static final class PosterItem {
        final String title, tag, posterUrl, providerName, queryHint;
        final int year;
        final boolean isSeries;
        final CatalogData.Entry rawEntry;
        PosterItem(String t, int y, boolean series, String poster, String prov, String q, CatalogData.Entry entry) {
            title = t; year = y; isSeries = series;
            tag = series ? "HD   TV" : ("HD   " + (y > 0 ? y : 2026));
            posterUrl = poster; providerName = prov; queryHint = q; rawEntry = entry;
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

    private Button addProviderTile(GridLayout grid, String label, String style, String shortText, int accent, Runnable action) {
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
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
        if (resolutionTask != null) resolutionTask.cancel(true);
        ++providerPlayRequest; providerLoading = false;
        page = "sources"; channel = "";

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

        addProviderTile(grid, "ARGENTINA\nSOLO CANALES", "tv", "TV", Color.WHITE,
            () -> showM3uList("Mametchi Argentina", "https://www.m3u.cl/lista/AR.m3u", false));

        addProviderTile(grid, "CANALES DE\nTV Y CINE\nRECOMENDADO", "tv", "TV", Color.WHITE,
            this::showM3uSources);

        addProviderTile(grid, "TDTCHANNELS\nTV EN ESPAÑOL", "tv", "TV", Color.WHITE,
            () -> showM3uList("TDTChannels TV", "https://www.tdtchannels.com/lists/tv.m3u8", true));

        addProviderTile(grid, "NOVELAS TURCAS\nY ASIÁTICAS", "icon", "🇹🇷", Color.rgb(220, 50, 60),
            () -> { activeProvider = "CineHax"; activeTab = "series"; home(); });

        addProviderTile(grid, "INFANTILES\nAUDIO LATINO", "icon", "🧸", Color.rgb(255, 180, 50),
            () -> { activeProvider = "CineHax"; activeTab = "kids"; home(); });

        addProviderTile(grid, "PELICULAS Y\nSERIES 3 (CINEHAX)", "netflix_bar", "LATINO HD", Color.rgb(220, 30, 40),
            () -> { activeProvider = "CineHax"; activeTab = "all"; home(); });

        addProviderTile(grid, "RECOMENDADO\nPARA PELÍCULAS\nY SERIES", "reel", "MOVIE", Color.WHITE,
            () -> { activeProvider = "CineHax"; activeTab = "all"; home(); });

        addProviderTile(grid, "PelisflixHD\n(Seriesflix)", "pelisflix", "P", Color.WHITE,
            () -> openChannel("Seriesflix"));

        addProviderTile(grid, "PoseidonHD2 /\nLaMovie", "poseidon", "P2", Color.WHITE,
            () -> { activeProvider = "LaMovie"; activeTab = "all"; home(); });

        addProviderTile(grid, "Canela TV /\nLaMovie Catálogo", "canela", "C", Color.WHITE,
            () -> openChannel("LaMovie"));

        addProviderTile(grid, "CineCalidad", "cinecalidad", "CC", Color.WHITE,
            () -> openChannel("CineCalidad"));

        addProviderTile(grid, "JKAnime /\nLatAnime", "jkanime", "jK", Color.rgb(250, 178, 22),
            () -> { activeProvider = "LatAnime"; activeTab = "anime"; home(); });

        addProviderTile(grid, "CINE ABIERTO\nBLENDER HD", "reel", "OPEN", Color.WHITE,
            () -> openChannel("Blender Open Movies"));

        addProviderTile(grid, "INTERNET ARCHIVE\nCLÁSICOS", "reel", "CC BY", Color.WHITE,
            () -> openChannel("Internet Archive"));

        addProviderTile(grid, "ESTADO DE\nLAS 32 FUENTES", "icon", "📊", Color.rgb(212, 175, 55),
            () -> openChannel("Estado"));

        // Todas las 32 fuentes del inventario original de la app (channels.json)
        for (int i = 0; i < channels.length(); i++) {
            JSONObject entry = channels.optJSONObject(i);
            if (entry == null) continue;
            final String name = entry.optString("name", "Fuente");
            if (name.equals("CineCalidad")) continue;
            String shortTag = name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "TV";
            String style = tvAnime(name) ? "jkanime" : (tvSeries(name) ? "pelisflix" : "reel");
            addProviderTile(grid, name + "\n(" + tvBadge(name).toUpperCase() + ")", style, shortTag, Color.rgb(212, 175, 55),
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

        TextView ubText = text("🚀  Buscar actualizaciones en GitHub  ·  v" + AppUpdater.getCurrentVersionName(this), 14, Color.WHITE);
        ubText.setTypeface(null, Typeface.BOLD);
        updateBanner.addView(ubText);

        updateBanner.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(129, 107, 245) : Color.rgb(32, 38, 54));
            g.setCornerRadius(dp(12));
            if (f) g.setStroke(dp(2), Color.WHITE);
            updateBanner.setBackground(g);
            updateBanner.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });

        updateBanner.setOnClickListener(v -> {
            android.widget.Toast.makeText(this, "Comprobando actualizaciones en GitHub...", android.widget.Toast.LENGTH_SHORT).show();
            AppUpdater.check(this, new AppUpdater.CheckCallback() {
                @Override public void onUpdateAvailable(AppUpdater.UpdateInfo info) {
                    AppUpdater.showUpdateDialog(MainActivity.this, info);
                }
                @Override public void onNoUpdate() {
                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "¡Ya tenés la versión más reciente (v" + AppUpdater.getCurrentVersionName(MainActivity.this) + ")!", android.widget.Toast.LENGTH_LONG).show());
                }
                @Override public void onError(String msg) {
                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "No se pudo consultar GitHub: " + msg, android.widget.Toast.LENGTH_SHORT).show());
                }
            });
        });

        setContentView(root);
        grid.post(() -> {
            if (grid.getChildCount() > 5) grid.getChildAt(5).requestFocus();
            else if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
        });
    }

    private GradientDrawable tvShape(int fill, int border, int corner) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(corner); d.setStroke(2,border);
        return d;
    }
    private Button tvAction(String label, String detail, Runnable action, int width, int height) {
        Button b=new Button(this);
        String textValue=label+(detail.isEmpty()?"":"\n"+detail);
        android.text.SpannableString styled=new android.text.SpannableString(textValue);
        if(!detail.isEmpty()){
            int start=label.length()+1;
            styled.setSpan(new android.text.style.RelativeSizeSpan(0.78f),
                start,textValue.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(177,194,210)),
                start,textValue.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        styled.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
            0,label.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setText(styled);
        b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        b.setPadding(17,9,14,9);b.setTextSize(15);b.setMaxLines(3);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setTextColor(Color.WHITE);
        b.setFocusable(true);
        b.setBackground(tvShape(Color.rgb(31,41,64),Color.rgb(64,78,110),20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,height);
        lp.setMargins(0,0,15,12);b.setLayoutParams(lp);
        b.setOnClickListener(v->action.run());
        b.setOnFocusChangeListener((v,selected)->{
            b.setBackground(tvShape(selected?Color.rgb(68,88,155):Color.rgb(31,41,64),
                selected?Color.rgb(212,175,55):Color.rgb(64,78,110),20));
            b.animate().scaleX(selected?1.08f:1f)
                .scaleY(selected?1.08f:1f).setDuration(150).start();
            b.setElevation(selected?12f:0f);
            if (selected && heroTitle != null) updateHero(label);
        });
        return b;
    }
    private void tvNav(LinearLayout side, String label, Runnable action) {
        Button b=tvAction(label,"",action,-1,60);
        b.setTextSize(14);b.setPadding(14,5,8,5); b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        side.addView(b);
    }
    private boolean tvSeries(String name) {
        return name.equals("SeriesMetro") || name.equals("Seriesflix") || name.equals("DoramasFlix")
            || name.equals("AresHD") || name.equals("DeTodo Peliculas") || name.equals("CineHax+");
    }
    private boolean tvAnime(String name) {
        return name.equals("TioAnime")||name.equals("LatAnime")||name.equals("AnimeAV1")
            ||name.equals("JKAnime")||name.equals("MonosChinos")
            ||name.equals("MundoDonghua")||name.equals("VerAni.me")
            ||name.equals("VerAnimes")||name.equals("BeatZ Anime");
    }
    private String tvBadge(String name) {
        if(name.equals("Demo"))return "Muestra de video";
        if(name.equals("Blender Open Movies")||name.equals("Internet Archive"))return "Videos autorizados";
        for(int i=0;i<channels.length();i++){
            JSONObject entry=channels.optJSONObject(i);
            if(entry==null||!name.equals(entry.optString("name")))continue;
            String state=entry.optString("status");
            if("previously_playback_tested".equals(state))return "Reproducción probada";
            if("catalog_and_episodes_tested_playback_pending".equals(state))return "Anime · video pendiente";
            if(entry.optBoolean("enabled",false))return "Adaptador en revisión";
            return "No integrado";
        }
        return "Explorar";
    }
    private void tvRow(LinearLayout body,String heading,String subtitle,String[] names) {
        TextView title=text(heading,22,Color.WHITE);title.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);
        tlp.setMargins(0,25,0,3);body.addView(title,tlp);
        TextView hint=text(subtitle,13,Color.rgb(173,184,206));
        body.addView(hint);
        android.widget.HorizontalScrollView scroller=new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(14,14,30,12);
        for(String name:names){
            Button card=tvAction(name,tvBadge(name),()->openChannel(name),236,132);
            rail.addView(card);
        }
        scroller.addView(rail);body.addView(scroller);
    }
    private void tvSection(String group) {
        page="section";channel="";
        String title = group.equals("anime") ? "Anime (Latino y Sub)" :
            group.equals("series") ? "Series (Audio Latino)" : "Películas (Audio Latino)";
        String subtitle = group.equals("anime") ? "Series de animación japonesa con doblaje latino y versiones subtituladas." :
            group.equals("series") ? "Series de televisión, temporadas y capítulos completos con audio latino." :
            "Catálogos de cine y estrenos con preferencia de audio latino.";
        LinearLayout body=newPage(title,title,subtitle);
        Button back=item("← Volver al inicio",body,this::home);
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(4);
        body.addView(grid);
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int width=Math.max(210,(dm.widthPixels-145)/4-18);
        for(int i=0;i<channels.length();i++){
            JSONObject entry=channels.optJSONObject(i);
            if(entry==null||!entry.optBoolean("enabled",false))continue;
            String name=entry.optString("name");
            if (group.equals("anime") && !tvAnime(name)) continue;
            if (group.equals("series") && (!tvSeries(name) && !name.equals("CineHax") && !name.equals("Pelispedia"))) continue;
            if (group.equals("movies") && (tvAnime(name) || (tvSeries(name) && !name.equals("DeTodo Peliculas")))) continue;
            Button b=tile(name,tvBadge(name),grid,width,()->openChannel(name));
        }
        back.requestFocus();
    }

    private TextView addSideIcon(LinearLayout side, String symbol, boolean current, String tooltip, Runnable action) {
        TextView btn = new TextView(this);
        btn.setText(symbol);
        btn.setTextSize(20);
        btn.setTextColor(current ? Color.WHITE : Color.rgb(185, 195, 212));
        btn.setGravity(Gravity.CENTER);
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setContentDescription(tooltip);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(current ? Color.rgb(52, 58, 70) : Color.TRANSPARENT);
        normal.setCornerRadius(12);
        btn.setBackground(normal);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(54, 52);
        lp.setMargins(0, 6, 0, 6);
        side.addView(btn, lp);

        btn.setOnClickListener(v -> action.run());
        btn.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(focused ? Color.rgb(68, 76, 94) : (current ? Color.rgb(52, 58, 70) : Color.TRANSPARENT));
            bg.setCornerRadius(12);
            if (focused) bg.setStroke(2, Color.WHITE);
            btn.setBackground(bg);
            btn.setTextColor(Color.WHITE);
            btn.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(120).start();
        });
        return btn;
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

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(196, -2);
        cardLp.setMargins(0, 0, 16, 8);
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

        card.addView(posterWrap, new LinearLayout.LayoutParams(-1, 260));

        TextView meta = text(item.tag, 12, Color.rgb(168, 178, 195));
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
            if (focused) fBg.setStroke(4, Color.WHITE);
            card.setBackground(fBg);
            card.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(130).start();
            card.setElevation(focused ? 10f : 0f);
        });

        card.setOnClickListener(v -> onPosterClicked(item));
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

        TextView meta = text(item.tag, 11, Color.rgb(245, 197, 66));
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
            if (focused) fBg.setStroke(3, Color.WHITE);
            wrap.setBackground(fBg);
            wrap.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(130).start();
            numberView.setTextColor(focused ? Color.rgb(245, 197, 66) : Color.rgb(240, 240, 240));
        });

        wrap.setOnClickListener(v -> onPosterClicked(item));
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

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(6, 4, 28, 10);
        int rank = 1;
        for (PosterItem it : items) {
            rail.addView(createTop10PosterCard(it, rank++));
            if (rank > 10) break;
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
        if (item.rawEntry != null) {
            openEntryDetail(item.rawEntry);
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

    private LinearLayout addPosterRow(LinearLayout body, String heading, java.util.List<PosterItem> initialItems) {
        TextView title = text(heading, 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.setMargins(8, 18, 0, 10);
        body.addView(title, tlp);

        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setPadding(6, 4, 28, 10);
        for (PosterItem it : initialItems) {
            rail.addView(createPosterCard(it));
        }
        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private void populateLiveRow(LinearLayout rail, String providerName, String kind, String query, int pageNum, int offset, boolean isPrimaryRow) {
        VideoProvider prov = VideoProviders.find(providerName);
        if (prov == null) return;
        new Thread(() -> {
            try {
                CatalogData.CatalogPage res = prov.catalog(kind, query, pageNum);
                if (res == null || res.entries.isEmpty()) return;
                java.util.List<PosterItem> live = new java.util.ArrayList<>();
                for (int i = offset; i < res.entries.size() && live.size() < 12; i++) {
                    CatalogData.Entry e = res.entries.get(i);
                    live.add(new PosterItem(
                        e.title,
                        e.year > 0 ? e.year : 2026,
                        !e.isMovie(),
                        e.poster,
                        providerName,
                        e.title,
                        e
                    ));
                }
                if (live.isEmpty()) return;
                runOnUiThread(() -> {
                    if (!page.equals("home") || isDestroyed()) return;
                    boolean hadFocus = rail.hasFocus();
                    rail.removeAllViews();
                    for (PosterItem item : live) {
                        rail.addView(createPosterCard(item));
                    }
                    if ((hadFocus || isPrimaryRow) && rail.getChildCount() > 0) {
                        rail.getChildAt(0).requestFocus();
                    }
                });
            } catch (Exception ignored) {}
        }, "live-row-" + providerName + "-" + kind + "-" + pageNum).start();
    }

    private java.util.List<PosterItem> defaultMovies2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Colisión", 2026, false, "https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", prov, "Colision", null),
            new PosterItem("Velocidad y Deseo", 2026, false, "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Drive", null),
            new PosterItem("Contra el huracán", 2026, false, "https://image.tmdb.org/t/p/w342/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg", prov, "Huracan", null),
            new PosterItem("Masterplan", 2026, false, "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Masterplan", null),
            new PosterItem("Tenzing", 2026, false, "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", prov, "Tenzing", null),
            new PosterItem("Sacrificio: Los Elegidos", 2026, false, "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", prov, "Sacrifice", null)
        );
    }

    private java.util.List<PosterItem> defaultSeries2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Lupin", 2026, true, "https://image.tmdb.org/t/p/w342/sgxawbFB5Vi5OkPWQLNfl3dvkNJ.jpg", prov, "Lupin", null),
            new PosterItem("La lista terminal", 2026, true, "https://image.tmdb.org/t/p/w342/71f3JHlJ3G5G0Fp7b3uX2hB8X1B.jpg", prov, "Terminal", null),
            new PosterItem("Cyberpunk: Edgerunners", 2026, true, "https://image.tmdb.org/t/p/w342/7jSWOc6jWSw5hZ78HB8Hw3pJxuk.jpg", prov, "Cyberpunk", null),
            new PosterItem("La mesa final", 2026, true, "https://image.tmdb.org/t/p/w342/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg", prov, "Mesa", null),
            new PosterItem("Tulsa King", 2026, true, "https://image.tmdb.org/t/p/w342/fwTv3RPRAIy0maOMns5eYRRwnDk.jpg", prov, "Tulsa King", null),
            new PosterItem("Crystal Lake", 2026, true, "https://image.tmdb.org/t/p/w342/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", prov, "Crystal", null)
        );
    }

    private java.util.List<PosterItem> defaultAction2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("El Despertar", 2026, false, "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", prov, "Despertar", null),
            new PosterItem("Sacrificio: Los Elegidos", 2026, false, "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", prov, "Sacrifice", null),
            new PosterItem("Animals", 2026, false, "https://image.tmdb.org/t/p/w342/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg", prov, "Animals", null),
            new PosterItem("Matchbox the Movie", 2026, false, "https://image.tmdb.org/t/p/w342/qhb1qOilapbapxWQn9jtRCMwXJF.jpg", prov, "Matchbox", null),
            new PosterItem("Hope: El primer impacto", 2026, false, "https://image.tmdb.org/t/p/w342/gKkl37BQuKTanygYQG1pyYgLVgf.jpg", prov, "Hope", null),
            new PosterItem("Entrega Al Límite", 2026, false, "https://image.tmdb.org/t/p/w342/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg", prov, "Runner", null)
        );
    }

    private java.util.List<PosterItem> defaultComedy2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Ali G: Kién Ez Yo?", 2026, false, "https://image.tmdb.org/t/p/w342/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg", prov, "Ali G", null),
            new PosterItem("Masterplan", 2026, false, "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Masterplan", null),
            new PosterItem("Matchbox the Movie", 2026, false, "https://image.tmdb.org/t/p/w342/qhb1qOilapbapxWQn9jtRCMwXJF.jpg", prov, "Matchbox", null),
            new PosterItem("Shaun, el cordero: El monstruo", 2026, false, "https://image.tmdb.org/t/p/w342/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg", prov, "Shaun", null),
            new PosterItem("Entrega Al Límite", 2026, false, "https://image.tmdb.org/t/p/w342/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg", prov, "Entrega", null)
        );
    }

    private java.util.List<PosterItem> defaultTerror2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Resident Evil: Noche Cero", 2026, false, "https://image.tmdb.org/t/p/w342/ntmaNFJ7O60n45E1E6HZlXeSV5e.jpg", prov, "Resident Evil", null),
            new PosterItem("Apocalipsis Z: Nuevo mundo", 2026, false, "https://image.tmdb.org/t/p/w342/pS2R3DEiucuizVkKP80qzdO97TE.jpg", prov, "Apocalipsis Z", null),
            new PosterItem("El Despertar", 2026, false, "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", prov, "Awakening", null),
            new PosterItem("Backrooms: Sin salida", 2026, false, "https://image.tmdb.org/t/p/w342/3YTb97zytlWRWZb7HrOiEhk4e6f.jpg", prov, "Backrooms", null),
            new PosterItem("La Sustancia", 2024, false, "https://image.tmdb.org/t/p/w342/w1PiIqM89r4AM7CiMEP4VLCEFUn.jpg", prov, "La Sustancia", null)
        );
    }

    private java.util.List<PosterItem> defaultTurkishNovelas(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true, "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov, "Erkenci Kus", null),
            new PosterItem("Kara Sevda (Amor Eterno)", 2015, true, "https://static.tvmaze.com/uploads/images/medium_portrait/34/86603.jpg", prov, "Kara Sevda", null),
            new PosterItem("Love is in the Air (Sen Çal Kapımı)", 2020, true, "https://static.tvmaze.com/uploads/images/medium_portrait/267/668853.jpg", prov, "Sen Cal Kapimi", null),
            new PosterItem("Hercai", 2019, true, "https://static.tvmaze.com/uploads/images/medium_portrait/197/494639.jpg", prov, "Hercai", null),
            new PosterItem("El Turco", 2025, true, "https://image.tmdb.org/t/p/w342/oJ7DyCIxyVQ0GlCmk7oAGSQgnr9.jpg", prov, "El Turco", null),
            new PosterItem("Taşacak Bu Deniz", 2025, true, "https://image.tmdb.org/t/p/w342/uHIOTJXN9nNTc51WyunL43Fvge3.jpg", prov, "Tasacak Bu Deniz", null),
            new PosterItem("Leyla: Hayat... Aşk... Adalet...", 2024, true, "https://image.tmdb.org/t/p/w342/ujZQDNdMbGSWk3I6biCuw2Uf5uO.jpg", prov, "Leyla", null),
            new PosterItem("Yargi (Secretos de Familia)", 2021, true, "https://static.tvmaze.com/uploads/images/medium_portrait/367/918118.jpg", prov, "Yargi", null)
        );
    }

    private java.util.List<PosterItem> defaultKDramas(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("El juego del calamar", 2021, true, "https://image.tmdb.org/t/p/w342/xx3Zurg2mVep7UhXAjeYuetOvsB.jpg", prov, "Juego del calamar", null),
            new PosterItem("Alice in Borderland", 2020, true, "https://image.tmdb.org/t/p/w342/xcOnCOpwe86Deau7P1VQNe0X6cg.jpg", prov, "Alice in Borderland", null),
            new PosterItem("Estamos Muertos", 2022, true, "https://image.tmdb.org/t/p/w342/8jClvC5U756Uhd8zDYek50ihO49.jpg", prov, "Estamos muertos", null),
            new PosterItem("Parásitos", 2019, false, "https://image.tmdb.org/t/p/w342/j4AOjNlhvudYDijiT0lxeHAsPkk.jpg", prov, "Parasitos", null),
            new PosterItem("Dulce hogar", 2020, true, "https://image.tmdb.org/t/p/w342/MIcVR5JVNBjvmd6xW6xnNbory5.jpg", prov, "Dulce hogar", null)
        );
    }

    private java.util.List<PosterItem> defaultSciFi(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Dune: Parte Dos", 2024, false, "https://image.tmdb.org/t/p/w342/8b8R8l88Qje9dn9OE8PY05Nxl1X.jpg", prov, "Dune", null),
            new PosterItem("Interstellar", 2014, false, "https://image.tmdb.org/t/p/w342/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", prov, "Interstellar", null),
            new PosterItem("Cyberpunk: Edgerunners", 2022, true, "https://static.tvmaze.com/uploads/images/medium_portrait/498/1246519.jpg", "LatAnime", "Cyberpunk", null),
            new PosterItem("El Problema de los 3 Cuerpos", 2024, true, "https://image.tmdb.org/t/p/w342/yv0Qv829k7X6dE1i75eK77jU0q.jpg", prov, "3 Cuerpos", null),
            new PosterItem("Alien: Romulus", 2024, false, "https://image.tmdb.org/t/p/w342/8PYqGSd8MOm5ce8io4qNSAiSExW.jpg", prov, "Alien Romulus", null)
        );
    }

    private java.util.List<PosterItem> defaultRomance(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Culpa Mía", 2023, false, "https://image.tmdb.org/t/p/w342/w46Vw536HwNnEzOa7o24Te0JJDb.jpg", prov, "Culpa Mia", null),
            new PosterItem("A Través de mi Ventana", 2022, false, "https://image.tmdb.org/t/p/w342/6gg7fvKc195viCD5jqom4El24vP.jpg", prov, "A traves de mi ventana", null),
            new PosterItem("Fabricante de Lágrimas", 2024, false, "https://image.tmdb.org/t/p/w342/9JBEPL0T2eHNG7FvJ6n5KekjOqL.jpg", prov, "Lagrimas", null),
            new PosterItem("La Idea de Ti", 2024, false, "https://image.tmdb.org/t/p/w342/z1p34vh7dEOnLDmyCrlUVLuoDzd.jpg", prov, "Idea de ti", null)
        );
    }

    private java.util.List<PosterItem> defaultAdults(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("365 días (2020)", 2020, false, "https://image.tmdb.org/t/p/w342/jz8T3hrU6GuMqSuQ4Rbd4MJUeaq.jpg", prov, "365", null),
            new PosterItem("365 días: Aquel Día", 2022, false, "https://image.tmdb.org/t/p/w342/k3J2GdYxhR6U2RfsHZOsmHVKW7m.jpg", prov, "365 dias", null),
            new PosterItem("365 días más", 2022, false, "https://image.tmdb.org/t/p/w342/dvKpJZUxI1Rsgvrl8HHv62CpBBh.jpg", prov, "365 dias", null),
            new PosterItem("Cincuenta Sombras de Grey", 2015, false, "https://image.tmdb.org/t/p/w342/mNZcZOIlTwDKd30xLnRR4p0ZELg.jpg", prov, "Cincuenta sombras", null),
            new PosterItem("Cincuenta Sombras Más Oscuras", 2017, false, "https://image.tmdb.org/t/p/w342/jvBAQOg2ObZKYXZGxYSz3Fkr7Qt.jpg", prov, "Cincuenta sombras", null),
            new PosterItem("Cincuenta Sombras Liberadas", 2018, false, "https://image.tmdb.org/t/p/w342/1J477oH6LxAAqTY4dCa7xTluvJE.jpg", prov, "Cincuenta sombras", null)
        );
    }

    private java.util.List<PosterItem> defaultKids(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Red: Crecer es una Fiera", 2022, false, "https://image.tmdb.org/t/p/w342/qsdjk9oAKSQMWs0Vt5Pyfh6O4GZ.jpg", prov, "Red", null),
            new PosterItem("Supergatitos", 2026, true, "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Superkitties", null),
            new PosterItem("Cars en la Carretera", 2026, true, "https://image.tmdb.org/t/p/w342/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg", prov, "Cars", null),
            new PosterItem("Tadeo el explorador", 2026, false, "https://image.tmdb.org/t/p/w342/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg", prov, "Tadeo", null),
            new PosterItem("Hot Wheels Let's Race", 2026, true, "https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", prov, "Hot Wheels", null)
        );
    }

    private java.util.List<PosterItem> defaultAnime() {
        return java.util.Arrays.asList(
            new PosterItem("Black Clover", 2026, true, "https://image.tmdb.org/t/p/w342/kaMisGG2U2R38L4G5G8xGvL4J0Z.jpg", "LatAnime", "Black Clover", null),
            new PosterItem("Dragon Ball Super", 2026, true, "https://image.tmdb.org/t/p/w342/qEUrbXJ2qt4Rg84Btlx4STOhgte.jpg", "LatAnime", "Dragon Ball", null),
            new PosterItem("Bleach: Thousand-Year", 2026, true, "https://image.tmdb.org/t/p/w342/2EewmxXe72ogD0EaWM8gqa0ccIw.jpg", "LatAnime", "Bleach", null),
            new PosterItem("Seven Deadly Sins", 2026, true, "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", "JKAnime", "Nanatsu", null),
            new PosterItem("Cyberpunk: Edgerunners", 2026, true, "https://image.tmdb.org/t/p/w342/7jSWOc6jWSw5hZ78HB8Hw3pJxuk.jpg", "LatAnime", "Cyberpunk", null)
        );
    }

    private java.util.List<PosterItem> defaultTop10(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true, "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov, "Erkenci Kus", null),
            new PosterItem("Colisión", 2026, false, "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Colision", null),
            new PosterItem("Cyberpunk: Edgerunners", 2026, true, "https://static.tvmaze.com/uploads/images/medium_portrait/498/1246519.jpg", "LatAnime", "Cyberpunk", null),
            new PosterItem("Tulsa King", 2026, true, "https://static.tvmaze.com/uploads/images/medium_portrait/646/1615902.jpg", prov, "Tulsa King", null),
            new PosterItem("Velocidad y Deseo", 2026, false, "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Drive", null),
            new PosterItem("Lupin", 2026, true, "https://static.tvmaze.com/uploads/images/medium_portrait/646/1617244.jpg", prov, "Lupin", null),
            new PosterItem("El Despertar", 2026, false, "https://image.tmdb.org/t/p/w342/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg", prov, "Despertar", null),
            new PosterItem("La lista terminal", 2026, true, "https://image.tmdb.org/t/p/w342/71f3JHlJ3G5G0Fp7b3uX2hB8X1B.jpg", prov, "Terminal", null),
            new PosterItem("Tenzing", 2026, false, "https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", prov, "Tenzing", null),
            new PosterItem("Sacrificio: Los Elegidos", 2026, false, "https://image.tmdb.org/t/p/w342/b33nnKl1GSFbao4l3fZDDqsMx0F.jpg", prov, "Sacrifice", null)
        );
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
                "Erkenci Kuş (Pájaro Soñador)",
                "⭐ 8.8  ·  2018  ·  51 Capítulos  ·  Comedia Romántica / Drama  ·  Audio Latino",
                "Sanem es una joven soñadora que trabaja en la tienda de su padre. Al comenzar en la prestigiosa agencia de Can Divit, su vida cambia entre enredos y un romance inolvidable.",
                "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg",
                new PosterItem("Erkenci Kuş (Pájaro Soñador)", 2018, true, "https://static.tvmaze.com/uploads/images/medium_portrait/159/397844.jpg", prov, "Erkenci Kus", null)
            ),
            new FeaturedHeroItem(
                "Colisión (2026)",
                "⭐ 8.6  ·  2026  ·  115 min  ·  Acción / Suspense  ·  Audio Latino 5.1",
                "Un ex agente de operaciones encubiertas queda atrapado en una emboscada en el corazón de la ciudad y debe proteger un testigo clave a toda costa.",
                "https://image.tmdb.org/t/p/w780/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
                new PosterItem("Colisión", 2026, false, "https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", prov, "Colision", null)
            ),
            new FeaturedHeroItem(
                "Cyberpunk: Edgerunners",
                "⭐ 8.9  ·  2026  ·  10 Capítulos  ·  Anime / Ciencia Ficción  ·  Audio Latino / Sub",
                "En una metrópolis distópica obsesionada con implantes cibernéticos, un joven talento marginado busca sobrevivir convirtiéndose en mercenario cyberpunk.",
                "https://static.tvmaze.com/uploads/images/original_untouched/498/1246519.jpg",
                new PosterItem("Cyberpunk: Edgerunners", 2026, true, "https://static.tvmaze.com/uploads/images/medium_portrait/498/1246519.jpg", "LatAnime", "Cyberpunk", null)
            ),
            new FeaturedHeroItem(
                "Tulsa King",
                "⭐ 8.7  ·  2026  ·  Temporada 2  ·  Crimen / Drama  ·  Sylvester Stallone",
                "Tras cumplir 25 años en prisión, un capo de la mafia de Nueva York es desterrado a Tulsa, Oklahoma, para levantar un inesperado imperio del crimen.",
                "https://static.tvmaze.com/uploads/images/original_untouched/646/1615902.jpg",
                new PosterItem("Tulsa King", 2026, true, "https://static.tvmaze.com/uploads/images/medium_portrait/646/1615902.jpg", prov, "Tulsa King", null)
            ),
            new FeaturedHeroItem(
                "Velocidad y Deseo (2026)",
                "⭐ 8.4  ·  2026  ·  122 min  ·  Acción / Carreras  ·  4K Ultra HD",
                "Pilotos clandestinos compiten en un circuito urbano nocturno de alta tecnología donde la velocidad extrema es la única ley.",
                "https://image.tmdb.org/t/p/w780/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg",
                new PosterItem("Velocidad y Deseo", 2026, false, "https://image.tmdb.org/t/p/w342/cdqLnri3NEGcmfnqwk2TSIYtddg.jpg", prov, "Drive", null)
            )
        );
    }

    private int currentHeroIndex = 0;
    private android.view.View buildHeroSlideShow(LinearLayout body, java.util.List<FeaturedHeroItem> slides) {
        if (slides.isEmpty()) return null;
        if (currentHeroIndex >= slides.size()) currentHeroIndex = 0;

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
                    : new CatalogData.Entry(it.posterItem.title, 0, it.posterItem.posterUrl, it.posterItem.posterUrl, it.posterItem.isSeries ? "serie" : "movie", it.posterItem.year, it.posterItem.providerName);
                ZappCatalogEngine.saveFavorite(this, e);
                android.widget.Toast.makeText(this, "Guardado en Favoritos: " + it.title, android.widget.Toast.LENGTH_SHORT).show();
            });
        };
        updateSlide.run();

        playBtn.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(150, 130, 255) : Color.rgb(129, 107, 245));
            g.setCornerRadius(dp(10));
            if (f) g.setStroke(dp(2), Color.WHITE);
            playBtn.setBackground(g);
            playBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });
        detailBtn.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(58, 68, 92) : Color.rgb(38, 44, 60));
            g.setCornerRadius(dp(10));
            if (f) g.setStroke(dp(2), Color.WHITE);
            detailBtn.setBackground(g);
            detailBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });
        favBtn.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(58, 68, 92) : Color.rgb(38, 44, 60));
            g.setCornerRadius(dp(10));
            if (f) g.setStroke(dp(2), Color.WHITE);
            favBtn.setBackground(g);
            favBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });

        bannerCard.setOnFocusChangeListener((v, f) -> {
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

    private static class LiveSportEvent {
        final String title;
        final String tournament;
        final String timeStatus;
        final boolean isLiveNow;
        final String streamUrl;
        final String icon;

        LiveSportEvent(String title, String tournament, String timeStatus, boolean isLiveNow, String streamUrl, String icon) {
            this.title = title;
            this.tournament = tournament;
            this.timeStatus = timeStatus;
            this.isLiveNow = isLiveNow;
            this.streamUrl = streamUrl;
            this.icon = icon;
        }
    }

    private java.util.List<LiveSportEvent> defaultSportsEvents() {
        return java.util.Arrays.asList(
            new LiveSportEvent("Real Madrid vs Barcelona · El Clásico", "LaLiga EA Sports · Señal 1080p HD", "🔴 EN VIVO", true, "https://www.tdtchannels.com/lists/tv.m3u8", "⚽"),
            new LiveSportEvent("Boca Juniors vs River Plate · Superclásico", "Liga Profesional · TNT Sports / ESPN Premium", "🔴 EN VIVO", true, "https://www.tdtchannels.com/lists/tv.m3u8", "⚽"),
            new LiveSportEvent("Manchester City vs Liverpool", "Premier League · ESPN HD", "🕒 Hoy 17:00", false, "https://www.tdtchannels.com/lists/tv.m3u8", "⚽"),
            new LiveSportEvent("Fórmula 1 · Gran Premio Clasificación", "F1 TV Pro / Fox Sports · Pista Principal", "🕒 Fin de Semana", false, "https://www.tdtchannels.com/lists/tv.m3u8", "🏎️"),
            new LiveSportEvent("NBA: Los Angeles Lakers vs Golden State Warriors", "NBA Live League Pass · ESPN 2", "🕒 Hoy 21:30", false, "https://www.tdtchannels.com/lists/tv.m3u8", "🏀"),
            new LiveSportEvent("HydraHD Sports · Canales Multicámara HD", "HydraHD Sports Network · Transmisión En Vivo", "🔴 EN VIVO", true, "https://www.tdtchannels.com/lists/tv.m3u8", "📡")
        );
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
                g.setStroke(dp(f ? 2 : 1), f ? Color.WHITE : (ev.isLiveNow ? Color.rgb(235, 60, 60) : Color.rgb(44, 52, 70)));
                card.setBackground(g);
                card.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
            });

            card.setOnClickListener(v -> {
                showM3uList("TDTChannels TV", "https://www.tdtchannels.com/lists/tv.m3u8", true);
            });

            rail.addView(card);
        }

        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private boolean globalSearchEnabled = true;
    private String lastLunifiedQuery = "";
    private int searchGeneration = 0;

    private LinearLayout buildSidebar(String currentSection) {
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        side.setPadding(8, 14, 8, 14);
        side.setBackgroundColor(Color.rgb(12, 15, 20));

        // Emblema superior del proveedor activo + logo Leo TV (clic cambia proveedor como en Lunified)
        LinearLayout topBrand = new LinearLayout(this);
        topBrand.setOrientation(LinearLayout.VERTICAL);
        topBrand.setGravity(Gravity.CENTER);
        topBrand.setFocusable(true);
        topBrand.setClickable(true);
        GradientDrawable brandBg = new GradientDrawable();
        brandBg.setColor(Color.rgb(22, 26, 36));
        brandBg.setCornerRadius(12);
        brandBg.setStroke(2, Color.rgb(65, 75, 102));
        topBrand.setBackground(brandBg);
        topBrand.setPadding(4, 6, 4, 6);

        ImageView lion = new ImageView(this);
        lion.setImageResource(ar.appvideo.player.R.drawable.leo_brand);
        lion.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topBrand.addView(lion, new LinearLayout.LayoutParams(44, 44));

        String shortProv = (activeProvider == null || activeProvider.isEmpty()) ? "CINEHAX" : activeProvider.toUpperCase();
        if (shortProv.length() > 8) shortProv = shortProv.substring(0, 8);
        TextView provLabel = text(shortProv, 9, Color.rgb(245, 197, 66));
        provLabel.setTypeface(null, Typeface.BOLD);
        provLabel.setGravity(Gravity.CENTER);
        provLabel.setSingleLine(true);
        topBrand.addView(provLabel);

        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(68, 72);
        logoLp.setMargins(0, 0, 0, 12);
        side.addView(topBrand, logoLp);
        topBrand.setOnClickListener(v -> showProviderSelector());
        topBrand.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable b = new GradientDrawable();
            b.setColor(focused ? Color.rgb(42, 50, 72) : Color.rgb(22, 26, 36));
            b.setCornerRadius(12);
            b.setStroke(focused ? 3 : 2, focused ? Color.WHITE : Color.rgb(65, 75, 102));
            topBrand.setBackground(b);
        });

        addSideIcon(side, "🔍", "search".equals(currentSection), "Buscar y Explorar", () -> showLunifiedSearch(lastLunifiedQuery));
        addSideIcon(side, "🏠", "all".equals(currentSection), "Inicio", () -> { activeTab = "all"; home(); });
        addSideIcon(side, "🎬", "movies".equals(currentSection), "Películas", () -> { activeTab = "movies"; home(); });
        addSideIcon(side, "🎞️", "series".equals(currentSection), "Series", () -> { activeTab = "series"; home(); });
        addSideIcon(side, "📺", "tv".equals(currentSection), "TV en vivo", this::showM3uSources);
        addSideIcon(side, "❤️", "favorites".equals(currentSection), "Favoritos y Kids", () -> { activeTab = "favorites"; home(); });
        addSideIcon(side, "🕒", "history".equals(currentSection), "Historial y Anime", () -> { activeTab = "history"; home(); });
        addSideIcon(side, "🔞", "adults".equals(currentSection), "Zona Adultos (+18)", () -> { activeTab = "adults"; home(); });

        android.widget.Space filler = new android.widget.Space(this);
        side.addView(filler, new LinearLayout.LayoutParams(1, 0, 1));

        addSideIcon(side, "⚙️", "sources".equals(currentSection), "Seleccionar Proveedor", this::showProviderSelector);
        return side;
    }

    private void openEntryDetail(CatalogData.Entry entry) {
        if (entry == null) return;
        String targetProvider = (entry.providerName != null && !entry.providerName.isEmpty()
            && !"Catálogo Global TV".equals(entry.providerName)) ? entry.providerName : activeProvider;
        VideoProvider prov = VideoProviders.find(targetProvider);
        if (prov == null) prov = VideoProviders.find("CineHax");
        if (prov != null) {
            CatalogBrowser.State st = new CatalogBrowser.State();
            st.kind = entry.isMovie() ? "movie" : "serie";
            st.selected = entry;
            catalogStates.put(prov.name(), st);
            openChannel(prov.name());
        }
    }

    private void openActorDetailSheet(ZappCatalogEngine.ActorItem actor) {
        if (actor == null) return;
        VideoProvider prov = VideoProviders.find(activeProvider);
        if (prov == null) prov = VideoProviders.find("CineHax");
        if (prov != null) {
            CatalogBrowser.State st = new CatalogBrowser.State();
            st.selectedActor = actor;
            catalogStates.put(prov.name(), st);
            openChannel(prov.name());
        }
    }

    /**
     * Pantalla de Búsqueda y Exploración estilo Lunified (SearchTvFragment + GlobalSearching):
     * - Barra superior de búsqueda ("Buscar Peliculas, Series" + botón X).
     * - Barra inferior con switch "Búsqueda global" (ON/OFF en verde).
     * - Grilla de 5 columnas de mosaicos coloridos por Año (2026..2010), País (Turquía, Corea, Japón, Argentina, México, España, Colombia, EE.UU., Brasil) y Géneros.
     * - Resultados unificados multicanal + traducción automática de títulos (ej. Erkenci Kuş <-> Pájaro Soñador) + Actores.
     */
    private void showLunifiedSearch(String initialQuery) {
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
        if (resolutionTask != null) resolutionTask.cancel(true);
        ++providerPlayRequest; providerLoading = false;
        page = "search"; channel = "";
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
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
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
            if (focused) g.setStroke(dp(2), Color.WHITE);
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
            if (focused) cBg.setStroke(dp(2), Color.WHITE);
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
            switchTrack.setGravity(globalSearchEnabled ? (Gravity.END | Gravity.CENTER_VERTICAL) : (Gravity.START | Gravity.CENTER_VERTICAL));
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
            if (focused) g.setStroke(2, Color.WHITE);
            globalBar.setBackground(g);
        });

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
                if (focused) bg.setStroke(4, Color.WHITE);
                card.setBackground(bg);
                card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
            });
        }
    }

    private void openExploreTileResults(ZappCatalogEngine.ExploreTile tile, LinearLayout contentBody) {
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
            java.util.List<CatalogData.Entry> fetched;
            try {
                fetched = ZappCatalogEngine.fetchExploreTile(tile, 1);
            } catch (Exception ignored) {
                fetched = java.util.Collections.emptyList();
            }
            final java.util.List<CatalogData.Entry> items = fetched;
            runOnUiThread(() -> {
                if (!page.equals("search") || token != searchGeneration || isDestroyed()) return;
                if (items.isEmpty()) {
                    loading.setText("No se encontraron títulos disponibles en " + tile.label + ".");
                    return;
                }
                loading.setText(items.size() + " títulos encontrados en " + tile.label + " · Tocá una portada para abrir su ficha completa con actores y capítulos.");
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
            final ZappCatalogEngine.GlobalSearchResult result =
                ZappCatalogEngine.searchUnified(query, globalSearchEnabled, activeProvider);
            runOnUiThread(() -> {
                if (!page.equals("search") || token != searchGeneration || isDestroyed()) return;
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
                    TextView empty = text("No se encontraron resultados para \"" + query + "\". Probá activando Búsqueda global o con otro término.", 17, Color.LTGRAY);
                    empty.setPadding(8, 16, 8, 16);
                    contentBody.addView(empty);
                    return;
                }

                // 2. Unificar resultados de todos los proveedores sin duplicados
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueMovies = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueSeries = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueAnime = new java.util.LinkedHashMap<>();

                for (java.util.Map.Entry<String, java.util.List<CatalogData.Entry>> group : result.byProvider.entrySet()) {
                    String provName = group.getKey();
                    for (CatalogData.Entry e : group.getValue()) {
                        String key = (e.title == null ? "" : e.title.trim().toLowerCase(java.util.Locale.ROOT));
                        if (key.isEmpty()) continue;
                        boolean isAnim = provName.contains("Anime") || "LatAnime".equals(e.providerName) || "DoramasFlix".equals(e.providerName);
                        if (isAnim) {
                            if (!uniqueAnime.containsKey(key)) uniqueAnime.put(key, e);
                        } else if (e.isMovie()) {
                            if (!uniqueMovies.containsKey(key)) uniqueMovies.put(key, e);
                        } else {
                            if (!uniqueSeries.containsKey(key)) uniqueSeries.put(key, e);
                        }
                    }
                }

                boolean firstFocused = false;
                if (!uniqueMovies.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueMovies.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName : activeProvider;
                        items.add(new PosterItem(e.title, e.year > 0 ? e.year : 2026, false, e.poster, prov, e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody, "🎬  Películas encontradas (" + items.size() + ")", items);
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueSeries.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueSeries.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName : activeProvider;
                        items.add(new PosterItem(e.title, e.year > 0 ? e.year : 2026, true, e.poster, prov, e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody, "🎞️  Series y Novelas encontradas (" + items.size() + ")", items);
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }

                if (!uniqueAnime.isEmpty()) {
                    java.util.List<PosterItem> items = new java.util.ArrayList<>();
                    for (CatalogData.Entry e : uniqueAnime.values()) {
                        String prov = (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName : activeProvider;
                        items.add(new PosterItem(e.title, e.year > 0 ? e.year : 2026, !e.isMovie(), e.poster, prov, e.title, e));
                    }
                    LinearLayout rail = addPosterRow(contentBody, "🌸  Anime y Doramas encontrados (" + items.size() + ")", items);
                    if (!firstFocused && rail.getChildCount() > 0) {
                        firstFocused = true;
                        rail.getChildAt(0).requestFocus();
                    }
                }
            });
        }, "global-search").start();
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
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int columns = dm.widthPixels >= 1100 ? 5 : 3;
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(columns);
        parent.addView(grid);
        for (CatalogData.Entry e : entries) {
            PosterItem pi = new PosterItem(
                e.title,
                e.year > 0 ? e.year : 2026,
                !e.isMovie(),
                e.poster,
                (e.providerName != null && !e.providerName.isEmpty()) ? e.providerName : activeProvider,
                e.title,
                e
            );
            android.view.View card = createPosterCard(pi);
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = 198;
            gp.height = -2;
            gp.setMargins(0, 0, 16, 18);
            grid.addView(card, gp);
        }
    }

    private void home() {
        if(catalogBrowser!=null){catalogBrowser.dispose();catalogBrowser=null;}
        if(resolutionTask!=null)resolutionTask.cancel(true);
        ++providerPlayRequest;providerLoading=false;
        page="home";channel="";

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(16, 20, 26));

        // Barra lateral izquierda persistente (estilo Lunified / Zapp TV)
        LinearLayout side = buildSidebar(activeTab);
        root.addView(side, new LinearLayout.LayoutParams(dp(88), -1));

        // Área principal derecha con carruseles de pósters
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(24), dp(14), 0, dp(10));
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

        String prov = (activeProvider == null || activeProvider.isEmpty()) ? "CineHax" : activeProvider;
        LinearLayout firstRail = null;

        if ("favorites".equals(activeTab)) {
            java.util.List<CatalogData.Entry> favs = ZappCatalogEngine.getFavorites(this);
            TextView favHeader = text("❤️  Mis Favoritos (se mantienen entre todos los proveedores)", 22, Color.WHITE);
            favHeader.setTypeface(null, Typeface.BOLD);
            favHeader.setPadding(8, 12, 8, 8);
            body.addView(favHeader);
            if (favs.isEmpty()) {
                TextView emptyFav = text("Todavía no agregaste títulos a Favoritos. Abrí cualquier película o serie y tocá 'Agregar a Favoritos'.", 15, Color.LTGRAY);
                emptyFav.setPadding(8, 4, 8, 14);
                body.addView(emptyFav);
            } else {
                renderEntriesGrid(body, favs);
            }
            LinearLayout rKids = addPosterRow(body, "Kids y Familia", defaultKids(prov));
            firstRail = rKids;
        } else if ("history".equals(activeTab)) {
            java.util.List<CatalogData.Entry> hist = ZappCatalogEngine.getHistory(this);
            TextView histHeader = text("🕒  Vistos recientemente", 22, Color.WHITE);
            histHeader.setTypeface(null, Typeface.BOLD);
            histHeader.setPadding(8, 12, 8, 8);
            body.addView(histHeader);
            if (hist.isEmpty()) {
                TextView emptyHist = text("Tu historial aparecerá acá a medida que abras fichas o reproduzcas capítulos.", 15, Color.LTGRAY);
                emptyHist.setPadding(8, 4, 8, 14);
                body.addView(emptyHist);
            } else {
                renderEntriesGrid(body, hist);
            }
            LinearLayout rAnime = addPosterRow(body, "Anime", defaultAnime());
            populateLiveRow(rAnime, "LatAnime", "movie", "", 1, 0, true);
            firstRail = rAnime;
        } else if ("adults".equals(activeTab)) {
            TextView adHead = text("🔞  Zona Adultos (+18) · Contenido para Mayores", 22, Color.rgb(255, 90, 90));
            adHead.setTypeface(null, Typeface.BOLD);
            adHead.setPadding(dp(8), dp(12), dp(8), dp(4));
            body.addView(adHead);

            TextView adSub = text("Películas eróticas, romances pasionales y thrillers adultos en audio latino.", 13, Color.rgb(200, 180, 180));
            adSub.setPadding(dp(8), 0, dp(8), dp(12));
            body.addView(adSub);

            LinearLayout rAd1 = addPosterRow(body, "🔞  Películas para Adultos (+18)", defaultAdults(prov));
            populateLiveRow(rAd1, "CineHax", "adults", "", 1, 0, true);
            firstRail = rAd1;

            LinearLayout rAd2 = addPosterRow(body, "❤️  Romances Pasionales y Eróticos", defaultRomance(prov));
            populateLiveRow(rAd2, "CineHax", "genre:romance", "", 1, 0, false);

            LinearLayout rAd3 = addPosterRow(body, "🕵️  Thrillers Eróticos y Suspenso Adulto", defaultTerror2026(prov));
            populateLiveRow(rAd3, "CineHax", "genre:suspense", "", 1, 0, false);
        } else {
            if ("all".equals(activeTab)) {
                // 1. Featured Hero Slideshow banner estilo MXL Movies
                buildHeroSlideShow(body, defaultFeaturedSlides(prov));

                // 2. Ranking Top 10 con números gigantes estilo MXL Movies / Netflix
                LinearLayout rTop10 = addTop10Row(body, "🔥  Top 10 en Leo TV hoy", defaultTop10(prov));
                if (firstRail == null) firstRail = rTop10;

                // 3. Deportes y Eventos en vivo estilo SK Tech
                addSportsLiveRow(body, "⚽  Deportes y Eventos en Vivo (Agenda SK Tech)", defaultSportsEvents());
            }

            if ("all".equals(activeTab) || "movies".equals(activeTab)) {
                LinearLayout r1 = addPosterRow(body, "🎬  Películas nuevas · 2026 (" + prov + ")", defaultMovies2026(prov));
                populateLiveRow(r1, prov, "movie", "", 1, 0, firstRail == null);
                if (firstRail == null) firstRail = r1;
            }

            if ("all".equals(activeTab) || "series".equals(activeTab)) {
                LinearLayout r2 = addPosterRow(body, "📺  Series nuevas · 2026 (" + prov + ")", defaultSeries2026(prov));
                populateLiveRow(r2, prov, "serie", "", 1, 0, firstRail == null);
                if (firstRail == null) firstRail = r2;
            }

            if ("all".equals(activeTab) || "series".equals(activeTab)) {
                LinearLayout rTr = addPosterRow(body, "🇹🇷  Novelas y Series Turcas", defaultTurkishNovelas(prov));
                populateLiveRow(rTr, "CineHax", "country:tr", "", 1, 0, false);
                if (firstRail == null) firstRail = rTr;
            }

            if ("all".equals(activeTab) || "series".equals(activeTab)) {
                LinearLayout rKr = addPosterRow(body, "🇰🇷  K-Dramas y Series Coreanas", defaultKDramas(prov));
                populateLiveRow(rKr, "CineHax", "country:kr", "", 1, 0, false);
            }

            if ("all".equals(activeTab) || "series".equals(activeTab) || "anime".equals(activeTab)) {
                LinearLayout rJp = addPosterRow(body, "🇯🇵  Japón y Anime Popular", defaultAnime());
                populateLiveRow(rJp, "CineHax", "country:jp", "", 1, 0, false);
            }

            if ("all".equals(activeTab) || "movies".equals(activeTab)) {
                LinearLayout r3 = addPosterRow(body, "💥  Acción y Aventura · 2026", defaultAction2026(prov));
                populateLiveRow(r3, "CineHax", "genre:accion", "", 1, 0, false);

                LinearLayout r5 = addPosterRow(body, "👻  Terror y Suspenso · 2026", defaultTerror2026(prov));
                populateLiveRow(r5, "CineHax", "genre:terror", "", 1, 0, false);

                LinearLayout rSc = addPosterRow(body, "🚀  Ciencia Ficción y Fantasía", defaultSciFi(prov));
                populateLiveRow(rSc, "CineHax", "genre:ciencia-ficcion", "", 1, 0, false);

                LinearLayout r4 = addPosterRow(body, "😂  Comedia · 2026", defaultComedy2026(prov));
                populateLiveRow(r4, "CineHax", "genre:comedia", "", 1, 0, false);

                LinearLayout rRo = addPosterRow(body, "❤️  Romance y Pasión", defaultRomance(prov));
                populateLiveRow(rRo, "CineHax", "genre:romance", "", 1, 0, false);
            }

            if ("all".equals(activeTab) || "kids".equals(activeTab)) {
                LinearLayout r7 = addPosterRow(body, "👶  Kids y Familia", defaultKids(prov));
                if (firstRail == null) firstRail = r7;
            }

            if ("all".equals(activeTab) || "adults".equals(activeTab)) {
                LinearLayout rAd = addPosterRow(body, "🔞  Zona Adultos (+18)", defaultAdults(prov));
                populateLiveRow(rAd, "CineHax", "adults", "", 1, 0, false);
            }
        }

        setContentView(root);
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
        if (heroTitle == null) return;
        if (name.equals("Demo")) {
            if (heroEyebrow != null) heroEyebrow.setText("DESTACADO  ·  PELÍCULA ABIERTA DE PRUEBA");
            heroTitle.setText("Big Buck Bunny (2008)");
            if (heroBadge != null) heroBadge.setText("1080p MP4  ·  Blender Foundation  ·  CC BY 3.0  ·  Reproducción probada");
            if (heroSynopsis != null) heroSynopsis.setText("Un conejo gigante y bondadoso del bosque decide darle una lección a tres roedores traviesos que molestan a criaturas indefensas.");
        } else if (name.equals("Películas (Latino)")) {
            if (heroEyebrow != null) heroEyebrow.setText("PELÍCULAS  ·  AUDIO LATINO Y ESTRENOS");
            heroTitle.setText("Cine en Audio Latino");
            if (heroBadge != null) heroBadge.setText("Películas con doblaje latino y castellano");
            if (heroSynopsis != null) heroSynopsis.setText("Catálogos de cine con preferencia de audio latino: CineHax, LaMovie, CineCalidad, Cuevana y películas abiertas.");
        } else if (name.equals("Series (Latino)")) {
            if (heroEyebrow != null) heroEyebrow.setText("SERIES  ·  TEMPORADAS COMPLETAS");
            heroTitle.setText("Series en Audio Latino");
            if (heroBadge != null) heroBadge.setText("Temporadas y capítulos completos en español");
            if (heroSynopsis != null) heroSynopsis.setText("Series y novelas organizadas por temporadas y episodios con audio latino: CineHax, SeriesMetro, Pelispedia.");
        } else if (name.equals("TV en vivo")) {
            if (heroEyebrow != null) heroEyebrow.setText("TELEVISIÓN EN VIVO  ·  SOLO ESPAÑOL (SIN RADIOS)");
            heroTitle.setText("TV Abierta en Español");
            if (heroBadge != null) heroBadge.setText("Canales de Argentina y España  ·  M3U8");
            if (heroSynopsis != null) heroSynopsis.setText("Señales de televisión abierta en español. Radios y canales en otros idiomas omitidos para mayor comodidad.");
        } else if (name.equals("Infantil (Dibujos)")) {
            if (heroEyebrow != null) heroEyebrow.setText("INFANTIL  ·  DOBLAJE LATINO");
            heroTitle.setText("Dibujos Animados Latinos");
            if (heroBadge != null) heroBadge.setText("Dibujos animados clásicos y Studio Ghibli Latino");
            if (heroSynopsis != null) heroSynopsis.setText("Selección infantil familiar con doblaje latino oficial.");
        } else if (name.equals("Anime (Latino)")) {
            if (heroEyebrow != null) heroEyebrow.setText("ANIME  ·  DOBLAJE LATINO Y SUBTITULADO");
            heroTitle.setText("Anime en Audio Latino");
            if (heroBadge != null) heroBadge.setText("LatAnime, TioAnime, JKAnime");
            if (heroSynopsis != null) heroSynopsis.setText("Series y películas de animación japonesa con doblaje latino oficial y opciones subtituladas.");
        } else if (name.equals("CineHax") || name.equals("CineHax+")) {
            if (heroEyebrow != null) heroEyebrow.setText("PROVEEDOR VOD  ·  AUDIO LATINO");
            heroTitle.setText("CineHax (Latino)");
            if (heroBadge != null) heroBadge.setText("Películas y series en audio latino  ·  Temporadas y episodios");
            if (heroSynopsis != null) heroSynopsis.setText("Catálogo estructurado de cine y series con resolución dinámica de servidores de video compatibles.");
        } else if (name.equals("LaMovie")) {
            if (heroEyebrow != null) heroEyebrow.setText("PROVEEDOR VOD  ·  CINE EN ESPAÑOL");
            heroTitle.setText("LaMovie (Latino)");
            if (heroBadge != null) heroBadge.setText("Audio Latino y Subtitulado  ·  TMDB integrado");
            if (heroSynopsis != null) heroSynopsis.setText("Catálogo de cine con carátulas y metadatos de películas con doblaje latino y castellano.");
        } else if (name.equals("LatAnime")) {
            if (heroEyebrow != null) heroEyebrow.setText("ANIME  ·  DOBLAJE LATINO OFICIAL");
            heroTitle.setText("LatAnime (Audio Latino)");
            if (heroBadge != null) heroBadge.setText("Doblaje latino oficial  ·  Capítulos completos");
            if (heroSynopsis != null) heroSynopsis.setText("Catálogo especializado de animación japonesa con audio latino oficial y capítulos verificados.");
        } else if (name.equals("Blender Open Movies")) {
            if (heroEyebrow != null) heroEyebrow.setText("CINE ABIERTO  ·  ANIMACIÓN LIBRE OFICIAL");
            heroTitle.setText("Blender Open Movies");
            if (heroBadge != null) heroBadge.setText("Elephants Dream, Big Buck Bunny, Sintel  ·  Verificado");
            if (heroSynopsis != null) heroSynopsis.setText("Películas y cortos de animación de código abierto producidos por Blender Foundation con enlaces y metadatos verificados.");
        } else if (name.equals("Internet Archive")) {
            if (heroEyebrow != null) heroEyebrow.setText("CATÁLOGO ONLINE  ·  DOMINIO PÚBLICO Y CC");
            heroTitle.setText("Internet Archive Cinema");
            if (heroBadge != null) heroBadge.setText("API Pública en vivo  ·  Películas clásicas y abiertas");
            if (heroSynopsis != null) heroSynopsis.setText("Colección cinematográfica de películas con licencia Creative Commons y dominio público, con portadas y reproducción directa.");
        } else if (name.equals("TDTChannels TV")) {
            if (heroEyebrow != null) heroEyebrow.setText("TELEVISIÓN EN VIVO  ·  EMISIONES ABIERTAS EN ESPAÑOL");
            heroTitle.setText("TDTChannels TV");
            if (heroBadge != null) heroBadge.setText("Emisiones abiertas en español  ·  M3U8");
            if (heroSynopsis != null) heroSynopsis.setText("Directorio de señales públicas y emisiones abiertas de televisión en español (radios omitidas).");
        } else if (name.equals("TioAnime") || name.equals("AnimeAV1") || name.equals("JKAnime")) {
            if (heroEyebrow != null) heroEyebrow.setText("ANIME  ·  SERIES Y PELÍCULAS DE ANIMACIÓN");
            heroTitle.setText(name);
            if (heroBadge != null) heroBadge.setText("Catálogo de episodios  ·  Video según disponibilidad");
            if (heroSynopsis != null) heroSynopsis.setText("Series de anime en emisión y finalizadas con selector de episodios y temporadas.");
        } else {
            if (heroEyebrow != null) heroEyebrow.setText("LEO TV  ·  " + tvBadge(name).toUpperCase());
            heroTitle.setText(name);
            if (heroBadge != null) heroBadge.setText(tvBadge(name));
            if (heroSynopsis != null) heroSynopsis.setText(descriptionFor(name));
        }
    }

    private void showMovieFicha(LinearLayout body, String title, int year, String genre, String duration, String license, String synopsis, Runnable playAction) {
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

        TextView metaLine = text(year + "  ·  " + genre + "  ·  " + duration + "  ·  " + license, 14, Color.rgb(212, 175, 55));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(0, 6, 0, 12);
        info.addView(metaLine, mlp);

        TextView synop = text(synopsis, 15, Color.rgb(205, 218, 235));
        synop.setMaxLines(4);
        synop.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, 0, 0, 20);
        info.addView(synop, slp);

        Button playBtn = tvAction("▶  Reproducir video completo", "Media3 ExoPlayer en pantalla completa", playAction, 420, 95);
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
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
        if (resolutionTask != null) resolutionTask.cancel(true);
        ++providerPlayRequest; providerLoading = false;
        page = "channel"; channel = name;
        String sub = "Catálogo y fichas enriquecidas · Volvé con ATRÁS.";
        if (!name.equals("Demo") && !name.equals("Blender Open Movies")) sub = descriptionFor(name);
        if (name.equals("Blender Open Movies")) sub = "Canal oficial Blender Open Movies: enlaces verificados al entrar; películas y tráiler identificados.";
        if (name.equals("Internet Archive")) sub = "Catálogo online de películas CC BY 3.0; fichas y enlaces desde la API pública.";
        if (name.equals("CineHax")) sub =
            "Catálogo online, búsqueda, fichas de actores y capítulos con vista previa.";
        if (name.equals("LaMovie")) sub = "Catálogo online, búsqueda, reparto y capítulos mediante servidores compatibles.";
        if (name.equals("SoloLatino")) sub =
            "Catálogo investigado; sin reproducción propia confirmada.";
        if (name.equals("Mis nubes")) sub =
            "Futura importación de bibliotecas personales autorizadas.";
        if (name.equals("Estado")) {
            sub = channels.length() + " fuentes inventariadas. 4 con reproducción probada, 2 anime con catálogo y episodios probados, 20 adaptadores provisionales sin QA.";
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
            item("Ver estado actual de las 32 fuentes", body,
                () -> showStatus("4 fuentes con reproducción probada; 2 anime con catálogo/episodios; 20 adaptadores HTML provisionales; 6 pendientes o restringidas."));
        } else if (name.equals("Demo")) {
            showMovieFicha(body, "Big Buck Bunny", 2008, "Animación / Cine Abierto", "10 min", "CC BY 3.0",
                "Un conejo gigante y de buen corazón decide darle una lección inolvidable a tres traviesos roedores del bosque (Frank, Rinky y Gamera) que se divierten molestando a las criaturas indefensas.",
                this::playDemo);
            item("ⓘ  Detalle técnico: video MP4 verificado en Media3 ExoPlayer", body,
                () -> showStatus("Demo: video independiente con reproducción y sesión multimedia verificadas."));
        } else {
            item("◈  Diagnóstico: " + referenceStatus(name), body,
                () -> showStatus(referenceStatus(name)));
            item("Canal todavía sin reproducción integrada", body,
                () -> showStatus("Este proveedor está inventariado, pero no dispone de adaptador de reproducción."));
        }
        status = text("", 16, Color.rgb(245, 197, 66));
        body.addView(status);
        if (provider != null) {
            CatalogBrowser.State saved = catalogStates.get(name);
            if (saved == null) { saved = new CatalogBrowser.State(); catalogStates.put(name, saved); }
            catalogBrowser = new CatalogBrowser(this, body, saved, provider, new CatalogBrowser.Host() {
                @Override public boolean active() { return page.equals("channel") && channel.equals(provider.name()); }
                @Override public void play(CatalogData.Entry entry, int season, int episode, String title) {
                    playProviderTitle(provider, entry, season, episode, title);
                }
            });
        }
        if (name.equals("Internet Archive")) loadArchiveLive(body);
        if (name.equals("Blender Open Movies")) loadBlenderOfficial(body);
        if (back != null) back.requestFocusFromTouch();
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
        button.setTextSize(15); button.setMaxLines(2); button.setEllipsize(android.text.TextUtils.TruncateAt.END); button.setTextColor(Color.WHITE);
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
    private CatalogBrowser catalogBrowser;
    private final java.util.Map<String,CatalogBrowser.State> catalogStates = new java.util.HashMap<>();
    private long providerPlayRequest;
    private boolean providerLoading;
    private final java.util.concurrent.ExecutorService videoResolvers = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> resolutionTask;
    private void playProviderTitle(VideoProvider provider, CatalogData.Entry entry, int season, int episode, String title) {
        if (providerLoading) return;
        final long request = ++providerPlayRequest;
        providerLoading = true;
        showStatus("Buscando servidor de video para " + title + "...");
        resolutionTask = videoResolvers.submit(() -> {
            VideoProvider.VideoLink link = null;
            String error = null;
            String resolvedProviderName = provider.name();
            if (!"Catálogo Global TV".equals(entry.providerName)) {
                try { link = provider.resolve(entry, season, episode); }
                catch (Exception ex) { error = ex.getMessage(); }
            }
            // Si proviene del Catálogo Global TV (TVMaze) o falló el proveedor primario, buscar en proveedores activos
            if (link == null) {
                String[] fallbackProviders = new String[]{"CineHax", "LaMovie", "SeriesMetro", "Seriesflix", "DoramasFlix", "Pelispedia"};
                String cleanTitle = entry.title == null ? "" : entry.title.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
                String altTitle = entry.originalTitle == null ? "" : entry.originalTitle.trim();
                String[] queries = altTitle.isEmpty() ? new String[]{cleanTitle} : new String[]{cleanTitle, altTitle};
                outer:
                for (String pName : fallbackProviders) {
                    VideoProvider vp = VideoProviders.find(pName);
                    if (vp == null) continue;
                    for (String q : queries) {
                        if (q.isEmpty()) continue;
                        try {
                            CatalogData.CatalogPage cp = vp.catalog(entry.isMovie() ? "movie" : "serie", q, 1);
                            for (CatalogData.Entry cand : cp.entries) {
                                try {
                                    link = vp.resolve(cand, season, episode);
                                    if (link != null) {
                                        resolvedProviderName = vp.name();
                                        break outer;
                                    }
                                } catch (Exception ignored) {}
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            final VideoProvider.VideoLink resolved = link;
            final String finalProv = resolvedProviderName;
            final String message = error != null ? error : "Stream aún no indexado en los servidores activos para este capítulo.";
            runOnUiThread(() -> {
                if (request != providerPlayRequest) return;
                providerLoading = false;
                if (!page.equals("channel")) return;
                if (resolved == null) showStatus("Aviso: " + message);
                else playMovie(title, resolved.url, finalProv, resolved.headers);
            });
        });
    }
    private void showStatus(String value) {
        if (status != null) status.setText(value);
    }
    private void setFullscreen(boolean enabled) {
        if (!page.equals("playing") || playbackView == null || playbackBody == null) return;
        fullscreen = enabled;
        root.setPadding(enabled ? 0 : 48, enabled ? 0 : 32, enabled ? 0 : 48, enabled ? 0 : 30);
        for (int i=0; i<root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (child != pageScroll) child.setVisibility(enabled ? android.view.View.GONE : android.view.View.VISIBLE);
        }
        for (int i=0; i<playbackBody.getChildCount(); i++) {
            android.view.View child = playbackBody.getChildAt(i);
            if (child != playbackView) child.setVisibility(enabled ? android.view.View.GONE : android.view.View.VISIBLE);
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
        if (enabled) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (enabled) playbackView.requestFocusFromTouch();
        else if (fullscreenButton != null) fullscreenButton.requestFocusFromTouch();
    }
    private void playDemo() { playMovie("Big Buck Bunny", DEMO_URL, "Demo"); }
    private void playMovie(String title, String url, String origin) {
        playMovie(title,url,origin,null);
    }
    private void playMovie(String title, String url, String origin, java.util.Map<String,String> headers) {
        playingChannel = origin;
        page = "playing";
        LinearLayout body = newPage(origin, title,
            "Reproducción directa · Usá OK para mostrar controles.");
        playbackBody = body;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        status = text("Cargando video...", 18, Color.WHITE);
            PlayerView view = new PlayerView(this);
            playbackView = view;
            view.setUseController(true);
            body.addView(view, new LinearLayout.LayoutParams(-1, 620));
            // Overlay survives fullscreen: status is no longer a hidden sibling.
            status.setTextSize(16);
            status.setPadding(18,10,18,10);
            status.setGravity(Gravity.CENTER);
            status.setBackgroundColor(Color.argb(204,12,22,38));
            android.widget.FrameLayout overlay=view.getOverlayFrameLayout();
            if (overlay != null) {
                android.widget.FrameLayout.LayoutParams overlayParams =
                    new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
                overlay.addView(status,overlayParams);
            } else {
                body.addView(status);
            }
            ExoPlayer.Builder builder = new ExoPlayer.Builder(this);
            androidx.media3.datasource.DefaultHttpDataSource.Factory http =
                new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent(headers != null && headers.containsKey("User-Agent")
                        ? headers.get("User-Agent") : HttpSupport.USER_AGENT)
                    .setConnectTimeoutMs(12000).setReadTimeoutMs(18000)
                    .setAllowCrossProtocolRedirects(false);
            if (headers != null) http.setDefaultRequestProperties(headers);
            builder.setMediaSourceFactory(
                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http));
            externalPlayer = builder.build();
            view.setPlayer(externalPlayer);
            externalPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        showStatus(externalPlayer != null && externalPlayer.getPlayWhenReady()
                            ? "Video preparado." : "Pausado.");
                        if (status != null) status.setVisibility(android.view.View.GONE);
                    } else if (state == Player.STATE_ENDED) {
                        showStatus("Video finalizado.");
                        if (status != null) status.setVisibility(android.view.View.VISIBLE);
                    } else if (state == Player.STATE_BUFFERING) {
                        showStatus("Cargando video...");
                        if (status != null) status.setVisibility(android.view.View.VISIBLE);
                    }
                    updateMediaState();
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
                    if (externalPlayer != null && externalPlayer.getPlaybackState() == Player.STATE_READY) {
                        if (status != null) status.setVisibility(android.view.View.GONE);
                        android.util.Log.i("LeoTVPlayer","playing="+playing+
                            " positionMs="+externalPlayer.getCurrentPosition());
                    }
                }
                @Override public void onPlayerError(PlaybackException error) {
                    Throwable cause=error;
                    int httpCode=0;
                    while(cause!=null) {
                        if(cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                            httpCode=((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)cause).responseCode;
                            break;
                        }
                        cause=cause.getCause();
                    }
                    String message=httpCode==403 ? "Acceso denegado por la fuente (HTTP 403). Probá otro canal."
                        : httpCode==404 ? "La fuente no existe (HTTP 404). Probá otro canal."
                        : httpCode>0 ? "Error HTTP "+httpCode+" de la fuente."
                        : "No se pudo reproducir: "+error.getErrorCodeName();
                    showStatus(message+"  Atras para elegir otro video.");
                    if(status!=null)status.setVisibility(android.view.View.VISIBLE);
                    if(mediaSession!=null)mediaSession.setPlaybackState(
                        new android.media.session.PlaybackState.Builder()
                        .setState(android.media.session.PlaybackState.STATE_ERROR,
                            externalPlayer.getCurrentPosition(),0f).build());
                    android.util.Log.e("LeoTVPlayer","Playback error origin="+origin+
                        " code="+error.getErrorCodeName()+" http="+httpCode,error);
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
        if (intent == null) return;
        String searchQuery = intent.getStringExtra("search_query");
        boolean openSearch = intent.getBooleanExtra("open_search", false);
        String detailQuery = intent.getStringExtra("open_detail_query");
        String actorName = intent.getStringExtra("open_actor_name");
        if (actorName != null && !actorName.isEmpty()) {
            new Thread(() -> {
                ZappCatalogEngine.GlobalSearchResult r = ZappCatalogEngine.searchUnified(actorName, true, activeProvider);
                if (!r.matchedActors.isEmpty()) {
                    final ZappCatalogEngine.ActorItem actor = r.matchedActors.get(0);
                    runOnUiThread(() -> openActorDetailSheet(actor));
                }
            }).start();
        } else if (detailQuery != null && !detailQuery.isEmpty()) {
            new Thread(() -> {
                ZappCatalogEngine.GlobalSearchResult r = ZappCatalogEngine.searchUnified(detailQuery, true, activeProvider);
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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(12, 14, 20));

        final LinearLayout splashContent = new LinearLayout(this);
        splashContent.setOrientation(LinearLayout.VERTICAL);
        splashContent.setGravity(Gravity.CENTER_HORIZONTAL);
        splashContent.setAlpha(0f);
        root.addView(splashContent, new LinearLayout.LayoutParams(-2, -2));

        ImageView brandImg = new ImageView(this);
        brandImg.setImageResource(ar.appvideo.player.R.drawable.leo_brand);
        brandImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        splashContent.addView(brandImg, new LinearLayout.LayoutParams(dp(180), dp(120)));

        TextView title = text("LEO TV", 36, Color.rgb(245, 197, 66));
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, dp(4));
        splashContent.addView(title);

        TextView sub = text("Películas, Series y TV en Vivo", 16, Color.rgb(180, 192, 214));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(24));
        splashContent.addView(sub);

        android.widget.ProgressBar spinner = new android.widget.ProgressBar(this);
        splashContent.addView(spinner, new LinearLayout.LayoutParams(dp(36), dp(36)));

        final TextView status = text("Iniciando catálogo...", 13, Color.rgb(140, 152, 174));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, 0);
        splashContent.addView(status);

        setContentView(root);
        splashContent.animate().alpha(1f).setDuration(550).start();

        // Comprobación silenciosa de actualizaciones en GitHub
        AppUpdater.check(this, new AppUpdater.CheckCallback() {
            @Override public void onUpdateAvailable(AppUpdater.UpdateInfo info) {
                pendingUpdate = info;
            }
            @Override public void onNoUpdate() {}
            @Override public void onError(String msg) {}
        });

        // Transición fluida hacia Inicio tras 1.8 segundos
        root.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            splashContent.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                home();
                handleLaunchIntent(getIntent());
                if (pendingUpdate != null) {
                    AppUpdater.showUpdateDialog(MainActivity.this, pendingUpdate);
                }
            }).start();
        }, 1800);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        loadChannels();
        loadCatalog();
        loadAudit();
        showSplashScreen();
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (page.equals("playing") && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                setFullscreen(!fullscreen);
            return true;
        }
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
        if (catalogBrowser != null) catalogBrowser.dispose();
        videoResolvers.shutdownNow();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (page.equals("playing")) {
            if (fullscreen) { setFullscreen(false); return; }
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (video != null) { video.stopPlayback(); video = null; }
            if (externalPlayer != null) { externalPlayer.release(); externalPlayer = null; }
            releaseMediaSession();
            if ("TV M3U".equals(playingChannel)) showM3uList(lastM3uName,lastM3uUrl,lastM3uPlayable);
            else openChannel(playingChannel);
        } else if (page.equals("channel")) {
            if (resolutionTask != null) resolutionTask.cancel(true);
        ++providerPlayRequest; providerLoading = false;
            if (catalogBrowser != null && catalogBrowser.back()) return;
            home();
        } else if (page.equals("search")) {
            if (lastLunifiedQuery != null && !lastLunifiedQuery.isEmpty()) {
                showLunifiedSearch("");
            } else {
                home();
            }
        }
        else if(page.equals("m3u_list"))showM3uSources();
        else if(page.equals("m3u_sources"))home();
        else if(page.equals("sources")||page.equals("section"))home();
        else super.onBackPressed();
    }
}
