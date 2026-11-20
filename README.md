# Video Propia - prototipo Android y Android TV

Proyecto local: C:\workspace\app-video-propia-android-v1

La pantalla principal presenta tarjetas grandes para entrar a cada canal por separado.

## Estado comprobado

- Compilación Gradle local sin conexión: BUILD SUCCESSFUL después de completar las dependencias de Media3.
- APK instalado en el emulador ChainReaction_TV_API34.
- Demo corta con VideoView.
- Reproductor Media3/ExoPlayer para los videos completos de prueba de Blender e Internet Archive.
- Imagen comprobada mediante capturas de Elephants Dream y Big Buck Bunny.
- Inventario de 32 fuentes candidatas: todavía no contiene conectores de reproducción de esos proveedores.
- CineHax y SoloLatino siguen pendientes de integración propia. No deben confundirse las pruebas anteriores en otras aplicaciones con una integración aquí.
- Sin instalación en un televisor físico en esta sesión.

## Herramientas locales

Abrir el emulador y la app desde PowerShell:

```powershell
& 'C:\workspace\app-video-propia-android-v1\iniciar-app.ps1'
```

Compilar utilizando las dependencias locales:

```powershell
& 'C:\workspace\app-video-propia-android-v1\build-local.ps1'
```

Las pruebas funcionales se ejecutan con Python y ADB, sin modelos ni APIs de IA:

```powershell
py -3 'C:\workspace\app-video-propia-android-v1\qa_reinicio.py'
```

Cada ejecución guarda sus resultados JSON, capturas y pantallas XML en una carpeta qa con fecha y hora. Esas fechas reflejan el reloj de la PC.

El script de inicio espera a que Android termine de arrancar y abre la app existente. No instala aplicaciones adicionales ni modifica el inicio de Windows.

## Límites

Los videos de prueba se reproducen desde URLs fijas. No se implementó un catálogo consultable de Internet Archive o Blender.
No se verificó el audio mediante escucha humana en esta sesión.
Los 32 proveedores figuran como pendientes en la interfaz; no se afirma que reproduzcan.

## Pantalla completa

Los videos abren automaticamente en pantalla completa, manteniendo la proporcion original.
Atras vuelve a la vista con titulo y estado sin reiniciar el video. Un segundo Atras vuelve al canal.
Menu alterna entre pantalla completa y vista normal. En la vista normal tambien aparece el boton Pantalla completa.
El cambio de tamano conserva la misma instancia del reproductor y su posicion.

Prueba dirigida: py -3 C:\workspace\app-video-propia-android-v1\qa_fullscreen.py

## CineHax integrado para prueba

La tarjeta CineHax ahora ofrece Matrix (1999). Android resuelve el enlace nuevo al pulsar reproducir y Media3 lo reproduce en pantalla completa. La prueba propia confirmo imagen, progreso y pausa/reanudacion.
Esto reemplaza el estado anterior de CineHax sin integrar. Los otros 31 candidatos siguen pendientes; no hay catalogo completo ni busqueda de CineHax todavia.
Prueba local: py -3 C:\workspace\app-video-propia-android-v1\qa_cinehax.py
