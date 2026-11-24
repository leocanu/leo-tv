import zipfile, re

apk_path = r'C:\Users\Pc\Downloads\Orbit TV-13EBD8EC.apk'
with zipfile.ZipFile(apk_path, 'r') as z:
    if 'resources.arsc' in z.namelist():
        arsc = z.read('resources.arsc')
        print('arsc size:', len(arsc))
        urls = re.findall(rb'https?://[^\x00-\x20"\'<>]+', arsc)
        for u in set(urls):
            s = u.decode('utf-8', errors='ignore')
            if not any(k in s for k in ['google', 'android', 'w3.org', 'schema.org']):
                print('ARSC URL:', s)
        terms = re.findall(rb'[\x20-\x7e]{5,}', arsc)
        print('Scanning terms in ARSC...')
        for t in terms:
            s = t.decode('utf-8', errors='ignore')
            if any(k in s.lower() for k in ['player_api', 'orbit', 'tivimate', 'xtream', 'username=', 'password=']):
                print('INTERESTING STRING:', s)
