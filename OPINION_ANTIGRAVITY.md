# Revisión Visual y Arquitectura UI/UX — Leo TV (Android TV 1080p)

> **Documento:** Evaluación visual, ergonómica y funcional de la interfaz real de **Leo TV** (`leotv-inicio.png`, 1920x1080)  
> **Destino:** `OPINION_ANTIGRAVITY.md`  
> **Estado de cambios en código:** *Sin modificaciones aplicadas* (en conformidad estricta con la directiva de no alterar código ni procesar archivos externos sin autorización explícita).

---

## 1. Diagnóstico de la Captura Real (`leotv-inicio.png`)

Al analizar la captura de pantalla a resolución nativa de 1080p (1920 × 1080 px) ejecutada sobre el emulador Android TV, se identifican con claridad las virtudes y los cuellos de botella del diseño actual:

### Puntos Positivos
1. **Paleta de fondo sobria y cinematográfica:** El azul oscuro/marino profundo (`#0B132B` / `#102035`) es adecuado para pantallas de TV (OLED / LED), reduciendo fatiga visual y evitando el banding blanco en salas oscuras.
2. **Organización temática clara:** La separación en *Destacados*, *Películas y series* y *Anime* sigue la lógica mental estándar de un usuario de streaming.
3. **Distribución en rieles horizontales (rails):** El patrón de navegación horizontal por filas es el estándar ergonómico para control remoto (D-Pad).

### Problemas Críticos Detectados en Pantalla
1. **Recorte vertical severo del texto en las tarjetas (Card Height Overflow):**
   - En *Destacados*, la 3.ª línea de las tarjetas está cortada por la mitad inferior ("Muestra de *video...*", "Videos *auto...*", "Reproducción *probada...*").
   - En *Películas y series* y *Anime*, la palabra *"revisión"* o *"pendiente"* queda literalmente mutilada en su línea base (ej. se ven sólo los puntos superiores de las letras).
   - **Causa raíz en código:** Tarjetas con altura forzada fija de `110px` (`tvAction(..., 228, 110)`), sumada a `padding(15, 7, 12, 7)` y texto en tamaño `16sp` con `maxLines(3)`. La altura de caja no alcanza para 3 líneas tipográficas en Android TV.
2. **Truncamiento y pérdida de etiquetas en el menú lateral:**
   - El botón *"Películas y series"* se muestra cortado como **"Películas y"** (perdiendo la palabra clave *"series"*).
   - El botón *"Video de prueba"* aparece como **"Video de"** (falta *"prueba"*).
   - **Causa raíz:** Botones con ancho ajustado al panel (`232px`) y padding que comprimen el ancho de renderizado del texto sin permitir salto de línea o marquee.
3. **Apariencia de "Wireframe / Consola de depuración" por falta de carátulas gráficas:**
   - Las tarjetas actuales son simples cajas rectangulares azulosas con texto plano. En una distancia de sillón (2 a 3.5 metros), el usuario de TV identifica contenido por **imágenes de portada (posters)** y logos, no leyendo bloques de texto denso.
4. **Estado de foco débil e imperceptible para control remoto:**
   - La escala actual en foco es `1.025f` (apenas 2.5% de incremento). Desde el sillón, un salto de 2.5% es prácticamente invisible.
   - El cambio de color de fondo (`#1F2940` a `#44589B`) carece del brillo, halo (glow) o borde de alto contraste (oro/blanco puro) característico de las interfaces de TV de primera línea.
5. **Vulnerabilidad a "Overscan" (Área Segura 16:9):**
   - El menú lateral inicia a solo `12px` del borde izquierdo y `18px` del superior.
   - En televisores físicos tradicionales, la norma de escaneo (overscan) oculta entre un 3% y un 5% del perímetro físico del panel (aproximadamente `54px` a `96px` de margen). En un TV real, la palabra "Inicio" o el logo del león podrían quedar parcialmente fuera del marco físico.

---

## 2. Comparativa Estructural: Leo TV vs. Interfaz Tipo Xuper / Magis

