# Comparativa de diagnóstico HTTP de anime — 2026-11-07

Pruebas secuenciales y locales realizadas en I5 y emulator-5554, con la APK de diagnóstico ya instalada. Se utilizó `qa_tioanime_detail.py` y `qa_naruto_detail.py`, sin modelos/API de IA externos. Los logs sólo muestran dominios, clases de excepción y códigos HTTP cuando están disponibles.

## TioAnime — Naruto Shippuden episodio 1
- QA: `qa-tioanime-20261107-081214/result.json`.
- Catálogo (24 en primera página), búsqueda Naruto (12), ficha, lista y botón Episodio 1 comprobados.
- Falló reproducción: mensaje de servidores no reproducibles.
- `www.yourupload.com`: IOException, HTTP 404.
- `v.tioanime.com`: IOException, HTTP 404.
- `ok.ru`: IOException sin código HTTP identificable.

## LatAnime — Naruto Shippuden Latino episodio 1
- QA: `qa-naruto-20261107-081347/result.json`.
- Catálogo (12), búsqueda Naruto (8), ficha, lista y botón Episodio 1 comprobados.
- Falló reproducción: mensaje de servidores no reproducibles.
- `filemoon.sx`: IOException sin HTTP identificable.
- `mixdrop.co`: SocketTimeoutException (timeout).
- `www.mp4upload.com`: EOFException (conexión cerrada).

## Conclusión limitada
Las consultas de catálogo y la extracción de episodios funcionan para los títulos probados, y se identifican servidores candidatos. Ninguno de esos candidatos produjo un video validado. No se puede concluir que todos los títulos fallen ni que Media3 esté defectuoso. No se intentaron eludir restricciones o protecciones. Los cuatro conectores con QA previo siguen sin modificarse.

## Próximo trabajo
Evaluar si conviene marcar los episodios como disponibilidad no confirmada, mostrar errores diferenciados y evitar reintentos repetitivos automáticos de servidores con HTTP 404; después probar un contenido autorizado que pueda reproducirse en Media3 como control.
