import os

path = r"app/src/main/java/ar/appvideo/player/MainActivity.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. showSourceSelectionDialog
t1 = '''        final String[] providers = currentPlayingEntry.isMovie()
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
            else if ("LatAnime".equals(p) || "TioAnime".equals(p)) label = "⛩️ " + p + " (Anime HD)";'''

r1 = '''        final String[] providers = currentPlayingEntry.isMovie()
                ? new String[] { "CineHax", "SoloLatino", "Cuevana", "CineCalidad", "Pelispedia", "Pelisplus", "Moflix", "GoodStream" }
                : new String[] { "CineHax", "SoloLatino", "Cuevana", "Seriesflix", "Pelispedia", "Pelisplus", "Moflix", "GoodStream", "LatAnime", "TioAnime" };

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
            else if ("Pelisplus".equals(p)) label = "🌐 Pelisplus (Audio Latino)";
            else if ("Moflix".equals(p)) label = "🌐 Moflix Stream (Multi-servidor)";
            else if ("GoodStream".equals(p)) label = "🌐 GoodStream (HD Rápido)";
            else if ("Seriesflix".equals(p)) label = "🌐 Seriesflix";
            else if ("LatAnime".equals(p) || "TioAnime".equals(p)) label = "⛩️ " + p + " (Anime HD)";'''

# 2. fallbackProviders (línea 693)
t2 = 'String[] fallbackProviders = new String[] { "CineHax", "SoloLatino", "Cuevana", "CineCalidad", "Pelispedia" };'
r2 = 'String[] fallbackProviders = new String[] { "CineHax", "SoloLatino", "Cuevana", "CineCalidad", "Pelispedia", "Pelisplus", "Moflix", "GoodStream" };'

# 3. activeSearchInput
t3 = '''    private boolean globalSearchEnabled = true;
    private String lastLunifiedQuery = "";
    private int searchGeneration = 0;'''

r3 = '''    private boolean globalSearchEnabled = true;
    private String lastLunifiedQuery = "";
    private android.widget.EditText activeSearchInput;
    private int searchGeneration = 0;'''

# 4. showLunifiedSearch searchInput assignment
t4 = 'final android.widget.EditText searchInput = new android.widget.EditText(this);'
r4 = '''final android.widget.EditText searchInput = new android.widget.EditText(this);
        activeSearchInput = searchInput;'''

# 5. renderLunifiedTilesGrid suggestions
t5 = '''    private void renderLunifiedTilesGrid(LinearLayout contentBody) {
        ++searchGeneration;
        contentBody.removeAllViews();

        java.util.List<ZappCatalogEngine.ExploreTile> tiles = ZappCatalogEngine.buildLunifiedTiles();'''

r5 = '''    private void renderLunifiedTilesGrid(LinearLayout contentBody) {
        ++searchGeneration;
        contentBody.removeAllViews();

        java.util.List<String> tasteSuggestions = UserTasteEngine.getZeroQuerySuggestions(this);
        if (tasteSuggestions != null && !tasteSuggestions.isEmpty()) {
            TextView sugHead = text("🎯  Sugerencias según tus gustos:", 15, Color.rgb(245, 197, 66));
            sugHead.setTypeface(null, Typeface.BOLD);
            sugHead.setPadding(dp(4), dp(4), dp(4), dp(6));
            contentBody.addView(sugHead);

            HorizontalScrollView hScroll = new HorizontalScrollView(this);
            hScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout sugRow = new LinearLayout(this);
            sugRow.setOrientation(LinearLayout.HORIZONTAL);
            sugRow.setPadding(0, 0, 0, dp(12));
            hScroll.addView(sugRow);

            for (String sug : tasteSuggestions) {
                Button sugBtn = new Button(this);
                sugBtn.setText("🔍  " + sug);
                sugBtn.setTextColor(Color.WHITE);
                sugBtn.setTextSize(13);
                sugBtn.setAllCaps(false);
                sugBtn.setFocusable(true);
                GradientDrawable sbd = new GradientDrawable();
                sbd.setColor(Color.rgb(45, 48, 62));
                sbd.setCornerRadius(dp(16));
                sbd.setStroke(dp(1), Color.rgb(85, 92, 118));
                sugBtn.setBackground(sbd);
                sugBtn.setPadding(dp(14), dp(6), dp(14), dp(6));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
                lp.setMargins(0, 0, dp(10), 0);
                sugBtn.setLayoutParams(lp);

                sugBtn.setOnFocusChangeListener((v, f) -> {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(f ? Color.rgb(129, 107, 245) : Color.rgb(45, 48, 62));
                    g.setCornerRadius(dp(16));
                    if (f) g.setStroke(dp(2), Color.WHITE);
                    sugBtn.setBackground(g);
                    sugBtn.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
                });

                sugBtn.setOnClickListener(v -> {
                    if (activeSearchInput != null) {
                        activeSearchInput.setText(sug);
                    }
                    lastLunifiedQuery = sug;
                    executeLunifiedQuery(sug, contentBody);
                });
                sugRow.addView(sugBtn);
            }
            contentBody.addView(hScroll);
        }

        java.util.List<ZappCatalogEngine.ExploreTile> tiles = ZappCatalogEngine.buildLunifiedTiles();'''

# 6. executeLunifiedQuery ranking
t6 = '''                // 2. Unificar resultados de todos los proveedores sin duplicados
                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueTurkish = new java.util.LinkedHashMap<>();'''

