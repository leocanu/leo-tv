# ENCARGO DE IMPLEMENTACIÓN — LEO TV (Antigravity IDE)

Actuá como desarrollador Android TV senior, diseñador UI/UX de TV y responsable de QA. **No te limites a revisar, opinar o hacer un plan: implementá mejoras reales, compilá y probá la aplicación por etapas, conservando siempre una versión funcional.** La meta es alcanzar la riqueza de navegación, organización y uso de Xuper, con código, marca, diseños y fuentes propios. No copiar código ni recursos de terceros ni replicar servicios privados. No esperes confirmación para cada modificación reversible: el usuario autorizó expresamente que avances y guardes todo. No hagas cambios destructivos.

## 0. Ubicaciones reales verificadas en la I5

**Proyecto principal (Windows):**
`C:\workspace\app-video-propia-android-v1`

**Referencia instalada y código actual:** Leo TV Android TV, paquete `ar.appvideo.player`, **versión 0.10.0**, Java + Gradle + Media3/ExoPlayer. Última APK compilada:
`C:\Users\Pc\Downloads\LeoTV-v0.10-ReproductorVerificado-2026-11-08.apk`

**Emulador Android TV:** AVD `ChainReaction_TV_API34`, cuando está encendido ADB `emulator-5554`; ADB ejecutable:
`C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe`

**APK de Xuper para inspección LOCAL, sin ejecutar enlaces privados ni extraer credenciales:**
`C:\Users\Pc\Downloads\XuperSBC-1080.apk`

**APK adicional de referencia:**
`C:\Users\Pc\Downloads\Magis Pro TV_4.0.apk`

**Informe ya existente de funciones y arquitectura de ambas aplicaciones:**
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\INVESTIGACION_MAGIS_XUPER_ESTRUCTURA_2026-11-07.md`

**Captura REAL del inicio actual de Leo TV:**
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\revision-antigravity\leotv-inicio.png` (de versión anterior; generar una actual)

**Crítica visual previa de Antigravity:**
`C:\workspace\app-video-propia-android-v1\OPINION_ANTIGRAVITY.md`

**Resultado de QA del reproductor Media3 más reciente:**
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\QA_REPRODUCTOR_LEOTV_2026-11-08.md`

**Compilación local:**
`C:\workspace\app-video-propia-android-v1\build-local.ps1`

**Archivos actuales relevantes:**
`app\src\main\java\ar\appvideo\player\MainActivity.java`
`app\src\main\assets\channels.json`
`app\src\main\assets\catalog.json`
`app\src\main\res\drawable\leo_brand.png`
`app\build.gradle`
Leerlos ANTES de cambiar nada. Puede haber modificaciones de otros chats/agentes: comprobar marcas de tiempo y hacer respaldo.

## 1. Qué nos interesa de Xuper

Estudiá la experiencia visual y funcional **desde la APK y el emulador** en la medida en que sea accesible sin inicio de sesión ajeno. Compará capturas, navegación con control remoto, foco, transiciones y distribución de elementos; no supongas prestaciones que no verificaste. La investigación anterior identificó estas categorías/pantallas: **Live, Specials SBCxON, Featured, Movies, Series, Kids, Anime, Explore**; recursos de detalle VOD, selección de temporadas, episodios, búsqueda, reproductor, continuar reproducción. Distinguí lo observado directamente de lo inferido por recursos de APK.

Queremos que **Leo TV** tenga equivalentes funcionales, en español y con estilo original:
- Inicio moderno con hero/banner, destacados, carruseles horizontales, portadas y categorías con información real.
- En vivo: canales por países/géneros, logo, selección, favoritos, recientes, EPG si hay datos autorizados.
- Películas: fichas con carátula, título, año, sinopsis, búsqueda, fuente disponible y reproducción real.
- Series: ficha, temporadas, episodios, reanudación de reproducción, vistos.
- Infantil, Anime y Explorar/búsqueda unificada.
- Biblioteca o “Mis nubes” que importe **archivos locales y listas autorizadas** (M3U/M3U8, carpetas/NAS), sin necesitar los antiguos servidores de My Family Cinema.
- Favoritos, historial y continuar viendo guardados localmente.
- Controles multimedia con D-Pad: OK, Atrás, pausar, avanzar, pantalla completa, progreso, estado de carga y error.
- Manejo claro de ausencia de fuente o error, sin prometer reproducción cuando solo existe un catálogo.

No construir pantallas vacías ni botones decorativos que simulen funcionalidad. Las secciones sin proveedor operativo deben mostrar el estado correcto.

## 2. Identidad visual propia

Marca: **Leo TV**. Usar el logo del león aprobado por el usuario, ubicado en `app\src\main\res\drawable\leo_brand.png`. El texto **“Leo” blanco** y **“TV” dorado**, para contraste con el fondo oscuro; no ponerle caja/fondo blanco. La imagen ya está integrada, no reemplazarla por un nuevo león. Se puede mantener una variante compacta para el menú. Paleta azul marino / blanco / dorado; tipografía clara y propia, buen contraste y safe areas TV 1080p y 720p. La revisión anterior señaló problemas de texto recortado, foco D-Pad débil y falta de portadas: corregir y verificar visualmente.

## 3. Listas y fuentes ya investigadas — MOSTRAR Y CONSERVAR

**Auditoría verificada (metadatos de las listas, no reproducción):**
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\AUDITORIA_LISTAS_2026-11-07.md`
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\AUDITORIA_LISTAS_2026-11-07.json`
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\CATALOGO_IPTV_TITULOS_2026-11-07.csv`

