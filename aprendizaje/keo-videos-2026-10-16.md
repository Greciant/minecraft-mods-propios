# TikToks que mandó el usuario: revisión del 2026-10-16

Claude in Chrome no estaba conectado, así que saqué la descripción y los subtítulos de cada TikTok con curl. No instalé nada.

## 1. https://vt.tiktok.com/ZSbEaJyNe/ — @soyjosef0.0, "JOSEFO'S PACK 2" (7680002192555658516)
Es la presentación de su modpack: montañas con vegetación, mucha distancia de visión, animaciones del jugador y de los mobs, viento, agua, sonidos de pasos según la superficie, estela de los botes y shaders Complementary con Euphoria Patches. **No dice qué mods usa, salvo estos:**
- **Voxy** (LOD, la distancia de visión enorme): solo Fabric en 26.x y licencia **All Rights Reserved** → no se puede portar. El pack ya tiene Distant Horizons 3.3.2.
- **Euphoria Patches**: el pack ya lo tiene (EuphoriaPatcher 1.10.0-r5.9). En Modrinth está la **1.10.5-r5.9.3-forge** (15-09), que se puede actualizar.
- Sonidos de pasos: el pack ya tiene Presence Footsteps.

## 2. https://vt.tiktok.com/ZSbE5wdhV/ — @thestudiousgoose, "5 Awesome Mods…" (7680995563109616909)
| Mod | Qué hace | Forge 1.20.1 | Estado |
|---|---|---|---|
| **Centered Crosshair** (MIT, github.com/JustAlittleWolf/centered-crosshair) | La mira de vanilla está corrida un píxel a la izquierda; este mod la centra. | ❌ solo Fabric (26.x) | **Para porteo**: es trivial y va solo en el cliente. |
| **Raised** (LGPL-3.0) | Sube la barra rápida (hotbar) 1 píxel para que no quede cortada; se puede ajustar. | ✅ Forge-1.20.1-6.0.2 (23-09) · https://modrinth.com/mod/raised | No está en el pack. Es solo de cliente; candidato ligero. |
| Legendary Tooltips | Marco en los tooltips de ítems raros o encantados. | ✅ | **Ya está en el pack.** |
| "Dion's Eating Animation" (resourcepack) | Animación al comer. | — | El pack ya tiene eatinganimation 5.1.0. |
| "recovered": texturas de libros encantados según el encantamiento | — | No lo identifiqué con certeza. Lo más parecido es **Enchanted Book Redesign** (MIT, Fabric/NeoForge 1.21.1, github.com/Tfarcenim/Enchanted-Book-Redesign), que colorea los libros según dónde se pueden aplicar. | Candidato menor para porteo. |

## 3. https://vt.tiktok.com/ZSgNmXR2e/ — @k4yzend, "3 mods vanilla" (7695952101704289556)
| Mod | Qué hace | Forge 1.20.1 | Estado |
|---|---|---|---|
| **Auditory Continued** (MIT, github.com/ItsSyfe/auditory-continued) | Renueva muchos sonidos de bloques y de interacción con ítems. | ❌ solo Fabric/Quilt (1.20.1–1.21.1) | **Para porteo.** Va en cliente y servidor según Modrinth; habría que revisarlo, porque probablemente basta con el cliente. |
| Shulker Box Tooltip | Ver el contenido de las shulker sin colocarlas. | ✅ | **Ya está en el pack** (shulkerboxtooltip-forge 4.0.4). |
| **Axolotl Buckets** (MIT) | El balde muestra el ajolote que lleva, incluidas las crías. | ❌ para 1.20 solo Fabric/Quilt (1.2.0+1.20); hay Forge solo en versiones más nuevas | **Para porteo** (pequeño). Alternativa sin portar: el resourcepack "colored-axolotl-buckets…" (MIT, 1.20.1), que funciona con CIT. |
(El resto del video es publicidad de un servidor.)
