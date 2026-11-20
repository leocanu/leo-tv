# Estado verificado al retomar tras el reinicio

- Dispositivo: I5, accesible mediante Desktop Commander.
- Android TV: ChainReaction_TV_API34, emulator-5554, arranque completo.
- Aplicacion: ar.appvideo.player/.MainActivity, abierta en Inicio.
- APK actualizado: app/build/outputs/apk/debug/app-debug.apk.
- Compilacion local sin conexion: BUILD SUCCESSFUL.
- Dependencia de Media3 completada previamente mediante Gradle; no se utilizaron modelos ni APIs de IA para procesar archivos.
- Copia previa de codigo, APK y README: backup-reinicio-20261106-095110.

## Cambios

- Correccion de error de compilacion: PlaybackException.getErrorCodeName().
- Foco inicial compatible con la alternancia entre mouse y control remoto.
- Sesion multimedia de Android para reproducir, pausar y desplazar la posicion.
- Actualizacion del estado multimedia al cambiar posicion.
- Pausa al salir de la actividad y liberacion del reproductor al destruirla.
- Estado visible de pausa.
- Script iniciar-app.ps1 para volver a abrir el entorno sin iniciar otra instancia del mismo emulador.
- Pruebas locales con Python y ADB. Los controles se verifican con el estado multimedia de Android, evitando reutilizar pantallas XML antiguas cuando UIAutomator no consigue una captura nueva.

## Resultados

La ultima prueba dirigida a controles termino con codigo 0: 11 comprobaciones aprobadas y 0 fallidas.
Evidencia: qa-20261106-100921/results.json y capturas de esa carpeta.

Se verificaron inicio, canal Blender, preparacion de video, pausa, reanudacion, progreso real del reloj, avance, retroceso, pausa al salir, regreso pausado e inicio final.

La posicion avanzo de 12.958 a 27.972 segundos con avance, y retrocedio a 22.972 segundos con retroceso.

La prueba completa anterior, qa-20261106-100705, verifico ademas Demo, navegacion Atras e imagen/preparacion de Big Buck Bunny completo desde Internet Archive. Detecto un estado de posicion multimedia desactualizado; esa parte se corrigio y se volvio a probar en la prueba dirigida final.

El registro de fallos de Android consultado al final no devolvio entradas.

## Pendientes reales

- 32 proveedores candidatos siguen sin conectores integrados en esta app.
- CineHax, SoloLatino y SeriesMetro no tienen reproduccion propia validada aqui.
- Blender e Internet Archive son entradas de prueba con URLs fijas, no catalogos completos.
- No se verifico audio por escucha humana en esta sesion.
- No se realizo instalacion ni prueba en TV fisico.

## Actualizacion: pantalla completa

APK compilado sin conexion e instalado en emulator-5554.
Los videos abren en pantalla completa. Atras reduce el video sin reiniciarlo; un segundo Atras regresa al canal.
Menu alterna el modo, y la vista normal incluye el boton Pantalla completa.
Se conserva la proporcion de imagen y no se cambia la instancia del reproductor.

Prueba qa-fullscreen-20261106-193729: 7 comprobaciones aprobadas, 0 fallidas, salida 0.
Verifico dimensiones reales 1920x1080 en Media3 y Demo, salida con Atras, conservacion exacta de pausa y posicion (4437 ms), alternancia con Menu, boton y regreso al canal.
Se inspecciono captura real del video ocupando toda la pantalla.
Copia previa: backup-fullscreen-20261106-193454.

## CineHax: primera prueba integrada en la app propia

CineHaxProvider.java implementa un conector propio en Android: obtiene el reproductor de Matrix por su ID, encuentra el enlace HLS y valida el manifiesto antes de entregarlo a Media3 con el User-Agent correspondiente.
No se incorporo el archivo JavaScript de Nuvio ni se requiere un servidor en la PC.

La prueba qa-cinehax-20261106-195533 paso 4 comprobaciones: tarjeta con Matrix, resolucion en Android, avance con pausa a 7510 ms y reanudacion hasta 12122 ms.
Captura inspeccionada: imagen de apertura de Matrix en pantalla completa y duracion visible 2:16:10.
No se confirmo nuevamente el idioma por escucha humana.
Se actualizo el inventario y la tarjeta: CineHax ofrece PRUEBA MATRIX.
Alcance: una pelicula de prueba. No se implemento busqueda ni catalogo completo de CineHax; los otros 31 candidatos siguen pendientes.

