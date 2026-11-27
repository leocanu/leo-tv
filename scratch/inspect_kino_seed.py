import zipfile
import json

apk_path = "scratch/kino.apk"
with zipfile.ZipFile(apk_path, 'r') as z:
    seed_data = z.read("assets/plugin-catalog-seed.json").decode('utf-8')
    data = json.loads(seed_data)
    
    print(f"Schema: {data.get('schema')}")
    plugins = data.get('plugins', [])
    print(f"Total plugins in seed: {len(plugins)}")
    
    for p in plugins:
        print("\n-------------------------------------------")
        print(f"ID: {p.get('id')} | Name: {p.get('name')}")
        print(f"Repo: {p.get('repo')}")
        print(f"Description: {p.get('description')}")
        print(f"Categories: {p.get('categories')}")
        print(f"URL: {p.get('url')}")
        if 'hosts' in p:
            print(f"Hosts: {p.get('hosts')}")
        if 'capabilities' in p:
            print(f"Capabilities: {p.get('capabilities')}")
