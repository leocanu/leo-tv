# 📋 REPORTE DE ESTADO Y ARQUITECTURA: ECOSISTEMA CHAIN REACTION & LEO TV

> **Fecha del reporte**: 23 de Noviembre de 2026  
> **Autor / Desarrollador**: Leo Canu  
> **Objetivo del documento**: Resumen técnico de sincronización, arquitectura, canales de release y últimos desarrollos implementados en las aplicaciones de escritorio, móvil y Android TV.

---

## 1. Visión General del Ecosistema

El proyecto **Chain Reaction** es un ecosistema multiplataforma de audio, video y streaming compuesto por tres pilares integrados bajo un mismo repositorio de distribución y actualizaciones automáticas:

1. **Chain Reaction Music (Desktop - Windows x64)**: Aplicación de escritorio desarrollada en Python + PySide/Qt enfocada en reproducción de música, descargas locales, gestión de bibliotecas, interfaz con soporte bilingüe (ES/EN), temas personalizables e integración con Matecito.
2. **Chain Reaction Music (Android TV & Mobile)**: Clientes nativos Android para reproducción musical, historial de cuenta y soporte de control remoto para TV.
3. **Leo TV (Android TV Streaming Suite)**: Aplicación nativa Android (`ar.appvideo.player`) para televisores Smart TV y TV Boxes (Android TV / Google TV). Integra catálogo de películas y series de múltiples servidores (CineHax, SoloLatino, Cuevana, CineCalidad, Pelispedia), señales de TV en vivo (IPTV-org, TDTChannels, Free-TV), motor YouTube+, y reproductor avanzado ExoPlayer/Media3.

---

## 2. Repositorio Central de Actualizaciones (OTA Distribution)

Todas las aplicaciones reciben actualizaciones automáticas en línea a través del repositorio público:  
🔗 **`https://github.com/leocanu/Chain-Reaction-Music-Updates`** (Rama `main`)

### Estructura de distribución por canales:
```text
Chain-Reaction-Music-Updates/
├── windows/
│   ├── candidate.json                     # Canal Windows Candidate (v0.1.58)
│   ├── SHA256SUMS.txt
│   └── ChainReactionMusic-Setup-0.1.58.exe
├── tv/
│   ├── latest.json                        # Manifiesto OTA de Chain Reaction TV
│   ├── ChainReactionMusic-TV-0.1.0-beta1-r30-v46.apk (VersionCode 46)
│   └── SHA256SUMS.txt
├── phone/
│   └── ...                                # Manifiestos y binarios móviles
└── leotv/
    ├── latest.json                        # Manifiesto OTA Leo TV (VersionCode 48)
    ├── version.json                       # Backup de compatibilidad
    └── LeoTV-v0.48.0.apk                  # Binario release firmado v0.48.0
```

---

## 3. Últimos Avances en Leo TV (Android TV - Versión v0.48.0)

Durante las últimas sesiones se implementaron las siguientes mejoras y correcciones críticas:

### A. Control Parental y Ocultación Total de Contenido +18
* **Requisito**: El usuario solicitó que la sección de adultos no sea visible en la barra lateral por defecto.
* **Implementación**:
  * La opción `🔞 Zona Adultos (+18)` fue completamente retirada de la barra de navegación lateral izquierda en estado normal.
  * Solo se vuelve visible si el usuario ingresa a **⚙️ Ajustes ➔ 🔒 Control Parental** e introduce el PIN de 4 dígitos.
  * Al desbloquear con el PIN, se añade dinámicamente el botón a la barra lateral para esa sesión. Si se vuelve a bloquear o se reinicia la app, vuelve a quedar 100% invisible.
  * La categoría *Adultos* en el estudio de TV en vivo también queda filtrada y oculta si el control parental está bloqueado.

### B. Corrección de Pixelación y Macrobloques (Adaptive Bitrate Fix)
* **Problema diagnosticado**: Al reproducir películas en HLS (como *Tornado 1996* o *Twisters 2024*), ExoPlayer arrancaba por defecto con la pista de menor ancho de banda (480p @ 540 kbps), viéndose muy borroso y pixelado en pantallas grandes de TV.
* **Solución**:
  * Se inyectó en los parámetros de selección de pistas de ExoPlayer:
    ```java
    TrackSelectionParameters trackParams = externalPlayer.getTrackSelectionParameters()
            .buildUpon()
            .setMaxVideoSize(1920, 1080)
            .setForceHighestSupportedBitrate(true)
            .build();
    externalPlayer.setTrackSelectionParameters(trackParams);
    ```
  * Ahora el reproductor arranca inmediatamente en 1080p FHD o 720p HD con la mayor tasa de bits disponible (1.2 Mbps a 2.8 Mbps).
  * Se integró un selector manual de resolución (`🎬 Calidad`: Auto, 1080p, 720p, 480p).

