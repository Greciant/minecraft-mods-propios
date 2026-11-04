# PC de pruebas (LAPTOP-CEJ0DL8U)

Instancia de CurseForge **WIP SSJ2 troll**: Minecraft 1.21.1, NeoForge 21.1.264, unos 280 mods.

- Mods clave: wipspowers 3.0.0, Awakened **1.23.1** (no 1.23.0), Grand Line 0.3.0, Curios, Player Animation Library, Impact Frame 1.1.0, Iris (con shaders activos).
- `mods-instalados.txt`: contenido actual de `mods/`. `mods-respaldo.txt`: versiones anteriores de wipspowers que guardé.
- `modlist.txt`: lista completa de mods con sus versiones, tal como sale en el log del juego.
- `config/`: configs de la instancia que importan para wipspowers (Awakened, Grand Line, Impact Frame, wipspowers, Particle Core, Iris).

Notas de las pruebas:
- Grand Line baja la calidad de los VFX a LOW cuando hay un shader de Iris activo; desde la 2.2.0 wipspowers lo anula y la deja en HIGH como máximo.
- Ruin Breach dice siempre "Nothing here can rot away". La causa no es que otro mod bloquee la rotura de bloques (con ignoreBreakVeto activo no aparece ningún WARN), así que el problema está en cómo detecta los bloques. Sigue pendiente.
