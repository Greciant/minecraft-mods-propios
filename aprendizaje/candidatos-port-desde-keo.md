# Candidatos a portar a Forge 1.20.1 (vistos en videos de Keo)

Lista viva. Pedido del usuario del 2026-10-10: los mods que mejoren la experiencia y solo existan por encima de 1.20.1 se le pasan al hilo de porteo de mods. Licencias según Modrinth.

| Mod | Qué hace | Versiones y loaders | Licencia / fuente | ¿Portable? |
|---|---|---|---|---|
| **Show My World** | En la pantalla de seleccionar mundo, el fondo muestra una vista previa del mundo elegido. | Fabric, NeoForge y Quilt, 26.1.2–26.3 | AGPL-3.0 · github.com/MiraculixxT/Worlds | ✅ licencia libre (el port tiene que quedar AGPL; para uso privado no hay problema). Solo cliente. |
| **Scroll Walk** | Alt + rueda del ratón para caminar más lento o más rápido (cámara lenta y "aura"). | Solo Fabric, 1.21.11–26.1.2 | MIT · github.com/Naw7k/Scroll-Walk | ✅ fácil. Solo cliente. |
| ~~Paint Anything~~ | Pinta cualquier bloque. | **Desde el 08-10 ya existe para Forge 1.20.1** (CurseForge) | ARR | **No hace falta portarlo:** se puede usar tal cual (ver keo-videos-2026-10-11.md). |
| **The Block Box** (vectorwing, el autor de Farmer's Delight) | Bloques de decoración vanilla+: hielo y nieve, variantes de metales y piedras, troncos tipo muro con o sin pinchos, sillas, farol volador que se coloca en el aire. | NeoForge 1.21.1 y 26.x (https://modrinth.com/mod/the-block-box) | Código **MIT**, assets **CC BY-NC 4.0** · github.com/vectorwing/BlockBox (rama 1.21.1) | ✅ se puede portar para uso personal (sin fines comerciales). Va en cliente y servidor. Encaja con el estilo vanilla+ del pack. |
| Mod de cofres con interfaz "RPG" (TikTok 7690422093715950868) | El cofre se abre como un panel que sigue al ratón, con modo primera persona. | Fabric y NeoForge, recientes | No identificado | Se parece a Spatial GUI, que ya está portado: no hace falta. |
| Better Copper Golem | El golem de cobre recuerda en qué cofre va cada ítem y ordena shulkers. | 26.2–26.3 | **All Rights Reserved** | ❌ salvo permiso del autor. Además necesita backport-copper-age. |
| Chest Preview Plus | Vista previa del contenido del cofre. | 26.x | **ARR** | ❌ |
| Block Dithering | Transición difuminada de bloques. | 26.x | CC-BY-ND (no permite derivados) | ❌ |
| Better Render Distance | — | Fabric 26.x | **ARR** | ❌ |
| Ecstatic, Spatial GUI | — | — | — | Ya portados (ver la memoria ports-forge-1201). |

**Actualización del 2026-10-11 — licencia de Paint Anything:** sigue **sin verificar**. No está en Modrinth y CurseForge bloquea las consultas sin navegador (403). Tampoco aparece en la búsqueda web. Hay que mirarla con Chrome en la página del mod; Keo dijo que tenía unas 73 descargas el 05-10.

## Agregados el 2026-10-16 (TikToks que mandó el usuario, sin portar)
| Mod | Qué hace | Versiones | Licencia / fuente | Nota |
|---|---|---|---|---|
| **Auditory Continued** | Renueva muchos sonidos de bloques e ítems. | Fabric/Quilt 1.20.1–1.21.1 | MIT · github.com/ItsSyfe/auditory-continued | ✅ portable. Revisar que no choque con Sound Physics y Presence Footsteps. |
| **Centered Crosshair** | Centra la mira, que en vanilla está corrida 1 px. | Fabric 26.x | MIT · github.com/JustAlittleWolf/centered-crosshair | ✅ trivial, solo cliente. |
| **Axolotl Buckets** | El balde muestra qué ajolote lleva dentro. | Fabric/Quilt 1.20 (Forge solo en versiones nuevas) | MIT | ✅ pequeño. Hay alternativa con resourcepack CIT. |
| Enchanted Book Redesign (probable "recovered" del video) | Libros encantados con colores según dónde se pueden aplicar. | Fabric/NeoForge 1.21.1 | MIT · github.com/Tfarcenim/Enchanted-Book-Redesign | ✅ menor. |
| Voxy (MCRcortex) | LOD de gran distancia, muy liviano. | Fabric; hubo una versión **1.20.4** (rama vieja) y luego 1.21.6–26.2 | **ARR** · github.com/MCRcortex/voxy | **Solo con permiso escrito del autor** (como Archaion), y entonces solo para uso privado y sin publicarlo. Su página pide expresamente que no se usen backports no oficiales. Técnicamente es difícil: necesita OpenGL 4.6, está integrado con Sodium (en Forge 1.20.1 habría que adaptarlo a Embeddium), los shaders necesitan soporte explícito (Oculus no lo tiene) y se pisa con Distant Horizons, que ya está en el pack. El mejor punto de partida sería la versión 1.20.4. |
