# Catálogo por proveedor — prueba 2026-11-06

- Usuario confirmó imagen y audio de Big Buck Bunny (Internet Archive) y Elephants Dream (Blender Open Movies).
- Se creó app/src/main/assets/catalog.json, versión de esquema 1, con dos proveedores y tres fichas de contenido autorizado (incluida una película compartida entre canales).
- MainActivity.loadCatalog() parsea JSON desde assets; showCatalog() genera fichas para el proveedor seleccionado, sin URLs de contenido incrustadas en la UI.
- Compilación Gradle: BUILD SUCCESSFUL.
- Instalación emulator-5554: Success.
- UIAutomator validó ficha de Big Buck Bunny obtenida del JSON en Internet Archive.
- Selección con DPAD: 'Video preparado. Reproduciendo.' en Media3.
- 32 proveedores candidatos siguen solo inventariados; sin integraciones activas.
- No se alteraron aplicaciones de TV físicas.
- Próximo paso: separar el contrato ProviderAdapter y crear importación local de catálogos personales con validación de URLs y permisos.
