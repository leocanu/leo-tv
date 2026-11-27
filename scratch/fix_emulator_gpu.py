import os
import shutil

user_profile = os.environ.get("USERPROFILE", r"C:\Users\Pc")
avd_dir = os.path.join(user_profile, r".android\avd\ChainReaction_TV_API34.avd")
config_ini = os.path.join(avd_dir, "config.ini")
quickboot_ini = os.path.join(avd_dir, "quickbootChoice.ini")
snapshot_dir = os.path.join(avd_dir, "snapshots", "default_boot")

# 1. Eliminar snapshot congelado
if os.path.exists(snapshot_dir):
    try:
        shutil.rmtree(snapshot_dir)
        print("Snapshot congelado default_boot eliminado exitosamente.")
    except Exception as e:
        print(f"Error eliminando snapshot: {e}")

# 2. Modificar quickbootChoice.ini
try:
    with open(quickboot_ini, "w", encoding="utf-8") as f:
        f.write("saveOnExit = false\n")
    print("quickbootChoice.ini actualizado a saveOnExit = false.")
except Exception as e:
    print(f"Error en quickbootChoice.ini: {e}")

# 3. Modificar config.ini
if os.path.exists(config_ini):
    with open(config_ini, "r", encoding="utf-8") as f:
        lines = f.readlines()

    new_lines = []
    for line in lines:
        s = line.strip()
        if s.startswith("hw.gpu.enabled="):
            new_lines.append("hw.gpu.enabled=yes\n")
        elif s.startswith("hw.gpu.mode="):
            new_lines.append("hw.gpu.mode=host\n")
        elif s.startswith("fastboot.forceColdBoot="):
            new_lines.append("fastboot.forceColdBoot=yes\n")
        elif s.startswith("fastboot.forceFastBoot="):
            new_lines.append("fastboot.forceFastBoot=no\n")
        elif s.startswith("firstboot.bootFromLocalSnapshot="):
            new_lines.append("firstboot.bootFromLocalSnapshot=no\n")
        elif s.startswith("firstboot.saveToLocalSnapshot="):
            new_lines.append("firstboot.saveToLocalSnapshot=no\n")
        elif s.startswith("vm.heapSize="):
            new_lines.append("vm.heapSize=512M\n")
        else:
            new_lines.append(line)

    with open(config_ini, "w", encoding="utf-8") as f:
        f.writelines(new_lines)
    print("config.ini optimizado: GPU habilitada (host), ColdBoot activado, heapSize 512M.")

# 4. Eliminar locks huérfanos
for item in os.listdir(avd_dir):
    if item.endswith(".lock"):
        full = os.path.join(avd_dir, item)
        try:
            if os.path.isdir(full):
                shutil.rmtree(full)
            else:
                os.remove(full)
            print(f"Lock eliminado: {item}")
        except Exception as e:
            print(f"Error eliminando lock {item}: {e}")
