$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "Esperando a que el emulador responda por ADB..."
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 3
    $devices = & $adb devices
    Write-Host "Dispositivos: $devices"
    if ($devices -match "emulator-\d+\s+device") {
        $ready = $true
        break
    }
}

if ($ready) {
    Write-Host "Emulador detectado. Esperando arranque del sistema (sys.boot_completed)..."
    for ($i = 0; $i -lt 30; $i++) {
        $boot = (& $adb shell getprop sys.boot_completed).Trim()
        if ($boot -eq "1") {
            Write-Host "Sistema Android TV listo."
            break
        }
        Start-Sleep -Seconds 2
    }

    Write-Host "Instalando kino.apk..."
    & $adb install -r "c:\workspace\app-video-propia-android-v1\kino.apk"
    Write-Host "Instalación de kino.apk finalizada."
} else {
    Write-Host "El emulador tardó demasiado en iniciar o no se detectó."
}
