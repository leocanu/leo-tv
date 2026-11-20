$ErrorActionPreference = 'Stop'
$source = 'C:\workspace\app-tv-propia-fuentes-v1\fuentes.json'
$target = 'C:\workspace\app-video-propia-android-v1\app\src\main\assets\channels.json'
$folder = Split-Path $target -Parent
if (-not (Test-Path $folder)) { New-Item -ItemType Directory -Path $folder -Force | Out-Null }
$data = Get-Content -Raw -Encoding UTF8 $source | ConvertFrom-Json
$channels = @($data.sources | ForEach-Object {
  [ordered]@{
    id = [string]$_.id
    name = [string]$_.name
    status = [string]$_.status
    enabled = [bool]$_.enabled_in_own_app
    description = [string]$_.description_declared
  }
})
$json = ConvertTo-Json -InputObject $channels -Depth 5
[System.IO.File]::WriteAllText($target,$json,(New-Object System.Text.UTF8Encoding($false)))
Write-Output ('CHANNEL_COUNT=' + $channels.Count)
Write-Output ('ENABLED=' + @($channels | Where-Object { $_.enabled }).Count)
Write-Output ('TARGET=' + $target)
