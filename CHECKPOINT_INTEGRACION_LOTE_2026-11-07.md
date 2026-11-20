# Integración por lotes — 2026-11-07

Decisión explícita del usuario: completar la configuración de proveedores primero; NO repetir QA de conectores existentes; al terminar, revisar en un único lote y corregir sólo los fallos. No usar recursos de nube para procesar archivos, ni repetir restricciones o bloqueos.

## Código agregado (compilado, NO QA)
Se registraron siete instancias de `HtmlCatalogProvider` en `VideoProviders.java` con orígenes sustentados en `LOCAL_CATALOG_DISCOVERY.json` y HTML guardado previamente:
- CineCalidad (www.cinecalidad.vg)
- Cuevana (wv3.cuevana3.eu)
- HenaoJara (ww1.henaojara.net)
- FlixLatam (flixlatam.com)
- VerPelisTop (www1.verpelis.top)
- VerAni.me (verani.me; modo anime)
- VerAnimes (wwv.veranimes.net; modo anime)

Se modificó `MainActivity.java` para mostrar estos como `ADAPTADOR PROVISIONAL`, con descripción de falta de pruebas. El canal Estado distingue 4 conectores con reproducción comprobada, 2 de anime con catálogo/episodios comprobados, 7 adaptadores provisionales sin QA y 19 fuentes pendientes o restringidas.

## Verificaciones
- Compilación local de Gradle: `BUILD SUCCESSFUL`, `BUILD_EXIT_CODE=0`.
- APK guardada: `C:\Users\Pc\Downloads\VideoPropia-TV-7AdaptadoresProvisionales-2026-11-07.apk`.
- SHA256: 2F93425DF5BF872DDA0A7C94900264FA6A166F44FA87FDCEDBD9DAC6A8A9E582.
- No se instaló esta APK ni se probó el catálogo/reproducción de los siete adaptadores por decisión del usuario de probar todo al final. No afirmar que funcionan. El código genérico puede requerir ajustes de estructura de cada sitio.
- No se modificaron algoritmos Media3 ni cuatro conectores con QA previo; no se invocó Ollama ni APIs de IA externas.

## Pendientes de configuración
- Los 19 candidatos restantes conservan ficha/diagnóstico y requieren diseño específico o evidencia HTML suficiente. No elevar como conectores operativos sitios excluidos por advertencia (SoloLatino), HTTP 403 (DeepFlix), redirección a Google (PelisSeriesHoy) o búsqueda 406 (Cineplus123).
- Evaluar más adaptadores con los HTML locales ya descargados; verificar tipos de tarjetas/URLs/formularios mediante parsers locales, sin solicitudes masivas.
- Al final: lote único de QA y clasificación por catálogo, búsqueda, episodios, resolución, reproducción y audio.