r6 = '''                // 2. Unificar y re-ordenar resultados según perfil de gustos del usuario
                for (java.util.List<CatalogData.Entry> pList : result.byProvider.values()) {
                    UserTasteEngine.rankSearchResults(MainActivity.this, pList);
                }

                java.util.LinkedHashMap<String, CatalogData.Entry> uniqueTurkish = new java.util.LinkedHashMap<>();'''

# 7. home() carrusel recomendado
t7 = '''                    LinearLayout rWl = addPosterRow(body, "🔖  Ver más tarde (" + wlList.size() + ")", wlPosters,
                            "CineHax", "movie", "", "watch_later");
                    if (firstRail == null)
                        firstRail = rWl;
                }

                // Obtener orden y carruseles ocultos configurados por el usuario'''

r7 = '''                    LinearLayout rWl = addPosterRow(body, "🔖  Ver más tarde (" + wlList.size() + ")", wlPosters,
                            "CineHax", "movie", "", "watch_later");
                    if (firstRail == null)
                        firstRail = rWl;
                }

                // 3b. Recomendados para vos (basado en historial de reproducción y gustos del usuario)
                final LinearLayout recContainer = new LinearLayout(this);
                recContainer.setOrientation(LinearLayout.VERTICAL);
                body.addView(recContainer);
                UserTasteEngine.getRecommendationsAsync(this, (recItems, becauseTitle, becauseItems) -> {
                    runOnUiThread(() -> {
                        if (recItems != null && !recItems.isEmpty() && "home".equals(page)) {
                            java.util.List<PosterItem> recPosters = new java.util.ArrayList<>();
                            for (CatalogData.Entry e : recItems) {
                                recPosters.add(new PosterItem(
                                        e.title,
                                        e.year > 0 ? e.year : 2026,
                                        !e.isMovie(),
                                        e.poster,
                                        e.providerName != null ? e.providerName : "CineHax",
                                        e.title,
                                        e));
                            }
                            addPosterRow(recContainer, "🎯  Recomendados para vos", recPosters,
                                    "CineHax", "movie", "", "recommended_for_you");
                        }
                        if (becauseItems != null && !becauseItems.isEmpty() && becauseTitle != null && !becauseTitle.isEmpty() && "home".equals(page)) {
                            java.util.List<PosterItem> becPosters = new java.util.ArrayList<>();
                            for (CatalogData.Entry e : becauseItems) {
                                becPosters.add(new PosterItem(
                                        e.title,
                                        e.year > 0 ? e.year : 2026,
                                        !e.isMovie(),
                                        e.poster,
                                        e.providerName != null ? e.providerName : "CineHax",
                                        e.title,
                                        e));
                            }
                            addPosterRow(recContainer, "🎬  Porque viste " + becauseTitle, becPosters,
                                    "CineHax", "movie", "", "because_you_watched");
                        }
                    });
                });

                // Obtener orden y carruseles ocultos configurados por el usuario'''

# 8. fallback providers en líneas 9147 y 9248
t8 = ': new String[] { "CineHax", "SoloLatino", "CineCalidad", "Seriesflix", "Cuevana", "LatAnime", "TioAnime", "SeriesMetro", "Pelispedia", "FlixLatam", "HenaoJara" };'
r8 = ': new String[] { "CineHax", "SoloLatino", "CineCalidad", "Seriesflix", "Cuevana", "Pelispedia", "Pelisplus", "Moflix", "GoodStream", "LatAnime", "TioAnime", "SeriesMetro", "FlixLatam", "HenaoJara" };'

# 9. safeHeaders en playMovie
t9 = '''        if (url != null && (url.contains("tvf90.com") || url.contains("ftlly.com"))) {
            safeHeaders.put("Referer", "https://tvf90.com/");
            safeHeaders.put("Origin", "https://tvf90.com");
        }'''

r9 = '''        if (url != null && (url.contains("tvf90.com") || url.contains("ftlly.com"))) {
            safeHeaders.put("Referer", "https://tvf90.com/");
            safeHeaders.put("Origin", "https://tvf90.com");
        } else if (url != null && (url.contains("flow.com.ar") || url.contains("flow.com.py") || url.contains("cvatt.com"))) {
            safeHeaders.put("x-flow-origin", "Android");
            safeHeaders.put("Referer", "https://portal.app.flow.com.ar/");
        } else if (url != null && (url.contains("fctv") || url.contains("garden") || url.contains("ppv"))) {
            safeHeaders.put("Referer", "https://fctv33hd.garden/");
            safeHeaders.put("Origin", "https://fctv33hd.garden");
        }'''

assert t1 in content, "t1 not found"
content = content.replace(t1, r1, 1)

assert t2 in content, "t2 not found"
content = content.replace(t2, r2, 1)

assert t3 in content, "t3 not found"
content = content.replace(t3, r3, 1)

assert t4 in content, "t4 not found"
content = content.replace(t4, r4, 1)

assert t5 in content, "t5 not found"
content = content.replace(t5, r5, 1)

assert t6 in content, "t6 not found"
content = content.replace(t6, r6, 1)

assert t7 in content, "t7 not found"
content = content.replace(t7, r7, 1)

assert t8 in content, "t8 not found"
content = content.replace(t8, r8)

assert t9 in content, "t9 not found"
content = content.replace(t9, r9, 1)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print(f"Success! New line count: {len(content.splitlines())}")
