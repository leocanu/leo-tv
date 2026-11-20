# Checkpoint — Rediseño de interfaz Android TV — 2026-11-07

## Pedido
Hacer la app de video propia más amigable para usuarios de Android TV, con referencia a XuperSBC y Magis. Mantener todos los proveedores y funcionamiento previo, sin instalar otra app como sustituto.

## Implementado
- `MainActivity.java`: `home()` rediseñado como dashboard de TV con panel lateral fijo, título claro, carruseles horizontales sin barras visibles y tarjetas rectangulares con resaltado visible al obtener foco.
- Menú lateral: Inicio, Películas y series, Anime, Todas las fuentes, Video de prueba y Estado.
- Secciones: Empezá por acá (demo, Blender Open Movies, Internet Archive y cuatro fuentes con reproducción anteriormente probada); Películas y series (fuentes provisionales); Anime (catálogos anime, pendiente verificar video).
- Cada tarjeta muestra etiqueta según registro real de la fuente: `Reproducción probada`, `Anime · video pendiente`, `Adaptador en revisión`, `No integrado`.
- `tvSection` muestra fuentes de la categoría elegida; `allSources` conserva acceso a las 32 fuentes. Botón ATRÁS desde sección y listado vuelve a portada.
- Tarjetas de la pantalla de fuentes reducidas a rectángulos, más legibles que la antigua grilla de cuadrados grandes.
- Ajuste de foco inicial en menú `Inicio` para el control remoto.
- No se cambiaron proveedores, datos de catálogo, permisos ni reproductor.

## Verificaciones
- Backup del código anterior: `app/src/main/java/ar/appvideo/player/MainActivity.pre-tv-dashboard-20261107.java.bak`.
- Script determinista de actualización: `apply_tv_dashboard_20261107.py`.
- Compilación Gradle de la versión final: `BUILD SUCCESSFUL` con `BUILD_EXIT_CODE=0`.
- `adb install -r`: `Success` sobre emulador `ChainReaction_TV_API34` (`emulator-5554`).
- Abre `ar.appvideo.player/.MainActivity` y se verificó por UIAutomator la portada, carruseles, menú, sección Películas y series, retroceso y listado completo de fuentes.
- XML locales: `video-tv-dashboard.xml`, `video-tv-section2.xml`, `video-tv-sources.xml`, `video-dashboard-v2.xml`.
- APK final archivado: `C:\Users\Pc\Downloads\VideoPropia-TV-InterfazAmigable-v2-2026-11-07.apk`.
- SHA256 APK: `6A0A1401AD1F8329696A1044608855BFDB1ACDC78241156BA0799796EDFECE7A`.

## Pendiente
- Revisión visual subjetiva por el usuario en el emulador.
- QA de todos los conectores y reproducción (no repetida ahora).
- Mejora posterior opcional: portadas reales, filas más densas, navegación entre tarjetas con D-pad, búsqueda global unificada. No afirmar que están implementadas.
