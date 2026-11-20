@echo off
chcp 65001 >nul
title Sincronizar LeoTV con GitHub
echo ====================================================
echo          SUBIENDO PROYECTO LEOTV A GITHUB
echo ====================================================
echo.
cd /d "%~dp0"
git add .
git commit -m "LeoTV v0.36.0: Player OSD limpio, D-Pad directo, sinopsis en espanol y filtrado latino"
echo.
echo Enviando cambios a la rama principal (main)...
git push -u origin main
echo.
if %ERRORLEVEL% EQU 0 (
    echo ====================================================
    echo   ¡PROYECTO SUBIDO EXITOSAMENTE A GITHUB!
    echo ====================================================
) else (
    echo ====================================================
    echo   Si te pide usuario y contrasenia:
    echo   - Usuario: tu usuario de GitHub (leocanu)
    echo   - Contrasenia: usa un Personal Access Token (PAT)
    echo     desde https://github.com/settings/tokens
    echo.
    echo   Asegurate de haber creado el repositorio en GitHub:
    echo   https://github.com/new con el nombre 'leo-tv'
    echo ====================================================
)
pause
