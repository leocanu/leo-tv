# Revisión de 9 códigos Downloader — 2026-11-07

Objetivo: comparar interfaces y fuentes de apps de video con nuestra app Android TV. El usuario indicó que cerró nuestra app de la tele. No impide revisar los enlaces; no demuestra conexión ADB. En I5 `adb devices -l` devolvió lista vacía y `emulator-5554` no estaba accesible.

Se consultaron páginas públicas de `https://go.aftvnews.com/<codigo>` y sus destinos JavaScript, sin descargar ni ejecutar APK ni saltar intermediarios.

| Código | Destino indicado en página AFTVnews | Estado |
|---|---|---|
| 3763704 | `https://www.mediafire.com/file/5dcgpujixbu0g5z/Magis_Pro_TV_4.0.apk/file` | APK externa llamada Magis Pro TV 4.0, origen/firma no verificados |
| 6045852 | `https://link-target.net/4768145/Ce2xCNtLLn3j` | Intermediario; APK final no identificado |
| 8517 | Página principal del acortador AFTVnews | Código sin redirección válida observada |
| 46027 | `https://www.dropbox.com/s/ajpwarjb7b90duc/Bmore%20Streams%20VOD.apk?raw=1` | APK externa llamada Bmore Streams VOD, origen/firma no verificados |
| 10656 | `https://ia601407.us.archive.org/13/items/app_release_backup/app-release.apk` | APK genérica `app-release.apk`, identidad no verificada |
| 5132319 | `https://link-center.net/4768145/HmbxBzB0vuGQ` | Intermediario; APK final no identificado |
| 2862413 | `https://link-hub.net/4768145/xjqB7Wvjnk4U` | Intermediario; APK final no identificado |
| 7376869 | `https://link-target.net/4768145/TMgHo98VJi0h` | Intermediario; APK final no identificado |
| 9771499 | `https://link-hub.net/8724813/WxB9tYSIgBkp` | Intermediario; APK final no identificado |

Ninguna APK se instaló ni ejecutó. Próximo requisito para pruebas en TV: acceso al dispositivo autorizado y verificación de paquetes/firma/procedencia; no otorgar permisos ni ejecutar binarios de procedencia desconocida. No se tocó el código ni la APK de nuestra app.
