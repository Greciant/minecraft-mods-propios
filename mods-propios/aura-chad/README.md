# Aura Chad (NeoForge 1.21.1)

Modo chad con una tecla (mouse 5 por defecto): aura azul, poses de gym de un video de referencia y musica.
Las poses se reproducen con **Player Animation Library** (PAL, ZigyTheBird) si esta instalada; si no, con un Mixin de respaldo.

## Poses: dos caminos

| | Con PAL (1.1.6+) | Sin PAL |
|---|---|---|
| Animacion | `assets/aurachad/player_animations/chad.json` (formato GeckoLib/Bedrock de PAL) | `assets/aurachad/animations/chad.animation.json` (Bedrock, leido por `ChadPoses`) |
| Quien posa | capa PAL `aurachad:chad`, prioridad **1600** | `PlayerModelMixin` (TAIL de `PlayerModel.setupAnim`) |
| Codigo | `client/pal/ChadPal` (unica clase que importa PAL) + `ChadPalBridge` (sin imports de PAL) | `client/ChadPoses` |

PAL es dependencia **opcional** (`type="optional"` en `neoforge.mods.toml`, `compileOnly` en Gradle). `ChadPalBridge` comprueba
`ModList` y, si algo falla al registrar o reproducir, cae al Mixin. Camara, giro del cuerpo y zoom siguen saliendo de
`chad.animation.json` (huesos `chad_root` y `chad_camera`), que no son huesos del jugador.

Origen de las poses: `tools/gen_bbmodel.py` genera las dos animaciones y los dos modelos de Blockbench
(`ref/blockbench/chad_poses.bbmodel` para el camino Mixin, `ref/blockbench/chad_pal.bbmodel` con la rig de PAL).
Se edita en Blockbench (plugin *GeckoLib Animation Utils*) y se exporta encima de `player_animations/chad.json`.
En el archivo de PAL los brazos y piernas llevan el Z con el signo opuesto al del `zRot` de Java (la tabla `PAL_SIGN` del generador).

### Codos y rodillas ("bend")
PAL 1.1.6 no dibuja el canal `bend` en las partes vanilla (lo calcula pero su `PlayerModelMixin` solo copia rotaciones). Por eso el bend va aparte,
con **bendy-lib** (KosmX, viene dentro de Emotecraft), que es dependencia **opcional** (`compileOnly`, `type="optional"`):

* `assets/aurachad/chad_bend.json` (generado por `tools/gen_bbmodel.py`): por hueso (`right_arm`, `left_arm`, `right_leg`, `left_leg`) claves de tiempo con
  `bend` (grados), `axis` (grados, alrededor del brazo) y, en brazos, `target` = direccion deseada del antebrazo (afuera, arriba, frente). El generador
  resuelve el eje para que el antebrazo se acerque al objetivo. El mismo `bend` se exporta tambien al canal `bend` de `player_animations/chad.json` (PAL lo ignora al dibujar).
* `client/bend/ChadBend` (unica clase que importa bendy-lib) registra un mutator propio `aurachad_bend` (BendableCuboid, `Direction.UP`) en el primer cubo de
  brazos, piernas, mangas y pantalones, y lo activa con `applyBend(eje, bend)` desde el Mixin de `PlayerModel.setupAnim` **solo mientras la entidad esta en modo chad**.
  Al terminar lo desactiva (solo el suyo; el mutator `bend` de playerAnimator no se toca). No depende de PAL ni de las capas: la prioridad 1600 y la 1550 de Haki quedan igual.
* `ChadBendBridge` comprueba `ModList` (`bendylib`) y captura cualquier fallo: sin bendy-lib o si algo falla, extremidades rectas, sin crash.
* **EMF + resourcepack FA+Player**: EMF sustituye las partes por `EMFModelPartVanilla` sin cubos, asi que bendy-lib no tendria nada que doblar. Por eso
  `client/emf/ChadEmfBridge` (reflexion, sin dependencia de compilacion) registra en la API publica de EMF una condicion
  (`EMFAnimationApi.registerVanillaModelCondition`) que fuerza el modelo vanilla **solo para los jugadores con el modo chad activo** (`ChadApi.isChadActive`).
  EMF la consulta en cada render, asi que vale tambien para quien ve a otro jugador en modo chad, y se suelta sola al terminar el modo, al desconectar o al
  morir/desaparecer la entidad. Los demas jugadores (p. ej. el que tiene Haki) conservan el modelo de EMF/Fresh Animations. Sin EMF no hace nada; si falla el registro, brazos rectos y sin crash.
* El autotest mide la geometria doblada real (`ChadBendBridge.measure`) y la compara con los `target`: lineas `BEND t=...` en `aurachad_autotest.txt`.

## Golpes: cortes secos y estallido de aura

