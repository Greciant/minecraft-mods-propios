# Shine 3.0 (tapeQ): cómo está hecho por dentro

*Análisis del 26-09-2026 a partir del jar real, no del repositorio de GitHub (que sigue en la v1.0).*

> **Licencia: All Rights Reserved** (lo declara su `fabric.mod.json`). Este documento es para **aprender las técnicas**.
> No se copia código a un mod publicado: en el port se reimplementa desde cero con nuestras propias fórmulas y constantes.
> Complementa `shine-y-animaciones.md` (notas previas en la nube) sin repetirlo.

## 0. De dónde sale el análisis

| Qué | Dónde |
|---|---|
| Jar analizado | `~/Descargas/shine-3.0.jar` (misma copia en `desarrollo/shine-port/instancia-fabric/.minecraft/mods/`, SHA verificado contra la web) |
| Descompilado (CFR 0.152, ya estaba hecho) | `desarrollo/shine-port/analisis/dec-full/` → 494 `.java`, paquete `com.bloom` |
| Recursos extraídos (shaders, texturas, JSON) | `desarrollo/shine-port/analisis/x/assets/shine/` |
| Mapa de mixins frente a 1.20.1 (hecho el 21-08) | `desarrollo/shine-port/analisis/mapa-viabilidad.txt` → 49 OK, 22 Sodium, 18 no existen en 1.20.1 |
| Versión posterior (no analizada a fondo) | `~/Descargas/shine-3.1.1+26.2.jar`: es para **MC 26.2 + Java 25 + Sodium 0.9**. Trae un backend Vulkan nuevo (`ShineRenderBackend`, `SodiumVulkanTerrainUniforms`, `OpenGl*FramebufferBridge`) y cambia `SodiumShaderLoaderMixin` por `SodiumShaderSourceTransformer`. Para portar a 1.20.1 **la 3.0 es mejor referencia**. |

La 3.0 declara Fabric, MC ~1.21.11, Sodium >= 0.8.12 y Java 21, y rompe con VulkanMod. Todo el mod es **solo de cliente**.

**Arquitectura general**
- `BloomClient` registra en `ClientTickEvents.END_CLIENT_TICK` unos 40 `*.tick()` (spawners y controladores).
- También registra unas 20 llamadas en `WorldRenderEvents.END_MAIN`, más `START_MAIN`, `BEFORE_ENTITIES` y `BEFORE_TRANSLUCENT`.
- Casi todo está en `client/experimental/<efecto>/`. Cada efecto se reparte en un *Spawner* (qué y dónde aparece), una *Particle* (lógica por instancia), a veces un *Renderer* propio y a veces un *SoundController*.
- La configuración está en `ExperimentalConfig` (JSON enorme con perfiles por bioma) más 122 pantallas de configuración.
- `ShineSpawnInterop` / `ShineFeatures` es una API (`api/v2`) para que otros mods apaguen efectos por zona (`allowsAny`, `allowsAt`).

---

## 1. Animaciones procedurales

### 1.1 Pájaros (`worldambience/WorldAmbienceParticle$BirdParticle`, `BirdHingedWingRenderer`)
**Qué son:** partículas del cliente (`WORLD_AMBIENCE_BIRD_PARTICLE`), no entidades. Solo aparecen en el Overworld.

**Cómo aparecen** (`WorldAmbienceEffectsSpawner.spawnBirds`):
- Hay un tope global (`birdCap`) y una densidad por bioma (`resolvedWorldAmbienceBirdProfile(biome)`).
- **Modo "seeding":** mientras haya menos pájaros que un objetivo mínimo (≈ densidad·1,5), se hacen muchos más intentos y la probabilidad de aparecer sube a ≥ 0,8. Así el mundo "se llena" enseguida y después se mantiene con probabilidades bajas (≈ 0,006·densidad·tasa·visibilidad).
- **Posado:** buscan una percha (`sampleBirdPerch`). Si no la hay y está activado el *open-air fallback*, aparecen en el aire.
- **Bandadas:** cada spawn crea un grupo de 2 a 4 alrededor de un ancla, en un radio de 6 a 18 bloques y con un ángulo común más un poco de ruido.
- **Separación de especies:** no se mezclan familias distintas dentro de un radio de 10 a 28 bloques. Si ya hay una especie cerca, se prefiere esa misma (`nearestBirdFamily`).
- La especie la elige `ShinePackManager.chooseBirdSpecies` usando el peso y las condiciones de bioma, dimensión, clima y hora (ver §3).
- Los parámetros de la partícula se reutilizan como datos: `vx,vz = cos/sin(ángulo)` y `vy = índice de especie`.