Revalidacion final de CineHax: qa-cinehax-20261106-200046, 4/4 comprobaciones aprobadas, salida 0. Matrix avanzo de 8345 a 12983 ms tras reanudar.
Se observo que el reproductor alterna los servidores vimeos.net y vimeos.zip. Se agrego el segundo dominio a la lista reconocida, tras una reapertura que inicialmente fallo al extraer el enlace. La prueba posterior paso.
El ultimo cambio de texto reemplaza la etiqueta generica de muestra autorizada por reproduccion directa; no modifica la resolucion ni el reproductor.

## Catalogo CineHax y episodios verificados

CineHax ahora consulta la API publica del sitio desde Android: listados de 24 por pagina, busqueda, filtros de peliculas/series/novelas, fichas, temporadas y episodios.
Se agregaron portadas, cache de metadatos de 12 paginas por cinco minutos y cache de imagenes de 8 MiB. Las imagenes usan tres tareas simultaneas; el catalogo usa dos. No se conservan peliculas completas ni enlaces HLS caducados.
El mismo reproductor obtiene enlaces nuevos para cualquier titulo/episodio por su identidad, sin fijarse a Matrix.
qa-catalog-20261106-202523 termino con salida 0: catalogo con portadas, busqueda Matrix, imagen/avance/reanudacion de Matrix desde resultados, busqueda de Breaking Bad, temporadas/episodios, reproduccion/reanudacion T1 E1 y pagina siguiente.
No se afirmo que cada titulo del catalogo reproduzca ni se verifico por escucha el idioma de cada episodio.
Se consultaron tambien las paginas publicas de los otros candidatos mediante scripts locales; SoloLatino sigue excluido de nuevas consultas por el aviso anterior del sitio. HTTP 200 no equivale a reproduccion y se detecto que PelisSeriesHoy actualmente redirige a Google.

## Navegador compartido y LaMovie verificados

Se separaron CatalogData, VideoProvider, CatalogBrowser, HttpSupport y el lector de Vimeos. LaMovie usa su API publica y sus propios identificadores; no se hace pasar una consulta de CineHax por otro proveedor.
LaMovie tiene catalogo paginado, busqueda, fichas, temporadas y episodios. Prioriza los servidores rotulados Latino y actualmente resuelve Vimeos.
El lector desempaqueta las cadenas publicas del reproductor sin ejecutar JavaScript. Una prueba Java local contra HTML real detecto un problema de recursion en expresiones regulares; se corrigio antes de instalar y la prueba recupero el manifiesto correctamente.
qa-catalog-lamovie-20261106-203841: salida 0, todas las comprobaciones aprobadas, incluidos Matrix y Breaking Bad T1 E1 con imagen/progreso/reanudacion.
qa-catalog-cinehax-20261106-204041: salida 0, todas las comprobaciones aprobadas tras compartir el navegador.
No se volvio a confirmar el idioma por escucha humana para esos nuevos casos.
Se prepararon adaptadores HTML propios de SeriesMetro y Pelispedia; todavia estan en la fase de compilacion/prueba y no se cuentan aqui como reproduccion verificada.

## SeriesMetro, Pelispedia y analisis local

Despues de recuperar ADB y reiniciar Android sin borrar datos:
- qa-catalog-seriesmetro-20261106-211106: salida 0; pelicula Matrix y Breaking Bad T1 E1 reproducen, pausan y reanudan; catalogo, busqueda y pagina siguiente aprobados.
- qa-catalog-pelispedia-20261106-211331: salida 0; mismas comprobaciones aprobadas con sus propios enlaces.
- Los resultados anteriores fallidos por seleccion/desplazamiento de la automatizacion y por perdida de ADB no se contaron como pruebas de reproduccion.
Se empleo el parser Jsoup 1.22.2 ya presente en la cache local, sin instalar otra herramienta.
Se comprobo Ollama con qwen2.5-coder:7b Q4_K_M: respuesta OK y 100% GPU, 4096 de contexto; size y size_vram coincidieron. La GPU detectada es RTX 3050 de 8192 MiB.
Se ejecuto analyze_sources_local.py para revision estatica de los conectores restantes. Los resultados estan marcados como sugerencias no verificadas y nunca se ejecuta codigo generado por el modelo. No se uso otro servicio de IA en la nube para ese analisis.

## Checkpoint de cierre 2026-11-07
El ultimo cambio de anime/MediaPageResolver compilo correctamente mediante build-local.ps1 en la PC I5: BUILD SUCCESSFUL, BUILD_EXIT_CODE=0. No fue instalado ni probado en Android. La primera tentativa desde el terminal restringido fallo al cargar native-platform.dll; la ejecucion local mediante Desktop Commander termino correctamente sin modificar dependencias. No queda una prueba ni analisis de IA ejecutandose por encargo de este tramo.
