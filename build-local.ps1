$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Pc\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
Set-Location 'C:\workspace\app-video-propia-android-v1'
$gradle='C:\Users\Pc\.gradle\wrapper\dists\gradle-8.2-bin\bbg7u40eoinfdyxsxr3z4i7ta\gradle-8.2\bin\gradle.bat'
& $gradle --offline --no-daemon :app:assembleDebug --console=plain *> build-attempt.log
$code=$LASTEXITCODE
Get-Content build-attempt.log -Tail 35
Write-Output ('BUILD_EXIT_CODE=' + $code)

if ($code -eq 0) {
    try {
        $version = (Get-Content version.json | ConvertFrom-Json).versionName
        $apkPath = 'app\build\outputs\apk\debug\app-debug.apk'
        if (Test-Path $apkPath) {
            Copy-Item $apkPath "C:\Users\Pc\Downloads\LeoTV-v$version.apk" -Force
            Copy-Item $apkPath "C:\Users\Pc\Downloads\LeoTV.apk" -Force
            Copy-Item $apkPath "C:\Users\Pc\Desktop\LeoTV-v$version.apk" -Force
            Copy-Item $apkPath "C:\Users\Pc\Desktop\LeoTV.apk" -Force
            Write-Output "APK copiado exitosamente como LeoTV-v$version.apk y LeoTV.apk en Descargas y Escritorio."
        }
    } catch {
        Write-Warning "No se pudo copiar automáticamente el APK con versión: $_"
    }
}
exit $code
