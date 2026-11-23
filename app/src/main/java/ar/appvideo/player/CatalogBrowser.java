package ar.appvideo.player;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Navegador de catálogo y ficha enriquecida estilo Zapp TV:
 * - Cabecera con portada grande, título original/alternativo, calificación, año, duración, sinopsis y Favoritos.
 * - Carrusel de Actores / Reparto con fotos y personajes -> clic abre Ficha Individual del Actor (PersonActivity) con filmografía.
 * - Selector de Temporadas, rangos de capítulos (1-25, 26-50...) y Episodios con miniatura 16:9, duración, fecha y sinopsis.
 */
public final class CatalogBrowser {
    public interface Host {
        boolean active();
        void play(CatalogData.Entry entry, int season, int episode, String title);
        default void download(CatalogData.Entry entry, int season, int episode, String title) {}
        default void playWithProvider(CatalogData.Entry entry, int season, int episode, String title, String providerName) {}
        default void playExternal(CatalogData.Entry entry, int season, int episode, String title) {}
    }

    private int dp(int dps) {
        return Math.round(dps * activity.getResources().getDisplayMetrics().density);
    }

    public static final class State {
        String kind = "movie", query = "";
        int page = 1, season = 0;
        CatalogData.Entry selected;
        ZappCatalogEngine.ActorItem selectedActor;
        final Stack<Runnable> backStack = new Stack<>();
    }

    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(4);
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(4);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(14 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    private final Activity activity;
    private final Host host;
    private final VideoProvider provider;
    private final State state;
    private final LinearLayout results;
    private final TextView message;
    private final EditText search;
    private final LinearLayout searchBarContainer;
    private final LinearLayout filterBarContainer;
    private boolean disposed;
    private int generation;
    private Future<?> pending;
    private final int violet = Color.rgb(129, 107, 245);
    private final int panel = Color.rgb(27, 33, 52);
    private final int cardDark = Color.rgb(22, 27, 40);

    public CatalogBrowser(Activity owner, LinearLayout body, State saved, VideoProvider source, Host events) {
        activity = owner;
        host = events;
        state = saved;
        provider = source;

        searchBarContainer = row();
        search = new EditText(owner);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setTextSize(17);
        search.setSingleLine(true);
        search.setHint("Buscar en " + provider.name());
        search.setText(state.query);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBarContainer.addView(search, new LinearLayout.LayoutParams(0, 74, 1));
        button(searchBarContainer, "Buscar", this::submitSearch, 170);
        body.addView(searchBarContainer);

        search.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                submitSearch();
                return true;
            }
            return false;
        });

        android.widget.HorizontalScrollView filterScroll = new android.widget.HorizontalScrollView(activity);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterBarContainer = row();
        filterBarContainer.setPadding(0, 4, 16, 8);
        filterScroll.addView(filterBarContainer);
        body.addView(filterScroll, new LinearLayout.LayoutParams(-1, -2));

        String pName = provider.name();
        if ("CineHax".equals(pName)) {
            button(filterBarContainer, "⭐ Estrenos Películas", () -> category("movie"), -2);
            button(filterBarContainer, "📺 Series", () -> category("serie"), -2);
            button(filterBarContainer, "🇹🇷 Novelas Turcas", () -> category("country:tr"), -2);
            button(filterBarContainer, "🇰🇷 K-Dramas (Corea)", () -> category("country:kr"), -2);
            button(filterBarContainer, "🇯🇵 Japón y Anime", () -> category("country:jp"), -2);
            button(filterBarContainer, "💥 Acción", () -> category("genre:accion"), -2);
            button(filterBarContainer, "👻 Terror", () -> category("genre:terror"), -2);
            button(filterBarContainer, "😂 Comedia", () -> category("genre:comedia"), -2);
            button(filterBarContainer, "🚀 Ciencia Ficción", () -> category("genre:ciencia-ficcion"), -2);
            button(filterBarContainer, "❤️ Romance", () -> category("genre:romance"), -2);
            button(filterBarContainer, "🕵️ Crimen / Suspenso", () -> category("genre:crimen"), -2);
            button(filterBarContainer, "👶 Animación", () -> category("genre:animacion"), -2);
            button(filterBarContainer, "📜 Documentales", () -> category("genre:documental"), -2);
        } else if ("DoramasFlix".equals(pName)) {
            button(filterBarContainer, "🌸 Doramas y K-Dramas", () -> category("serie"), -2);
            button(filterBarContainer, "🇹🇷 Novelas Turcas", () -> category("turcas"), -2);
            button(filterBarContainer, "🎬 Películas Asiáticas", () -> category("movie"), -2);
        } else if (pName.contains("Anime")) {
            button(filterBarContainer, "🔥 Anime en Emisión", () -> category("movie"), -2);
            button(filterBarContainer, "🎙️ Doblaje Latino", () -> category("serie"), -2);
            button(filterBarContainer, "🎬 Películas de Anime", () -> category("movie"), -2);
        } else if ("SeriesMetro".equals(pName) || "Seriesflix".equals(pName)) {
            button(filterBarContainer, "📺 Series Nuevas", () -> category("serie"), -2);
            button(filterBarContainer, "🇹🇷 Novelas Turcas", () -> category("turcas"), -2);
            button(filterBarContainer, "🎬 Películas", () -> category("movie"), -2);
        } else {
            button(filterBarContainer, provider.primaryCategoryLabel(), () -> category("movie"), -2);
            if (provider.supportsSeries()) button(filterBarContainer, "Series", () -> category("serie"), -2);
            if (provider.supportsNovels()) button(filterBarContainer, "Novelas Turcas", () -> category("novel"), -2);
            button(filterBarContainer, "💥 Acción", () -> category("genre:accion"), -2);
            button(filterBarContainer, "👻 Terror", () -> category("genre:terror"), -2);
            button(filterBarContainer, "😂 Comedia", () -> category("genre:comedia"), -2);
        }

        message = label("", 16);
        body.addView(message);

        results = new LinearLayout(owner);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results);

        if (state.selectedActor != null) {
            showActorSheet(state.selectedActor, null);
        } else if (state.selected != null) {
            detail(state.selected);
        } else {
            catalog();
        }
    }

    private void setHeaderControlsVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        if (searchBarContainer != null) searchBarContainer.setVisibility(vis);
        if (filterBarContainer != null) filterBarContainer.setVisibility(vis);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private TextView label(String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.LTGRAY);
        text.setPadding(4, 8, 4, 8);
        return text;
    }

    private GradientDrawable background(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(selected ? violet : panel);
        d.setCornerRadius(14);
        d.setStroke(2, selected ? Color.WHITE : Color.rgb(70, 80, 113));
        return d;
    }

    private GradientDrawable pillBg(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(2, stroke);
        return d;
    }

    private Button button(LinearLayout parent, String text, Runnable action, int width) {
        Button b = new Button(activity);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setSingleLine(true);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setPadding(32, 10, 32, 10);
        b.setBackground(background(false));
        int minH = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        b.setMinHeight(minH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width > 0 ? width : -2, -2);
        lp.setMargins(0, 6, 12, 6);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
        b.setOnFocusChangeListener((v, focused) -> b.setBackground(background(focused)));
        return b;
    }

    private void submitSearch() {
        String query = search.getText().toString().trim();
        if (query.length() > 120) {
            message.setText("La búsqueda es demasiado larga.");
            return;
        }
        ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(search.getWindowToken(), 0);
        state.query = query;
        state.page = 1;
        state.selected = null;
        state.selectedActor = null;
        state.season = 0;
        state.backStack.clear();
        catalog();
    }

    private void category(String kind) {
        state.kind = kind;
        state.query = "";
        state.page = 1;
        state.selected = null;
        state.selectedActor = null;
        state.season = 0;
        state.backStack.clear();
        search.setText("");
        catalog();
    }

    private boolean current(int token) {
        return !disposed && host.active() && generation == token;
    }

    private interface Work { Object run() throws Exception; }
    private interface Render { void run(Object result) throws Exception; }

    private void load(String loading, Work work, Render render) {
        final int token = ++generation;
        if (pending != null) pending.cancel(true);
        results.removeAllViews();
        message.setText(loading);
        pending = NETWORK.submit(() -> {
            try {
                Object data = work.run();
                activity.runOnUiThread(() -> {
                    if (!current(token)) return;
                    try {
                        render.run(data);
                    } catch (Exception error) {
                        message.setText("No se pudo mostrar el resultado.");
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (current(token)) message.setText("No se pudo cargar. Reintentá con Buscar o cambiá de categoría.");
                });
            }
        });
    }

    private void catalog() {
        setHeaderControlsVisible(true);
        state.selected = null;
        state.selectedActor = null;
        final String kind = state.kind, query = state.query;
        final int page = state.page;
        load("Cargando catálogo de " + provider.name() + "...", () -> provider.catalog(kind, query, page), data -> {
            CatalogData.CatalogPage response = (CatalogData.CatalogPage) data;
            message.setText((query.isEmpty() ? "Catálogo" : "Resultados de \"" + query + "\"")
                + " · Página " + response.page + " · "
                + (response.totalKnown ? response.total + " títulos informados por la fuente" : response.entries.size() + " títulos en esta página"));
            if (response.entries.isEmpty()) {
                results.addView(label("No se encontraron títulos.", 18));
                return;
            }
            int columns = activity.getResources().getDisplayMetrics().widthPixels >= 1100 ? 5 : 3;
            int availWidth = Math.max(700, activity.getResources().getDisplayMetrics().widthPixels - 210);
            int size = (availWidth - (columns - 1) * 18) / columns;
            GridLayout grid = new GridLayout(activity);
            grid.setColumnCount(columns);
            results.addView(grid);
            for (CatalogData.Entry entry : response.entries) card(grid, entry, size);
            LinearLayout pages = row();
            if (page > 1) button(pages, "Página anterior", () -> { state.page = page - 1; catalog(); }, 280);
            if (response.next) button(pages, "Cargar más · Página " + (page + 1), () -> { state.page = page + 1; catalog(); }, 360);
            results.addView(pages);
        });
    }

    private void card(GridLayout grid, CatalogData.Entry entry, int width) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(6, 6, 6, 8);
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(Color.TRANSPARENT);
        normalBg.setCornerRadius(14);
        card.setBackground(normalBg);
        int posterHeight = (int) (width * 1.40f);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = width;
        lp.height = posterHeight + 84;
        lp.setMargins(0, 0, 16, 20);
        grid.addView(card, lp);

        FrameLayout posterWrap = new FrameLayout(activity);
        GradientDrawable imgBg = new GradientDrawable();
        imgBg.setColor(panel);
        imgBg.setCornerRadius(14);
        posterWrap.setBackground(imgBg);
        posterWrap.setClipToOutline(true);

        TextView fallback = new TextView(activity);
        fallback.setText(entry.title);
        fallback.setTextSize(13);
        fallback.setTextColor(Color.rgb(180, 192, 215));
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(10, 10, 10, 10);
        posterWrap.addView(fallback, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterWrap.addView(image, new FrameLayout.LayoutParams(-1, -1));
        card.addView(posterWrap, new LinearLayout.LayoutParams(-1, posterHeight));
        loadPoster(activity, image, entry.poster);

        TextView meta = new TextView(activity);
        meta.setTextSize(11);
        meta.setTypeface(null, Typeface.BOLD);
        meta.setPadding(4, 6, 4, 2);

        RatingEngine.RatingInfo rInfo = RatingEngine.getRating(entry, activity);
        String kindTag = entry.isMovie() ? (entry.year > 0 ? String.valueOf(entry.year) : "CINE") : "SERIE";
        if (rInfo.hasScore()) {
            meta.setText(String.format(java.util.Locale.US, "⭐ %.1f %s  ·  %s", rInfo.score, rInfo.source, kindTag));
            meta.setTextColor(Color.rgb(245, 197, 66));
        } else {
            meta.setText("Sin calificación  ·  " + kindTag);
            meta.setTextColor(Color.rgb(175, 185, 200));
            RatingEngine.resolveRatingAsync(entry.title, entry.year, !entry.isMovie(), entry, activity, r -> {
                if (r != null && r.hasScore()) {
                    meta.setText(String.format(java.util.Locale.US, "⭐ %.1f %s  ·  %s", r.score, r.source, kindTag));
                    meta.setTextColor(Color.rgb(245, 197, 66));
                }
            });
        }
        card.addView(meta);

        TextView title = new TextView(activity);
        title.setText(entry.title);
        title.setTextSize(13);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(4, 0, 4, 4);
        card.addView(title, new LinearLayout.LayoutParams(-1, -2));

        card.setOnClickListener(v -> {
            state.backStack.push(this::catalog);
            detail(entry);
        });
        card.setOnFocusChangeListener((v, focused) -> {
            GradientDrawable focusBg = new GradientDrawable();
            focusBg.setColor(focused ? Color.rgb(24, 30, 44) : Color.TRANSPARENT);
            focusBg.setCornerRadius(14);
            if (focused) focusBg.setStroke(3, Color.WHITE);
            card.setBackground(focusBg);
            card.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(130).start();
        });
    }

    public static void loadPoster(Activity act, ImageView target, String address) {
        if (address == null || address.isEmpty()) {
            target.setImageDrawable(null);
            return;
        }
        Bitmap cached = CACHE.get(address);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        target.setTag(address);
        IMAGES.submit(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(address).openConnection();
                conn.setRequestProperty("User-Agent", HttpSupport.USER_AGENT);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(8000);
                if (conn.getResponseCode() != 200) return;
                Bitmap bitmap;
                try (InputStream in = conn.getInputStream()) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
                if (bitmap == null) return;
                CACHE.put(address, bitmap);
                act.runOnUiThread(() -> {
                    if (!act.isDestroyed() && address.equals(target.getTag())) target.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Ficha enriquecida estilo Zapp TV (DetailInfoTemplate + RowPersonLiteInfoTemplate + EpisodeSelectTemplate).
     */
    public void detail(CatalogData.Entry entry) {
        setHeaderControlsVisible(false);
        state.selected = entry;
        state.selectedActor = null;
        state.season = 0;
        final int token = ++generation;
        if (pending != null) pending.cancel(true);
        results.removeAllViews();
        message.setText("");

        // Guardar en historial de vistos recientemente (como en Lunified/Zapp TV)
        ZappCatalogEngine.addHistory(activity, entry);

        // Barra superior de retorno
        LinearLayout topNav = row();
        topNav.setGravity(Gravity.CENTER_VERTICAL);
        Button backBtn = button(topNav, "←  Volver", () -> {
            if (!state.backStack.isEmpty()) state.backStack.pop().run();
            else { state.selected = null; catalog(); }
        }, 190);
        TextView provBadge = new TextView(activity);
        String pName = (entry.providerName != null && !entry.providerName.isEmpty()) ? entry.providerName : provider.name();
        provBadge.setText("FUENTE: " + pName.toUpperCase());
        provBadge.setTextSize(12);
        provBadge.setTextColor(Color.rgb(160, 178, 210));
        provBadge.setTypeface(null, Typeface.BOLD);
        provBadge.setPadding(16, 6, 16, 6);
        topNav.addView(provBadge);
        results.addView(topNav);

        // Contenedor principal 2 columnas (Póster a la izquierda + Metadatos y Acciones a la derecha)
        LinearLayout heroCard = new LinearLayout(activity);
        heroCard.setOrientation(LinearLayout.HORIZONTAL);
        heroCard.setPadding(22, 20, 22, 22);
        heroCard.setBackground(pillBg(Color.rgb(20, 25, 38), Color.rgb(48, 58, 84), 18));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, -2);
        heroLp.setMargins(0, 6, 16, 18);
        results.addView(heroCard, heroLp);

        // Póster grande con bordes redondeados (estilo Zapp TV)
        FrameLayout posterFrame = new FrameLayout(activity);
        posterFrame.setBackground(pillBg(Color.rgb(32, 38, 56), Color.rgb(82, 94, 130), 16));
        posterFrame.setClipToOutline(true);
        TextView posterFallback = new TextView(activity);
        posterFallback.setText(entry.title);
        posterFallback.setTextColor(Color.LTGRAY);
        posterFallback.setGravity(Gravity.CENTER);
        posterFallback.setPadding(12, 12, 12, 12);
        posterFrame.addView(posterFallback, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView posterImg = new ImageView(activity);
        posterImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterFrame.addView(posterImg, new FrameLayout.LayoutParams(-1, -1));
        loadPoster(activity, posterImg, entry.poster);
        boolean isPortraitDetail = activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int posterW = isPortraitDetail ? dp(115) : dp(180);
        int posterH = isPortraitDetail ? dp(165) : dp(260);
        heroCard.addView(posterFrame, new LinearLayout.LayoutParams(posterW, posterH));

        // Columna derecha: Título, Título original/AKA, Badges, Sinopsis, Botones de acción
        LinearLayout infoCol = new LinearLayout(activity);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(isPortraitDetail ? dp(12) : 26, 2, 8, 2);
        heroCard.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView titleView = new TextView(activity);
        titleView.setText(entry.title);
        titleView.setTextSize(26);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(null, Typeface.BOLD);
        infoCol.addView(titleView);

        if (entry.originalTitle != null && !entry.originalTitle.isEmpty() && !entry.originalTitle.equalsIgnoreCase(entry.title)) {
            TextView origView = new TextView(activity);
            origView.setText("Título original / AKA: " + entry.originalTitle);
            origView.setTextSize(14);
            origView.setTextColor(Color.rgb(175, 190, 220));
            origView.setPadding(0, 4, 0, 4);
            infoCol.addView(origView);
        }

        // Fila de insignias (Rating, Año, Duración, Tipo, Idioma)
        LinearLayout badgesRow = row();
        badgesRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
        brLp.setMargins(0, 10, 0, 12);
        infoCol.addView(badgesRow, brLp);

        RatingEngine.RatingInfo detailRating = RatingEngine.getRating(entry, activity);
        String ratingTxt = detailRating.hasScore()
                ? detailRating.formattedDetail()
                : "Sin calificación";
        TextView ratingBadge = addBadge(badgesRow, ratingTxt, Color.rgb(55, 44, 14), Color.rgb(250, 200, 50));
        if (!detailRating.hasScore()) {
            ratingBadge.setTextColor(Color.rgb(180, 190, 205));
            ratingBadge.setBackground(pillBg(Color.rgb(38, 44, 58), 0, 8));
        }
        if (entry.year > 0) addBadge(badgesRow, String.valueOf(entry.year), Color.rgb(34, 42, 62), Color.WHITE);
        addBadge(badgesRow, entry.isMovie() ? "PELÍCULA" : "SERIE TV", Color.rgb(36, 52, 84), Color.rgb(150, 205, 255));
        if (entry.runtime > 0) addBadge(badgesRow, entry.runtime + " min", Color.rgb(34, 42, 62), Color.LTGRAY);
        addBadge(badgesRow, "ESPAÑOL / LATINO", Color.rgb(20, 68, 48), Color.rgb(110, 240, 165));
        String provBadgeText = (entry.providerName != null && !entry.providerName.isEmpty()) ? entry.providerName : "CineHax HD";
        addBadge(badgesRow, "🌐 " + provBadgeText, Color.rgb(45, 36, 68), Color.rgb(215, 175, 255));

        final TextView synopsisView = new TextView(activity);
        String initialOverview = (entry.overview != null && entry.overview.trim().length() >= 15)
            ? entry.overview
            : (detailRating.overview != null && detailRating.overview.trim().length() >= 15)
                ? detailRating.overview
                : "Cargando sinopsis y detalles verificados en español...";
        synopsisView.setText(initialOverview);
        synopsisView.setTextSize(15);
        synopsisView.setTextColor(Color.rgb(220, 228, 240));
        synopsisView.setLineSpacing(4f, 1.08f);
        synopsisView.setMaxLines(10);
        synopsisView.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(synopsisView, new LinearLayout.LayoutParams(-1, -2));

        TextView tipView = new TextView(activity);
        tipView.setText("💡 Si el video se ve pixelado o anda lento, tocá '🌐 Elegir servidor' para probar otras fuentes o '📺 Reproductor externo' para abrir con VLC / JustPlayer.");
        tipView.setTextSize(12);
        tipView.setTextColor(Color.rgb(160, 180, 215));
        tipView.setPadding(0, 8, 0, 4);
        infoCol.addView(tipView);

        boolean needsScore = !detailRating.hasScore();
        boolean needsOverview = (entry.overview == null || entry.overview.trim().length() < 15)
                && (detailRating.overview == null || detailRating.overview.trim().length() < 15);
        if (needsScore || needsOverview) {
            RatingEngine.resolveRatingAsync(entry.title, entry.year, !entry.isMovie(), entry, activity, r -> {
                if (r != null) {
                    if (r.hasScore()) {
                        ratingBadge.setText(r.formattedDetail());
                        ratingBadge.setTextColor(Color.rgb(250, 200, 50));
                        ratingBadge.setBackground(pillBg(Color.rgb(55, 44, 14), 0, 8));
                    }
                    if (r.overview != null && r.overview.trim().length() >= 15) {
                        entry.setOverview(r.overview);
                        synopsisView.setText(r.overview);
                    } else if (entry.overview == null || entry.overview.trim().isEmpty()) {
                        synopsisView.setText("Ficha disponible en el catálogo de Leo TV. Seleccioná Reproducir o elegí un capítulo para iniciar la reproducción.");
                    }
                }
            });
        }

        // Botones de acción principales (Reproducir + Descargar + Favoritos) con scroll horizontal para celulares y TV
        android.widget.HorizontalScrollView actionsScroll = new android.widget.HorizontalScrollView(activity);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actionsRow = row();
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        actionsScroll.addView(actionsRow);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(-1, -2);
        actLp.setMargins(0, 16, 0, 0);
        infoCol.addView(actionsScroll, actLp);

        if (entry.directPlayback()) {
            Button playBtn = button(actionsRow, "▶   Reproducir ahora", () -> host.play(entry, 0, 0, entry.title), -2);
            playBtn.requestFocus();
            button(actionsRow, "🌐  Elegir servidor", () -> showSourceSelectionDialog(entry, 0, 0), -2);
            button(actionsRow, "📺  Reproductor externo", () -> host.playExternal(entry, 0, 0, entry.title), -2);
            button(actionsRow, "⬇   Descargar película", () -> host.download(entry, 0, 0, entry.title), -2);
        } else {
            Button playFirstBtn = button(actionsRow, "▶   Reproducir T1 · Cap. 1", () -> host.play(entry, 1, 1, entry.title + " · T1 E1"), -2);
            playFirstBtn.requestFocus();
            button(actionsRow, "🌐  Elegir servidor", () -> showSourceSelectionDialog(entry, 1, 1), -2);
            button(actionsRow, "📺  Reproductor externo", () -> host.playExternal(entry, 1, 1, entry.title + " · T1 E1"), -2);
            button(actionsRow, "⬇   Descargar T1 · Cap. 1", () -> host.download(entry, 1, 1, entry.title + " · T1 E1"), -2);
        }

        boolean favInitial = ZappCatalogEngine.isFavorite(activity, entry);
        final Button favBtn = new Button(activity);
        updateFavButtonStyle(favBtn, favInitial);
        LinearLayout.LayoutParams favLp = new LinearLayout.LayoutParams(230, 76);
        favLp.setMargins(4, 6, 10, 6);
        actionsRow.addView(favBtn, favLp);
        favBtn.setOnClickListener(v -> {
            boolean nowFav = ZappCatalogEngine.toggleFavorite(activity, entry);
            updateFavButtonStyle(favBtn, nowFav);
            Toast.makeText(activity, nowFav ? "Agregado a Favoritos ❤️" : "Quitado de Favoritos", Toast.LENGTH_SHORT).show();
        });
        favBtn.setOnFocusChangeListener((v, focused) -> updateFavButtonStyle(favBtn, ZappCatalogEngine.isFavorite(activity, entry), focused));

        boolean wlInitial = ZappCatalogEngine.isWatchLater(activity, entry);
        final Button wlBtn = new Button(activity);
        updateWatchLaterButtonStyle(wlBtn, wlInitial);
        LinearLayout.LayoutParams wlLp = new LinearLayout.LayoutParams(230, 76);
        wlLp.setMargins(4, 6, 12, 6);
        actionsRow.addView(wlBtn, wlLp);
        wlBtn.setOnClickListener(v -> {
            boolean nowWl = ZappCatalogEngine.toggleWatchLater(activity, entry);
            updateWatchLaterButtonStyle(wlBtn, nowWl);
            Toast.makeText(activity, nowWl ? "Guardado en Ver más tarde 🔖" : "Quitado de Ver más tarde", Toast.LENGTH_SHORT).show();
        });
        wlBtn.setOnFocusChangeListener((v, focused) -> updateWatchLaterButtonStyle(wlBtn, ZappCatalogEngine.isWatchLater(activity, entry), focused));

        Button trailerBtn = button(actionsRow, "🎬  Ver Tráiler (YouTube+)", () -> {
            Toast.makeText(activity, "Buscando tráiler oficial en YouTube+...", Toast.LENGTH_SHORT).show();
            String tQuery = entry.title + " " + (entry.year > 0 ? entry.year : "") + " trailer oficial espanol latino";
            YouTubeEngine.searchAsync(tQuery, ytResults -> {
                if (ytResults != null && !ytResults.isEmpty()) {
                    CatalogData.Entry yt = ytResults.get(0);
                    YouTubePlayerActivity.start(activity, yt.pageUrl, entry.title + " (Tráiler Oficial)", yt.originalTitle);
                } else {
                    Toast.makeText(activity, "No se encontró tráiler en YouTube+", Toast.LENGTH_SHORT).show();
                }
            });
        }, 270);
        LinearLayout.LayoutParams tLp = (LinearLayout.LayoutParams) trailerBtn.getLayoutParams();
        if (tLp != null) {
            tLp.setMargins(4, 6, 10, 6);
        }

        // SECCIÓN 1: REPARTO Y ACTORES CON FICHAS INDIVIDUALES (Estilo Zapp TV RowPersonLiteInfoTemplate)
        LinearLayout castSection = new LinearLayout(activity);
        castSection.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams castSecLp = new LinearLayout.LayoutParams(-1, -2);
        castSecLp.setMargins(0, 6, 16, 16);
        results.addView(castSection, castSecLp);

        TextView castHeader = new TextView(activity);
        castHeader.setText("REPARTO Y ACTORES  ·  TOCÁ UN ACTOR PARA VER SU FICHA Y FILMOGRAFÍA");
        castHeader.setTextSize(15);
        castHeader.setTextColor(Color.rgb(245, 197, 66));
        castHeader.setTypeface(null, Typeface.BOLD);
        castHeader.setPadding(4, 6, 4, 8);
        castSection.addView(castHeader);

        TextView castStatus = label("Cargando actores y fotos del reparto...", 14);
        castSection.addView(castStatus);

        HorizontalScrollView castScroll = new HorizontalScrollView(activity);
        castScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout castRail = row();
        castRail.setPadding(4, 6, 20, 8);
        castScroll.addView(castRail);
        castSection.addView(castScroll);

        NETWORK.submit(() -> {
            final List<ZappCatalogEngine.ActorItem> castList = ZappCatalogEngine.fetchCast(entry);
            activity.runOnUiThread(() -> {
                if (!current(token)) return;
                if (castList.isEmpty()) {
                    castStatus.setText("Reparto principal no informado para este título.");
                    return;
                }
                castSection.removeView(castStatus);
                for (ZappCatalogEngine.ActorItem actor : castList) {
                    castRail.addView(createActorCard(actor, entry));
                }
            });
        });

        // SECCIÓN 2: TEMPORADAS Y CAPÍTULOS CON MINIATURA 16:9 (Estilo Zapp TV EpisodeSelectTemplate)
        if (!entry.directPlayback()) {
            LinearLayout epSection = new LinearLayout(activity);
            epSection.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams epSecLp = new LinearLayout.LayoutParams(-1, -2);
            epSecLp.setMargins(0, 6, 16, 24);
            results.addView(epSection, epSecLp);

            TextView epHeader = new TextView(activity);
            epHeader.setText("TEMPORADAS Y CAPÍTULOS");
            epHeader.setTextSize(18);
            epHeader.setTextColor(Color.WHITE);
            epHeader.setTypeface(null, Typeface.BOLD);
            epHeader.setPadding(4, 4, 4, 8);
            epSection.addView(epHeader);

            HorizontalScrollView seasonScroll = new HorizontalScrollView(activity);
            seasonScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout seasonBar = row();
            seasonScroll.addView(seasonBar);
            epSection.addView(seasonScroll);

            HorizontalScrollView rangeScroll = new HorizontalScrollView(activity);
            rangeScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout rangeBar = row();
            rangeScroll.addView(rangeBar);
            epSection.addView(rangeScroll);

            TextView epLoading = label("Cargando temporadas y capítulos...", 15);
            epSection.addView(epLoading);

            LinearLayout epListContainer = new LinearLayout(activity);
            epListContainer.setOrientation(LinearLayout.VERTICAL);
            epSection.addView(epListContainer);

            NETWORK.submit(() -> {
                final JSONArray seasons = ZappCatalogEngine.fetchSeasonsWithFallback(entry, provider);
                activity.runOnUiThread(() -> {
                    if (!current(token)) return;
                    if (seasons == null || seasons.length() == 0) {
                        epLoading.setText("No se encontraron temporadas disponibles.");
                        return;
                    }
                    int firstSeason = 1;
                    for (int i = 0; i < seasons.length(); i++) {
                        JSONObject sObj = seasons.optJSONObject(i);
                        if (sObj == null) continue;
                        final int sNum = sObj.optInt("season", i + 1);
                        if (i == 0) firstSeason = sNum;
                        String sLabel = sObj.optString("name", "Temporada " + sNum);
                        if (sObj.has("count") && sObj.optInt("count") > 0) {
                            sLabel += " (" + sObj.optInt("count") + " caps.)";
                        }
                        button(seasonBar, sLabel, () -> loadSeasonEpisodesVisual(entry, sNum, rangeBar, epLoading, epListContainer, token), -2);
                    }
                    loadSeasonEpisodesVisual(entry, firstSeason, rangeBar, epLoading, epListContainer, token);
                });
            });
        } else {
            backBtn.setNextFocusDownId(View.NO_ID);
        }
    }

    private void showSourceSelectionDialog(CatalogData.Entry entry, int season, int episode) {
        final String[] providers = entry.isMovie()
                ? new String[] { "CineHax", "SoloLatino", "Cuevana", "CineCalidad", "Pelispedia" }
                : new String[] { "CineHax", "SoloLatino", "Cuevana", "Seriesflix", "Pelispedia", "LatAnime", "TioAnime" };

        String[] display = new String[providers.length + 1];
        for (int i = 0; i < providers.length; i++) {
            String p = providers[i];
            if ("CineHax".equals(p)) display[i] = "🌟 CineHax (Vimeos Full HD 1080p)";
            else if ("SoloLatino".equals(p)) display[i] = "🌐 SoloLatino (Multi-calidad / Audio Latino)";
            else if ("Cuevana".equals(p)) display[i] = "🌐 Cuevana (Servidores rápidos)";
            else if ("CineCalidad".equals(p)) display[i] = "🌐 CineCalidad (Estrenos en HD)";
            else if ("Pelispedia".equals(p)) display[i] = "🌐 Pelispedia";
            else if ("Seriesflix".equals(p)) display[i] = "🌐 Seriesflix";
            else if ("LatAnime".equals(p) || "TioAnime".equals(p)) display[i] = "⛩️ " + p + " (Anime HD)";
            else display[i] = "🌐 " + p;
        }
        display[providers.length] = "📺 Abrir con Reproductor Externo (VLC / JustPlayer)";

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(activity);
        b.setTitle("🌐 Seleccionar Servidor / Fuente");
        b.setItems(display, (dialog, which) -> {
            dialog.dismiss();
            if (which < providers.length) {
                String chosenProv = providers[which];
                Toast.makeText(activity, "Conectando con " + chosenProv + "...", Toast.LENGTH_SHORT).show();
                host.playWithProvider(entry, season, episode, entry.title, chosenProv);
            } else {
                host.playExternal(entry, season, episode, entry.title);
            }
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void loadSeasonEpisodesVisual(CatalogData.Entry entry, int seasonNum, LinearLayout rangeBar,
                                          TextView epStatus, LinearLayout epListContainer, int token) {
        state.season = seasonNum;
        rangeBar.removeAllViews();
        epListContainer.removeAllViews();
        epStatus.setVisibility(View.VISIBLE);
        epStatus.setText("Cargando capítulos con vista previa de la Temporada " + seasonNum + "...");

        NETWORK.submit(() -> {
            final List<ZappCatalogEngine.EpisodeCard> episodes = ZappCatalogEngine.fetchRichEpisodes(entry, provider, seasonNum, 1);
            activity.runOnUiThread(() -> {
                if (!current(token)) return;
                if (episodes.isEmpty()) {
                    epStatus.setText("No hay capítulos disponibles en la Temporada " + seasonNum + ".");
                    return;
                }
                epStatus.setText("Temporada " + seasonNum + "  ·  " + episodes.size() + " capítulos disponibles");

                final int pageSize = 25;
                if (episodes.size() > pageSize) {
                    int totalGroups = (episodes.size() + pageSize - 1) / pageSize;
                    for (int g = 0; g < totalGroups; g++) {
                        final int startIdx = g * pageSize;
                        final int endIdx = Math.min(episodes.size(), startIdx + pageSize);
                        int firstEpNum = episodes.get(startIdx).episode;
                        int lastEpNum = episodes.get(endIdx - 1).episode;
                        button(rangeBar, "Cap. " + firstEpNum + " – " + lastEpNum,
                            () -> renderEpisodeSlice(entry, seasonNum, episodes.subList(startIdx, endIdx), epListContainer), -2);
                    }
                    renderEpisodeSlice(entry, seasonNum, episodes.subList(0, pageSize), epListContainer);
                } else {
                    renderEpisodeSlice(entry, seasonNum, episodes, epListContainer);
                }
            });
        });
    }

    private void renderEpisodeSlice(CatalogData.Entry entry, int seasonNum,
                                    List<ZappCatalogEngine.EpisodeCard> slice, LinearLayout container) {
        container.removeAllViews();
        for (ZappCatalogEngine.EpisodeCard ep : slice) {
            container.addView(createEpisodeCardView(entry, seasonNum, ep));
        }
    }

    private View createEpisodeCardView(CatalogData.Entry entry, int seasonNum, ZappCatalogEngine.EpisodeCard ep) {
        LinearLayout rowCard = new LinearLayout(activity);
        rowCard.setOrientation(LinearLayout.HORIZONTAL);
        rowCard.setGravity(Gravity.CENTER_VERTICAL);
        rowCard.setPadding(12, 10, 16, 10);
        rowCard.setFocusable(true);
        rowCard.setClickable(true);
        rowCard.setBackground(pillBg(cardDark, Color.rgb(48, 58, 82), 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 12);
        rowCard.setLayoutParams(lp);

        // Miniatura 16:9 del capítulo (estilo Zapp TV EpisodeSelectTemplate)
        FrameLayout thumbBox = new FrameLayout(activity);
        thumbBox.setBackground(pillBg(Color.rgb(32, 38, 56), Color.rgb(68, 78, 106), 10));
        thumbBox.setClipToOutline(true);

        TextView epNumFallback = new TextView(activity);
        epNumFallback.setText("CAP. " + ep.episode);
        epNumFallback.setTextSize(13);
        epNumFallback.setTextColor(Color.rgb(185, 198, 222));
        epNumFallback.setTypeface(null, Typeface.BOLD);
        epNumFallback.setGravity(Gravity.CENTER);
        thumbBox.addView(epNumFallback, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView thumbImg = new ImageView(activity);
        thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbBox.addView(thumbImg, new FrameLayout.LayoutParams(-1, -1));
        String thumbUrl = (ep.thumbUrl != null && !ep.thumbUrl.isEmpty()) ? ep.thumbUrl : entry.poster;
        loadPoster(activity, thumbImg, thumbUrl);

        // Icono ▶ superpuesto en esquina inferior derecha de la miniatura
        TextView playBadge = new TextView(activity);
        playBadge.setText(" ▶ ");
        playBadge.setTextSize(11);
        playBadge.setTextColor(Color.WHITE);
        playBadge.setBackground(pillBg(Color.argb(200, 129, 107, 245), 0, 6));
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        pbLp.setMargins(6, 6, 6, 6);
        thumbBox.addView(playBadge, pbLp);

        rowCard.addView(thumbBox, new LinearLayout.LayoutParams(172, 98));

        // Columna de texto del episodio
        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(18, 2, 8, 2);
        rowCard.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView epTitle = new TextView(activity);
        epTitle.setText("T" + seasonNum + " · Cap. " + ep.episode + "  —  " + ep.title);
        epTitle.setTextSize(16);
        epTitle.setTextColor(Color.WHITE);
        epTitle.setTypeface(null, Typeface.BOLD);
        epTitle.setSingleLine(true);
        epTitle.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(epTitle);

        String subMeta = (ep.runtime > 0 ? ep.runtime + " min" : "45 min")
            + (ep.airDate != null && !ep.airDate.isEmpty() ? "  ·  Emisión: " + ep.airDate : "")
            + "  ·  Audio Latino / Sub";
        TextView epMeta = new TextView(activity);
        epMeta.setText(subMeta);
        epMeta.setTextSize(12);
        epMeta.setTextColor(Color.rgb(245, 197, 66));
        epMeta.setPadding(0, 2, 0, 4);
        textCol.addView(epMeta);

        String syn = (ep.overview != null && !ep.overview.isEmpty() && !"null".equalsIgnoreCase(ep.overview.trim()))
            ? ep.overview.trim()
            : ("Capítulo " + ep.episode + " de la temporada " + seasonNum + " de " + entry.title + ".");
        TextView epSyn = new TextView(activity);
        epSyn.setText(syn);
        epSyn.setTextSize(13);
        epSyn.setTextColor(Color.rgb(195, 205, 222));
        epSyn.setMaxLines(2);
        epSyn.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(epSyn);

        String playLabel = entry.title + " · T" + seasonNum + " E" + ep.episode + " · " + ep.title;
        rowCard.setOnClickListener(v -> host.play(entry, seasonNum, ep.episode, playLabel));

        Button epDlBtn = new Button(activity);
        epDlBtn.setText("⬇");
        epDlBtn.setTextSize(14);
        epDlBtn.setTextColor(Color.WHITE);
        epDlBtn.setFocusable(true);
        epDlBtn.setContentDescription("Descargar capítulo");
        GradientDrawable edBg = new GradientDrawable();
        edBg.setColor(Color.rgb(36, 50, 78));
        edBg.setCornerRadius(10);
        edBg.setStroke(2, Color.rgb(65, 85, 130));
        epDlBtn.setBackground(edBg);
        epDlBtn.setOnClickListener(v -> host.download(entry, seasonNum, ep.episode, playLabel));
        epDlBtn.setOnFocusChangeListener((v, f) -> {
            GradientDrawable g = new GradientDrawable();
            g.setColor(f ? Color.rgb(55, 75, 120) : Color.rgb(36, 50, 78));
            g.setCornerRadius(10);
            g.setStroke(f ? 3 : 2, f ? Color.WHITE : Color.rgb(65, 85, 130));
            epDlBtn.setBackground(g);
        });
        LinearLayout.LayoutParams edLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        edLp.setMargins(dp(8), 0, dp(4), 0);
        rowCard.addView(epDlBtn, edLp);
        rowCard.setOnFocusChangeListener((v, focused) -> {
            rowCard.setBackground(pillBg(
                focused ? Color.rgb(38, 48, 76) : cardDark,
                focused ? Color.WHITE : Color.rgb(48, 58, 82),
                14
            ));
            rowCard.animate().scaleX(focused ? 1.02f : 1f).scaleY(focused ? 1.02f : 1f).setDuration(120).start();
        });
        return rowCard;
    }

    private View createActorCard(ZappCatalogEngine.ActorItem actor, CatalogData.Entry fromEntry) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(8, 8, 8, 10);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(pillBg(cardDark, Color.rgb(52, 62, 88), 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(148, 224);
        lp.setMargins(0, 0, 14, 4);
        card.setLayoutParams(lp);

        FrameLayout photoBox = new FrameLayout(activity);
        photoBox.setBackground(pillBg(Color.rgb(34, 42, 62), Color.rgb(75, 88, 120), 12));
        photoBox.setClipToOutline(true);

        String initials = actor.name.length() >= 2 ? actor.name.substring(0, 2).toUpperCase() : "★";
        TextView initView = new TextView(activity);
        initView.setText(initials);
        initView.setTextSize(22);
        initView.setTextColor(Color.rgb(190, 205, 230));
        initView.setTypeface(null, Typeface.BOLD);
        initView.setGravity(Gravity.CENTER);
        photoBox.addView(initView, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        ImageView photo = new ImageView(activity);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoBox.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        loadPoster(activity, photo, actor.photoUrl);
        card.addView(photoBox, new LinearLayout.LayoutParams(128, 142));

        TextView nameView = new TextView(activity);
        nameView.setText(actor.name);
        nameView.setTextSize(13);
        nameView.setTextColor(Color.WHITE);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setPadding(2, 6, 2, 0);
        card.addView(nameView, new LinearLayout.LayoutParams(-1, -2));

        TextView roleView = new TextView(activity);
        roleView.setText(actor.role != null && !actor.role.isEmpty() ? actor.role : "Ver filmografía");
        roleView.setTextSize(11);
        roleView.setTextColor(Color.rgb(175, 190, 215));
        roleView.setGravity(Gravity.CENTER);
        roleView.setSingleLine(true);
        roleView.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(roleView, new LinearLayout.LayoutParams(-1, -2));

        card.setOnClickListener(v -> showActorSheet(actor, fromEntry));
        card.setOnFocusChangeListener((v, focused) -> {
            card.setBackground(pillBg(
                focused ? Color.rgb(44, 54, 86) : cardDark,
                focused ? Color.rgb(245, 197, 66) : Color.rgb(52, 62, 88),
                14
            ));
            card.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(130).start();
        });
        return card;
    }

    /**
     * Ficha Individual de Actor (estilo Zapp TV PersonActivity / PersonInfoTemplate + Lunified PeopleTvFragment).
     */
    public void showActorSheet(ZappCatalogEngine.ActorItem actor, CatalogData.Entry returnToEntry) {
        setHeaderControlsVisible(false);
        if (returnToEntry != null) {
            state.backStack.push(() -> detail(returnToEntry));
        }
        state.selectedActor = actor;
        final int token = ++generation;
        if (pending != null) pending.cancel(true);
        results.removeAllViews();
        message.setText("");

        LinearLayout topNav = row();
        topNav.setGravity(Gravity.CENTER_VERTICAL);
        Button backBtn = button(topNav, "←  Volver", () -> {
            if (!state.backStack.isEmpty()) state.backStack.pop().run();
            else if (state.selected != null) detail(state.selected);
            else catalog();
        }, 190);
        TextView sectionTag = new TextView(activity);
        sectionTag.setText("FICHA INDIVIDUAL DE ACTOR / ACTRIZ  ·  ZAPP TV");
        sectionTag.setTextSize(13);
        sectionTag.setTextColor(Color.rgb(245, 197, 66));
        sectionTag.setTypeface(null, Typeface.BOLD);
        sectionTag.setPadding(16, 6, 16, 6);
        topNav.addView(sectionTag);
        results.addView(topNav);

        // Cabecera biográfica del actor
        LinearLayout bioCard = new LinearLayout(activity);
        bioCard.setOrientation(LinearLayout.HORIZONTAL);
        bioCard.setGravity(Gravity.CENTER_VERTICAL);
        bioCard.setPadding(22, 18, 22, 18);
        bioCard.setBackground(pillBg(Color.rgb(20, 26, 40), Color.rgb(60, 74, 108), 18));
        LinearLayout.LayoutParams bioLp = new LinearLayout.LayoutParams(-1, -2);
        bioLp.setMargins(0, 8, 16, 18);
        results.addView(bioCard, bioLp);

        FrameLayout avatarBox = new FrameLayout(activity);
        avatarBox.setBackground(pillBg(Color.rgb(34, 42, 62), Color.rgb(245, 197, 66), 16));
        avatarBox.setClipToOutline(true);
        ImageView avatarImg = new ImageView(activity);
        avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarBox.addView(avatarImg, new FrameLayout.LayoutParams(-1, -1));
        loadPoster(activity, avatarImg, actor.photoUrl);
        bioCard.addView(avatarBox, new LinearLayout.LayoutParams(165, 215));

        LinearLayout actorInfo = new LinearLayout(activity);
        actorInfo.setOrientation(LinearLayout.VERTICAL);
        actorInfo.setPadding(24, 4, 8, 4);
        bioCard.addView(actorInfo, new LinearLayout.LayoutParams(0, -2, 1));

        TextView actorName = new TextView(activity);
        actorName.setText(actor.name);
        actorName.setTextSize(26);
        actorName.setTextColor(Color.WHITE);
        actorName.setTypeface(null, Typeface.BOLD);
        actorInfo.addView(actorName);

        if (actor.role != null && !actor.role.isEmpty()) {
            TextView roleTxt = new TextView(activity);
            roleTxt.setText("Personaje destacado: " + actor.role);
            roleTxt.setTextSize(15);
            roleTxt.setTextColor(Color.rgb(245, 197, 66));
            roleTxt.setPadding(0, 4, 0, 4);
            actorInfo.addView(roleTxt);
        }

        final TextView metaBio = new TextView(activity);
        metaBio.setText("Consultando datos biográficos y filmografía completa...");
        metaBio.setTextSize(14);
        metaBio.setTextColor(Color.rgb(190, 205, 228));
        metaBio.setPadding(0, 6, 0, 6);
        actorInfo.addView(metaBio);

        TextView worksHeader = new TextView(activity);
        worksHeader.setText("PELÍCULAS Y SERIES DE " + actor.name.toUpperCase());
        worksHeader.setTextSize(17);
        worksHeader.setTextColor(Color.WHITE);
        worksHeader.setTypeface(null, Typeface.BOLD);
        worksHeader.setPadding(4, 8, 4, 10);
        results.addView(worksHeader);

        final LinearLayout worksContainer = new LinearLayout(activity);
        worksContainer.setOrientation(LinearLayout.VERTICAL);
        results.addView(worksContainer);
        backBtn.requestFocus();

        NETWORK.submit(() -> {
            final ZappCatalogEngine.ActorProfile profile = ZappCatalogEngine.fetchActorProfile(actor);
            activity.runOnUiThread(() -> {
                if (!current(token)) return;
                StringBuilder sb = new StringBuilder();
                if (profile.actor.country != null && !profile.actor.country.isEmpty()) {
                    sb.append("País: ").append(profile.actor.country).append("   ·   ");
                }
                if (profile.actor.birthday != null && !profile.actor.birthday.isEmpty()) {
                    sb.append("Nacimiento: ").append(profile.actor.birthday).append("   ·   ");
                }
                sb.append(profile.works.size()).append(" títulos disponibles en catálogo");
                metaBio.setText(sb.toString());

                if (profile.works.isEmpty()) {
                    worksContainer.addView(label("No se encontraron otras obras registradas para este actor.", 16));
                    return;
                }
                int columns = activity.getResources().getDisplayMetrics().widthPixels >= 1100 ? 5 : 3;
                int availWidth = Math.max(700, activity.getResources().getDisplayMetrics().widthPixels - 210);
                int size = (availWidth - (columns - 1) * 18) / columns;
                GridLayout grid = new GridLayout(activity);
                grid.setColumnCount(columns);
                worksContainer.addView(grid);
                for (CatalogData.Entry work : profile.works) {
                    card(grid, work, size);
                }
            });
        });
    }

    private TextView addBadge(LinearLayout row, String textVal, int bgColor, int textColor) {
        TextView badge = new TextView(activity);
        badge.setText(textVal);
        badge.setTextSize(12);
        badge.setTextColor(textColor);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(12, 5, 12, 5);
        badge.setBackground(pillBg(bgColor, 0, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, 10, 0);
        row.addView(badge, lp);
        return badge;
    }

    private void updateFavButtonStyle(Button btn, boolean isFav) {
        updateFavButtonStyle(btn, isFav, btn.isFocused());
    }

    private void updateFavButtonStyle(Button btn, boolean isFav, boolean focused) {
        btn.setText(isFav ? "❤️   En Favoritos" : "🤍   Agregar a Favoritos");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setAllCaps(false);
        int fill = isFav ? Color.rgb(175, 28, 62) : panel;
        if (focused) fill = isFav ? Color.rgb(210, 38, 78) : violet;
        btn.setBackground(pillBg(fill, focused ? Color.WHITE : Color.rgb(90, 102, 138), 14));
    }

    private void updateWatchLaterButtonStyle(Button btn, boolean isWl) {
        updateWatchLaterButtonStyle(btn, isWl, btn.isFocused());
    }

    private void updateWatchLaterButtonStyle(Button btn, boolean isWl, boolean focused) {
        btn.setText(isWl ? "🔖   En Ver más tarde" : "📑   Ver más tarde");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setAllCaps(false);
        int fill = isWl ? Color.rgb(38, 110, 175) : panel;
        if (focused) fill = isWl ? Color.rgb(48, 140, 220) : violet;
        btn.setBackground(pillBg(fill, focused ? Color.WHITE : Color.rgb(90, 102, 138), 14));
    }

    public boolean back() {
        if (!state.backStack.isEmpty()) {
            state.backStack.pop().run();
            return true;
        }
        if (state.selectedActor != null) {
            state.selectedActor = null;
            if (state.selected != null) detail(state.selected);
            else catalog();
            return true;
        }
        if (state.selected != null) {
            state.selected = null;
            catalog();
            return true;
        }
        return false;
    }

    public void dispose() {
        disposed = true;
        ++generation;
        if (pending != null) pending.cancel(true);
    }
}
