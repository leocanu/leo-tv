$body = '{"videoId": "rZtTUCxpPuI", "context": {"client": {"clientName": "ANDROID", "clientVersion": "19.09.37", "hl": "es", "gl": "AR"}}}'
$res = Invoke-RestMethod -Uri 'https://www.youtube.com/youtubei/v1/player' -Method Post -Body $body -ContentType 'application/json'
Write-Output ("HLS Manifest: " + $res.streamingData.hlsManifestUrl)

