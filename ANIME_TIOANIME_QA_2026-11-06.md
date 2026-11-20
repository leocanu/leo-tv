# QA TioAnime — Naruto Shippuden — 2026-11-06

## Ejecución
- Se reutilizó `qa_naruto_detail.py` para crear `qa_tioanime_detail.py` sin modificar el código Java ni el reproductor.
- Emulador `emulator-5554` en I5, APK con diagnóstico `VideoProviderQA` ya instalada.
- QA guardado: `qa-tioanime-20261106-225051/result.json`.
- Catálogo TioAnime: 24 títulos visibles en primera página.
- Búsqueda Naruto: 12 resultados.
- Ficha Naruto Shippuden y botón `Episodios` accesibles.
- Lista real: episodios 1, 2 y 3 visibles y seleccionables.
- Intento de Episodio 1: falla `Los servidores de este titulo no pudieron reproducirse.`; no se confirmó video/audio/progreso.

## Diagnóstico filtrado (solo dominio y tipo, sin URLs completas)
- www.yourupload.com: IOException
- v.tioanime.com: IOException (varios candidatos)
- ok.ru: IOException (dos candidatos)
- `initial_resolver error=IOException`; `all_servers_failed ajax_options_tried=0`.

## Comparación con LatAnime
- LatAnime: Filemoon IOException; Mixdrop SocketTimeoutException; MP4Upload EOFException.
- TioAnime: YourUpload, v.tioanime.com y OK.ru fallaron con IOException.
- Ambas fuentes validan catálogo, búsqueda y episodios, pero ninguna tiene reproducción de anime confirmada en el título probado.
- No se han inferido causas HTTP específicas ni se han eludido restricciones. Sin cambios al código de reproducción en este tramo.

## Próximo criterio
Conservar los cuatro conectores previamente probados. Antes de intentar nuevas técnicas de extracción, verificar que haya una fuente de medios autorizada y compatible. Registrar por separado errores de respuesta HTTP y formatos, sin retener URLs con tokens.