El cambio entre poses es brusco: entre golpes la pose queda quieta y en cada golpe salta de una vez. La tabla unica `HITS` de `tools/gen_bbmodel.py`
(t, pose, codos D/I, objetivos, camara, fuerza; detalle en `ref/golpes-musica.md`) genera `chad.json` (PAL), `chad.animation.json` (Mixin, giro y camara),
`chad_bend.json`, los `.bbmodel` y `chad_hits.json`. Los saltos son claves `{"pre","post"}` de Bedrock: PAL 1.1.6 las lee nativas (guarda el "pre" 1 ms antes
del "post"); en los `.bbmodel` son claves con dos puntos e interpolacion `step`. Lo unico continuo es el acercamiento de camara 5,13 -> 7,6 s y la caminata final (desde 17,91 s).
La capa PAL arranca un tick despues de la activacion para compartir reloj con `ChadClient.ageSeconds`.

En cada golpe `ChadClient` lanza, para cada jugador en modo chad que sigue el cliente (sin paquetes nuevos), un estallido proporcional a la fuerza (`ChadBurst`):
explosion radial 60 x fuerza, anillo de onda en el suelo 36 x fuerza y chorro hacia arriba 20 x fuerza, con la particula `aurachad:burst` (8-16 ticks, friccion 0,8,
azul con nucleos casi blancos, `level.addParticle`, asi que respeta la opcion de particulas). El contorno neon de `ChadAuraLayer` pulsa 0,3 s en cada golpe.

## Capas y prioridad (para convivir con Haki u otros mods)

PAL ordena las capas por prioridad; la de mayor prioridad se aplica al final y pisa los ejes que anima.
Guia de PAL: 0-10 poses base, 1000 emotes, 1500+ gameplay.

* `aurachad:chad` -> **1600**. La animacion define *todos* los ejes de rotacion de `head`, `torso`, `right_arm`, `left_arm`,
  `right_leg` y `left_leg`, asi que mientras dura el modo chad ninguna capa de menor prioridad se mezcla con ella.
* Otro mod (Haki) deberia registrar su capa con prioridad **menor**, por ejemplo **1550**, y reproducirla solo en sus jugadores:

```java
// NeoForge: dentro de FMLClientSetupEvent.enqueueWork (PAL lo exige)
PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
    ResourceLocation.fromNamespaceAndPath("haki", "pose"), 1550,
    player -> new PlayerAnimationController(player, (controller, state, setter) -> PlayState.STOP));

// al empezar / terminar la pose
var layer = (PlayerAnimationController) PlayerAnimationAccess.getPlayerAnimationLayer(player, HAKI_LAYER);
layer.triggerAnimation(HAKI_ANIM);          // ResourceLocation = nombre dentro de assets/haki/player_animations/*.json
layer.stopTriggeredAnimation();             // o replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, EasingType.EASE_IN_OUT_SINE), HAKI_ANIM)
```

* Si Haki sigue con su Mixin: llamar `com.aurachad.client.ChadApi.isChadActive(entity)` (clase de cliente, cargar por reflexion
  o con `ModList.isLoaded("aurachad")`) y no aplicar la pose cuando sea `true`.
* Cada capa es **por jugador** (un controlador por `AbstractClientPlayer`), y el estado de chad se guarda por id de entidad: el que usa
  una habilidad no recibe la pose de la otra.

## Compatibilidad con EMF / Fresh Animations: Player Extension

EMF corre despues de `setupAnim` y deja el modelo con su propia pose (resourcepack FA+Player), pisando tanto el Mixin como la capa PAL.
Con **Entity Player Compat** (neoforge 1.21.1, 2.2.3) EMF se pausa en los huesos que anima PAL/PlayerAnimator y las poses se ven.
Ver `ref/entregas/compat/`.

## Pruebas

`./gradlew runAutotest` (sin PAL, Mixin) - `runAutotestPal` (solo PAL en `run-pal/mods`) - `runAutotestBend` (PAL + bendy-lib en `run-bend/mods`, sin EMF; aqui se ven y miden los codos) - `runAutotestCompat` (pila de WIPSS2: tambien mide los codos y comprueba el modelo de EMF antes/durante/despues en
`run-compat/mods` + packs en `run-compat/resourcepacks`). Todas con `JAVA_HOME` de Java 21. Con `-PhakiMode=same|split` prueban a Haki:
`same` = la misma entidad con chad y haki; `split` = el jugador con haki y un `RemotePlayer` con chad. El autotest mide cada corte (t-0,03 pose anterior, t+0,03 pose nueva, menos de 1 grado), los antebrazos en las mesetas y un estallido por golpe en su tiempo (`CORTE`, `BEND`, `BURST`, `RESUMEN`). `tools/montar_entregas.py` arma `comparacion.png` y el preview con musica. Si existe `aurachad_frames` en la carpeta de juego
el autotest guarda un fotograma por tick para armar el video con ffmpeg.
