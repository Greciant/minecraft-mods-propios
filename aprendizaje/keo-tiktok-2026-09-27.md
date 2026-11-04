# Keo (TikTok @elkeomc): últimos 10 videos (revisado el 2026-09-27)

Fuente: https://www.tiktok.com/@elkeomc, con los videos ordenados por "Recientes". El texto sale de la descripción, de los subtítulos automáticos (ASR, español) y de las respuestas de Keo en los comentarios. Verifiqué los nombres de los mods en Modrinth porque el ASR los escribe mal.
Fechas en UTC. La disponibilidad para **Forge 1.20.1** la comprobé en la API de Modrinth ese mismo día.

> ⚠️ No pude comparar contra las notas de YouTube: `keo-y-similares.md` no existe ni en `~/Minecraft/aprendizaje/` ni en `/mnt/project-files/`. La marca **NUEVO** indica solo que la cosa no está en el modpack actual (`clientes/wip-1.20.1-ACTIVO`, 264 jars). No es una comparación con esas notas.

> Video fijado (no cuenta entre los 10): https://www.tiktok.com/@elkeomc/video/7647020882921032967, del 2026-06-03 (antiguo). Trata de launchers no premium clones de Prism (PolyMC / "PineconeMC" / MultiMC): cuenta offline, descargador de Java Adoptium integrado, mod Quick Skin. No sirve para el pack.

---

## 1. "Estoy haciendo un modpack de más de 700 mods"
- URL: https://www.tiktok.com/@elkeomc/video/7690034769702325525 · 2026-09-27 · 2:34
- Tema: su nuevo modpack gigante (715 mods, todavía sin configurar). No dice el loader, pero en pantalla se ve **Forge 47.4.x / MC 1.20.1 + ModernFix**, o sea la misma base que el pack del usuario.
- Datos concretos:
  - Crear un mundo tarda unos 5 min. El pack usa **18–20 GB de RAM** con 30 GB asignados. Consigue >60 FPS con shaders pesados.
  - Problemas: la generación de estructuras se solapa ("es estúpida", dice que hay mods que lo arreglan) y el inventario se llena al iniciar. Tiene 45 pestañas de creativo y ~375 páginas de EMI.
  - **Bug grave: el mod de física del pasto (el ASR dice "gracier Gras") se ve roto SIN shaders**, así que lo va a quitar. No pude identificarlo con certeza.
  - Lleva 81 h solo metiendo mods. Su pack anterior de 700 mods era "Keo Mods Unlimited".
- Comentarios: los seguidores piden un "Keo vanilla optimizado" y preguntan cómo quitar el contador de FPS del Keo optimizado. Uno pregunta cuál es el mod de las animaciones y Keo dice que no sabe cuándo cambiaron.
- Útil: sirve de referencia de que un pack Forge 1.20.1 con más de 700 mods es viable si se asigna mucha RAM. También avisa de que los mods de física de pasto pueden romperse sin shaders.

## 2. "Oficialmente tenemos nueva dimensión en Minecraft después de 14 años"
- URL: https://www.tiktok.com/@elkeomc/video/7689904155627900181 · 2026-09-26 · 2:08
- Tema: noticias de la Minecraft Live. Anunciaron **The Shift** (la dimensión del Warden, vista antes en Dungeons) para el año que viene, y para el drop 26.4 cuevas de hielo, bloques de luz decorativos, carámbanos que caen con el calor y un zombi de hielo que congela.
- En el video usaba un mod de "The Shift" (el ASR dice "meleon the shift"). No lo encontré en Modrinth.
- Útil: nada directo para 1.20.1. Es solo contexto de vanilla.

## 3. "Uno de los mejores mods de la historia para hacer Minecraft más difícil"
- URL: https://www.tiktok.com/@elkeomc/video/7689710300043562261 · 2026-09-26 · 2:03
- Mod: **Behemoths**, de AquTheSeal (el mismo creador que **Celestisynth**). Enlace: https://modrinth.com/mod/behemoths
  - Añade "Pandemonium Curses": al conseguir ciertos logros o matar ciertos jefes salta una **votación en el servidor** (votan todos los jugadores) sobre un modificador global, por ejemplo buffs para los esqueletos o regeneración más lenta al comer.
  - Trae jefes y mobs gigantes muy difíciles, con buenas animaciones y sonidos, y armas/herramientas que sueltan. Los disparadores de la votación se pueden configurar.
  - Keo dice que tiene **compatibilidad directa con L_Ender's Cataclysm**, que sus jefes pueden salir en las votaciones.
  - **Forge 1.20.1** ✅, sin dependencias en Modrinth, cliente y servidor. Última versión 1.1.1.0 (2026-04-04). Keo avisa de que no se actualiza desde hace unos 6 meses.
