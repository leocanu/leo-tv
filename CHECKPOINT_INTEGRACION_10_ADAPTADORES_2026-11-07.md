# Lote 2: integración de conectores provisionales — 2026-11-07

## Decisión de trabajo
El usuario pidió integrar todas las fuentes primero y recién al final ejecutar un repaso único y corregir los errores. No revalidar repetidamente los conectores previamente probados.

## Cambios concretos
- `VideoProviders.java`: se sumaron AnimeAV1 (`https://animeav1.com`), BeatZ Anime (`https://www.beatz-anime.net`) y MundoDonghua (`https://www.mundodonghua.com`) como adaptadores `HtmlCatalogProvider` provisionales.
- `HtmlCatalogProvider.java`: se reconocen fichas `/donghua/` en selector y filtro de entradas anime; se agregó extracción de enlaces `/ver/<slug>/<numero>` y `/ver/<slug>/<numero>/<identificador>` para el dominio de MundoDonghua. Sin alterar la selección ni Media3 para proveedores ya implementados.
- `MainActivity.java`: actualizado estado de 7 a 10 adaptadores provisionales y de 19 a 16 pendientes/restringidos. Los provisionales aparecen expresamente identificados como sin QA.
- Fuentes consultadas únicamente desde HTML locales ya existentes en `C:\Users\Pc\AppData\Local\Temp\appvideo-catalog-discovery`; se observaron `/media/` (AnimeAV1), `/bd/` (BeatZ Anime) y `/donghua/`, `/ver/` (MundoDonghua).

## Validación técnica (NO de reproducción)
- Compilación Java/Gradle: `BUILD SUCCESSFUL` y `BUILD_EXIT_CODE=0`, 30 tareas, 4 ejecutadas.
- APK archivada sin instalar ni probar: `C:\Users\Pc\Downloads\VideoPropia-TV-10AdaptadoresProvisionales-2026-11-07.apk`.
- SHA256: `37D35618E9230F37ACC83248ED18C23B52709D18EC2B46BFF1F3F0051CC038BE`.
- Inventario total 32: 4 conectores con reproducción previamente probada, 2 anime con catálogo/episodios comprobados y reproducción pendiente, 10 adaptadores provisionales y 16 inventariados no integrados o restringidos. Los 10 adaptadores provisionales no tienen QA Android y pueden necesitar ajustes al tipo real de HTML.
- Sin IA remota adicional, ni nuevos modelos de IA invocados, ni sondeo masivo ni elusión de bloqueos.

## Próximo paso
Continuar con los 16 no integrados; diseñar adaptadores para HTML más heterogéneos conservado estado provisonal. Al final realizar QA por lotes de catálogo, búsqueda, episodios, reproducción/audio y fullscreen, reparar fallos reales.
