# Checkpoint — 26 adaptadores — 7/11/2026

Acuerdo vigente: primero configuración e integración; QA general por lotes al final; entonces corregir fallos. No volver a probar cada fuente ahora.

## Cambios realizados
- Revisado el HTML local de Cineplus123 (`C:\Users\Pc\AppData\Local\Temp\appvideo-catalog-discovery\cineplus123.html`): 67 tarjetas `article`, 69 enlaces de tipo `/peliculas/`, 42 enlaces de tipo `/serie-de-tv/` (coincidencias, no necesariamente títulos únicos).
- Se agregó Cineplus123 al registro de `VideoProviders.java` usando `HtmlCatalogProvider("Cineplus123","https://cineplus123.org",true,null)`, expresamente **provisional**; su búsqueda había dado HTTP 406 en una comprobación anterior. Ni catálogo Android ni video se han probado en este lote.
- `MainActivity.java`: recuentos actualizados: 4 con reproducción anteriormente comprobada, 2 anime con episodios pero reproducción fallida, 20 adaptadores provisionales sin QA, 6 pendientes o restringidas.
- `channels.json`: Cineplus123 marcado como `adapter_compiled_search_406_previous_probe`, `enabled=true` sólo significa adaptador registrado. Backup `channels.pre-cineplus123-20261107.json`.

## Verificaciones
- Compilación local `BUILD SUCCESSFUL`, código salida 0.
- APK archivada sin instalar ni ejecutar QA: `C:\Users\Pc\Downloads\VideoPropia-TV-26Adaptadores-2026-11-07.apk`; SHA256 `AAD1B51EBC93ED1B8BD1461AC49DE6FBB7548401F9CC1480A6A1E86130A68053`.
- Total inventariado 32; 26 con adaptador registrado; 6 pendientes sin conector real.
- Pendientes: PelisSeriesHoy (redirigía a Google), Embed69 (sin catálogo identificado), Pelisforte (sin catálogo identificado), AllCalidad (sin catálogo identificado), SoloLatino (advertencia de posible bloqueo, excluido), DeepFlix (HTTP 403 anterior). No inventar disponibilidad ni eludir controles.

No se usaron modelos de IA locales o servicios externos para procesar archivos; únicamente PowerShell, scripts Python estándar, lectura de HTML local y Gradle en I5.