- Útil: **NUEVO**. Encaja con un servidor multijugador y es de Forge 1.20.1 nativo.

## 4. "Cómo tener wallpapers animados de Minecraft"
- URL: https://www.tiktok.com/@elkeomc/video/7689698385342844180 · 2026-09-26 · 1:37
- Tema: Wallpaper Engine (de pago, unos 3 USD). En Workshop busca "Goku Keo" o "keomc", o "Cozy fireplace Minecraft" (con Alex).
- Útil: nada para el modpack.

## 5. "Shaders raros para Minecraft"
- URL: https://www.tiktok.com/@elkeomc/video/7689339694051527957 · 2026-09-25 · 2:09
- Shaders que muestra:
  - **Back to Basics**: aspecto pixel-art. No lo encontré en Modrinth.
  - **Legacy Console Edition Shaders**: el look de Xbox 360 / PS3. Keo dice que está oculto en la tienda. En Modrinth está como `legacy-console-edition-shaders`, solo 1.21.
  - **Ditherpunk**: retro, con modos 1-bit y Game Boy. Soporta de 1.18.2 a 26.x, así que 1.20.1 ✅ (Iris u OptiFine; en Forge 1.20.1 necesita Oculus).
  - **"2KS"**: efecto de cámara antigua, borrosa y temblorosa. No lo identifiqué.
  - **Comic Shaders** (kloppi): contorno de cómic con el trazo desplazado a propósito respecto al bloque. Soporta de 1.9 a 1.21.4.
- Útil: poco. Solo son alternativas curiosas a Complementary/Eclipse.

