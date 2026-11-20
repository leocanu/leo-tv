# GUÍA PARA RETOMAR EL TRABAJO — LEO TV (Antigravity IDE)

> **Documento:** Comandos completos y verificados en PowerShell 5.1 / Windows para retomar, compilar, probar y revertir el proyecto.  
> **Ubicación:** `ANTIGRAVITY_RETOMAR.md`  

---

## 1. Contexto Rápido de la Estación de Trabajo (I5 / Windows)

- **Directorio del Proyecto:** `C:\workspace\app-video-propia-android-v1`
- **JDK / Java:** `C:\Program Files\Android\Android Studio\jbr` (Java 17 OpenJDK)
- **Android SDK:** `C:\Users\Pc\AppData\Local\Android\Sdk`
- **Gradle Wrapper Local:** `C:\Users\Pc\.gradle\wrapper\dists\gradle-8.2-bin\bbg7u40eoinfdyxsxr3z4i7ta\gradle-8.2\bin\gradle.bat`
- **ADB:** `C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- **Emulador AVD:** `ChainReaction_TV_API34` (`emulator-5554`)
- **Última APK Generada:** `C:\Users\Pc\Downloads\LeoTV-v0.12-AudioLatinoSinRadios-2026-11-08.apk`
- **Paquete de la App:** `ar.appvideo.player`
- **Actividad Principal:** `ar.appvideo.player/.MainActivity`

---

## 2. Comandos de PowerShell 5.1 para Continuar

### A. Abrir y Verificar el Emulador Android TV
```powershell
$androidSdk = 'C:\Users\Pc\AppData\Local\Android\Sdk'
$adb = Join-Path $androidSdk 'platform-tools\adb.exe'
$emulator = Join-Path $androidSdk 'emulator\emulator.exe'

$running = Get-Process | Where-Object { $_.ProcessName -like "*qemu*" -or $_.ProcessName -like "*emulator*" }
if (-not $running) {
    Start-Process -FilePath $emulator -ArgumentList '-avd','ChainReaction_TV_API34','-no-snapshot-load' -WindowStyle Normal
    Write-Host "Iniciando emulador ChainReaction_TV_API34..."
    for ($i = 0; $i -lt 60; $i++) {
        $boot = & $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null
        if ($boot -match '^1') { Write-Host "Emulador listo!"; break }
        Start-Sleep -Seconds 2
    }
} else {
    Write-Host "Emulador ya en ejecución."
}
& $adb devices
```

### B. Compilar la APK con Gradle Offline
```powershell
Set-Location 'C:\workspace\app-video-propia-android-v1'
powershell -ExecutionPolicy Bypass -File .\build-local.ps1
```

### C. Instalar la APK en el Emulador y Lanzar Leo TV
```powershell
$adb = 'C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apk = 'C:\workspace\app-video-propia-android-v1\app\build\outputs\apk\debug\app-debug.apk'

# Instalar reemplazando la versión anterior
& $adb -s emulator-5554 install -r $apk

# Iniciar la actividad principal
& $adb -s emulator-5554 shell am start -n ar.appvideo.player/.MainActivity
```

### D. Enviar Teclas de Control Remoto (D-Pad) y Capturar Pantalla
```powershell
$adb = 'C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe'

# Enviar flechas o enter (21: IZQ, 22: DER, 19: ARRIBA, 20: ABAJO, 23: OK, 4: ATRAS)
& $adb -s emulator-5554 shell input keyevent 20 # Abajo
Start-Sleep -Milliseconds 300
& $adb -s emulator-5554 shell input keyevent 23 # OK / Seleccionar

# Capturar pantalla actual
& $adb -s emulator-5554 exec-out screencap -p > "pruebas-downloader\leotv-captura-reciente.png"
```

### E. Revertir Cambios si es Necesario
Respaldos con marca de tiempo disponibles en:
`C:\workspace\app-video-propia-android-v1\pruebas-downloader\`

Para restaurar el estado v0.10 previo a la intervención de Antigravity:
```powershell
$backup = 'C:\workspace\app-video-propia-android-v1\pruebas-downloader\backup-antigravity-v011-20261108-022903'
Copy-Item "$backup\MainActivity.java" 'C:\workspace\app-video-propia-android-v1\app\src\main\java\ar\appvideo\player\MainActivity.java' -Force
Copy-Item "$backup\build.gradle" 'C:\workspace\app-video-propia-android-v1\app\build.gradle' -Force
Copy-Item "$backup\AndroidManifest.xml" 'C:\workspace\app-video-propia-android-v1\app\src\main\AndroidManifest.xml' -Force
```

---

## 3. Estado de los Documentos de Seguimiento
- `ANTIGRAVITY_ESTADO.md` — Visión global, bloques completados y pendientes.
- `ANTIGRAVITY_CAMBIOS.md` — Log cronológico de versiones v0.10 -> v0.11 -> v0.12 y respaldos.
- `ANTIGRAVITY_QA.md` — Pruebas ejecutadas, inspección en vivo de Xuper y capturas reales.
- `ANTIGRAVITY_FUENTES.md` — Inventario actualizado con radios omitidas y filtro en español.
- `OPINION_ANTIGRAVITY.md` — Evaluación visual previa del diseño TV.
