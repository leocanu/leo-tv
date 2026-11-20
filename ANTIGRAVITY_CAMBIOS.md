# LEO TV — REGISTRO DE CAMBIOS (Antigravity IDE)

> **Documento:** Registro cronológico de archivos modificados, razones técnicas, respaldos, rutas de APK y versiones.  
> **Ubicación:** `ANTIGRAVITY_CAMBIOS.md`  

---

## Historial de Modificaciones

### Snapshot Inicial: Leo TV v0.10.0 (2026-11-08T01:42)
- **APK Base:** `C:\Users\Pc\Downloads\LeoTV-v0.10-ReproductorVerificado-2026-11-08.apk`
- **Respaldos:** `pruebas-downloader/backup-playback-20261108-013858` y `pruebas-downloader/backup-media-session-20261108-014127`.

---

### Versión 0.11.0 (2026-11-08T02:33)
- **APK Compilada:** `C:\Users\Pc\Downloads\LeoTV-v0.11-HeroYDetalleVerificado-2026-11-08.apk` (4.854.385 bytes)
- **Respaldo:** `pruebas-downloader/backup-antigravity-v011-20261108-022903`
- **Mejoras clave:**
  - Hero Banner dinámico en Home (`heroBox`, `updateHero`) reactivo a `onFocusChangeListener`.
  - Ficha técnica de detalle (`showMovieFicha`) en 2 columnas con carátula enmarcada en dorado, badge HD, sinopsis y botón de reproducción directa.
  - Corrección de altura de menú lateral a 66px.

---

### Versión 0.12.0 (2026-11-08T03:11)
- **APK Compilada:**
  - `C:\workspace\app-video-propia-android-v1\app\build\outputs\apk\debug\app-debug.apk` (4.855.305 bytes)
  - `C:\Users\Pc\Downloads\LeoTV-v0.12-AudioLatinoSinRadios-2026-11-08.apk` (4.855.305 bytes)
- **Archivos modificados:**
  1. `app/build.gradle`:
     - `versionCode 12`, `versionName '0.12.0'`.
  2. `app/src/main/java/ar/appvideo/player/MainActivity.java`:
     - **Omisión completa de Radios:** Se eliminó `TDTChannels Radio` de las fuentes M3U en `showM3uSources()`.
     - **Filtro de canales en español:** La sección de TV en vivo (`showM3uSources`) quedó restringida a señales abiertas en español: TDTChannels TV, Mametchi Argentina, Mametchi Dibujos (Latino), Mametchi Studio Ghibli (Latino) y Directorio hispano.
     - **División de Películas y Series en Audio Latino:** Se desglosó `tvSection` en `movies` ("Películas en Audio Latino") y `series` ("Series en Audio Latino") usando la nueva función `tvSeries(name)`.
     - **Ajuste ergonómico del menú lateral:** Se ajustaron los botones del menú lateral a una sola línea (`setSingleLine(true)` con `TruncateAt.END`) y altura uniforme de 60px con padding `(14, 5, 8, 5)` para evitar desbordes y cortes de texto.
     - **Metadatos de Audio Latino en Hero Banner:** `updateHero` ahora prioriza y resalta información de doblaje latino ("Audio Latino / Castellano", "Doblaje Latino Oficial", etc.) en las tarjetas de catálogo y en el banner superior.
- **Capturas de verificación generadas en el emulador:**
  - `pruebas-downloader/xuper-real-running.png` (Xuper en ejecución real).
  - `pruebas-downloader/xuper-movies-screen.png` (Pantalla Movies de Xuper en ejecución real).
  - `pruebas-downloader/leotv-v12-home.png` (Home v0.12 con menú limpio y rieles en audio latino).
  - `pruebas-downloader/leotv-v12-tvenvivo.png` (Sección TV en vivo sin radios y solo en español).
  - `pruebas-downloader/leotv-v12-peliculas-latino.png` (Sección Películas en Audio Latino).

---

