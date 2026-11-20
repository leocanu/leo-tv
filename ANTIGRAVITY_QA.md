# LEO TV — REGISTRO DE QA Y PRUEBAS REALES (Antigravity IDE)

> **Documento:** Evidencias de pruebas ejecutadas, logs verificados, fallos conocidos y límites reales.  
> **Ubicación:** `ANTIGRAVITY_QA.md`  

---

## 1. Evidencia de Ejecución en Emulador Android TV (`emulator-5554`)

### A. Inspección en Vivo de Xuper TV (`sbc.ovn`)
- Se lanzó la aplicación instalada en el emulador mediante Intent launcher (`android.intent.category.LAUNCHER`).
- **Captura 1 (`xuper-real-running.png`):** Pantalla principal de Xuper con logo superior izquierdo `SBC+ON`, reloj digital (`06:02 AM`), rail lateral de navegación (`LIVE`, `Especiales SBCxON`, `FEATURED`, `MOVIES`, `SERIES`, `KIDS`, `ANIME`), botón `★ CANALES` y ventana central de reproducción con marco neón.
- **Captura 2 (`xuper-movies-screen.png`):** Navegación D-Pad hacia la pestaña `MOVIES`, mostrando el foco activo en forma de píldora celeste brillante y el intento de conexión al servicio de catálogo.

### B. Pruebas de Leo TV v0.11.0
- **Captura Home (`leotv-v11-home.png`):** Verificación de Hero Banner superior y logotipo oficial `leo_brand.png`.
- **Navegación D-Pad (`leotv-v11-focus-demo.png` y `leotv-v11-focus-blender.png`):** El foco responde inmediatamente actualizando el Hero Banner dinámico con sinopsis, año, licencia y calidad.
- **Ficha Técnica VOD (`leotv-v11-ficha-detalle.png`):** Pantalla en 2 columnas con recuadro de carátula dorada, badge `HD 1080p · CC BY`, sinopsis y botón `▶ Reproducir video completo`.
- **Reproducción Media3 ExoPlayer (`leotv-v11-playback.png`):** Reproducción a pantalla completa con barra de progreso, controles de transporte y duración `00:10 · 00:10`. Retorno limpio a la Ficha de Detalle mediante tecla ATRÁS (KEYCODE_BACK).

### C. Pruebas de Leo TV v0.12.0 (Ajustes de Audio Latino y Exclusión de Radios)
- **Captura Home (`leotv-v12-home.png`):** Menú lateral reestructurado con etiquetas directas: `Inicio`, `Películas (Latino)`, `Series (Latino)`, `TV en vivo`, `Infantil (Dibujos)`, `Anime (Latino)`, `Explorar fuentes`, `Video de prueba`, `Estado`. Rieles en Home renombrados a *"Destacados (Latino)"*, *"Películas y series (Latino)"* y *"Anime (Latino y Sub)"*.
- **Captura TV en Vivo (`leotv-v12-tvenvivo.png`):** Verificación de que **todas las radios fueron omitidas**. Se presentan solo canales y colecciones en español: TDTChannels TV (España/Latino), Mametchi Argentina, Mametchi Dibujos Latinos, Mametchi Ghibli Latino y Directorio Hispano.
- **Captura Películas Latino (`leotv-v12-peliculas-latino.png`):** Grid dedicado de proveedores de cine en español (CineHax, LaMovie, CineCalidad, Cuevana, Pelisplus, Pelispedia, etc.).

---

## 2. Límites y Observaciones Técnicas

- Se descartaron las pruebas de radios para no desperdiciar recursos ni cargar contenido no deseado.
- Los canales IPTV que devuelven HTTP 403 (ej. señales con geobloqueo o restricción del emisor) son informados en pantalla sin trabar la interfaz.
- La experiencia visual de Leo TV ahora comparte la misma jerarquía y flujo de navegación que Xuper, manteniendo código y marca 100% propios.
