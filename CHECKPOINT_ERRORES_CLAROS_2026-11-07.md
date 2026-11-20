# Mejora de errores de reproducción — 2026-11-07

- I5 / emulator-5554. Cambios locales en MediaPageResolver.java y HtmlCatalogProvider.java.
- MediaPageResolver agrega resumen de errores acotado al intento actual con ThreadLocal: HTTP 404, timeout, conexión interrumpida y errores restantes; no registra URLs completas en la interfaz ni archivos.
- Se conserva la deduplicación existente mediante `visited` para no volver a recorrer una URL exactamente igual durante el mismo intento. No se introdujo bloqueo por dominio: distintas URLs del mismo host pueden representar archivos distintos.
- HtmlCatalogProvider presenta el resumen al usuario cuando fallan todos los servidores; Media3 y los cuatro proveedores validados previamente no se modificaron.
- Gradle BUILD SUCCESSFUL. ADB install Success.
- QA Android: qa-tioanime-20261107-082124/result.json. Búsqueda Naruto, ficha Naruto Shippuden, Episodios y botón Episodio 1 comprobados. La UI mostró: `Diagnóstico: 5 enlace(s) HTTP 404; 2 error(es) de servidor.` No hay reproducción confirmada.
- APK respaldada: C:\Users\Pc\Downloads\VideoPropia-TV-Errores-Claros-2026-11-07.apk
- SHA256: FFAEE47BE266C41B236F7E8E102A4B13057BDF65B3D6522417C2635478676AC3
- Nota: el recuento corresponde a errores de intentos (no significa 5 servidores independientes). La deduplicación por URL ya existía; no se implementó caché temporal de errores HTTP 404 entre intentos separados.
- Siguiente: comprobar mensaje en LatAnime y elegir mejoras que no dependan de servidores externos no verificables. No reclamar anime funcional hasta observar reproducción real.
