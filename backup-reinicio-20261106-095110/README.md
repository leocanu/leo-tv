# App propia de video — prototipo Android v1

Esqueleto independiente para Android y Android TV. Sin conectores habilitados, sin descargas completas y sin fuentes de terceros integradas.

Este hito implementa una pantalla básica y un reproductor nativo VideoView a modo de prueba de compilación. Media3/ExoPlayer está previsto para el siguiente hito, no implementado todavía.

La demostración requiere una URL HTTPS autorizada guardada en la preferencia local demo_url; la interfaz todavía no tiene pantalla de configuración de URL. Por defecto, el botón muestra una advertencia y no reproduce.

No se han compilado ni probado APKs aún. Los 32 proveedores originales siguen solo en el inventario independiente.

Android SDK detectado: C:\Users\Pc\AppData\Local\Android\Sdk (platform 35, build-tools 35.0.0). Android Studio JBR presente. Gradle 8.2 encontrado en caché; la compilación puede requerir Gradle 8.2.1+ y AGP 8.2.2.

Para compilar, se necesita verificar plugin AGP, Gradle wrapper y compatibilidad local.