| Elemento UI / UX | Leo TV (Estado Actual) | Estándar Xuper / Magis TV / Leanback Moderno |
| :--- | :--- | :--- |
| **Menú Lateral** | Barra lateral fija estática de 232px con botones alargados con texto recortado. | **Rail colapsable (Icon-only ~72–80dp):** Sólo iconos dorados/blancos. Se expande con animación suave a ~240dp únicamente cuando el usuario navega a la izquierda extrema. |
| **Cabecera / Hero** | Saludo estático plano de texto: *"¿Qué querés ver hoy?"* que ocupa ~160px de alto sin dinamismo. | **Banner Hero Dinámico (Billboard superior ~320–380dp):** Al posicionar el foco sobre cualquier tarjeta, la parte superior muestra el póster panorámico (backdrop), logo del título, año, género, sinopsis y botón "Reproducir". |
| **Relación de Aspecto de Tarjetas** | Cajas rectangulares apaisadas (228 × 110 px) 100% texto. | **Tarjetas con relación estandarizada:**<br>• Películas/Series: **Póster vertical 2:3** (ej. 150 × 225 dp).<br>• Canales en Vivo: **Banner 16:9** (ej. 240 × 135 dp) con logo del canal.<br>• Colecciones: Formato apaisado con gradiente oscuro inferior. |
| **Feedback de Foco (D-Pad)** | Escala mínima de 1.025x y borde tenue azulado. | **Foco de alto contraste (10-Foot UI):** Escala de **1.10x – 1.12x**, elevación z-index (sombras), borde dorado brillante (`#D4AF37`) o blanco puro de 2–3dp y desplazamiento automático de títulos largos (*marquee*). |
| **Manejo de Títulos Largos** | Corte con puntos suspensivos y salto de línea recortado verticalmente. | Título en 1 sola línea bajo el póster; al recibir foco, se activa **texto en marquesina animada (horizontal scroll)** para leer el título completo sin romper el layout. |

---

## 3. Integración del Nuevo Logotipo (León de Cuerpo Entero + "Leo TV")

### Desafío Geométrico
Un león de cuerpo entero estilizado, de perfil hacia la derecha, con el texto centrado debajo (*Leo* azul marino y *TV* dorado), posee una **proporción marcadamente vertical** (aproximadamente 1:1.3 o 1:1.5 de relación alto/ancho).

Si se intenta colocar este logotipo completo dentro de un menú lateral estrecho de 200–230px:
- Si se achica para no ocupar altura, el león se vuelve ilegible (mancha indistinguible de 30px).
- Si se agranda para apreciar los detalles del león, consumirá más de 180px verticales, empujando la botonera hacia abajo y desbordando la pantalla.

### Solución Arquitectónica Recomendada: "Sistema de Marca Adaptativo"
1. **Versión Compacta / Monograma (para el Menú Lateral / Rail):**
   - Utilizar el vector actual refinado (`leo_lion.xml`) o el emblema de la cabeza/corona de león en línea dorada (`#D6BC82`), enmarcado en un glifo cuadrado de `44 × 44 dp`.
   - Al lado (o al expandir el rail): el texto estilizado **LEO** (blanco hueso / azul hielo) **TV** (dorado metálico `#D4AF37`).
2. **Versión Full / Identidad Completa (Cuerpo Entero):**
   - **Pantalla de Carga / Splash Screen:** Protagonismo central absoluto del león completo con fondo azul marino profundo.
   - **Esquina Superior Derecha o Watermark:** Como marca de agua sutil en la esquina superior derecha del Hero Banner (`opacity: 0.25`), o en la pantalla de "Acerca de / Estado".

---

## 4. Los 5 Cambios Priorizados y Concretos para Leo TV

### Prioridad 1: Resolver el Desbordamiento Tipográfico y Proporción de Tarjetas
* **Problema:** El texto de la 3.ª línea está guillotinado en todas las tarjetas horizontales, y en el menú faltan palabras ("Películas y", "Video de").
* **Acción Concreta:**
  1. En las tarjetas de texto provisionales (`tvAction`), modificar la altura de `110px` a mínimo `124px` o reducir el subtítulo a `12sp` con `maxLines(2)`.
  2. Implementar la regla de oro de tarjetas de streaming: **1 línea para el título en negrita (`15sp`) + 1 línea para el estado/badge (`12sp`)**, eliminando párrafos extensos dentro de la tarjeta.
  3. En el menú lateral, aumentar el ancho de `232px` a `250px` (o permitir 2 líneas explícitas con espaciado vertical compensado), evitando que "Películas y series" mutile la palabra "series".