**Máquina de estados** (constantes en la clase):
- Estados: `FLYING=0`, `LANDING=1` (con dos subfases, `APPROACH` y `FLARE`), `PERCHED=2`, `CHECKING=3` (mira a los lados en el suelo) y `SPOOKED=4` (huye).
- Pasa a `SPOOKED` cuando cualquier entidad entra en su radio de evasión (`birdAvoidRadius`), no solo el jugador. Se cancela el aterrizaje si pasan más de 240 ticks o si la percha deja de ser válida.

**Animación "hinged wing"** (la parte interesante):
- **Ala con muelle:** el ángulo del ala no se asigna directamente. Es un **muelle amortiguado**: `vel += (objetivo - ángulo)·respuesta; vel *= amortiguación; ángulo += vel`, con respuesta y amortiguación interpoladas según un parámetro de "suavidad".
- **Ciclo de aleteo** asimétrico: ≈ 42 % del ciclo para la bajada y ≈ 58 % para la subida, cada tramo con *smoothstep*.
- **Ritmo:** la frecuencia de aleteo cambia sola cada pocos segundos, con una transición en *smoothstep* entre una escala mínima y otra máxima. Esa variación es la que lo hace parecer vivo. Al huir se multiplica por 1,28 y al posarse (*flare*) aumenta.
- **Planeo:** si baja con suficiente velocidad horizontal entra en planeo (ala quieta) con un *cooldown*.
- **Alabeo (*bank*):** es proporcional al giro entre la dirección anterior y la nueva (`atan2` del producto cruzado y escalar) ×4,8, limitado a ±30° (±35° huyendo, ±16° en *flare*), y también va con muelle.
- **Cabeceo (*pitch*):** `atan2(vy, velHorizontal)` limitado a [-24°, +32°], también con muelle.
- **Giro limitado:** la dirección visual gira como máximo 8, 10 o 14° por tick según el estado, así no hay giros bruscos.

**Render:**
- **De lejos o posados:** *sprite* de partícula normal con una atlas propia (`shine_pack_content.png`) y un shader `shine_particle_bird_depth_alpha` que escribe profundidad.
- **Volando cerca:** se dibujan como **malla de 3 quads** (ala izquierda, cuerpo, ala derecha) en `BirdHingedWingRenderer`, en `END_MAIN`, con su propio `RenderPipeline`.

### 1.2 Mariposas (`ButterflyParticle`, `ButterflyHingedWingRenderer`)
- **Cuándo:** solo con cielo despejado, en el Overworld y con perfil de bioma. Usan el mismo patrón de *seeding* que los pájaros.
- **Movimiento:** hay un punto objetivo aleatorio cercano que se cambia de vez en cuando. La altura se limita respecto al suelo (`heightmap MOTION_BLOCKING`) más un margen. La trayectoria se sigue con una respuesta suave (≈ 0,055) y encima se suma:
  - un **zigzag lateral** senoidal (`sin(edad·0,055 + fase)`),
  - un **arco de subida** senoidal cuando gana altura,
  - un pequeño alabeo `sin(edad·0,13 + fase)·0,1`.
  Cada mariposa tiene su propio `wander` y `flapSpeed` aleatorios.
