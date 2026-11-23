@echo off
chcp 65001 >nul
title Publicar Leo TV v0.47.0 en GitHub (Downloader Direct APK)
color 0b

echo ====================================================================
echo             PUBLICAR LEOTV v0.47.0 EN GITHUB PARA DOWNLOADER
echo ====================================================================
echo.
echo Repositorio destino: https://github.com/leocanu/leo-tv
echo Version: v0.47.0
echo APK generado: %USERPROFILE%\Desktop\LeoTV-v0.47.0.apk
echo.

cd /d "%~dp0"

echo [1/3] Sincronizando codigo fuente y commits...
git add app/src/main/java/ version.json app/build.gradle subir-a-github.bat
git commit -m "release: LeoTV v0.47.0 - Selector de Calidad HD/1080p, TMDB anime y filtro estrenos" 2>nul
echo.

echo [2/3] Creando y actualizando etiqueta de version (tag v0.47.0)...
git tag -d v0.47.0 2>nul
git tag -a v0.47.0 -m "Leo TV v0.47.0 - Selector de calidad de video, series completas y resolutores"
echo.

echo [3/3] Subiendo rama main y tag a GitHub...
git push origin main
git push origin v0.47.0 --force

echo.
if %ERRORLEVEL% EQU 0 (
    echo ====================================================================
    echo   ¡CODIGO Y ETIQUETA SUBIDOS EXITOSAMENTE A GITHUB!
    echo ====================================================================
    echo.
    echo Pasos finales para Downloader en tu TV:
    echo.
    echo 1. Se abrira automaticamente tu navegador en la pagina de Release:
    echo    https://github.com/leocanu/leo-tv/releases/new?tag=v0.47.0
    echo.
    echo 2. En esa pagina web:
    echo    - Titulo de la release: Leo TV v0.47.0
    echo    - Arrastra el archivo 'LeoTV-v0.47.0.apk' que tenes en tu Escritorio:
    echo      %USERPROFILE%\Desktop\LeoTV-v0.47.0.apk
    echo    - Hace clic en el boton verde "Publish release".
    echo.
    echo 3. URL directa y permanente para poner en la app DOWNLOADER:
    echo.
    echo    https://github.com/leocanu/leo-tv/releases/download/v0.47.0/LeoTV-v0.47.0.apk
    echo.
    echo ====================================================================
    start https://github.com/leocanu/leo-tv/releases/new?tag=v0.45.0
) else (
    echo [ERROR] No se pudo conectar con GitHub. Verifica tu conexion a internet o tus credenciales.
)

echo.
pause
