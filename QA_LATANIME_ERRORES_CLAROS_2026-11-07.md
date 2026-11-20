# QA LatAnime: errores claros — 2026-11-07

- Prueba secuencial local en I5 / emulator-5554 con `qa_naruto_detail.py`, sin modificar Java ni el reproductor en este tramo.
- Resultado `qa-naruto-20261107-083303/result.json`: catálogo LatAnime 12 títulos, búsqueda Naruto 8 resultados, ficha Naruto Shippuden Latino, episodios y selección de episodio 1 comprobados.
- La UI muestra: `Los servidores de este título no pudieron reproducirse. Diagnóstico: 1 conexión(es) interrumpida(s); 2 error(es) de servidor.`
- No se confirmó reproducción de video ni audio.
- Logcat VideoProviderQA: filemoon.sx category=io IOException; mixdrop.co category=tls SSLHandshakeException; www.mp4upload.com category=connection_closed EOFException. El resumen actual agrupa TLS y otros fallos en "error de servidor"; podría mejorarse para distinguir certificados/TLS.
- El mensaje de error de TioAnime ya había sido validado en `qa-tioanime-20261107-082124/result.json`, con cinco errores HTTP 404 y dos errores genéricos de servidor.
- Estado: comprobada la mejora UI en ambos proveedores; las fuentes anime siguen sin reproducción verificada. Los cuatro proveedores previamente validados no se tocaron.
