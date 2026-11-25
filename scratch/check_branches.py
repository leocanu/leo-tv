import subprocess

path = r'C:\Users\Pc\Documents\Chain-Reaction-Music'

for query in ['ambient', 'ambiente', 'deshabilit', 'screensaver', 'salvapantallas', 'sleep', 'wake', 'reposo']:
    try:
        out = subprocess.check_output(
            ['git', 'log', '--all', f'--grep={query}', '-i', '--oneline', '-n', '10'],
            cwd=path, text=True, stderr=subprocess.DEVNULL
        )
        if out.strip():
            print(f"=== Commits con '{query}' en todas las ramas ===")
            print(out.strip())
            print()
    except Exception as e:
        print(f"Error buscando '{query}':", e)