### Prioridad 2: Respetar el Área Segura de TV (Overscan Safe Margins 16:9)
* **Problema:** Márgenes perimetrales de 12px a 18px que colisionan con biseles de pantallas físicas reales.
* **Acción Concreta:**
  1. Establecer un padding global del contenedor raíz (`root`) de **`48px` en laterales (horizontal) y `36px` en extremos verticales (superior/inferior)**. Esto cumple el estándar *Android TV Safe Area (CEA-2014)*.
  2. Aplicar un gradiente de desvanecimiento (*edge fade*) a la derecha de los `HorizontalScrollView` para que el usuario entienda intuitivamente que el carrusel continúa más allá de la pantalla.

### Prioridad 3: Sistema de Foco de Alto Contraste para Control Remoto (D-Pad Experience)
* **Problema:** La escala de `1.025f` y el cambio a azul medio no transmiten claramente qué elemento tiene el control activo desde 3 metros.
* **Acción Concreta:**
  1. **Factor de escala:** Aumentar `scaleX` y `scaleY` a **`1.08f` (o `1.10f`)** con interpolación suave (`animate().scaleX(1.08f).setDuration(150)`).
  2. **Borde de foco:** Sustituir el borde tenue por un trazo de **`3dp` en color Dorado Leo (`#D4AF37`)** o Blanco Puro (`#FFFFFF`) con esquinas redondeadas de `16dp`.
  3. **Elevación:** Aplicar `v.setElevation(focused ? 12f : 0f)` para que el elemento enfocado flote visualmente por encima de las tarjetas vecinas sin ser solapado.

### Prioridad 4: Transformación Gradual a Tarjetas con Carátulas (Posters 2:3 y Canales 16:9)
* **Problema:** La interfaz luce como una lista de botones de base de datos en vez de una experiencia inmersiva de TV.
* **Acción Concreta:**
  1. Diferenciar los adaptadores por tipo de medio:
     - **Películas y Anime:** Adoptar el contenedor vertical estándar **`140dp × 210dp` (ratio 2:3)**. Si no hay carátula online aún disponible, utilizar un fondo degradado marino (`#132238` a `#0A111C`) con un icono temático vectorial centrado y el título al pie sobre una cortina semitransparente.
     - **TV en Vivo / M3U:** Tarjetas apaisadas **`200dp × 112dp` (ratio 16:9)** con fondo neutro oscuro y logo de canal centrado.
  2. Activar `b.setEllipsize(TextUtils.TruncateAt.MARQUEE)` y `b.setSelected(focused)` en el título al enfocar, permitiendo leer nombres largos como *"Internet Archive Open Source Movies"* sin recortar nada.

### Prioridad 5: Evolución hacia Menú Lateral Colapsable y Espacio "Hero" Superior
* **Problema:** El bloque superior actual (`LEO TV · EXPLORAR`, `¿Qué querés ver hoy?` y la nota explicativa) consume casi un 30% del espacio vertical con texto estático repetitivo.
* **Acción Concreta:**
  1. Reducir la cabecera fija a una franja superior discreta: a la izquierda el isotipo del león + texto compacto `LEO TV`, y a la derecha reloj digital y estado de conexión/fuentes.
  2. Destinar la franja superior liberada para que funcione como **visor de detalles dinámico**: cuando el foco esté en cualquier tarjeta de la fila, ese espacio mostrará el nombre grande, la fuente (ej. "Blender Foundation", "Cuevana", "TioAnime") y el estado de la transmisión en tiempo real.
  3. Esto allana el camino para que Leo TV tenga la misma sensación de fluidez y modernidad que Xuper o Magis, sin requerir bibliotecas propietarias pesadas.

---

## 5. Resumen de Tokens Visuales Recomendados (Identidad Leo TV)

* **Fondo Principal:** `#0A1118` (Azul Noche Profundo / Deep Navy)
* **Superficie de Paneles / Tarjetas:** `#131E2E` (Azul Pizarra Oscuro)
* **Acento Principal / Identidad Leo:** `#D4AF37` / `#E5C158` (Oro Imperial / Dorado)
* **Foco Activo D-Pad:** Borde `#D4AF37` de 3dp, Escala 1.08x, Fondo `#1C2D45`
* **Texto Primario:** `#FFFFFF` (Blanco puro, 100% opacidad)
* **Texto Secundario / Metadatos:** `#94A3B8` (Gris Pizarra Claro, legibilidad AA en TV)
* **Safe Area Padds:** `Left: 48px`, `Right: 48px`, `Top: 36px`, `Bottom: 36px`

---
*Fin del informe de revisión visual. Archivo guardado localmente en `OPINION_ANTIGRAVITY.md`.*