### Versión 0.13.0 (2026-11-08T22:36)
- **APK Compilada:**
  - `C:\Users\Pc\Downloads\LeoTV-v0.13-CarruselesYProveedores-2026-11-08.apk`
- **Respaldo previo:** `pruebas-downloader/backup-antigravity-v013/`
- **Archivos modificados:**
  1. `app/build.gradle`: `versionCode 13`, `versionName '0.13.0'`.
  2. `app/src/main/java/ar/appvideo/player/CatalogBrowser.java`:
     - Exposición de `loadPoster(Activity, ImageView, String)` con caché LRU compartida.
     - Rediseño de tarjetas de grilla (`card()`) con póster vertical redondeado, etiqueta `HD   2026` / `HD   TV`, título blanco de 2 líneas y marco blanco de foco para control remoto.
  3. `app/src/main/java/ar/appvideo/player/MainActivity.java`:
     - **Nueva Pantalla `SELECCIONAR PROVEEDOR` (`showProviderSelector()`)**: Grilla centrada de 3 columnas con tarjetas cuadradas de emblemas visuales (`ARGENTINA SOLO CANALES`, `CANALES DE TV Y CINE`, `TDTCHANNELS TV`, `PELICULAS Y SERIES 3`, `RECOMENDADO PARA PELÍCULAS Y SERIES`, `PelisflixHD`, `PoseidonHD2`, `Canela TV`, `CineCalidad`, `JKAnime / LatAnime`, etc.) y filtro superior `SOLO ESPAÑOL / LATINO`.
     - **Nueva Pantalla Principal con Barra Lateral de Íconos y Carruseles de Pósters (`home()`)**:
       - Barra lateral izquierda compacta (`78px`) con íconos (`🔍`, `🏠`, `🎬`, `🎞️`, `📺`, `❤️`, `🕒`, `⚙️`, `▶`).
       - Carruseles horizontales por categoría (`Películas nuevas · 2026`, `Series nuevas · 2026`, `Acción · 2026`, `Comedia · 2026`, `Terror · 2026`, `Drama · 2026`, `Kids`, `Anime`) poblados en vivo desde los proveedores (`CineHax`, `LaMovie`, `LatAnime`) con pósters verticales reales, etiqueta `HD   2026` / `HD   TV`, títulos completos de 2 líneas y borde blanco al recibir foco D-Pad.
- **Capturas de verificación:**
  - `pruebas-downloader/leotv-v13-home.png`
  - `pruebas-downloader/leotv-v13-proveedores.png`

---

### Versión 0.14.0 (2026-11-09)
- **APK Compilada:**
  - `C:\Users\Pc\Downloads\LeoTV-v0.14-MXL-SKTech-Zapp-2026-11-09.apk`
- **Mejoras clave:**
  - Hero Slideshow Banner con carátulas en alta definición, ⭐ 8.8 rating y botones directos.
  - Top 10 con números gigantes estilo MXL/Netflix.
  - Eventos Deportivos en Vivo (Agenda SK Tech & HydraHD).
  - Búsqueda global en paralelo sobre 8 proveedores con DP fix de interfaz.
  - Corrección de carátula Erkenci Kuş y descripciones "null" en episodios.

---

### Versión 0.15.0 (2026-11-10)
- **APK Compilada:**
  - `C:\Users\Pc\Downloads\LeoTV-v0.15-Splash-AutoUpdate-2026-11-09.apk` (4.908.067 bytes)
- **Archivos modificados:**
  1. `app/build.gradle`: `versionCode 15`, `versionName '0.15.0'`, dependencia `androidx.core:core:1.8.0`.
  2. `app/src/main/AndroidManifest.xml`: Permiso `REQUEST_INSTALL_PACKAGES` y definición de `androidx.core.content.FileProvider`.
  3. `app/src/main/res/xml/file_paths.xml`: Rutas seguras para `cache-path` y `external-cache-path`.
  4. `app/src/main/java/ar/appvideo/player/AppUpdater.java`:
     - Consulta automática a GitHub (`https://raw.githubusercontent.com/leocanu/leo-tv/main/version.json`).
     - Diálogo modal para Android TV con progreso de descarga y botón de instalación con `FileProvider`.
  5. `app/src/main/java/ar/appvideo/player/MainActivity.java`:
     - Splash Screen cinematográfico al iniciar con logo de Leo TV, spinner y transición a Home.
     - Botón manual de "Buscar actualizaciones en GitHub" en Configuración (`showProviderSelector`).
  6. `version.json`: Plantilla para el repositorio GitHub con `versionCode 15`, `versionName 0.15.0`, changelog y enlace a release.