- **Malla de alas con bisagra**, la técnica más fácil de portar:
  - La textura es de 11×11 px: ala izquierda en las columnas 0–5, cuerpo en la 5–6 y ala derecha en la 6–11.
  - Cada vértice del ala se rota alrededor de su bisagra: `derecha = bisagra + d·cos(ángulo)` y `arriba = |d|·sin(ángulo)`, siendo `d` la distancia en píxeles a la bisagra.
  - Después se transforma con una base ortonormal (derecha/arriba/adelante) calculada a partir de la dirección, el *pitch* y el *bank*.
  - Se interpola todo con el *partialTick*. El color de vértice lleva el brillo del *lightmap* en esa posición y el alfa del *fade*.
- Hay 5 familias de color (rojo, naranja, amarillo, azul, violeta), una textura cada una. Se dibujan con blend alfa, sin *cull* y sin escribir profundidad.

### 1.3 Gansos y buitres
- `GooseHingedWingRenderer` son bandadas en V con un *cooldown* entre bandadas. `VultureRenderer` son planeadores en círculo.
- Usan el mismo esquema que los pájaros: *sprite* + ala con bisagra y muelle. Cada uno tiene su `*SoundController`, con sonido posicional según la distancia.

### 1.4 Hierba que se dobla: dos sistemas
**A) Hierba vanilla y follaje (`foliagesway/FoliageSwayController`) y B) briznas generadas (`grassblades/`).** Los dos comparten la misma idea:

1. **Quién empuja:** `InteractiveContactBody.activeBodies()` junta el jugador (o su barca) y los mobs vivos cercanos (`LivingEntity` que no vayan montados). Cada uno aporta su AABB y su velocidad.
2. **Celdas con muelle en la CPU:** por cada bloque (A) o por cada celda de 1/4 de bloque (B) se guarda un vector de doblado `(x, z)`.
   - Cada tick se integra un muelle amortiguado hacia el objetivo, que es "alejarse del cuerpo".
   - Al dejar de haber contacto, la recuperación arranca con suavidad (*smoothstep* de 10 ticks) y la rigidez se multiplica por `recovery`.
   - Hay una opción de "aplastado persistente", que mantiene el doblado N ticks.
   - El módulo se limita con un *soft clamp* (0,75 en briznas y 6 en follaje).
3. **Rasterizado a textura:**
   - Todo se escribe en un `NativeImage` de **512×512** centrado en el jugador.
   - Mitad izquierda: el doblado actual. Mitad derecha: el del tick anterior, para interpolar en la GPU con `u_Shine…TickAlpha`, que es el tiempo desde el tick normalizado a 50 ms y suavizado.
   - Codificación por píxel: `R = x`, `G = z` (con signo, pasados a 0..255) y `B = altura` relativa ×4. Así se distinguen varias alturas en la misma columna.
   - El follaje ocupa otra zona de la misma imagen, en 16 capas de 64×64 según `y mod 16`.
4. **GPU:** esa textura se enlaza como *sampler* extra (`u_ShineGrassBladeInteractionTex`) en el shader de terreno. El **vertex shader desplaza los vértices superiores** de la planta. No se reconstruye ningún chunk cuando pisas la hierba.
5. **Cómo sabe el shader qué vértice es planta:**
   - `markInteractionArgb()` guarda la información en el **alfa del color de vértice**: `alpha = 150 + grupo·5 + flags`. El grupo sale de la paridad x/z y de si es planta doble; los flags indican "candidata a viento" y "sombra animada".
   - Las briznas generadas usan una etiqueta de quad `0x475242xx` (el texto "GRB" + id).

**B) Briznas 3D generadas (`GrassBladesModel`):**
- Es un *wrapper* de `BakedModel`, registrado con `ModelLoadingPlugin.modifyBlockModelAfterBake` de Fabric (FRAPI `emitQuads`), que **añade geometría** sobre el bloque de hierba:
  - de 1 a 3 segmentos por brizna, con perfil de ancho y curvatura,
  - "micro-tufts" en una rejilla de 1,5,
  - densidad regional con *value noise*,
  - bordes expuestos (máscara N/E/S/O),
  - tinte del bioma con variación HSB.
