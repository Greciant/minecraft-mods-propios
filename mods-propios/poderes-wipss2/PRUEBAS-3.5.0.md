# wipspowers 3.5.0 "THE AWAKENING": registro de pruebas

Todo esto se corrió en la nube; no en tu PC.
- **Servidor dedicado de prueba:** el pack completo (170 mods de servidor, NeoForge 21.1.264).
- **Cliente:** entorno de desarrollo con renderizado por software, sin GPU.

## 1. Compilación y audit
- `gradle build`: OK.
- `gradle runAudit -PauditMods`: **AUDIT OK**.
  - Todos los mixins de servidor aplican contra Awakened 1.23.1.
  - Se crean los magos de Hielo, Slime y Luchador.
  - Se vuelca el balance de daño por nivel.

## 2. Autotest de servidor con el pack completo: 85/85 PASS
- **Cómo se corre:** `wipss2-pack/tools/selftest.py`, que lanza el servidor con `-Dwips.selftest=true` y ejecuta `/wips selftest`.
- **Qué hace:** usa jugadores falsos de NeoForge y mobs reales (husks de 400 de vida) en una arena plana, activa cada habilidad y mide el resultado.
- **Log completo:** `minecraft-binarios/WIPSS2/capturas-3.5.0/autotest-servidor.txt`.

| Área | Qué se verificó (resultado medido) |
|---|---|
| Nivel y daño | Maestría neutralizada para el jugador (10 → 10, antes ~16). Multiplicador x0,300 / 0,422 / 0,758 / 1,033 / 1,500 en los niveles 1/10/25/35/50. Sube de nivel con XP, tope en 50 y la vida máxima sigue en 20. |
| XP | Rendimientos decrecientes (2,00 → 0,00 XP en el evento 122 de la misma fuente). Tope por minuto: 28 XP en 121 eventos. |
| Objetos quitados | La reliquia no llega por entrega directa ni por recompensa de trial (que se cambia por +120 XP de nivel). Tampoco aparece soltada en el suelo, y el adamantium no llega. 120 tiradas de los cofres de Awakened: 282 objetos, 0 prohibidos. |
| Ruin 4 y 5 | El túnel abre 7/7 y a los 16 s sigue abierto (permanente). Collapse destruye bloques. |
| Shinra Tensei | Está en la ranura 5. Enemigo a 5 bloques: −31 de vida y empujado 8 bloques. Alcanza a uno a 15 bloques. 4362 bloques destruidos para siempre, cola vacía. |
| Despertar | Las partículas viejas quedan silenciadas y suena un único efecto. |
| Hielo | Cargas 1 y 2. Congela a las 3 cargas y las reinicia. Sin cargas mientras está congelado; clavado en el lugar; dura ~1 s. Escarcha: 8 golpes en 4 s sin atacar. Ventisca: atrae de 7,0 a 0,7 bloques en 2 s. La plataforma de hielo aparece bajo los pies, desaparece a los 4 s y deja de crearse al desactivar. Témpanos: máximo 3, 3 impactos a 10 bloques, ninguno queda persiguiendo a un objetivo que desapareció. Territorio: congela al que está dentro y al que entra después, nunca al jugador, y descongela a los 5 s. |
| Slime | Una gota cada 3 golpes (dos golpes en el mismo tick cuentan uno) y cura al recogerla. Mini slimes: 3, sin duplicar, cambian de objetivo, atacan y nunca al dueño. Recubrimiento: 10 → 15 y tope en 20. Disparo: 4 impactos en 1,5 s, un solo modificador de −70 %, termina a los 4 s. Explosión: daño en área, solo pega quien pisa un charco. Pelota: contacto con daño y aturdimiento de 2 s, dura 15 s y luego restaura la velocidad. |
| Luchador | La barra sube con daño de un mob y no con caída ni magia. Barra llena: x1,20 de daño y x0,80 de daño recibido. Puños: onda a un enemigo cercano y fuego. Reel In trae enemigos desde 40 bloques, al Warden desde 25 (sin límite de tamaño) y al Ender Dragon desde 30 hasta pocos bloques. Represalia: centro/borde/final en relación 3,33 (200/60/60 %), consume la barra y pega más con barra llena. Salto: la onda escala con la altura (base 7 tras 5 bloques, 22 tras 30) y no hay autodaño. Golpe serio: sin carga pega y no rompe bloques; a carga máxima destruye 5087 bloques para siempre, repartidos en ticks. |
| Rendimiento | 30 enemigos con ventisca + congelación territorial + Shinra a la vez: 4,64 ms/tick de media (base con 30 enemigos quietos: 5,72). |

Problemas reales que encontró el autotest y quedaron arreglados:
1. **Los críticos de wipspowers (2.6.0) tumbaban el servidor** si un jugador falso hacía daño (deployers de Create, máquinas). Además, los jugadores falsos ya no hacen críticos.
2. **Ridiculous Impact Frames 1.2.0 tenía la misma caída.** Va parcheado desde wipspowers (`ImpactFakePlayerMixin`, opcional).
3. **Envíos de paquetes de wipspowers a jugadores falsos:** también lanzaban NullPointerException. Ahora se saltean (`PowerKit.send`/`sendAround`).
4. **Mini slimes:** con la IA vanilla de slime no se acercaban al objetivo. Ahora saltan hacia él con movimiento propio.
5. **Reel In contra el Ender Dragon:** la IA del dragón pisaba la velocidad. Ahora también se lo mueve por posición.

## 3. Regresión: escenario de combate del banco
- **Escenario:** 24 magos, 30 Legendary Monsters, sniffers y gólems; 121 entidades.
- **MSPT:** 7,0 ms de media, p95 10,5 y peor tick 27 ms. Es una sola repetición; la máquina varía entre sesiones.
- **ERROR en el log:** 28, igual que la corrida F de la optimización, y ninguno es de wipspowers.

## 4. Prueba visual de cliente (renderizado por software)
Con `WIPS_SET=nw gradle runFx -PwithLodestar` se sacaron 72 capturas. Las hojas resumen están en `minecraft-binarios/WIPSS2/capturas-3.5.0/`. Se ve que funcionan:
- el despertar con la estrella de Lodestar;
- la ventisca y el cubo de hielo en el congelado;
- los témpanos orbitando;
- el suelo de hielo con enemigos congelados;
- la explosión de baba con sus charcos;
- la pelota de slime (otros la ven como un slime grande);
- la carga y el túnel del Golpe Serio a carga máxima;
- el cráter de Shinra;
- el HUD de nivel ("Lv 27") y los íconos nuevos en la barra de habilidades.

Además:
- Los mixins de cliente aplican sin errores (incluido el que quita "Nominate").
- El dibujo de Lodestar por reflexión no dio avisos.

## 5. Pendiente de verificar en el PC (no se pudo aquí)
- **Sensación de control de la Pelota de slime:** el rebote y el impulso los calcula el cliente del propio jugador, y el arnés no mueve jugadores.
- **Pisadas de hielo con la tecla de salto real:** el autotest llamó directo al salto en el aire.
- **Hada de Lodestar con Explorer's/Nature's Compass:** hay que comprobar que desaparece al llegar a una estructura que está a otra altura.
- **Partida real:** multijugador con dos personas reales, y el balance con Progressive World Difficulty, que no está en el pack.