Se registraron 6.428 entradas M3U **sin desduplicar y sin probar todos los enlaces**. Fuentes concretas:

| Fuente | Entradas auditadas | URL |
|---|---:|---|
| TDTChannels TV | 573 | https://www.tdtchannels.com/lists/tv.m3u8 |
| TDTChannels Radio | 1052 | https://www.tdtchannels.com/lists/radio.m3u8 |
| IPTV-SV | 1883 | https://raw.githubusercontent.com/JMigue85/IPTV-SV/refs/heads/main/IPTVSV.m3u |
| IPTV-org Español | 2421 | https://iptv-org.github.io/iptv/languages/spa.m3u |
| Mametchi Argentina (M3U.CL) | 147 | https://www.m3u.cl/lista/AR.m3u |
| Mametchi Dibujos | 279 | https://mametchikitty.github.io/Listas-IPTV/dibujos-animados.m3u |
| Mametchi Studio Ghibli | 23 | https://mametchikitty.github.io/Listas-IPTV/studio-ghibli-latino.m3u |
| Telechancho | 50 | https://telechancho.github.io/telechancho-iptv/telechancho-infinity.m3u |

También investigar como referencias, **sin descartar ni habilitar reproducción automáticamente**:
- https://github.com/mametchikitty/Listas-IPTV
- https://github.com/JMigue85/IPTV-SV
- https://www.tdtchannels.com/listas/
- https://www.adslzone.net/noticias/streaming-tv/iptv-gratis-ver-mas-7000-canales-television/
- https://es.scribd.com/document/403853519/Listas-fase-1
- https://es.scribd.com/document/406950321/How-to-Import-a-MFC-Cloud-Package
- https://gitlab.com/jillhart/jill_s_cinema
- Listas de ChispaIPTV recopiladas antes: revisar el documento/inventario existente en el proyecto; 27 enlaces acortados históricos, muchos no vigentes.

**Importante:** que haya una URL públicamente accesible NO demuestra derecho a redistribuir películas/series o retransmitir. Mantener todo en un inventario con estados `verificada`, `reproducción pendiente`, `no disponible`, `autorización desconocida`. Se permite analizar títulos y metadatos; para reproducción integrada, priorizar emisiones abiertas, licencias claras y bibliotecas propias/autorizadas. No evadir contraseñas, DRM, geobloqueos, HTTP 403 ni autenticaciones. No acceder a cuentas ni recopilar credenciales de Xuper/Magis/MFC. No instalar software de terceros como producto final: construimos **nuestra propia app**.

## 4. Reproductor: NO romper lo comprobado

Leo TV v0.10 usa Media3/ExoPlayer para MP4 y otras fuentes, con overlays de estado en pantalla completa, errores HTTP separados y estado de MediaSession actualizado. **Prueba real:** *Big Buck Bunny* (demostración corta) comenzó a reproducirse y terminó hacia los 10 s; no se verificó audio por escucha humana ni todas las fuentes. Una señal TDTChannels La 1 respondió HTTP 403 anteriormente. Leé el QA y conservá un caso de reproducción de referencia antes y después de modificar.