- Tiene **LOD por sección**: FULL, MEDIUM, FAR y HIDDEN, con histéresis, recalculado cada 5 ticks y con un máximo de 12 reconstrucciones por paso.
- El viento en las briznas es solo de shader (`WindResponse`, `Stiffness`, `PhaseVariation`, `TipLag`, la punta va con retraso).

### 1.5 Hojas y "ramas"
- **Viento en hojas, "pixel wind" (`FoliagePixelWindRenderer`):**
  - No mueve vértices. Crea una **máscara del tamaño de la atlas de bloques** donde marca qué píxeles son follaje, hoja o hierba (R/G/B), deduciéndolo de los modelos y *sprites* de los bloques configurados.
  - El *fragment shader* desplaza la **UV dentro del *sprite*** según el tiempo y la posición en el mundo. Las hojas "ondulan" píxel a píxel sin tocar la geometría.
- **Movimiento de bloques genérico (`ExperimentalBlockMotionRenderer`):**
  - Hasta 15 perfiles configurables por id de bloque (amplitud ≤ 0,16, velocidad, escala, aleatoriedad, solo caras superiores, distorsión de textura).
  - Se suben como `uniform vec4[16]`. El índice de perfil se añade al vértice desde mixins al *block renderer* (`ModelBlockRendererMixin` y `SodiumBlockRendererMixin`).
  - Añade una "falda" inferior para que no se vean huecos cuando el bloque se mueve.
- **"Ramas":** en el código **no hay ramas de árbol animadas**. `BRANCH_LITTER` son **ramitas en el suelo** (`InteractiveGroundLitterParticle.Kind.BRANCH`) que se apartan al pisarlas, igual que la hojarasca (`leaflitter`), las flores (`flowerlitter`), la lenteja de agua y los nenúfares.

---

## 2. Partículas propias y el editor de partículas

### 2.1 Partículas "de sistema"
- Se registran con `ParticleFactoryRegistry` y usan tipos propios (`BloomMod.*_PARTICLE`).
- Tienen *render layers* propios (`ShineParticleRenderLayers`) con shaders dedicados en `shaders/core/`:
  - `shine_particle_soft_alpha`,
  - `premultiplied_soft_add`,
  - `depth_soft_alpha`, que da **partículas suaves** porque se desvanecen al acercarse a la geometría,
  - `lava_*`, `nether_rays`, `amethyst_sparkle`, etc.
- En 1.21.11 las dibujan a mano: `ParticleEngine` → `QuadParticleRenderState` más un *buffer cache* propio con rotación (`ParticleBufferCacheAccessor.shine$rotate`), por familias (pájaros, gansos, mariposas, hojas, rayos de luz). Esta parte depende de clases que no existen en 1.20.1 (ver §6).
- Se ordenan por transparencia (`TransparencySorting`, `ParticleStorageSorting`).

### 2.2 Editor de partículas personalizadas (`customparticles/` + `CustomParticlesConfigScreen`)
**Formato de datos:**
- Todo está en `config/shine/custom_particles/definitions.json` (Gson, `schemaVersion 2`) y `…/textures/*.png`.
- Tres comportamientos:
  - `ambient_air`: flota alrededor del jugador,
  - `falling_from_blocks`: cae desde los bloques de la lista `blockIds` (hojas…),
  - `interactive_ground`: queda en el suelo y reacciona al pisarla.
- Campos: textura, `frameCount` / `frameTicks` (tira animada), tamaño ± variación, opacidad, *fade in/out* (`none` / `opacity` / `squish`), tinte RGB, velocidad de giro, densidad, tope, rango, vida, dirección en grados, velocidades horizontal y vertical, gravedad, `drift` y `interactionStrength`.
- Condiciones: dimensión, clima, hora, rango de Y, biomas y bloques.

