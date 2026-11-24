import urllib.request
import json

base = 'http://line.znn8.xyz:80'
user = '12PUBV'
password = 'HD4FGR'

url = f'{base}/player_api.php?username={user}&password={password}'
headers = {'User-Agent': 'IPTVSmartersPro'}

print("Testing Xtream API:", url)
try:
    resp = urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=10)
    data = json.loads(resp.read().decode('utf-8'))
    print("User info status:", data.get('user_info', {}).get('status'))
    print("Server info:", data.get('server_info', {}).get('url'))
    
    # Check live categories
    cat_url = f'{base}/player_api.php?username={user}&password={password}&action=get_live_categories'
    cats = json.loads(urllib.request.urlopen(urllib.request.Request(cat_url, headers=headers), timeout=10).read().decode('utf-8'))
    print("Live categories count:", len(cats))
    for c in cats[:15]:
        print(" -", c.get('category_name'), "id:", c.get('category_id'))
except Exception as e:
    print("Error:", e)
