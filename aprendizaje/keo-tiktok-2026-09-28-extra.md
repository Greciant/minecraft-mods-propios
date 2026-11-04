# TikToks que mandaste el 2026-09-28: qué se instaló y qué se puede portar

| # | Video | Mod | ¿Forge 1.20.1? | Qué hice |
|---|---|---|---|---|
| 1 | @elkeomc 7689324831078141204 | Ecstatic (LOD a partir de la semilla) | ❌ solo NeoForge 1.21.1 | Evaluación de port (abajo) |
| 2 | @tandatmc2koo4 7689356557464718599 | **Spatial GUI**, de tastytrash: los inventarios se dibujan como un panel en 3D delante del jugador | ❌ Fabric/NeoForge 1.21.1–26.3 | Evaluación de port (abajo) |
| 3 | @elkeomc 7684873370697010453 | ReAnimated | port propio | Sigue como opcional: no se pudo probar el cliente con el pack completo |
| 4 | @elkeomc 7684506593773374741 | Mods de la ModJam | ❌ 26.x / NeoForge 1.21.1 | Evaluación de port (abajo) |
| 5 | @elkeomc 7682938253418253575 | **Block Pack** 1.1.2, de blockfactorystudio (los de Bosses'Rise): más de 1200 bloques de decoración | ✅ | **Instalado** en cliente y servidor |

## Evaluación de ports a Forge 1.20.1

- **Spatial GUI**
  - Licencia MIT, código en github.com/tastytrash/Spatial-GUI. Son unas 3,3k líneas y 15 mixins.
  - La mitad de los mixins van al renderizado: GameRenderer, LevelRenderer, Camera, el `GuiRenderer` y el `PictureInPictureRenderer` de 1.21.6+.
  - En 1.20.1 no existe ese sistema de GUI. Habría que reescribir el núcleo: dibujar la pantalla en un framebuffer y pintarlo como un plano en el mundo.
  - Encima, con Embeddium y Oculus hay riesgo alto de choques.
  - **Esfuerzo medio-alto; no lo empecé.** Se puede hacer si lo quieres.
- **Ecstatic**
  - Licencia CC-BY-NC-SA 4.0: se permite portar para uso personal, no comercial, compartiéndolo con la misma licencia. Son unas 8,9k líneas.
  - Genera el terreno lejano con el ruido de worldgen de 1.21.1 y tiene un renderizador LOD propio.
  - **No tiene sentido en tu pack**: con Tectonic y Lithosphere el terreno lejano no coincidiría con el real (el propio autor dice que no soporta worldgen modificado), y ya tienes Distant Horizons 3.3.2.
  - **No recomendado.**
- **Mods de la ModJam** (todos están en 26.x o NeoForge 1.21.1):
  - Archaion: All Rights Reserved. **No se puede.**
  - Ancient Remnants: Monoliths (Obscuria) y Timeless Echoes: licencia personalizada, habría que leerla. Además son de 26.1.
  - Noble Phantasms: MIT, pero son unas 35,7k líneas y 272 clases, para 26.1.2, con componentes de datos por todas partes.
  - **Todos son ports grandes (de 26.x a 1.20.1); no recomendados.**