**Editor (GUI propia, 868 líneas):**
- Lista a la izquierda con Añadir, Duplicar, Borrar e Importar. A la derecha, pestañas con botones y *sliders*, cada uno con descripción.
- Importa PNG con diálogo nativo y copia atómica (archivo temporal y luego `move`).
- **Exporta e importa `.shinepack`**, que es un ZIP con `manifest.json`, `definitions.json` y `textures/`. La importación se hace en una carpeta de *staging*, valida el manifiesto y hace *rollback* si algo falla.

**Runtime (`CustomParticleRuntime`):**
- No usa el `ParticleEngine`. Lleva **su propia lista de instancias** y las dibuja por lotes: un *mesh* por definición y textura, en `END_MAIN`, como *billboards* orientados a la cámara.
- Recarga las texturas en caliente si cambia el `lastModified` del PNG.
- **Idea buena y fácil de portar:** crear partículas desde datos sin compilar.

---

## 3. Shine Packs (`shinepack/ShinePackManager`)

Son **resource packs normales** con una carpeta `assets/<ns>/shine/`:

| Archivo | Esquema | Contenido |
|---|---|---|
| `shine/pack.json` | `shine-pack-v1` | `name`, `description`, `author`, `version` |
| `shine/birds/*.json` | `shine-bird-v1` | `frames{idleLeft, idleRight, checkingLeft, checkingRight, flyingLeft[≤16], flyingRight[≤16]}`, `weight`, `sizeMultiplier`, `flightAnimation` (`loop` / `ping_pong`), `hingedWingStyle` (`none` / `brown` / `black`), condiciones |
| `shine/flowers/*.json` | `shine-flower-v1` | `texture`, `weight`, `sizeMultiplier`, `tint`, condiciones (por defecto sobre `grass_block`) |

**Condiciones** (comunes a pájaros y flores):
- `biomes` / `excludeBiomes` (ids o `#tags`), `dimensions` / `excludeDimensions`,
- `blocks` / `excludeBlocks` (solo flores),
- `weather`: `any`, `clear`, `rain` o `thunder`,
- `time`: `any`, `day` o `night`.

**Cómo se carga:**
- Con un *reload listener* (`shine:shine_packs`). Se cosen todas las texturas en una **atlas propia** (`shine:textures/atlas/shine_pack_content.png`) de máximo 256 px por textura, 512 texturas y 64 Mpx.
- Límites: 128 packs, 64 pájaros y 128 flores.
- Si un pack falla, se guarda un `LoadIssue` para mostrarlo en la GUI.
- Los pájaros y flores que trae el mod se definen igual (JSON generado en código). Es decir, **el contenido del propio mod es un pack más**, un buen patrón a copiar como idea.

---

## 4. Lluvia por caras de bloque y charcos (`experimental/weather/`)

**Lluvia en las caras laterales (`BlockSideRainSpawner`):**
- Se engancha en **`ClientLevel.animateTick`** (mixin `LanternTorchAnimateTickMixin`), que ya elige al azar bloques cerca del jugador. No hace falta un escaneo propio.
- Si el bloque muestreado es aire sin fluido, mira sus 4 vecinos horizontales. Si el vecino está en la lista `blockSideRainBlocks` y en esa columna llueve (`isRainAt`, con `canSeeSky`), crea una partícula **exactamente sobre la cara**.
- Para acertar con bloques que no son cubos usa `VoxelShape.max(axis, …)` / `min(axis, …)` en una Y y Z aleatorias. Así funciona en escaleras, losas, vallas, etc. Desplaza la partícula 0,004 hacia fuera para evitar *z-fighting*.
- La normal de la cara viaja en `vx`/`vz` y en `vy` va un marcador de "lluvia ligera". La probabilidad es densidad × intensidad × 0,24, y la intensidad de lluvia sube ×(1 + 0,35·tormenta).

