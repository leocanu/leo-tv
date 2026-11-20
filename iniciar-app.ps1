$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$androidSdk = 'C:\Users\Pc\AppData\Local\Android\Sdk'
$adb = Join-Path $androidSdk 'platform-tools\adb.exe'
$emulator = Join-Path $androidSdk 'emulator\emulator.exe'
$running = Get-CimInstance Win32_Process -Filter "Name='qemu-system-x86_64.exe'" | Where-Object { $_.CommandLine -like '*ChainReaction_TV_API34*' }
if (-not $running) {
    Start-Process -FilePath $emulator -ArgumentList '-avd','ChainReaction_TV_API34','-no-snapshot-load' -WindowStyle Normal
}
$booted = $false
for ($attempt=0; $attempt -lt 90; $attempt++) {
    $boot = $null
    try { $boot = & $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null } catch { $boot = $null }
    if ($boot -match '^1') { $booted = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $booted) { throw 'Android TV no termino de iniciar en el plazo previsto.' }
& $adb -s emulator-5554 shell am start -n ar.appvideo.player/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir Video Propia.' }
Write-Output 'Android TV y Video Propia abiertos.'
