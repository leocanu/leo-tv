# -*- coding: utf-8 -*-
"""
Script de actualización para Leo TV:
1. Tarjeta '••• Ver todo' al final de cada carrusel que abre pantalla completa de 6 columnas
2. Carga paginada automática en la cuadrícula de 6 columnas con ScrollListener y botón D-Pad
3. Conservación de foco y posición de desplazamiento al volver a Inicio
4. Pantalla Inicio con ~20 carruseles relevantes priorizando turcas, doramas, anime y películas
5. Secciones independientes: Favoritos y Ver más tarde con almacenamiento persistente sin duplicados
6. Personalización de Inicio: Reordenar y Ocultar carruseles con persistencia local
"""

import sys

with open("app/src/main/java/ar/appvideo/player/MainActivity.java", "r", encoding="utf-8") as f:
    content = f.read()

# 1. Agregar import de Toast si no existe
if "import android.widget.Toast;" not in content:
    content = content.replace(
        "import android.widget.GridLayout;",
        "import android.widget.GridLayout;\nimport android.widget.Toast;"
    )

# 2. Agregar variables de estado de retorno de Category Grid
state_vars = """    private int returnScrollY = 0;
    private String returnCarouselId = "";
"""
if "private int returnScrollY = 0;" not in content:
    content = content.replace(
        "private LinearLayout root;",
        "private LinearLayout root;\n" + state_vars
    )

# 3. Código de createSeeAllCard, openCategoryFullGrid, returnToHomeFromCategoryGrid, showCategoryGridView, createGridPosterCard
grid_methods = """
    private android.view.View createSeeAllCard(String categoryTitle, String providerName, String kind, String query, String carouselId) {
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
        return card;
    }

    private void openCategoryFullGrid(String categoryTitle, String providerName, String kind, String query, String carouselId) {
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
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
        if (resolutionTask != null) resolutionTask.cancel(true);
        ++providerPlayRequest; providerLoading = false;
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

        TextView provBadge = text(providerName + "  ·  Catálogo Completo Multidimensional (6 columnas)", 13, Color.rgb(168, 178, 195));
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
            if (focused) b.setStroke(dp(3), Color.WHITE);
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
            @Override public void run() {
                if (isLoading[0] || !hasMore[0] || prov == null) return;
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
                                    e
                                ));
                            }
                        }
                        final boolean nextAvailable = res != null && (res.next || res.entries.size() >= 12);
                        runOnUiThread(() -> {
                            if (!page.equals("category_grid") || isDestroyed()) return;
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

                            counterText.setText("Cargados: " + totalLoaded[0] + " títulos  ·  Página " + (currentPage[0] - 1));
                            if (hasMore[0]) {
                                statusText.setText("⬇ Desplázate hacia abajo para cargar la página " + currentPage[0]);
                                loadMoreBtn.setText("⬇  Cargar página " + currentPage[0]);
                                loadMoreBtn.setEnabled(true);
                                loadMoreBtn.setVisibility(android.view.View.VISIBLE);
                            } else {
                                statusText.setText("✓ Has llegado al final del catálogo (" + totalLoaded[0] + " títulos)");
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
            if (focused) b.setStroke(dp(3), Color.WHITE);
            loadMoreBtn.setBackground(b);
            if (focused && !isLoading[0] && hasMore[0]) {
                loadNextPage.run();
            }
        });

        gridScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isLoading[0] || !hasMore[0]) return;
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

        int posterH = (int)(cardWidth * 1.45f);
        card.addView(posterWrap, new LinearLayout.LayoutParams(-1, posterH));

        TextView meta = text(item.tag, 11, Color.rgb(168, 178, 195));
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
            if (focused) fBg.setStroke(dp(3), Color.rgb(245, 197, 66));
            card.setBackground(fBg);
            card.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
            card.setElevation(focused ? 10f : 0f);
        });

        card.setOnClickListener(v -> onPosterClicked(item));
        return card;
    }
"""

# Reemplazar addPosterRow y populateLiveRow existentes por las versiones sobrecargadas
old_add_poster_row = """    private LinearLayout addPosterRow(LinearLayout body, String heading, java.util.List<PosterItem> initialItems) {
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
    }"""