**Charcos (`RainPuddleSpawner` + `RainPuddleParticle`):**
- Son partículas planas y persistentes, no bloques ni texturas en el mundo.
- **Acumulador:** se suma `0,032·densidad·intensidad` por tick y se crea un charco por cada unidad, con un máximo de 8 por tick y un tope de 2500.
- **Después de la lluvia** siguen apareciendo un rato (`rainPuddleAfterRainDensity` y `postRainSpawnLevel`) y luego se secan (*fade*).
- **Dónde:** el centro de búsqueda **se adelanta en la dirección del jugador** (hasta un 28 % del rango), así los charcos aparecen donde vas a mirar.
- **Validación de la huella:** comprueba 9 puntos (centro, 4 bordes a 0,92·r y 4 diagonales a 0,65·r). Todos deben ser aire sobre un bloque sólido y a cielo abierto. Así el charco no queda colgando de un borde.
- Otras piezas de lluvia: `CustomRainSpawner` / `CustomRainParticle` / `CustomRainSplashParticle` (sustituyen la lluvia vanilla con `LevelRendererCustomRainMixin`), `LightRainController` (llovizna sin lluvia vanilla, por bioma), `LightRainBounceArcRenderer` (arcos de rebote) y `LightningSkyFlashController`.

---

## 5. Post-proceso

Hay dos cadenas: **Bloom** (`client/render/`) y **Experimental** (`experimental/render/ExperimentalPostProcessor`, 3981 líneas, con un único shader `post/experimental.fsh` de 1923 líneas).

### 5.1 Bloom "de verdad" (fuentes marcadas, no solo por umbral)
1. **Fuente separada:**
   - El shader de terreno de Sodium se parchea para escribir en **MRT**: `layout(location=1) out vec4 bloomColor` y `location=2` para la máscara de *rim light*.
   - La intensidad por bloque viaja en bits de material (`shine_decode_source_strength`).
   - Las entidades y partículas se redibujan en ese *target* con `BloomSourceRenderer`, que usa máscaras de emisión por textura (`BloomMaskAtlas`, `BloomEntityMaskTextures`).
2. **Oclusión:** se captura la profundidad del terreno (`BEFORE_ENTITIES`) y de los oclusores (`BEFORE_TRANSLUCENT`) para que el bloom no atraviese paredes.
3. **Cadena de mips:**
   - `extract` (umbral, *soft knee*, *clamp* de brillos y límite por distancia),
   - `downsample` en N niveles (según el radio),
   - blur separable horizontal y vertical, con pasadas extra opcionales,
   - `composite` con **pesos por nivel** calculados en Java (`computeLevelWeights(radius)`).
   - Tiene "perfiles" de radio distintos y una ruta combinada a baja resolución si caben.
4. **PostChain por código:** el `PostChain` se construye **por código** (`class_9962` = `PostChainConfig` en 1.21) según la resolución y los niveles, en vez de un JSON fijo. Se cachea y se reconstruye solo si cambia algo.

### 5.2 God rays (dos versiones)
**Barato, en pantalla (`computeGodRays` en `experimental.fsh`):**
- Es el clásico *radial blur* hacia la posición del sol o la luna en pantalla: N muestras (≤ MAX), `decay`, `weight`, `exposure` y `density`.
- Solo acumula donde la profundidad es cielo (`depth ≥ 0.999999`), multiplicado por la forma del disco solar.
- Usa *jitter* con *interleaved gradient noise* y una máscara que se atenúa en los bordes de la pantalla.
- El sol y la luna se mezclan según el ángulo (`getLightWeights`). Se corta con lluvia (`GodRaysRainCutoff`).

**Caro, "sky volumetric" (`post/sky_volumetric_*.fsh`, 5 pasadas):**
- `depth` → `aperture` (dónde se ve el cielo a través de hojas o huecos) → `render`, que hace *ray-march* con mapas `LightShadow` y `LightAperture` (cerca y lejos), `AirDensity`, `ShaftContrast`, `VolumeDistance`…
- Luego `temporal`, que acumula con el historial para bajar el ruido, y `composite`.
- Es lo que da los haces que entran entre las copas de los árboles.

