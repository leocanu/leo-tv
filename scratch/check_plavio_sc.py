import zipfile, re

with zipfile.ZipFile(r"C:\Users\Pc\.gemini\antigravity\brain\2811ca5c-8815-4dd8-b8fd-57a7c13fb3d5\scratch\extracted_apps\plavio.apk", 'r') as z:
    for n in z.namelist():
        if n.endswith('.dex'):
            data = z.read(n)
            matches = re.findall(rb'https?://[^\s\"\'<>]+soundcloud[^\s\"\'<>]*', data)
            if matches:
                for m in set(matches):
                    print(m.decode('utf-8', errors='ignore'))
