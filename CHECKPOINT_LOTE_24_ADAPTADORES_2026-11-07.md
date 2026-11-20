# Checkpoint — 24 adaptadores registrados — 2026-11-07

## Decisión vigente
Configurar primero, hacer una sola revisión general por lotes al final y entonces corregir los que fallen; no repetir ahora las pruebas previas de reproducción. No eludir restricciones de terceros ni probar SoloLatino (advertencia previa de bloqueo).

## Realizado en este lote
- Desde los HTML locales de `C:\Users\Pc\AppData\Local\Temp\appvideo-catalog-discovery` se revisaron enlaces y estructuras de 12 candidatos restantes.
- `VideoProviders.java`: se sumaron **8 adaptadores provisionales** `Pelisplus` (pelisplushd.bz), `Seriesflix` (pelisflixhd.love), `AresHD` (areshd.com), `CineHax+` (cinehaxplus.com), `CineHDPlus` (cinehdplus.org), `DeTodo Peliculas` (detodopeliculas.nu), `JKAnime` (jkanime.net) y `MonosChinos` (vww.monoschinos2.net).
- `HtmlCatalogProvider.java`: selector de enlaces de fichas fallback para fuentes provisionales con estructura no basada en `article`, filtros de URL de películas/series y detección de rutas anime `/slug/numero` para JKAnime. Esta adaptación es genérica y **NO está validada** en cada fuente.
- `MainActivity.java`: resumen de estado actualizado a 4+2+18+8.
- `app/src/main/assets/channels.json`: estado de 32 fuentes actualizado; copia anterior en `app/src/main/assets/channels.pre-status-batch-20261107.json`. `enabled=true` significa solo que la fuente tiene conector registrado, no reproducción verificada.

## Estado de las 32 fuentes
- 4: reproducción anteriormente probada — CineHax, LaMovie, SeriesMetro, Pelispedia.
- 2: catálogo/búsqueda/episodios probados pero reproducción de los títulos anime examinados fallida — TioAnime y LatAnime.
- 18: adaptadores compilados pero aún SIN QA Android ni reproducción confirmada (10 anteriores y 8 nuevos).
- 8: sin conector reproducible registrado, debido a estructura insuficiente, restricciones o datos pendientes: PelisSeriesHoy (redirección a Google), Embed69 (sin catálogo identificable), Pelisforte (sitio sin catálogo identificado), AllCalidad (sin enlaces identificables), DoramasFlix (React, requiere parser propio), SoloLatino (advertencia previa, excluido), DeepFlix (403 anterior), Cineplus123 (búsqueda 406 anterior).

## Comprobaciones realizadas
- Gradle `BUILD SUCCESSFUL` dos veces: compilación Java (14 s) y empaquetado de cambios de inventario (11 s).
- APK archivada sin instalar en dispositivos: `C:\Users\Pc\Downloads\VideoPropia-TV-24Adaptadores-2026-11-07.apk`; SHA-256 `6E306B84DF80E54DA131FE10CA08FA7BF12F66ED03C122E396DDD4A0A66DB2AB`.
- No se ejecutaron tests de reproducción ni redes masivas, conforme a la decisión del usuario. No se usaron modelos locales ni IA externa para procesar archivos; fueron scripts Python/PowerShell y compilación Gradle locales.

## Pendiente
- Evaluar adaptador específico para DoramasFlix sólo con datos que permitan extraer títulos y enlaces claramente, sin registrar un conector ficticio.
- Revisar luego el conjunto en una ronda de QA: catálogo, búsqueda, temporadas, episodios, resolución, reproducción/audio y fullscreen; clasificar fallos separadamente.
