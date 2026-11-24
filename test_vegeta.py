import urllib.request
import json

url = 'http://vegetatvv2.duckdns.org/bootstrap.php'
headers = {
    'User-Agent': 'Mozilla/5.0 (compatible; VegetaTVCache/2.0)',
    'Accept': '*/*'
}

try:
    req = urllib.request.Request(url, headers=headers)
    resp = urllib.request.urlopen(req, timeout=10)
    data = resp.read().decode('utf-8')
    print("Vegeta response length:", len(data))
    print("Preview:\n", data[:1000])
except Exception as e:
    print("Error:", e)
