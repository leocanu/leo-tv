import subprocess

path = r'C:\Users\Pc\Documents\Chain-Reaction-Music'

out = subprocess.check_output(
    ['git', 'log', 'fix/downloads-playback', '-n', '15', '--oneline'],
    cwd=path, text=True
)
print("Últimos commits en la rama fix/downloads-playback:")
print(out)