new_add_poster_row = grid_methods + """
    private LinearLayout addPosterRow(LinearLayout body, String heading, java.util.List<PosterItem> initialItems) {
        return addPosterRow(body, heading, initialItems, "CineHax", "movie", "", "");
    }

    private LinearLayout addPosterRow(LinearLayout body, String heading, java.util.List<PosterItem> initialItems,
                                      String providerName, String kind, String query, String carouselId) {
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
        if (providerName != null && !providerName.isEmpty() && !"SK Tech".equals(providerName)) {
            rail.addView(createSeeAllCard(heading, providerName, kind, query, carouselId));
        }
        scroller.addView(rail);
        body.addView(scroller);
        return rail;
    }

    private void populateLiveRow(LinearLayout rail, String providerName, String kind, String query, int pageNum, int offset, boolean isPrimaryRow) {
        populateLiveRow(rail, providerName, kind, query, pageNum, offset, isPrimaryRow, "", "");
    }

    private void populateLiveRow(LinearLayout rail, String providerName, String kind, String query, int pageNum, int offset, boolean isPrimaryRow,
                                String categoryTitle, String carouselId) {
        VideoProvider prov = VideoProviders.find(providerName);
        if (prov == null) return;
        new Thread(() -> {
            try {
                CatalogData.CatalogPage res = prov.catalog(kind, query, pageNum);
                if (res == null || res.entries.isEmpty()) return;
                java.util.List<PosterItem> live = new java.util.ArrayList<>();
                for (int i = offset; i < res.entries.size() && live.size() < 14; i++) {
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
                    if (providerName != null && !providerName.isEmpty() && !"SK Tech".equals(providerName)) {
                        rail.addView(createSeeAllCard(categoryTitle, providerName, kind, query, carouselId));
                    }
                    if ((hadFocus || isPrimaryRow) && rail.getChildCount() > 0) {
                        rail.getChildAt(0).requestFocus();
                    }
                });
            } catch (Exception ignored) {}
        }, "live-row-" + providerName + "-" + kind + "-" + pageNum).start();
    }"""

if old_add_poster_row in content:
    content = content.replace(old_add_poster_row, new_add_poster_row)
else:
    print("Warning: old_add_poster_row not matched exactly! Checking variations...")
    sys.exit(1)

