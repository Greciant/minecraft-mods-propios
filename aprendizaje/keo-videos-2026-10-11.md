# Keo: revisión del 2026-10-11

- **TikTok: SIN REVISAR.** Claude in Chrome no está conectado (lo intenté 2 veces). Siguen pendientes los videos posteriores a 7694534169845681429 (09-10).
- **YouTube @elkeomc:** no hay nada nuevo. Los últimos siguen siendo P1yedJEQ-Mw y el short sfof2gRlJV8. Lo revisé sin navegador.
- **Licencia de Paint Anything:** no la pude verificar (no está en Modrinth y CurseForge da 403 sin navegador). Queda anotado en `candidatos-port-desde-keo.md`.
- No hay candidatos nuevos para porteo.

## Actualización (2026-10-11, con Chrome)
Hay 4 TikToks nuevos (10-10 y 11-10).

| Video | Mod | ¿Forge 1.20.1? | Nota |
|---|---|---|---|
| 7695299384912645396 (11-10) | **Advancement Trophies Reforged** (`advancement-trophies-reforged`, MIT): da un trofeo decorativo por cada logro, también de otros mods. Con Shift muestra la fecha del logro y con L abre el logro. | ✅ 1.4.1 (2026-09-29) | Buen candidato de "colección". **Ojo:** con muchos mods, al crear el mundo llena el inventario de trofeos; se puede configurar para desactivar logros concretos. Va en cliente y servidor. |
| 7694931212162370837 (10-10) | **The Block Box**, de vectorwing (el autor de Farmer's Delight): bloques de decoración vanilla+ (hielo y nieve, variantes de hierro, oro, cobre y piedras, troncos tipo muro con o sin pinchos, sillas, farol volador que se coloca en el aire). | ❌ NeoForge 1.21.1 y 26.x | **Para porteo.** Ver `candidatos-port-desde-keo.md`. |
| 7695257670076386580 (11-10) | **Vitrail** (`vitrail-shaders`, LGPL-3.0): shaders de Iris sobre **Vulkan**, con muchos más FPS. | ❌ 26.2–26.3 | No se puede portar: 1.20.1 no tiene Vulkan. |
| 7694916549479582996 (10-10) | Compró Minecraft para PS4. | — | No sirve para el pack. |

**Paint Anything:** ahora **sí existe para Forge 1.20.1**: `paint_anything-1.0.0-forge-1.20.1.jar` (08-10, 716 KB), de GoldhidersMods, https://www.curseforge.com/minecraft/mc-mods/paint-anything. La licencia es **All Rights Reserved**, pero para usarlo no hace falta portarlo. Va en cliente y servidor. Tiene unas 184 descargas y es muy nuevo; habría que probarlo en una copia.
