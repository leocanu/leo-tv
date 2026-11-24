import zipfile, re

with zipfile.ZipFile(r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps\plavio.apk", 'r') as z:
    for n in z.namelist():
        if n.endswith('.dex') or n.endswith('.json') or n.endswith('.js'):
            data = z.read(n)
            # Find client_id
            for m in re.findall(rb'client_id=[a-zA-Z0-9_\-]+', data):
                print("Found SoundCloud param:", m.decode('utf-8'))
            for m in re.findall(rb'https?://api-v2\.soundcloud\.com/[^\s\"\'<>]+', data):
                print("Found SoundCloud API:", m.decode('utf-8'))
