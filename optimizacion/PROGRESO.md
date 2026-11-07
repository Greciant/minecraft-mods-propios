# Optimización de WIPSS2: registro de progreso

## Hecho
- [x] Pack completo armado (217 mods; BlockBand queda fuera por peso). Los 47 jars que mandó el usuario, verificados por SHA-1.
- [x] Servidor de pruebas idéntico (NeoForge 21.1.264, Java 21, 4 núcleos, heap de 8 GB) con 170 mods de servidor.
- [x] **Mods que tumban un servidor dedicado:** Sandevistan (sandeviscraft 1.1.1) e Insight Chests 1.0.5. En un solo jugador no afecta.
- [x] Arnés de medición: `tools/bench.py`, `serie.sh`, `resumen.py`, `gcstats.py`, `jfrtop.py`, `jfrmods.py` y `jfrcaller.py`.
- [x] Línea base B0 (3 repeticiones).
- [x] Cuello de botella: 27 % del tick en búsquedas de POI (aldeanos con AcquirePoi y mascotas de Chiikawa).
- [x] Lithium 0.15.4: **MSPT −28 %, p95 −32 %, generación −5 %**. Se agrega.
- [x] Solapamiento de ModernFix y Lithium en la caché de temperatura de biomas: queda a cargo de ModernFix (`lithium.properties`).
- [x] Noisium: sin mejora medible (−0,8 %, dentro del ruido). Descartado.
- [x] Reparto del tick con Lithium: magos ~2 %, Legendary Monsters ~1,5 %, wipspowers 0 %. No se toca su IA.
- [x] Bug de Legendary Monsters: la Phantom Dagger con ítem vacío falla en cada autoguardado. Arreglo en wipspowers (`ArrowEmptyStackSaveMixin`).
- [x] `WipsTerrain`: cola ordenada por tiempo en lugar de recorrer todos los bloques pendientes en cada tick.
- [x] Cliente: Iris con sombras a 32 chunks y render a 12, y Particle Core con tick asíncrono a la vez que AsyncParticles.
  - Script de perfiles: `cliente/aplicar-perfil.ps1`.
  - Protocolo de FPS y planilla: `cliente/PROTOCOLO-FPS.md` y `cliente/planilla-fps.csv`.
- [x] Texturas: wipspowers ocupa 20 MB, sin duplicados ni archivos sin uso, y se cargan bajo demanda.

- [x] Series de GC y heap: G0, G1 (G1 ajustado, el elegido), Z1 (ZGC, opción B) y H6 (6 GB, igual que 8 GB).
- [x] wipspowers 3.4.0 compilado, `runAudit` OK y PNG recomprimidos sin pérdida (20,07 → 15,99 MB, píxeles idénticos).
- [x] Argumentos de JVM: cliente con 8 GB y G1 ajustado (`cliente/JVM-CURSEFORGE.md`); servidor con 6 GB y G1 ajustado (`servidor/user_jvm_args.txt`).
- [x] Validación final A/F en la misma sesión: MSPT −13 %, p95 −18 %, pausa de GC máx. −66 %, heap tras generar 2,16 → 1,25 GB y 0 errores de la daga. Detalle en `INFORME.md`.

## Pendiente (en el PC del usuario)
- [ ] Medir FPS con `cliente/PROTOCOLO-FPS.md` y anotarlos en `cliente/planilla-fps.csv`.
- [ ] Opcional: mandar `config/` y `options.txt` para ajustar los perfiles a su configuración exacta.
