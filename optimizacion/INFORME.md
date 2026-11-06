# Optimización de WIPSS2: informe

Pack: WIPSS2, NeoForge 21.1.264, Minecraft 1.21.1. Son 218 mods; las pruebas usan los 217 de tu instancia, sin BlockBand.
Todos los jars son los de tu instancia (verificados por SHA-1), con una excepción: Iron's Spells se bajó de Modrinth y su SHA-1 no coincide con tu jar (es la misma versión, 3.16.3, pero otra compilación).

## Qué se midió y dónde
- **Aquí (servidor dedicado de prueba):** CPU de 4 núcleos, Java 21, heap de 8 GB salvo que se indique otra cosa, mundo nuevo con semilla `wipss2bench`. En el servidor corren 170 mods; los 47 restantes son solo de cliente.
  Cada configuración se midió **3 veces** y se informa la **mediana**. El arnés está en `wipss2-pack/tools` (no está en el repo): `bench.py`, `serie.sh` y `resumen.py`.
  1. **Arranque:** segundos hasta "Done".
  2. **Generación:** Chunky, radio de 512 bloques (4.225 chunks), con Tectonic.
  3. **Combate:** área forzada con 24 magos (2 de cada poder), 30 mobs de Legendary Monsters, 20 sniffers y 12 gólems de hierro peleando, unas 130 entidades. Durante 90 s se miden el MSPT (milisegundos por tick: menos es mejor, y 50 es el límite de 20 TPS) y el perfil de CPU con Java Flight Recorder.
  4. **Memoria:** heap ocupado tras un GC completo forzado, y pausas de GC naturales (sin contar las forzadas por la medición).
- **En tu PC (FPS):** aquí no hay GPU, así que los FPS se miden allá con `cliente/PROTOCOLO-FPS.md`.
  **Todavía no hay cifras de FPS y este informe no inventa ninguna.**

## Resultados medidos (servidor)
| Configuración | Arranque | Generación (4.225 chunks) | MSPT medio | MSPT p95 | Peor tick | Pausa de GC máx. |
|---|---|---|---|---|---|---|
| B0: pack original | 14,7 s | 186 s (22,7 chunks/s) | 5,3 ms | 11,1 ms | 113 ms | (sin medir) |
| L1: + Lithium | 15,3 s | 178 s (23,8 chunks/s) | **3,8 ms** | **7,6 ms** | 85 ms | (sin medir) |
| G0: + Lithium, G1 por defecto (con log de GC) | 14,9 s | 180 s | 4,4 ms | 7,2 ms | 135 ms | 165 ms |
| G1: + Lithium, G1 ajustado | 14,3 s | 178 s | **3,8 ms** | **5,8 ms** | **80 ms** | **101 ms** |

**Aviso:** entre una serie y otra el contenedor de pruebas se reinició y quedó ~2,3 veces más lento. Lo confirmó la serie de control C1, la misma configuración que G1. Por eso Z1 y H6 solo se comparan contra C1, que corrió en esas mismas condiciones:

| Configuración (máquina lenta) | Arranque | Generación | MSPT medio | MSPT p95 | Peor tick |
|---|---|---|---|---|---|
| C1: control, igual que G1 | 30,8 s | 412 s | 11,4 ms | 21,3 ms | 79 ms |
| Z1: ZGC generacional | 33,6 s | 404 s | 11,9 ms | 18,3 ms | **46 ms** |
| H6: G1 ajustado, 6 GB | 31,4 s | 414 s | 12,0 ms | 19,5 ms | 115 ms |

Conclusión: 6 GB rinden igual que 8 GB en el servidor. ZGC iguala a G1 ajustado en promedio y mejora el peor tick, pero gasta más memoria fuera del heap. Por eso queda G1 ajustado como opción principal y ZGC como opción B para probar en el PC.

**Mods evaluados y descartados por no dar mejora medible:** Noisium (generación 177,6 → 176,1 s, −0,8 %, dentro del ruido).

## Qué consume el servidor (perfil de CPU)
- **Sin Lithium**, el 27 % del tick se iba en búsquedas de POI (puntos de interés). Los responsables:
  - los aldeanos, buscando cama o estación de trabajo;
  - el sensor `PetPlacesSensor` de las mascotas de Chiikawa, con un `findClosest` en cada tick.
- **Con Lithium** desaparece. Lo que más cuesta pasa a ser el pathfinding y las colisiones de vanilla.
- **Reparto con Lithium:**
  - Magos de Awakened: ~1,9 % del tick.
  - Legendary Monsters: 1–1,7 %.
  - Chiikawa: 1,6–2,8 %.
  - wipspowers: 0 %.

  Por eso **no se tocó la IA de los magos ni de los mobs**: ganaría como mucho ~2 % y cambiaría su comportamiento.

## Problemas de estabilidad encontrados
1. **Sandevistan (sandeviscraft 1.1.1)** tumba cualquier servidor dedicado al arrancar: su mixin de `LivingEntity` carga clases que solo existen en el cliente. En un solo jugador o LAN no pasa. Si alguna vez abres un servidor dedicado, ese jar va solo en el cliente.
2. **Insight Chests 1.0.5**: el mismo caso. Solo cliente.
3. **Legendary Monsters, Phantom Dagger**: en cada autoguardado da "Cannot encode empty ItemStack" y la daga no se guarda. **Arreglado en wipspowers 3.4.0** (`ArrowEmptyStackSaveMixin`): cuando el ítem está vacío se guarda un compuesto vacío y al cargar vuelve el ítem por defecto.
4. **ModernFix y Lithium** reescribían los dos la caché de temperatura de biomas, con un conflicto de *overwrite* en el log. Ahora queda a cargo de ModernFix (`config/lithium.properties`).

## Cliente (gráficos): hallazgos y cambios
- **Shaders**: Iris tiene activo Toonlamentry con `maxShadowRenderDistance=32`, mientras el render distance es 12. Las sombras más allá de lo visible igual se dibujan, y con shaders suele ser el mayor costo de GPU.
  Los perfiles la dejan en 16, 12 u 8 chunks. Con render 12, el perfil Equilibrado (12) cubre todo lo que se ve.
- **Partículas**: Particle Core tenía `asynchronousTicking=true` y AsyncParticles hace lo mismo, así que había dos mods procesando las partículas en paralelo.
  Ahora solo lo hace AsyncParticles. La culling y el límite de Particle Core siguen activos, y no se quita ninguna partícula.
- **Opciones**: hoy tienes partículas en "mínimas", y eso oculta parte de los VFX de los poderes.
  Calidad las deja en "todas", y Equilibrado y Rendimiento en "reducidas": los tres muestran más que ahora.
- **Texturas**: no se tocó ninguna. Las más pesadas del pack son:
  - las hojas de animación de wipspowers (20 MB, 49 archivos, sin duplicados ni archivos sin uso, cargadas bajo demanda);
  - el atlas de VFX de Grand Line (4096×4096);
  - los skyboxes de FDBosses (4096×8192).