### C. Selector de Servidores y Fuentes Alternativas (Multi-Provider Fallback)
* **Requisito**: Permitir al usuario cambiar de servidor si un enlace se corta, anda lento o tiene mala calidad.
* **Implementación**:
  * **En la ficha técnica**: Botón **`🌐 Elegir servidor`** que despliega un diálogo con las fuentes disponibles:
    * 🌟 *CineHax (Vimeos Full HD 1080p)*
    * 🌐 *SoloLatino (Multi-calidad / Audio Latino)*
    * 🌐 *Cuevana (Servidores rápidos)*
    * 🌐 *CineCalidad (Estrenos en HD)*
    * 🌐 *Pelispedia (Clásicos y estrenos)*
    * 🌐 *Seriesflix (Series completas)*
    * ⛩️ *LatAnime / TioAnime (Anime HD)*
  * **Durante la reproducción**: Botón **`🌐 Servidor`** en el OSD inferior y opción **`🌐 Cambiar Fuente / Servidor`** en el menú de ajustes ⚙️. Al cambiar de proveedor, el sistema resuelve el nuevo enlace en segundo plano y realiza un cambio suave (*seamless seek*) conservando el minuto y segundo exactos donde iba el espectador.

### D. Soporte para Reproductor Externo (VLC / JustPlayer / Nova)
* Se implementó un despachador por Android `Intent` (`Intent.ACTION_VIEW` con MIME `video/*`) tanto en la ficha previa como en el reproductor interno.
* Transfiere la URL del stream junto con los encabezados HTTP requeridos (`User-Agent` y `Referer`) a reproductores instalados en el dispositivo (VLC, JustPlayer, MX Player), permitiendo decodificación y escalado por hardware externo.

### E. Rediseño del OSD y Navegación por Control Remoto (D-Pad TV)
* **Reubicación de controles**: La barra de acciones (`playerOsdBar`) se movió a la parte inferior (`Gravity.BOTTOM`, margen 75dp), alineada con los estándares de Android TV.
* **Separación del HUD**: El cartel flotante de "Pausado / +10s / -10s" se trasladó al extremo superior (`Gravity.TOP`, margen 28dp), eliminando por completo la superposición con el icono central de reproducción.
* **D-Pad Navigation**:
  * Flecha **ARRIBA** o **ABAJO**: Ingresa el foco directamente a la barra de controles inferior.
  * Flechas **IZQUIERDA / DERECHA**: Navegan suavemente entre los botones (Audio, Calidad, Servidor, VLC, Aspecto, Descargar).
  * Botón **OK / ENTER**: Ejecuta la acción del botón enfocado sin pausar accidentalmente el video.
  * Botón **⚙️ Engranaje nativo de ExoPlayer**: Vinculado al diálogo general de ajustes de reproducción.

### F. Resolución TMDB para Títulos Clásicos
* En películas anteriores a los 2000 (ej. *Tornado 1996*), la búsqueda por texto devolvía `entry.tmdb = 0`, provocando error 400 en CineHax.
* Se agregó `ZappCatalogEngine.resolveTmdbMovieId(entry)` utilizando la API oficial de TMDB, resolviendo el ID (ej. TMDB 664 para Twister 1996) y conectando con el stream original 1080p.

---

## 4. Estado Actual de Versiones

| Módulo / App | Versión Actual | Canal / Repositorio | Estado |
| :--- | :--- | :--- | :--- |
| **Leo TV (Android TV)** | `0.48.0` (`vc: 48`) | `leocanu/Chain-Reaction-Music-Updates/leotv/` | ✅ Estable / Publicado OTA |
| **Chain Reaction Music TV** | `0.1.0-beta1-r30` (`vc: 46`) | `leocanu/Chain-Reaction-Music-Updates/tv/` | ✅ Estable / Publicado OTA |
| **Chain Reaction Desktop** | `0.1.58` (RC1) | `leocanu/Chain-Reaction-Music-Updates/windows/` | ✅ Candidate / Activo |

---

## 5. Próximos Pasos Sugeridos

1. Monitorear la retroalimentación de la actualización en vivo en el televisor físico mediante `AppUpdater.check`.
2. Continuar puliendo la integración de subtítulos externos (WebVTT / SRT) en las fuentes que solo proveen audio en versión original.
3. Explorar caché local de fragmentos HLS en dispositivos TV con baja velocidad de conexión.