## 6. "Este es el mejor mod de la historia" (LOD)
- URL: https://www.tiktok.com/@elkeomc/video/7689324831078141204 · 2026-09-25 · 2:09
- Mod: **Ecstatic** (https://modrinth.com/mod/ecstatic), un LOD "extremadamente ligero".
  - Genera el terreno lejano **a partir de la semilla**, como Chunkbase, sin generar ni visitar los chunks. Así se ven cientos de chunks al instante en un mundo nuevo, sin pregenerar con Chunky.
  - Por defecto usa un render distance de 144 chunks, y el número de hilos se puede configurar con F4.
  - Resultados de Keo: ~1300 FPS al 25 % de la calidad del mod, frente a 600–800 FPS de DH a 32 chunks mostrando menos terreno.
  - Limitación, según los comentarios y la respuesta de Keo: todavía **no funciona con worldgen modificado** (biomas o terreno de mods), aunque la página dice que lo será.
  - **Solo NeoForge 1.21.1** ❌ para Forge 1.20.1. Tiene unas 2.6k descargas.
- Útil: no aplica al pack. Además, con Tectonic/Lithosphere mostraría un terreno que no coincide con el real.

## 7. "Con este mod puedes hacer tus propios discos"
- URL: https://www.tiktok.com/@elkeomc/video/7688922097514597652 · 2026-09-24 · 2:36
- Mod: **Music Disc Maker**, de KURONAMI (https://modrinth.com/mod/music-disc-maker).
  - Se pega un enlace de YouTube, Spotify, SoundCloud, Bandcamp o un audio directo en la mesa y te da un disco con el título y la carátula. También se pueden teñir los discos.
  - Trae un tocadiscos dorado (avanzar, volumen, pausa), un pedestal para exponer discos, un álbum (lista de reproducción), un boombox y **altavoces enlazados** (clic derecho agachado sobre el tocadiscos).
  - En servidor la reproducción está sincronizada. Hay un resourcepack que mejora las texturas ("Music Disc Maker Retextured"), aunque a Keo le dio un bug visual al activarlo.
  - **Forge 1.20.1** ✅ (3.0.5+forge-1.20.1, del 2026-09-26). Es obligatorio en cliente y servidor, con la misma versión en todos.
- Útil: **NUEVO**. Da juego social en el servidor. Hay que comprobar si en el servidor descarga el audio desde internet.

## 8. "Mods que deberían estar en Minecraft vanilla"
- URL: https://www.tiktok.com/@elkeomc/video/7688905012067470612 · 2026-09-24 · ~2:10
- Mods:
  - **Block Dithering** (ZipeStudio): el cristal o los andamios se vuelven transparentes con tramado al acercarte. Configurable por bloque y compatible con shaders. Solo Fabric/NeoForge 1.21.1+ ❌.
  - **Sound Visualizer** (DGNeoS): indicadores en pantalla de dónde vienen los sonidos, al estilo Fortnite, con iconos y colores configurables. Pensado para accesibilidad. **Forge 1.20.1** ✅ (1.0.2), solo cliente.
    - Alternativa para 1.20.1: "Synesthesia: Sound Visualizer" (addon de Extreme Sound Muffler).
  - **Chest Preview** (Hackartem, `chest-preview-plus`): vista flotante y animada del contenido del cofre sin abrirlo. Solo cliente. Soporta de 1.21.1 a 26.3, así que no hay versión Forge 1.20.1 ❌.
  - **Show My World** (Miraculixx): muestra un panorama de tu mundo o servidor en la lista antes de entrar. Fabric/NeoForge 1.21.1+ ❌.
- Útil: **Sound Visualizer** es NUEVO y sirve para 1.20.1. El resto no está para Forge 1.20.1.

## 9. "La End Update se acerca"
- URL: https://www.tiktok.com/@elkeomc/video/7688578335902944533 · 2026-09-23 · ~1:30
- Tema: en el primer snapshot del último drop del año, **Vulkan pasa a ser la API gráfica por defecto en Java** (se puede volver a OpenGL). A Keo le da 1000–1300 FPS con Vulkan frente a más de 2000 con OpenGL. También especula con la End update y con Vibrant Visuals para Java.
- Útil: nada para 1.20.1. Es contexto de futuro.

## 10. "Distant Horizons es el mejor mod de la historia"
- URL: https://www.tiktok.com/@elkeomc/video/7688567583569726741 · 2026-09-23 · ~2:40
- Tema: **Distant Horizons ya salió de beta** y tiene versión release. Ahora renderiza los LOD de forma dinámica: mejoran de calidad al acercarte y muestran las texturas reales de los bloques lejanos (antes se veían "de plastilina").
  - Consejos: la generación de chunks lejanos viene desactivada por defecto; hay que activar el rendering en el menú de pausa (botón de DH). Él pregenera con **Chunky** unos 1000 bloques de radio.
  - Números: a 32 chunks, DH da ~630 FPS frente a ~300 en vanilla. Voxy (solo Fabric) sigue rindiendo mucho mejor, unos 1000 FPS, pero DH es más compatible con shaders.
- **Relevante para el pack:** tiene `DistantHorizons-3.2.0-b-1.20.1-fabric-forge.jar`, una beta. En Modrinth ya están las **release 3.3.0 (2026-09-17) y 3.3.2-1.20.1 (2026-09-21)** para Forge 1.20.1.

---

## Útil / le podría gustar

| Qué | Por qué | ¿Forge 1.20.1? |
|---|---|---|
| **Actualizar Distant Horizons 3.2.0-b → 3.3.2** | Es la release estable, con LOD dinámico y texturas reales a lo lejos. El pack ya lleva DH en beta. Hay que probarlo con Oculus + Complementary/Eclipse. | ✅ 3.3.2-1.20.1 en Modrinth |
| **Behemoths** (AquTheSeal) | Jefes gigantes y un sistema de "maldiciones" que el servidor vota; es muy social para multijugador. Tiene integración con Cataclysm. Aviso: lleva unos 6 meses sin actualizarse. | ✅ 1.1.1.0 |
| **Music Disc Maker** | Discos con cualquier enlace, álbum, boombox y altavoces, sincronizados en el servidor. Tiene que estar en cliente y servidor con la misma versión. | ✅ 3.0.5+forge-1.20.1 |
| **Sound Visualizer** (DGNeoS) | Indicadores de dirección de los sonidos. Solo cliente, así que es fácil de meter. Encaja con Sound Physics Remastered, que ya está en el pack. | ✅ 1.0.2 |
| Ditherpunk (shader) | Shader retro configurable (1-bit, Game Boy), como opción de estilo en Oculus. | ✅ (Iris/OptiFine, 1.18.2+) |
| *Aviso:* mods de física de pasto | En el pack de 715 mods de Keo se veía roto sin shaders. Si el pack mete uno, hay que probarlo con los shaders desactivados. | — |
| *No aplica:* Ecstatic, Block Dithering, Chest Preview, Show My World | Buenas ideas, pero solo existen para NeoForge/Fabric 1.21.1+. Ecstatic además no funciona con worldgen modificado (Tectonic/Lithosphere). | ❌ |

Para el mod propio de animaciones: ningún video trata de animaciones del jugador ni de partículas. Lo único cercano es que Behemoths tiene jefes con animaciones muy trabajadas (el mismo autor que Celestisynth), por si sirve de referencia visual.
