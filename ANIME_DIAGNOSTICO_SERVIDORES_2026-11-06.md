# Diagnóstico real de reproductores de anime — I5 — 2026-11-06

## Qué se hizo
- Se añadieron registros mínimos a `MediaPageResolver.java` y `HtmlCatalogProvider.java`, identificando etapa, dominio y tipo de excepción sin guardar URL completa, tokens ni parámetros.
- Gradle: `BUILD SUCCESSFUL` (30 tareas, 4 ejecutadas). Instalación ADB en emulator-5554: `Success`.
- Prueba Android reproducible: `qa_naruto.py` no existe; se usó `qa_naruto_detail.py`, resultados en `qa-naruto-20261106-224720/result.json`.
- LatAnime: búsqueda Naruto, ficha Naruto Shippuden Latino, entrada a Episodios y botón Episodio 1 encontrados. Falló la reproducción con mensaje 'Los servidores de este titulo no pudieron reproducirse'.

## Log filtrado VideoProviderQA
- `player_candidates=3 data_player_elements=6`
- `player_candidate host=filemoon.sx error=IOException`
- `player_candidate host=mixdrop.co error=SocketTimeoutException`
- `player_candidate host=www.mp4upload.com error=EOFException`
- `initial_resolver error=IOException`
- `all_servers_failed ajax_options_tried=0`

## Interpretación acotada
- El catálogo y el parser sí obtienen referencias de reproductores; los tres servidores candidatos no produjeron un enlace validado.
- Se sabe el tipo de excepción, pero no aún la causa HTTP/compatibilidad precisa de cada servidor; no asumir caída universal, bloqueo de IP ni fallo de Media3.
- No se confirmó imagen, audio ni reanudación del episodio. Los registros no incluyen links sensibles ni se eludieron controles.

## Próximo trabajo
- Repetir un caso de TioAnime con el mismo diagnóstico y comparar rutas; verificar interfaces y otras obras autorizadas antes de cambios a resolutores.
- Mantener otros cuatro proveedores aprobados sin alterarlos.
