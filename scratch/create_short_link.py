import urllib.request
import json

url = "https://files.catbox.moe/a9x8yj.apk"

# Test direct download headers
req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "Downloader/1.0"})
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        print("Catbox HEAD status:", resp.status)
        print("Catbox Content-Length:", resp.headers.get("Content-Length"))
        print("Catbox Content-Type:", resp.headers.get("Content-Type"))
except Exception as e:
    print("Catbox error:", e)

# Create a short link on tinyurl or is.gd for easy typing in Downloader
for service in ["https://tinyurl.com/api-create.php?url=", "https://is.gd/create.php?format=simple&url="]:
    try:
        req = urllib.request.Request(service + urllib.parse.quote(url), headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            print("Shortened URL (" + service.split("/")[2] + "):", resp.read().decode("utf-8"))
    except Exception as e:
        print("Error shortening:", e)