# 4. Métodos para las listas por defecto adicionales de los ~20 carruseles
extra_defaults = """
    private java.util.List<PosterItem> defaultCrime2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Peaky Blinders", 2022, true, "https://image.tmdb.org/t/p/w342/vUUqzWa2LnHIVqkaKVlVGkVcZIW.jpg", prov, "Peaky Blinders", null),
            new PosterItem("Breaking Bad", 2013, true, "https://image.tmdb.org/t/p/w342/ztkUQFLlC19CCMYHW9o1zWhJRNq.jpg", prov, "Breaking Bad", null),
            new PosterItem("Fargo", 2024, true, "https://image.tmdb.org/t/p/w342/6fn4B5g44oFvjEa1RkWc5E7VwL8.jpg", prov, "Fargo", null),
            new PosterItem("Better Call Saul", 2022, true, "https://image.tmdb.org/t/p/w342/fC2HDm5t0kHjUmYIMBYVZsXL0qj.jpg", prov, "Better Call Saul", null),
            new PosterItem("Narcos", 2017, true, "https://image.tmdb.org/t/p/w342/rTmal9fMfwhBXoNsWljK2aoVo4U.jpg", prov, "Narcos", null)
        );
    }

    private java.util.List<PosterItem> defaultDrama2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("El Sultán (Muhteşem Yüzyıl)", 2014, true, "https://image.tmdb.org/t/p/w342/dZ8Q6PZ8vQj4i0iR9C8qg2N2m9O.jpg", prov, "El Sultan", null),
            new PosterItem("Resurrección: Ertuğrul", 2019, true, "https://image.tmdb.org/t/p/w342/gB5tZ8e6P7z8i0iR9C8qg2N2m9O.jpg", prov, "Ertugrul", null),
            new PosterItem("The Crown", 2023, true, "https://image.tmdb.org/t/p/w342/1DMEzmAe2c33pWz2wK8c8jY1U2b.jpg", prov, "The Crown", null),
            new PosterItem("Succession", 2023, true, "https://image.tmdb.org/t/p/w342/7nR65n58H6x3Pz2wK8c8jY1U2b.jpg", prov, "Succession", null),
            new PosterItem("Chernobyl", 2019, true, "https://image.tmdb.org/t/p/w342/hlLXt2tOPT6RRnjiUmoxyG1LTFi.jpg", prov, "Chernobyl", null)
        );
    }

    private java.util.List<PosterItem> defaultLatin2026(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Betty la Fea: La Historia Continúa", 2024, true, "https://image.tmdb.org/t/p/w342/w9J4q4O1H5X4C6s9p1j8N3X5P.jpg", prov, "Betty la fea", null),
            new PosterItem("El Encargado", 2024, true, "https://image.tmdb.org/t/p/w342/u3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", prov, "El Encargado", null),
            new PosterItem("Monarca", 2021, true, "https://image.tmdb.org/t/p/w342/q3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", prov, "Monarca", null),
            new PosterItem("La Casa de las Flores", 2020, true, "https://image.tmdb.org/t/p/w342/8jClvC5U756Uhd8zDYek50ihO49.jpg", prov, "Casa de las flores", null),
            new PosterItem("Los Simuladores", 2004, true, "https://image.tmdb.org/t/p/w342/9JBEPL0T2eHNG7FvJ6n5KekjOqL.jpg", prov, "Simuladores", null)
        );
    }

    private java.util.List<PosterItem> defaultAsianFantasy(String prov) {
        return java.util.Arrays.asList(
            new PosterItem("Aterrizaje de Emergencia en tu Corazón", 2020, true, "https://image.tmdb.org/t/p/w342/q3B2Jq8e2c33pWz2wK8c8jY1U2b.jpg", "DoramasFlix", "Crash Landing", null),
            new PosterItem("Goblin: El Guardián Solitario", 2016, true, "https://image.tmdb.org/t/p/w342/8jClvC5U756Uhd8zDYek50ihO49.jpg", "DoramasFlix", "Goblin", null),
            new PosterItem("Belleza Verdadera (True Beauty)", 2021, true, "https://image.tmdb.org/t/p/w342/xcOnCOpwe86Deau7P1VQNe0X6cg.jpg", "DoramasFlix", "True Beauty", null),
            new PosterItem("Mi Amor de las Estrellas", 2014, true, "https://image.tmdb.org/t/p/w342/xx3Zurg2mVep7UhXAjeYuetOvsB.jpg", "DoramasFlix", "My Love from the Star", null),
            new PosterItem("La Gloria", 2023, true, "https://image.tmdb.org/t/p/w342/MIcVR5JVNBjvmd6xW6xnNbory5.jpg", "DoramasFlix", "The Glory", null)
        );
    }

    private java.util.List<PosterItem> defaultAnimeShonen() {
        return java.util.Arrays.asList(
            new PosterItem("Demon Slayer: Kimetsu no Yaiba", 2024, true, "https://image.tmdb.org/t/p/w342/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg", "LatAnime", "Demon Slayer", null),
            new PosterItem("Jujutsu Kaisen", 2023, true, "https://image.tmdb.org/t/p/w342/hFWP5HkbVEe40hrXgtCeKpRFHG2.jpg", "LatAnime", "Jujutsu Kaisen", null),
            new PosterItem("Attack on Titan (Shingeki no Kyojin)", 2023, true, "https://image.tmdb.org/t/p/w342/hTP1DtLGFamjfu8WqjnuQdP1n4i.jpg", "LatAnime", "Shingeki", null),
            new PosterItem("One Piece", 2024, true, "https://image.tmdb.org/t/p/w342/dum1NmioVvXjH4x1q2q3q4r5.jpg", "LatAnime", "One Piece", null),
            new PosterItem("Solo Leveling", 2024, true, "https://image.tmdb.org/t/p/w342/geCRueV3ElhRTr0Q2xBuMiXLzAc.jpg", "LatAnime", "Solo Leveling", null)
        );
    }

    private java.util.List<PosterItem> defaultClassicMovies() {
        return java.util.Arrays.asList(
            new PosterItem("La Noche de los Muertos Vivos", 1968, false, "https://archive.org/services/img/night_of_the_living_dead", "Internet Archive", "Night of the living dead", null),
            new PosterItem("Metrópolis", 1927, false, "https://archive.org/services/img/Metropolis_1927", "Internet Archive", "Metropolis", null),
            new PosterItem("Charada", 1963, false, "https://archive.org/services/img/Charade_1963", "Internet Archive", "Charade", null),
            new PosterItem("Superman (1941)", 1941, false, "https://archive.org/services/img/superman_1941", "Internet Archive", "Superman", null),
            new PosterItem("El Maquinista de la General", 1926, false, "https://archive.org/services/img/TheGeneralBusterKeaton", "Internet Archive", "The General", null)
        );
    }

    private java.util.List<PosterItem> defaultBlenderMovies() {
        return java.util.Arrays.asList(
            new PosterItem("Big Buck Bunny", 2008, false, "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4", "Blender Open Movies", "Big Buck Bunny", null),
            new PosterItem("Elephants Dream", 2006, false, "https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov", "Blender Open Movies", "Elephants Dream", null),
            new PosterItem("Sintel", 2010, false, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "Blender Open Movies", "Sintel", null),
            new PosterItem("Tears of Steel", 2012, false, "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "Blender Open Movies", "Tears of Steel", null)
        );
    }

    private java.util.List<String> getDefaultCarouselOrder() {
        return java.util.Arrays.asList(
            "top10", "turkish", "kdramas", "anime", "movies2026", "series2026",
            "action", "terror", "scifi", "comedy", "romance", "crime",
            "historical", "latin", "asian_fantasy", "shonen", "kids",
            "classic", "blender", "adults", "sports"
        );
    }

    private String getCarouselDisplayName(String id) {
        if ("top10".equals(id)) return "🔥  Top 10 en Leo TV hoy";
        if ("turkish".equals(id)) return "🇹🇷  Novelas y Series Turcas";
        if ("kdramas".equals(id)) return "🇰🇷  K-Dramas y Series Coreanas";
        if ("anime".equals(id)) return "🇯🇵  Japón y Anime Popular";
        if ("movies2026".equals(id)) return "🎬  Películas nuevas · 2026";
        if ("series2026".equals(id)) return "📺  Series nuevas · 2026";
        if ("action".equals(id)) return "💥  Acción y Aventura Sin Límites";
        if ("terror".equals(id)) return "👻  Terror, Suspenso y Misterio";
        if ("scifi".equals(id)) return "🚀  Ciencia Ficción y Futuros Distópicos";
        if ("comedy".equals(id)) return "😂  Comedia y Risas en Familia";
        if ("romance".equals(id)) return "❤️  Romance, Pasión y Melodrama";
        if ("crime".equals(id)) return "🕵️  Crimen, Mafia y Suspense Policial";
        if ("historical".equals(id)) return "👑  Dramas de Época e Históricos";
        if ("latin".equals(id)) return "🇲🇽 🇦🇷  Series y Novelas Latinas";
        if ("asian_fantasy".equals(id)) return "🏯  Doramas Románticos y Fantasía Asiática";
        if ("shonen".equals(id)) return "⚔️  Anime Shonen y Batallas Épicas";
        if ("kids".equals(id)) return "👶  Kids y Animación Familiar";
        if ("classic".equals(id)) return "🏛️  Cine Clásico de Oro (Dominio Público)";
        if ("blender".equals(id)) return "🌱  Cortometrajes y Cine Abierto 4K";
        if ("adults".equals(id)) return "🔞  Thrillers Eróticos y Suspenso Adulto (+18)";
        if ("sports".equals(id)) return "⚽  Deportes y Eventos en Vivo";
        return id;
    }
"""