- **Capturas de verificación:**
  - `leotv_splash.png` (Splash Screen Leo TV).
  - `leotv_settings_updater.png` (Configuración con botón de actualización).
  - `leotv_v15_home.png` (Inicio v0.15.0).
  - `xuper_main.png` (Xuper Smart Box funcionando en el emulador).

---

### Versión 0.16.0 (2026-11-10)
- **APK Compilada:**
  - `C:\Users\Pc\Downloads\LeoTV-v0.16-Netflix-Categorias-TV-2026-11-10.apk` (5.101.316 bytes)
- **Archivos modificados:**
  1. `app/build.gradle`: `versionCode 16`, `versionName '0.16.0'`.
  2. `app/src/main/java/ar/appvideo/player/CineHaxProvider.java`:
     - Soporte para taxonomías `/taxonomies/genre/{slug}` y `/taxonomies/country/{slug}` (`country:tr` para novelas turcas, `country:jp` para series japonesas y anime, `country:kr` para k-dramas coreanos).
     - Soporte para búsquedas temáticas de adultos (`adults`).
  3. `app/src/main/java/ar/appvideo/player/DoramasFlixProvider.java`:
     - Corrección de endpoints: búsqueda en `/buscar?q=`, listados en `/doramas` y `/peliculas`.
  4. `app/src/main/java/ar/appvideo/player/ZappCatalogEngine.java`:
     - Búsqueda global unificada con detección de intención semántica (`isTurkishIntent`, `isJapaneseIntent`, `isKoreanIntent`, `isHorrorIntent`, `isActionIntent`, `isAdultIntent`).
  5. `app/src/main/java/ar/appvideo/player/CatalogBrowser.java`:
     - Barra horizontal de chips de categorías específicas para cada proveedor activo.
  6. `app/src/main/java/ar/appvideo/player/MainActivity.java`:
     - **TV en Vivo Renovada**: Grilla de categorías (`addLiveTvCategoryCard`) con badges `🔴 EN VIVO`, íconos temáticos y visualización de canales en grilla de 2 columnas (`renderM3uChannelsGrid`) con badges rojos `▶ VER`, foco D-Pad y reproducción fluida en ExoPlayer.
     - **Organización estilo Netflix**: Rieles de Novelas y Series Turcas (🇹🇷), K-Dramas (🇰🇷), Japón y Anime (🇯🇵), Acción (💥), Terror (👻), Ciencia Ficción (🚀), Comedia (😂), Romance (❤️), Kids y Familia (👶), y Zona Adultos +18 (🔞).
     - **Eliminación total de Demo**: Quitado ícono de Demo del menú lateral (reemplazado por Zona Adultos +18) y reemplazado mosaico de Demo por "NOVELAS TURCAS Y ASIÁTICAS" en el selector de proveedores.
  7. `version.json`: Actualizado a `versionCode 16`, `versionName '0.16.0'`.
- **Capturas de verificación:**
  - `leotv_tv_categories_clean.png` (Menú de categorías de TV en vivo con badges EN VIVO).
  - `leotv_argentina_channels.png` (Grilla de 90 canales de TV con badges ▶ VER).
  - `leotv_search_series_turcas_done.png` (Búsqueda unificada: 51 novelas/series turcas y 17 películas).
  - `leotv_live_check.png` (Rieles estilo Netflix en Home con Novelas Turcas y K-Dramas).
  - `leotv_provider_selector.png` (Selector de proveedores con mosaico de Novelas Turcas en lugar de Demo).
