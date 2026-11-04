# Sello de energía de wipspowers (1.7.0+) — cómo ajustarlo

Código: `src/main/java/wips/powers/client/SigilRenderer.java` (NeoForge 1.21.1). Todos los valores están arriba del archivo, en el bloque `AJUSTES`. Después de cambiarlos: `./gradlew build` y copiar el jar.

## Qué hace

Cada técnica (las 5 de Seiryuu e Island Shaker) dibuja:
- un **anillo de energía** bajo los pies (sin símbolos ni runas desde la 1.8.0),
- un **anillo interior** que gira al revés,
- un **halo giroscópico** a la altura del pecho (no sale en tu propia primera persona),
- una **estela de partículas** que sale del borde y sube en espiral.

Todo se calcula con `t = ticks + partialTick`, así que es fluido a cualquier FPS. Se dibuja con `RenderType.eyes` (mezcla aditiva): brilla sobre cualquier fondo y no depende de la luz del mundo.

## Los 4 efectos y sus valores

| Efecto | Constante | Qué regula | Más alto = |
|---|---|---|---|
| **1. Easing** (aparición) | `APPEAR_TICKS` (9) | duración del "pop" con `easeOutBack` | aparece más lento |
| | `APPEAR_OVERSHOOT` (1.7) | rebote al aparecer (0 = sin rebote) | rebota más |
| | `FADE_TICKS` (12) | desvanecido final con `smoothstep` | se apaga más suave |
| | `RELEASE_TICKS` (8), `RELEASE_PUNCH` (0.45) | estallido hacia afuera al soltar (`easeOutCubic`) | estallido más largo / más grande |
| | `SPIN_RAMP` (2.5), `RAMP_TICKS` (30) | aceleración cúbica del giro mientras cargas | gira más rápido al final de la carga |
| **2. Respiración y latido** | `BREATH_AMP` (0.045), `BREATH_SPEED` (0.21) | escala que sube y baja | respira más / más rápido |
| | `STRETCH` (0.035) | estira en X mientras encoge en Z (orgánico) | más deformación |
| | `BEAT_PERIOD` (22), `BEAT_AMP` (0.07), `BEAT_GLOW` (0.25) | latido doble "lub-dub": cada cuántos ticks, cuánto crece y cuánto brilla | latidos más espaciados / más fuertes |
| | `HOVER_AMP` (0.05), `HOVER_SPEED` (0.13) | flotación vertical del sello | flota más |
| **3. Brillo y partículas** | `ALPHA` (0.9) | intensidad del brillo (aditivo) | más luminoso |
| | `SPARKS_PER_TICK` (3) | partículas por tick en el borde | estela más densa |
| | colores y partícula por técnica | método `style(...)`: color exterior, interior, tipo de partícula y radio | — |
| **4. Rotación compuesta** | `SPIN_Y` (3.0) | giro principal, grados por tick | gira más rápido |
| | `WOBBLE_X` / `WOBBLE_Z` (6 / 4.5) y sus `_SPEED` | oscilación como flotando en un campo de energía (grados, rad/tick) | se mece más / más rápido |
| | `HALO_TILT` (68), `HALO_PRECESS` (4.5), `HALO_SCALE` (0.42) | inclinación, precesión y tamaño del halo del pecho | halo más acostado / gira más / más grande |

Consejo: cambia un valor a la vez, de a poco (±20%), y compáralo con las capturas automáticas (`./gradlew runFx`).
