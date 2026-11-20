# Revisión por lotes de 32 candidatos — 2026-11-06

Método: scripts Python locales (`audit_providers.py`, `probe_public_sites.py`) ejecutados en la I5; no se usaron servicios ni APIs de IA en la nube. Ollama local instalado y API versión 0.40.15 disponibles, pero no se invocó ningún modelo porque las verificaciones fueron deterministas.

## Verificaciones
- 32 entradas en `channels.json`; 32 entradas en `provider_audit.json`; 0 faltantes; 0 duplicadas.
- 32/32 URLs de referencia de código público respondieron HTTP 200. Se guardaron SHA-256 y metadatos. Esto no implica licencias verificadas ni reproducción.
- Una petición HEAD al dominio declarado de cada proveedor autorizado para prueba en vivo: 17 HTTP 200, 1 HTTP 403 (DeepFlix), 14 omitidos por restricciones previas, sin eludir ningún control.
- No se consultaron reproductores intermedios ni videos de sitios no autorizados. Ninguno de los 32 candidatos externos fue conectado para reproducir video. Los dos canales de películas abiertas de demostración sí tienen audio e imagen confirmados.
- Se integró el diagnóstico en la UI de cada canal y en la pantalla Estado.
- Gradle: BUILD SUCCESSFUL; instalación ADB en emulator-5554: Success; Estado UI muestra '32 candidatos. Sitios: 17 accesibles, 1 restringido, 14 excluidos. 0 conectores externos activos.'
- APK de prueba copiada a `C:\Users\Pc\Downloads\VideoPropia-TV-32Canales-auditoria-2026-11-06.apk`.

## Próximo bloque real de trabajo
Implementar adaptadores de catálogo/reproducción para fuentes con derechos y permisos de uso comprobados. HTTP 200 de una portada o de un script no habilita automáticamente reproducción ni licencia de integración. Por ahora los 32 candidatos se pueden navegar y auditar juntos en el emulador, pero no reproducen contenido de esos sitios.
