# Cierre de inventario de fuentes — 2026-11-07

## Decisión del usuario
Primero integrar/configurar; después una sola ronda general de pruebas, y por último corregir las fuentes que fallen. No volver a probar conectores conocidos ahora.

## Resultado comprobado del inventario en I5
- 32 fuentes registradas en `app/src/main/assets/channels.json`.
- 4 con reproducción probada anteriormente: CineHax, LaMovie, SeriesMetro, Pelispedia.
- 2 con catálogo y episodios probados, pero reproducción fallida de capítulos examinados: TioAnime y LatAnime.
- 20 adaptadores compilados, sin QA de reproducción de este lote; incluyen DoramasFlix (catálogo parcial) y Cineplus123 (búsqueda HTTP 406 en revisión anterior).
- 6 sin adaptador real por evidencia insuficiente o restricciones, detalladas abajo.

## Seis pendientes — evidencia local, NO nueva prueba de conexión
- **PelisSeriesHoy**: `peliserieshoy.html` contiene página de Google (título Google); se había registrado redirección a sitio ajeno. No crear conector con dominio sin catálogo.
- **Embed69**: archivo `embed69.html` de 2 bytes, 0 enlaces y 0 tarjetas. No se identificó catálogo.
- **Pelisforte**: archivo `pelisforte.html` con WordPress/tema Torofilm, 0 `article`, solo 2 enlaces `<a>`; no se reconocen fichas públicas de catálogo en la captura. No asumir que el sitio esté vacío actualmente.
- **AllCalidad**: archivo `allcalidad.html` tiene metadatos WordPress/JS, 0 `article`, 1 enlace `<a>`, y menciona una ruta API en un script. **No se verificó el funcionamiento ni permisos de esa API**; sin conector.
- **SoloLatino**: exclusión explícita previa por advertencia de posible bloqueo. No realizar más sondeos ni intentar evitar controles.
- **DeepFlix**: HTTP 403 en comprobación anterior. No forzar acceso.

## Preparación de la QA final
- Archivo `QA_FINAL_MANIFIESTO_2026-11-07.json`: 32 entradas con etapa previa, tareas previstas y resultados inicializados como `not_run_in_this_batch`.
- Comprobado programáticamente el recuento 4 + 2 + 20 + 6 = 32, sin IDs repetidos.
- Esta revisión **no ejecutó pruebas de streaming, descargas ni navegador Android**. El resultado de registro/compilación de un adaptador no equivale a que funcione.

## Estado de compilaciones
- APK de 26 adaptadores ya compilada y preservada en `C:\Users\Pc\Downloads\VideoPropia-TV-26Adaptadores-2026-11-07.apk` de turno anterior. En este turno no se modificó código Java ni se recompiló APK; no se generó una versión nueva innecesaria.
- Sin uso de modelos locales ni APIs externas de IA: análisis determinista local vía PowerShell y Python estándar.

## Decisión para siguiente tarea
La integración de fuentes con evidencia suficiente llegó al límite del material disponible. Pasar, cuando se retome, al **único lote de QA final** sin volver a explorar indefinidamente los seis bloqueados; clasificar fallos por carga de catálogo, búsqueda, temporadas/episodios, resolución, video/audio y fullscreen. Investigar cada fallo con evidencia precisa y sin eludir restricciones.
