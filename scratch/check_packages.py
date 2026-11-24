import subprocess

adb = r"C:\Users\Pc\AppData\Local\Android\Sdk\platform-tools\adb.exe"
out = subprocess.check_output([adb, "-s", "emulator-5554", "shell", "dumpsys", "package"], text=True, errors="ignore")

lines = out.splitlines()
cur_pkg = None
results = []
for l in lines:
    s = l.strip()
    if s.startswith("Package ["):
        cur_pkg = s.split("[")[1].split("]")[0]
    elif "lastUpdateTime=" in s and cur_pkg:
        t = s.split("lastUpdateTime=")[1]
        results.append((t, cur_pkg))
        cur_pkg = None

results.sort(reverse=True)
print("=== 25 Most recently updated packages on emulator ===")
for t, p in results[:25]:
    print(f"{t} -> {p}")