Avanzá en diagnóstico de reproducción por lotes: diferenciar enlace caído, 403, CDN, HLS/MP4, MIME, codec y fallo de Media3; log resumido; prueba de pausa, avance, reanudar, orientación, D-Pad y pantalla completa. No usar solo `HEAD` como prueba de reproducción.

## 5. Ejecución y entregables OBLIGATORIOS

**Tenés autorización para implementar y probar cambios reversibles en el proyecto. No te limites a entregar recomendaciones ni a esperar confirmaciones innecesarias.** Trabajá por lotes, priorizando resultados comprobables:

A. Inspeccionar APK Xuper de forma local, reunir capturas reales si es posible, y dejar matriz `Xuper → Leo TV` (funcionalidad, estado actual, implementación, prueba).
B. Modernizar home y navegación con estructura propia, menú adaptativo, carruseles, estados reales y foco D-Pad visible. Incorporar portadas solo cuando estén justificadas y disponibles con permisos.
C. Implementar al menos **un flujo vertical funcional completo** (por ejemplo inicio → películas abiertas → ficha → reproducir → volver, o canales libres → elegir → reproducir). Elegir según disponibilidad comprobada.
D. Conectar inventario de fuentes sin perder las anteriores; categorizar, buscar, deduplicar visualmente y respetar autorizaciones.
E. Compilar **APK debug** con `build-local.ps1`, instalar en `emulator-5554` cuando esté disponible, tomar capturas, correr pruebas, corregir errores. Incrementar `versionCode` y `versionName` de cada nueva entrega, y guardar APK versionada en Descargas sin sobrescribir otras.
F. Reducir complejidad de `MainActivity.java` gradualmente si conviene, pero no hacer una reescritura masiva de golpe.

### Registro para retomarlo sin perder el hilo

Crear desde el principio:
`C:\workspace\app-video-propia-android-v1\ANTIGRAVITY_ESTADO.md` — estado objetivo, alcanzado y próximo bloque; actualizarlo **después de cada cambio relevante**.
`C:\workspace\app-video-propia-android-v1\ANTIGRAVITY_CAMBIOS.md` — archivos tocados, razón, respaldos, rutas de APK, versión y decisiones de diseño.
`C:\workspace\app-video-propia-android-v1\ANTIGRAVITY_QA.md` — pruebas intentadas y resultados **verificados**, log resumido, errores conocidos, qué falta probar.
`C:\workspace\app-video-propia-android-v1\ANTIGRAVITY_FUENTES.md` — inventario de fuentes sin eliminar las dudosas, con riesgo/autorización/estado.
`C:\workspace\app-video-propia-android-v1\ANTIGRAVITY_RETOMAR.md` — instrucciones concretas y comandos completos de PowerShell 5.1 para continuar desde ChatGPT o cualquier otro colaborador: cómo abrir, compilar, instalar, lanzar, probar, revertir, con rutas y estado real.

**Antes de tocar archivos:** revisar qué ya existe, respaldar archivos afectados con sello temporal fuera de `app/src`, no sobrescribir APK ni trabajo ajeno. Si no hay repositorio Git configurado, no asumirlo: guardar snapshots y explicar cómo recuperarlos. Nunca borrar fuentes ni instalaciones automáticamente.

**Preferencias técnicas del usuario:** Windows PowerShell 5.1, comandos completos compatibles, primero scripts deterministas/locales y herramientas existentes; IA local si fuese necesaria y estuviera comprobada; no enviar archivos privados a servicios de IA en nube sin permiso específico. Esta petición autoriza a Antigravity a analizar y mejorar el **código y las imágenes del proyecto Leo TV**, no datos ajenos o secretos. Evitar operaciones costosas por cada entrada de un catálogo; analizar por lotes. Comunicar con precisión lo preparado, compilado, probado y pendiente.

### Comunicación final

En tu respuesta indicar: (1) qué cambiaste realmente, (2) qué verificaste con datos concretos, (3) la ruta exacta de la APK, (4) qué quedó bloqueado y por qué, y (5) dónde está `ANTIGRAVITY_RETOMAR.md`. **No declares que la app tiene todas las funciones de Xuper si aún no se probaron.** Si agotás una sesión, dejá los documentos actualizados ANTES de terminar, para que otro colaborador pueda continuar sin repetir la investigación.

**Empezá ya inspeccionando proyecto, referencia Xuper e informes existentes. Luego IMPLEMENTÁ una primera mejora verificable y guardá los cinco documentos de continuidad.**