### 5.3 DoF (`applyDepthOfField`)
- **Enfoque:** en automático usa `DepthOfFieldHitFocusDistance` (Java lanza un rayo desde la cámara y pasa la distancia del impacto). Si no hay impacto, usa la profundidad del centro de la pantalla. También admite distancia fija.
- **Radio de desenfoque:** `smoothstep(0, rango, |dist - foco|) · maxBlur · intensidad`. Delante del foco se multiplica ×0,65 para que no emborrone tanto las manos y lo cercano.
- **Muestreo:** disco con **ángulo áureo** (*Vogel spiral*), rotado por píxel con ruido, con radio `sqrt(i/N)`. Cada muestra pesa menos si su profundidad difiere mucho de la del centro (*depth-aware*), lo que evita el "halo" en los bordes.
- **El mismo shader hace además:**
  - *motion blur*, viñeta, ruido de cámara, desaturación con poca luz,
  - gradación de color (lift/gain/warmth/vibrance/rueda tonal),
  - niebla de suelo y niebla por bioma,
  - distorsión de calor, refracción de frío, *afterimage* (12 ecos),
  - *lens flare* por *sprites*,
  - paleta adaptativa con *dithering* Bayer y Floyd-Steinberg.
  Todo va con `uniform` en bloques `std140` y se activa por flags.

---

## 6. ¿Qué se puede portar a un mod propio en Forge 1.20.1?

**Contexto del cliente WIP:**
- Forge 47.4.16 con **Embeddium 0.3.31** (fork de Sodium) y **Oculus 1.8.0**. Sinytra Connector está presente.
- Ya portado en `desarrollo/shine-port/forge-port` (shineport 1.1.0): filtro paso bajo del audio, burbujas en cofres, *camera shake*, transición de niebla, destello de rayo, salpicaduras de agua, humo de antorchas y luces ambientales.

**Diferencia clave:**
- 1.21.11 usa el nuevo `RenderPipeline` / `GpuDevice` / `RenderPass` y *render states* (`QuadParticleRenderState`, `SubmitNodeCollection`…).
- 1.20.1 usa `ShaderInstance` + `RenderSystem` + `BufferBuilder` / `Tesselator` y `RenderLevelStageEvent`.
- **La lógica (CPU) se porta casi igual; el render hay que reescribirlo.**