content = content.replace("private java.util.List<PosterItem> defaultMovies2026(String prov) {", extra_defaults + "\n    private java.util.List<PosterItem> defaultMovies2026(String prov) {")

# 5. Métodos para Personalizar Carruseles, Favoritos y Ver más tarde
personalization_and_sections = """
    private void showCarouselCustomizer() {
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
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

        TextView sub = text("Organiza el orden de los carruseles y oculta los que no uses. Los cambios son persistentes.", 13, Color.rgb(168, 178, 195));
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

        final java.util.List<String> currentOrder = new java.util.ArrayList<>(ZappCatalogEngine.getCarouselOrder(this, getDefaultCarouselOrder()));
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
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
        page = "favorites"; activeTab = "favorites";

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

            TextView sub = text("Explora el catálogo en Inicio o Búsqueda, abre la ficha de cualquier película o serie y presiona '❤️ Agregar a Favoritos'.", 14, Color.rgb(168, 178, 195));
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
            if (side.getChildCount() > 5) side.getChildAt(5).requestFocus();
        });
    }

    private void showWatchLaterPage() {
        if (catalogBrowser != null) { catalogBrowser.dispose(); catalogBrowser = null; }
        page = "watch_later"; activeTab = "watch_later";

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

            TextView sub = text("Guarda películas y series para encontrarlas rápidamente cuando tengas tiempo para verlas.", 14, Color.rgb(168, 178, 195));
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
            if (side.getChildCount() > 6) side.getChildAt(6).requestFocus();
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
                e
            );
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
        String[] options = new String[] {
            "⚙️  Personalizar Carruseles de Inicio (Ordenar y Ocultar)",
            "📡  Seleccionar Proveedor Activo (" + ((activeProvider == null || activeProvider.isEmpty()) ? "CineHax" : activeProvider) + ")"
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) showCarouselCustomizer();
            else showProviderSelector();
        });
        builder.setNegativeButton("Cerrar", null);
        builder.show();
    }
"""

