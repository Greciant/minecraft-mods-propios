# Mapa rápido del proyecto (léelo antes de explorar)

Modpack Forge 1.20.1 "arquitecto" (Forge 47.4.16 + Sinytra Connector, ~290 mods) con servidor propio.
Índice de carpetas detallado: `LEEME.txt` (de 2026-09-03; lo de abajo manda si discrepa).

## Dónde está cada cosa
| Qué | Ruta |
|---|---|
| Mods canónicos del cliente | `~/Minecraft/mods/pool-1.20.1` (`_retirados/` = descartados) |
| Modpack para instalar | `~/Sync/Compartida/1-MODPACK/1.20.1/mods` (= pool menos maps3d, que va por 7-MAPAS) |
| Servidor real | `~/Minecraft/servidores/wip-1.20.1-ACTIVO` (puerto 25567, `./start-consola.sh`, `./cmd.sh stop`, Java 21) |
| Cliente viejo, NO comparar | `~/Minecraft/clientes/wip-1.20.1-ACTIVO` (copia de 2026-08-20) |
| Ports propios | `~/Minecraft/desarrollo/mods-propios/<mod>-forge-1.20.1/` (+ `<mod>-upstream/`, `LEEME-port.txt`) |
| Harness de pruebas | `desarrollo/mods-propios/packtest-forge-1.20.1` |
| Shine port | `~/Minecraft/desarrollo/shine-port` |
| Respaldos antes de cambios | `~/Minecraft/respaldos/<tema>-<fecha>/` |
| Notas de aprendizaje | `~/Minecraft/aprendizaje/` |
| Entregas al PC | `~/Sync/Compartida/N-*` (carpetas numeradas con .bat + .ps1; 9-PORTS = ports) |
| Instalador del cliente | `~/Sync/Compartida/INSTALAR-CLIENTE.bat`, `INSTALAR-TODO.bat` |
| Web del pack | repo GitHub `Greciant/pack-wip` |

## Flujo de un mod nuevo o port
1. Portátil: buscar antes si ya existe port/fork 1.20.1 en Modrinth/CurseForge. Si no, portar y compilar (`./gradlew build`, Java 21).
2. Portátil: dejar el jar en `~/Sync/Compartida/9-PORTS` (u `8-KEO/opcional`) con pasos de prueba escritos.
3. PC (hilo "Porteos y pruebas en el PC", instancia Prism `WIP-Cliente-para-PC`): probar en juego. Los hilos del PC y del portátil no se hablan; el coordinador pasa los mensajes.
4. Solo si el PC confirma: copiar a pool + 1-MODPACK + servidor a la vez, con respaldo antes.
5. Avisar qué jars/configs cambiaron (para la web).

## Reglas de recursos
- Portátil (14 GB RAM, GPU débil): solo compilar y trabajo sin gráficos; un trabajo pesado a la vez. Pruebas en juego, en el PC.
- Escalera de modelos: Sonnet low para revisar/instalar; Sonnet medium para compilar y portar; si falla 2 veces, Sonnet high; Opus solo después o para ports muy difíciles.
- Lo mecánico (buscar archivos, leer logs, comparar jars) va al subagente `portero` (Haiku).
- Nada de workflows multiagente salvo que el usuario lo pida.
- Arrancar o parar el servidor: siempre con confirmación del usuario.
