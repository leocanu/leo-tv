import urllib.request
import json
import os

apk_path = r"app\build\outputs\apk\debug\app-debug.apk"
if not os.path.exists(apk_path):
    print("APK not found")
    exit(1)

print(f"Uploading {apk_path} ({os.path.getsize(apk_path)} bytes) to filepush/uguu/bashupload/etc...")

# Let's test uguu.se
try:
    with open(apk_path, "rb") as f:
        file_bytes = f.read()
    
    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="files[]"; filename="LeoTV-v0.43.0.apk"\r\n'
        f"Content-Type: application/vnd.android.package-archive\r\n\r\n"
    ).encode("utf-8") + file_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    req = urllib.request.Request("https://uguu.se/upload.php", data=body)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("User-Agent", "Mozilla/5.0")
    
    with urllib.request.urlopen(req, timeout=30) as resp:
        res = resp.read().decode("utf-8")
        print("uguu response:", res)
except Exception as e:
    print("uguu error:", e)

# Test filepush / oshi / bashupload
try:
    with open(apk_path, "rb") as f:
        req = urllib.request.Request("https://bashupload.com/LeoTV-v0.43.0.apk", data=f.read(), method="PUT")
        with urllib.request.urlopen(req, timeout=30) as resp:
            print("bashupload response:", resp.read().decode("utf-8"))
except Exception as e:
    print("bashupload error:", e)

# Test catbox.moe
try:
    boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW2"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="reqtype"\r\n\r\n'
        f"fileupload\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="fileToUpload"; filename="LeoTV-v0.43.0.apk"\r\n'
        f"Content-Type: application/vnd.android.package-archive\r\n\r\n"
    ).encode("utf-8") + file_bytes + f"\r\n--{boundary}--\r\n".encode("utf-8")

    req = urllib.request.Request("https://catbox.moe/user/api.php", data=body)
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("User-Agent", "Mozilla/5.0")
    with urllib.request.urlopen(req, timeout=30) as resp:
        res = resp.read().decode("utf-8")
        print("catbox response:", res)
except Exception as e:
    print("catbox error:", e)