| Técnica | Portabilidad | Cómo en Forge 1.20.1 |
|---|---|---|
| Spawners (seeding + acumulador + tope + perfil por bioma) | **Alta** | `TickEvent.ClientTickEvent` y `level.addParticle`. Lógica pura. |
| Pájaros y mariposas como partículas con máquina de estados | **Alta** | `TextureSheetParticle` + `RegisterParticleProvidersEvent`. Si el pájaro es un *sprite* animado, no hace falta ningún render propio. |
| Alas con bisagra (malla 3 quads, base derecha/arriba/adelante) | **Alta** | `RenderLevelStageEvent` en `AFTER_PARTICLES`, `Tesselator` `POSITION_TEX_COLOR` y un `RenderType` translúcido sin *cull*. Las matemáticas son trigonometría básica y se escriben de nuevo. |
| Muelles amortiguados (alas, *bank*, *pitch*, hierba) | **Alta** | Son 3 líneas de física. Se reimplementan. |
| Lluvia en caras de bloque | **Alta** | Mixin a `ClientLevel.animateTick` (sí existe en 1.20.1, ver mapa) + `VoxelShape.max/min` + partícula propia. **Buen primer efecto nuevo.** |
| Charcos (partículas planas + huella de 9 puntos + adelanto) | **Alta** | Partícula horizontal (quad en XZ) con `ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT`. |
| Editor de partículas personalizadas (JSON + PNG + GUI) | **Media-alta** | Gson + `DynamicTexture` + `Screen` propio. Es mucho trabajo de GUI, pero sin riesgo técnico. El formato `.shinepack` (ZIP) es una idea libre. |
| Shine Packs (contenido desde resource packs + atlas propia) | **Media** | `RegisterClientReloadListenersEvent` + `SpriteLoader` / `TextureAtlas` propia, o en 1.20.1 más fácil con `TextureStitchEvent` / *atlas source* JSON. |
| Hierba que se dobla (textura de campo + shader) | **Media-baja** | Posible con Embeddium: su `me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader.getShaderSource` existe y los archivos `sodium:blocks/block_layer_opaque.vsh/.fsh` también. Se puede hacer la misma inyección por `String.replace` con un mixin `@Inject(RETURN)`. **Pero con un shaderpack de Oculus activo esos shaders no se usan** y el efecto desaparece. También hay que marcar los vértices de planta (alfa del color) con un mixin al *block renderer* de Embeddium. |
| Briznas 3D generadas | **Media-baja** | No hay FRAPI en Forge. Alternativas: la API `org.embeddedt.embeddium.api.ChunkMeshEvent` / `MeshAppender` de Embeddium 0.3.31 (añadir quads a la sección) o un `BakedModel` *wrapper* con `ModelEvent.ModifyBakingResult`. El coste en polígonos obliga a hacer LOD. |
| Hojas con "pixel wind" (máscara de atlas + desplazamiento de UV) | **Media-baja** | Mismo problema del shader de terreno que la hierba. La máscara de atlas es fácil; lo difícil es la inyección. |
| Bloom por fuentes (MRT desde el terreno) | **Baja** | Requiere parchear el *framebuffer* de Embeddium para MRT y todo el *pipeline*. Con Oculus choca. Mejor un bloom por umbral con `PostChain` JSON (1.20.1 lo soporta) o dejarlo al shaderpack. |
| God rays en pantalla (*radial blur* con máscara de cielo) | **Media** | `PostChain` propio con el *depth* del `mainRenderTarget`. En 1.20.1 hay que copiar la profundidad a un *target* auxiliar. Es un algoritmo clásico (GPU Gems 3) y se escribe desde cero. |
| God rays volumétricos (*ray-march* + temporal) | **Baja** | Mucho trabajo. No compensa si ya se usa un shaderpack. |
| DoF (disco áureo + rechazo por profundidad + autoenfoque por raycast) | **Media** | Igual que los god rays: `PostChain` + profundidad + un `uniform` desde Java (`Minecraft.hitResult` para la distancia de foco). Técnica estándar. |
| Partículas con *buffer cache* propio (1.21.11) | **No** | `class_11977` / `class_11944` no existen en 1.20.1. Se usa `ParticleRenderType` o render manual en `RenderLevelStageEvent`. |

### Orden recomendado para el port
1. Lluvia en caras de bloque.
2. Charcos.
3. Mariposas con bisagra.
4. Pájaros (*sprite* + máquina de estados, y después alas con bisagra).
5. Editor de partículas por JSON.
6. Si hace falta más: god rays y DoF en pantalla como `PostChain` opcional, desactivado automáticamente si Oculus tiene un shaderpack cargado.

Hierba, pixel wind y bloom MRT solo si aceptamos que **no funcionarán con shaderpack** (Oculus). En ese caso conviene detectarlo con `IrisApi.getInstance().isShaderPackInUse()` y apagarlos.

### Ideas de diseño que vale la pena imitar (son ideas, no código)
- **Seeding + acumulador:** poblar rápido al llegar y mantener luego con probabilidades bajas.
- **Datos de la partícula en `vx/vy/vz`**, para evitar tipos de partícula con `ParticleOptions` complejos.
- **Doblado de hierba en una textura con el tick actual y el anterior**, e interpolación en la GPU: cero reconstrucción de chunks.
- **Metadatos en el alfa del color de vértice** para que el shader distinga tipos de geometría.
- **El contenido del mod como un pack más:** el mismo cargador sirve para lo oficial y para lo de la comunidad.
- **API de interop con `allowsAt(feature, level, pos)`** para que otros mods o servidores apaguen efectos por zona.