content = content.replace("private LinearLayout buildSidebar(String currentSection) {", personalization_and_sections + "\n    private LinearLayout buildSidebar(String currentSection) {")

# 6. Actualizar buildSidebar con las secciones independientes
old_sidebar_icons = """        addSideIcon(side, "🔍", "search".equals(currentSection), "Buscar y Explorar", () -> showLunifiedSearch(lastLunifiedQuery));
        addSideIcon(side, "🏠", "all".equals(currentSection), "Inicio", () -> { activeTab = "all"; home(); });
        addSideIcon(side, "🎬", "movies".equals(currentSection), "Películas", () -> { activeTab = "movies"; home(); });
        addSideIcon(side, "🎞️", "series".equals(currentSection), "Series", () -> { activeTab = "series"; home(); });
        addSideIcon(side, "📺", "tv".equals(currentSection), "TV en vivo", this::showM3uSources);
        addSideIcon(side, "❤️", "favorites".equals(currentSection), "Favoritos y Kids", () -> { activeTab = "favorites"; home(); });
        addSideIcon(side, "🕒", "history".equals(currentSection), "Historial y Anime", () -> { activeTab = "history"; home(); });
        addSideIcon(side, "🔞", "adults".equals(currentSection), "Zona Adultos (+18)", () -> { activeTab = "adults"; home(); });

        android.widget.Space filler = new android.widget.Space(this);
        side.addView(filler, new LinearLayout.LayoutParams(1, 0, 1));

        addSideIcon(side, "⚙️", "sources".equals(currentSection), "Seleccionar Proveedor", this::showProviderSelector);"""

new_sidebar_icons = """        addSideIcon(side, "🔍", "search".equals(currentSection), "Buscar y Explorar", () -> showLunifiedSearch(lastLunifiedQuery));
        addSideIcon(side, "🏠", "all".equals(currentSection), "Inicio", () -> { activeTab = "all"; home(); });
        addSideIcon(side, "🎬", "movies".equals(currentSection), "Películas", () -> { activeTab = "movies"; home(); });
        addSideIcon(side, "🎞️", "series".equals(currentSection), "Series", () -> { activeTab = "series"; home(); });
        addSideIcon(side, "📺", "tv".equals(currentSection), "TV en vivo", this::showM3uSources);
        addSideIcon(side, "❤️", "favorites".equals(currentSection), "Favoritos", this::showFavoritesPage);
        addSideIcon(side, "🔖", "watch_later".equals(currentSection), "Ver más tarde", this::showWatchLaterPage);
        addSideIcon(side, "🔞", "adults".equals(currentSection), "Zona Adultos (+18)", () -> { activeTab = "adults"; home(); });

        android.widget.Space filler = new android.widget.Space(this);
        side.addView(filler, new LinearLayout.LayoutParams(1, 0, 1));

        addSideIcon(side, "⚙️", "sources".equals(currentSection), "Ajustes y Personalizar", this::showSettingsDialog);"""

if old_sidebar_icons in content:
    content = content.replace(old_sidebar_icons, new_sidebar_icons)
else:
    print("Warning: old_sidebar_icons not found!")
    sys.exit(1)

# 7. Actualizar home() para renderizar los ~20 carruseles ordenados y filtrados por preferencias
old_home_block = """        if ("favorites".equals(activeTab)) {
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
        }"""

