# Continuidad de APP propia de video

Leo quiere continuar en otro chat del proyecto porque queda poca cuota. Priorizar scripts y recursos locales; avanzar autonomamente cuando se tenga acceso y autorizacion. Si necesita intervenir Leo, pedir una sola accion y esperar. Este chat es remoto; Ollama es el modelo local comprobado. No suponer acceso a la PC desde otro chat.

## Ubicacion y ejecucion

- PC I5; proyecto real: `C:\workspace\app-video-propia-android-v1`.
- Fuentes Java: `app\src\main\java\ar\appvideo\player`.
- Compilar en PowerShell: `& 'C:\workspace\app-video-propia-android-v1\build-local.ps1'`.
- APK: `C:\workspace\app-video-propia-android-v1\app\build\outputs\apk\debug\app-debug.apk`.
- SDK: `C:\Users\Pc\AppData\Local\Android\Sdk`.
- Emulador Android TV existente `ChainReaction_TV_API34`, serial `emulator-5554`, 1920x1080.
- Actividad: `ar.appvideo.player/.MainActivity`.
- Instalar solo despues de decidir que version probar: `& 'C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 install -r 'C:\workspace\app-video-propia-android-v1\app\build\outputs\apk\debug\app-debug.apk'`.
- Arrancar: `& 'C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s emulator-5554 shell am start -W -n ar.appvideo.player/.MainActivity`.

## Comprobado

Pantalla completa implementada y probada: abre automaticamente al reproducir, Atras vuelve al reproductor integrado conservando posicion, Menu alterna. Leo confirmo audio y funcionamiento en las pruebas previas.

Cuatro fuentes tienen catalogo nativo, portadas, busqueda, pelicula, temporadas, episodios y paginacion. Pruebas Android reales aprobaron Matrix y Breaking Bad T1 E1 con avance, pausa y reanudacion:

- CineHax: `qa-catalog-cinehax-20261106-204041`.
- LaMovie: `qa-catalog-lamovie-20261106-203841`.
- SeriesMetro: `qa-catalog-seriesmetro-20261106-211106`.
- Pelispedia: `qa-catalog-pelispedia-20261106-211331`.

Los resultados y capturas estan dentro del proyecto. Estos casos no prueban todo el catalogo ni el idioma de cada video. Las fuentes originales Blender e Internet Archive siguen en el proyecto. Revisar `ESTADO_ACTUAL.md` para registros anteriores.

## Cambio en curso: anime, todavia sin prueba real

Se registraron TioAnime y LatAnime en VideoProviders y se amplio HtmlCatalogProvider para anime, busqueda por formularios propios y episodios. TioAnime declara los numeros en un array publico; LatAnime incluye enlaces explicitos. CatalogBrowser muestra Episodios sin inventar temporadas.

La compilacion previa de estos cambios fue exitosa. El ultimo cambio agrega a MediaPageResolver la lectura de enlaces publicos en arrays JS, data-player base64 y servidores mp4upload/yourupload/ok.ru/v.tioanime. No ejecuta JavaScript. Prioriza los reproductores declarados antes de archivos de video para evitar anuncios. La compilacion de este ultimo cambio se verifica al cerrar esta sesion; consultar `build-attempt.log` y el checkpoint final de ESTADO_ACTUAL.

Estos cambios de anime NO se instalaron ni probaron en el emulador durante este tramo. El APK instalado anteriormente corresponde a las cuatro fuentes comprobadas. No presentar anime como terminado.

Siguiente trabajo: comprobar compilacion final; inspeccionar resolucion de reproductores de TioAnime y LatAnime; instalar; crear prueba Android de busqueda Naruto, ficha, Episodios y reproduccion real. Reutilizar `qa_catalog.py` sin ejecutar su main de peliculas al importar. Los helpers actuales esperan sufijo Serie y tendran que aceptar Anime. No correr dos automatizaciones UI simultaneamente.

Fixtures HTML locales sin nuevas consultas: `C:\Users\Pc\AppData\Local\Temp\appvideo-catalog-discovery` contiene `tioanime-episode.html`, `latanime-episode.html`, perfiles y busquedas. TioAnime ofrece enlaces YourUpload/Okru/v.tioanime; LatAnime usa data-player base64 con Filemoon/Mixdrop/mp4upload, entre otros. Que un servidor figure no prueba que funcione.

## Recursos ya usados

Ollama `http://127.0.0.1:11434`, modelo `qwen2.5-coder:7b` Q4_K_M. Se verifico respuesta y 100% GPU en RTX3050 de 8GiB. El analisis local de 18 conectores termino; resultados `local-analysis-*\summary.json`, marcados no verificados. Algunas respuestas fueron incompletas. No repetir el lote ni ejecutar codigo del modelo sin revision. El modelo fue descargado de memoria al terminar el script; no se afirma que siga cargado.

Scripts y descubrimientos: `analyze_sources_local.py`, `probe_catalogs_local.py`, `discover_remaining_sources.py`, `LOCAL_CATALOG_DISCOVERY.json`, `REMAINING_SOURCE_DISCOVERY.json`, `ANIME_DISCOVERY.json`. Jsoup 1.22.2 ya estaba en cache y se usa sin nuevas instalaciones.

## Pendientes y limites

- Actualizar inventario `C:\workspace\app-tv-propia-fuentes-v1\fuentes.json`, assets/channels.json, provider_audit.json y texto Estado/README: parte de su informacion es anterior a las cuatro fuentes. La UI usa el registro real para varias etiquetas; no convertir inventarios viejos en evidencia.
- Continuar otras fuentes solo con pruebas propias. HTTP200 no equivale a reproduccion.
- SoloLatino excluido de nuevas pruebas por aviso previo del sitio sobre bloqueo de IP. No repetir ni eludir.
- PelisSeriesHoy redirigia a Google; DeepFlix HTTP403; Cineplus123 busqueda406. No contar como operativos.
- ADB tuvo cortes; reiniciar Android sin borrar datos y `adb reconnect offline` recuperaron. No atribuir al reproductor un fallo de automatizacion o launcher.
- Backups preservados; el mas reciente previo al anime es `backup-anime-20261106-211612`. No borrar ni sobrescribir las versiones guardadas.

Mensaje sugerido para el otro chat: Lee CONTINUAR_APP_VIDEO_2026-11-07.md del proyecto. Retoma desde los cambios de anime pendientes, usando recursos locales. No cuentes una fuente como funcional hasta comprobar reproduccion real.

## Checkpoint de cierre 2026-11-07
El ultimo cambio de anime/MediaPageResolver compilo correctamente mediante build-local.ps1 en la PC I5: BUILD SUCCESSFUL, BUILD_EXIT_CODE=0. No fue instalado ni probado en Android. La primera tentativa desde el terminal restringido fallo al cargar native-platform.dll; la ejecucion local mediante Desktop Commander termino correctamente sin modificar dependencias. No queda una prueba ni analisis de IA ejecutandose por encargo de este tramo.
