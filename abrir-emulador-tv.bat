@echo off
title Iniciando Emulador Android TV
echo ====================================================
echo        INICIANDO EMULADOR ANDROID TV EN PANTALLA
echo ====================================================
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd ChainReaction_TV_API34 -gpu host -no-snapshot-load
exit
