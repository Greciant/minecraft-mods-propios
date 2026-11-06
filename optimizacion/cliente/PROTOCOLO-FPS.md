# Cómo medir los FPS en el PC (antes y después)

Los FPS solo se pueden medir en tu PC, porque el entorno de pruebas no tiene GPU. Para que el antes y el después se puedan comparar, todo tiene que ser igual en las dos mediciones: el mismo mundo, el mismo lugar, la misma hora del juego, el mismo perfil, el mismo shader y el mismo cargador enchufado.

## Herramienta
**CapFrameX** (gratis, https://www.capframex.com) mide el FPS medio, el 1 % low y el 0,1 % low, y guarda el tiempo de cada frame. Instálalo una vez.

En CapFrameX, ve a *Capture* → *Hotkey*: **F11** y duración de **60 s**. Ten el overlay apagado mientras mides.

## Preparación (una sola vez)
1. Crea un mundo de pruebas con la semilla `wipss2bench`, en creativo y con trucos.
2. Teletransporta a `/tp 0 120 0`, pon `/time set 6000`, `/weather clear` y `/gamerule doDaylightCycle false`.
3. Guarda y sal. Haz una copia de la carpeta del mundo: todas las mediciones parten de esa copia.

## Escenarios (60 s cada uno, 3 veces, se anota la mediana)
| # | Escenario | Qué hacer durante los 60 s |
|---|---|---|
| 1 | Exploración | Volar en línea recta hacia +X a velocidad de vuelo normal (genera terreno de Tectonic). |
| 2 | Quieto con vista lejana | Quedarse en `/tp 0 160 0` mirando al horizonte, sin moverse. |
| 3 | Combate | `/execute positioned ~10 ~ ~ run summon awakened:gamma_mage` ×6 + 6 mobs de Legendary Monsters; pelear con tu poder. |
| 4 | Habilidades grandes | Con Scaler en World Breaker: Stomp ×3, Mass Leap ×2. Después Graviton: Chibaku Tensei. |
| 5 | Sesión larga | Jugar 30 min normal. Anotar la RAM en el F3 al principio y al final (para ver fugas). |

## Qué anotar
`planilla-fps.csv`: escenario, perfil, versión (antes o después), FPS medio, 1 % low, 0,1 % low, RAM del F3 (en uso / asignada) y uso de GPU (Administrador de tareas → Rendimiento).

## Perfiles
Con el juego cerrado, desde la carpeta `optimizacion\cliente`:
```
powershell -ExecutionPolicy Bypass -File .\aplicar-perfil.ps1 -Instancia "RUTA\A\LA\INSTANCIA" -Perfil Equilibrado
```
El script guarda primero una copia de lo que cambia, en `respaldo-perfil-<fecha>` dentro de la instancia.