new_home_block = """        if ("favorites".equals(activeTab)) {
            showFavoritesPage();
            return;
        } else if ("watch_later".equals(activeTab)) {
            showWatchLaterPage();
            return;
        } else if ("adults".equals(activeTab)) {
            TextView adHead = text("🔞  Zona Adultos (+18) · Contenido para Mayores", 22, Color.rgb(255, 90, 90));
            adHead.setTypeface(null, Typeface.BOLD);
            adHead.setPadding(dp(8), dp(12), dp(8), dp(4));
            body.addView(adHead);

            TextView adSub = text("Películas eróticas, romances pasionales y thrillers adultos en audio latino.", 13, Color.rgb(200, 180, 180));
            adSub.setPadding(dp(8), 0, dp(8), dp(12));
            body.addView(adSub);

            LinearLayout rAd1 = addPosterRow(body, "🔞  Películas para Adultos (+18)", defaultAdults(prov), "CineHax", "adults", "", "adults");
            populateLiveRow(rAd1, "CineHax", "adults", "", 1, 0, true, "🔞  Películas para Adultos (+18)", "adults");
            firstRail = rAd1;

            LinearLayout rAd2 = addPosterRow(body, "❤️  Romances Pasionales y Eróticos", defaultRomance(prov), "CineHax", "genre:romance", "", "romance");
            populateLiveRow(rAd2, "CineHax", "genre:romance", "", 1, 0, false, "❤️  Romances Pasionales y Eróticos", "romance");

            LinearLayout rAd3 = addPosterRow(body, "🕵️  Thrillers Eróticos y Suspenso Adulto", defaultTerror2026(prov), "CineHax", "genre:suspense", "", "terror");
            populateLiveRow(rAd3, "CineHax", "genre:suspense", "", 1, 0, false, "🕵️  Thrillers Eróticos y Suspenso Adulto", "terror");
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

                // Obtener orden y carruseles ocultos configurados por el usuario
                java.util.List<String> order = ZappCatalogEngine.getCarouselOrder(this, getDefaultCarouselOrder());
                java.util.Set<String> hidden = ZappCatalogEngine.getHiddenCarousels(this);

                for (String cId : order) {
                    if (hidden.contains(cId)) continue;

                    if ("top10".equals(cId)) {
                        LinearLayout rTop10 = addTop10Row(body, "🔥  Top 10 en Leo TV hoy", defaultTop10(prov));
                        if (firstRail == null) firstRail = rTop10;
                    } else if ("turkish".equals(cId)) {
                        LinearLayout rTr = addPosterRow(body, "🇹🇷  Novelas y Series Turcas", defaultTurkishNovelas(prov), "CineHax", "country:tr", "", "turkish");
                        populateLiveRow(rTr, "CineHax", "country:tr", "", 1, 0, firstRail == null, "🇹🇷  Novelas y Series Turcas", "turkish");
                        if (firstRail == null) firstRail = rTr;
                    } else if ("kdramas".equals(cId)) {
                        LinearLayout rKr = addPosterRow(body, "🇰🇷  K-Dramas y Series Coreanas", defaultKDramas(prov), "CineHax", "country:kr", "", "kdramas");
                        populateLiveRow(rKr, "CineHax", "country:kr", "", 1, 0, false, "🇰🇷  K-Dramas y Series Coreanas", "kdramas");
                    } else if ("anime".equals(cId)) {
                        LinearLayout rJp = addPosterRow(body, "🇯🇵  Japón y Anime Popular", defaultAnime(), "CineHax", "country:jp", "", "anime");
                        populateLiveRow(rJp, "CineHax", "country:jp", "", 1, 0, false, "🇯🇵  Japón y Anime Popular", "anime");
                    } else if ("movies2026".equals(cId)) {
                        LinearLayout r1 = addPosterRow(body, "🎬  Películas nuevas · 2026 (" + prov + ")", defaultMovies2026(prov), prov, "movie", "", "movies2026");
                        populateLiveRow(r1, prov, "movie", "", 1, 0, firstRail == null, "🎬  Películas nuevas · 2026 (" + prov + ")", "movies2026");
                        if (firstRail == null) firstRail = r1;
                    } else if ("series2026".equals(cId)) {
                        LinearLayout r2 = addPosterRow(body, "📺  Series nuevas · 2026 (" + prov + ")", defaultSeries2026(prov), prov, "serie", "", "series2026");
                        populateLiveRow(r2, prov, "serie", "", 1, 0, firstRail == null, "📺  Series nuevas · 2026 (" + prov + ")", "series2026");
                        if (firstRail == null) firstRail = r2;
                    } else if ("action".equals(cId)) {
                        LinearLayout r3 = addPosterRow(body, "💥  Acción y Aventura Sin Límites", defaultAction2026(prov), "CineHax", "genre:accion", "", "action");
                        populateLiveRow(r3, "CineHax", "genre:accion", "", 1, 0, false, "💥  Acción y Aventura Sin Límites", "action");
                    } else if ("terror".equals(cId)) {
                        LinearLayout r5 = addPosterRow(body, "👻  Terror, Suspenso y Misterio", defaultTerror2026(prov), "CineHax", "genre:terror", "", "terror");
                        populateLiveRow(r5, "CineHax", "genre:terror", "", 1, 0, false, "👻  Terror, Suspenso y Misterio", "terror");
                    } else if ("scifi".equals(cId)) {
                        LinearLayout rSc = addPosterRow(body, "🚀  Ciencia Ficción y Futuros Distópicos", defaultSciFi(prov), "CineHax", "genre:ciencia-ficcion", "", "scifi");
                        populateLiveRow(rSc, "CineHax", "genre:ciencia-ficcion", "", 1, 0, false, "🚀  Ciencia Ficción y Futuros Distópicos", "scifi");
                    } else if ("comedy".equals(cId)) {
                        LinearLayout r4 = addPosterRow(body, "😂  Comedia y Risas en Familia", defaultComedy2026(prov), "CineHax", "genre:comedia", "", "comedy");
                        populateLiveRow(r4, "CineHax", "genre:comedia", "", 1, 0, false, "😂  Comedia y Risas en Familia", "comedy");
                    } else if ("romance".equals(cId)) {
                        LinearLayout rRo = addPosterRow(body, "❤️  Romance, Pasión y Melodrama", defaultRomance(prov), "CineHax", "genre:romance", "", "romance");
                        populateLiveRow(rRo, "CineHax", "genre:romance", "", 1, 0, false, "❤️  Romance, Pasión y Melodrama", "romance");
                    } else if ("crime".equals(cId)) {
                        LinearLayout rCri = addPosterRow(body, "🕵️  Crimen, Mafia y Suspense Policial", defaultCrime2026(prov), "CineHax", "genre:crimen", "", "crime");
                        populateLiveRow(rCri, "CineHax", "genre:crimen", "", 1, 0, false, "🕵️  Crimen, Mafia y Suspense Policial", "crime");
                    } else if ("historical".equals(cId)) {
                        LinearLayout rHis = addPosterRow(body, "👑  Dramas de Época e Históricos", defaultDrama2026(prov), "CineHax", "genre:drama", "", "historical");
                        populateLiveRow(rHis, "CineHax", "genre:drama", "", 1, 0, false, "👑  Dramas de Época e Históricos", "historical");
                    } else if ("latin".equals(cId)) {
                        LinearLayout rLat = addPosterRow(body, "🇲🇽 🇦🇷  Series y Novelas Latinas", defaultLatin2026(prov), "CineHax", "country:mx", "", "latin");
                        populateLiveRow(rLat, "CineHax", "country:mx", "", 1, 0, false, "🇲🇽 🇦🇷  Series y Novelas Latinas", "latin");
                    } else if ("asian_fantasy".equals(cId)) {
                        LinearLayout rAsi = addPosterRow(body, "🏯  Doramas Románticos y Fantasía Asiática", defaultAsianFantasy(prov), "DoramasFlix", "serie", "", "asian_fantasy");
                        populateLiveRow(rAsi, "DoramasFlix", "serie", "", 1, 0, false, "🏯  Doramas Románticos y Fantasía Asiática", "asian_fantasy");
                    } else if ("shonen".equals(cId)) {
                        LinearLayout rSho = addPosterRow(body, "⚔️  Anime Shonen y Batallas Épicas", defaultAnimeShonen(), "LatAnime", "movie", "", "shonen");
                        populateLiveRow(rSho, "LatAnime", "movie", "", 1, 0, false, "⚔️  Anime Shonen y Batallas Épicas", "shonen");
                    } else if ("kids".equals(cId)) {
                        LinearLayout r7 = addPosterRow(body, "👶  Kids y Animación Familiar", defaultKids(prov), "CineHax", "genre:animacion", "", "kids");
                        populateLiveRow(r7, "CineHax", "genre:animacion", "", 1, 0, false, "👶  Kids y Animación Familiar", "kids");
                    } else if ("classic".equals(cId)) {
                        addPosterRow(body, "🏛️  Cine Clásico de Oro (Dominio Público)", defaultClassicMovies(), "Internet Archive", "movie", "", "classic");
                    } else if ("blender".equals(cId)) {
                        addPosterRow(body, "🌱  Cortometrajes y Cine Abierto 4K", defaultBlenderMovies(), "Blender Open Movies", "movie", "", "blender");
                    } else if ("adults".equals(cId)) {
                        LinearLayout rAd = addPosterRow(body, "🔞  Thrillers Eróticos y Suspenso Adulto (+18)", defaultAdults(prov), "CineHax", "adults", "", "adults");
                        populateLiveRow(rAd, "CineHax", "adults", "", 1, 0, false, "🔞  Thrillers Eróticos y Suspenso Adulto (+18)", "adults");
                    } else if ("sports".equals(cId)) {
                        addSportsLiveRow(body, "⚽  Deportes y Eventos en Vivo (Agenda SK Tech)", defaultSportsEvents());
                    }
                }
            } else if ("movies".equals(activeTab)) {
                LinearLayout r1 = addPosterRow(body, "🎬  Películas nuevas · 2026 (" + prov + ")", defaultMovies2026(prov), prov, "movie", "", "movies2026");
                populateLiveRow(r1, prov, "movie", "", 1, 0, true, "🎬  Películas nuevas · 2026", "movies2026");
                firstRail = r1;

                LinearLayout r3 = addPosterRow(body, "💥  Acción y Aventura", defaultAction2026(prov), "CineHax", "genre:accion", "", "action");
                populateLiveRow(r3, "CineHax", "genre:accion", "", 1, 0, false, "💥  Acción y Aventura", "action");

                LinearLayout r5 = addPosterRow(body, "👻  Terror y Suspenso", defaultTerror2026(prov), "CineHax", "genre:terror", "", "terror");
                populateLiveRow(r5, "CineHax", "genre:terror", "", 1, 0, false, "👻  Terror y Suspenso", "terror");

                LinearLayout rSc = addPosterRow(body, "🚀  Ciencia Ficción", defaultSciFi(prov), "CineHax", "genre:ciencia-ficcion", "", "scifi");
                populateLiveRow(rSc, "CineHax", "genre:ciencia-ficcion", "", 1, 0, false, "🚀  Ciencia Ficción", "scifi");

                LinearLayout r4 = addPosterRow(body, "😂  Comedia", defaultComedy2026(prov), "CineHax", "genre:comedia", "", "comedy");
                populateLiveRow(r4, "CineHax", "genre:comedia", "", 1, 0, false, "😂  Comedia", "comedy");

                LinearLayout rRo = addPosterRow(body, "❤️  Romance", defaultRomance(prov), "CineHax", "genre:romance", "", "romance");
                populateLiveRow(rRo, "CineHax", "genre:romance", "", 1, 0, false, "❤️  Romance", "romance");
            } else if ("series".equals(activeTab)) {
                LinearLayout r2 = addPosterRow(body, "📺  Series nuevas · 2026 (" + prov + ")", defaultSeries2026(prov), prov, "serie", "", "series2026");
                populateLiveRow(r2, prov, "serie", "", 1, 0, true, "📺  Series nuevas · 2026", "series2026");
                firstRail = r2;

                LinearLayout rTr = addPosterRow(body, "🇹🇷  Novelas y Series Turcas", defaultTurkishNovelas(prov), "CineHax", "country:tr", "", "turkish");
                populateLiveRow(rTr, "CineHax", "country:tr", "", 1, 0, false, "🇹🇷  Novelas y Series Turcas", "turkish");

                LinearLayout rKr = addPosterRow(body, "🇰🇷  K-Dramas y Series Coreanas", defaultKDramas(prov), "CineHax", "country:kr", "", "kdramas");
                populateLiveRow(rKr, "CineHax", "country:kr", "", 1, 0, false, "🇰🇷  K-Dramas y Series Coreanas", "kdramas");

                LinearLayout rHis = addPosterRow(body, "👑  Dramas de Época e Históricos", defaultDrama2026(prov), "CineHax", "genre:drama", "", "historical");
                populateLiveRow(rHis, "CineHax", "genre:drama", "", 1, 0, false, "👑  Dramas de Época e Históricos", "historical");

                LinearLayout rLat = addPosterRow(body, "🇲🇽 🇦🇷  Series y Novelas Latinas", defaultLatin2026(prov), "CineHax", "country:mx", "", "latin");
                populateLiveRow(rLat, "CineHax", "country:mx", "", 1, 0, false, "🇲🇽 🇦🇷  Series y Novelas Latinas", "latin");
            }
        }"""

if old_home_block in content:
    content = content.replace(old_home_block, new_home_block)
else:
    print("Warning: old_home_block not found!")
    sys.exit(1)

# 8. Actualizar onBackPressed() para contemplar category_grid, favorites, watch_later y carousel_customizer
old_back_code = """    @Override public void onBackPressed() {
        if (page.equals("playing")) {"""

new_back_code = """    @Override public void onBackPressed() {
        if (page.equals("category_grid")) {
            returnToHomeFromCategoryGrid();
            return;
        }
        if (page.equals("carousel_customizer") || page.equals("favorites") || page.equals("watch_later")) {
            home();
            return;
        }
        if (page.equals("playing")) {"""

if old_back_code in content:
    content = content.replace(old_back_code, new_back_code)
else:
    print("Warning: old_back_code not found!")
    sys.exit(1)

# Guardar los cambios
with open("app/src/main/java/ar/appvideo/player/MainActivity.java", "w", encoding="utf-8") as f:
    f.write(content)

print("MainActivity.java updated successfully!")

