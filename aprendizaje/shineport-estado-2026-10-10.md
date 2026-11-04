# Shineport: estado al 10-oct-2026

Port propio de efectos de Shine a Forge 1.20.1. Solo cliente y solo para uso personal:
Shine es "All Rights Reserved", así que **no se publica ni se comparte**.
Las texturas son nuestras (scripts de Blender en `desarrollo/shine-port/blender/`), nunca las de Shine.

## Versión instalada: 1.8.4

- Está en `~/Minecraft/mods/pool-1.20.1/` y en `~/Sync/Compartida/1-MODPACK/1.20.1/mods/` (también en `modlist.txt`).
  La 1.8.1 quedó en `pool-1.20.1/_retirados/`.
- Pasó la prueba en el PC el 10-oct. Los gorriones huyen a unos 11 bloques cuando te acercas andando, sin errores.
- Código: `~/Minecraft/desarrollo/shine-port/forge-port/` (no es repo git). El historial de versiones está en
  `~/Sync/Compartida/10-SHINEPORT/LEEME.txt`, y las pruebas del PC en `10-SHINEPORT/pruebas-pc-<ver>/RESUMEN.txt`.
- Compilar en el portátil: `JAVA_HOME=<java-21> ./gradlew build` desde `forge-port`. **El portátil no lanza
  Minecraft** (se reinicia con la carga). El juego se prueba en el PC.
- Para mandar una versión al PC: copia el jar a `10-SHINEPORT/jars-<ver>/` y añade `ACTUALIZAR-SHINEPORT-<ver>.bat`
  (usa `aplicar-shineport.ps1 -Jars jars-<ver>`). Solo cuando pase la prueba se instala en el pool y en 1-MODPACK.

## Efectos (opciones en `config/shineport-client.toml`)

| Efecto | Sección del toml | Qué hace |
|---|---|---|
| Destello de rayo | `[lightning_flash]` | El cielo se ilumina cuando cae un rayo cerca |
| Niebla suave | `[fog]` | Cambio de color de niebla gradual entre biomas |
| Luces de ambiente | `[ambient_lights]` | Motas sobre linternas, chispas sobre antorchas |
| Humo de antorcha | (mixin) | Humo de hoguera en lugar del humo plano |
| Burbujas de cofre | `[chest_bubbles]` | Burbujas al abrir un cofre bajo el agua |
| Salpicadura | `[water_splash]` | La salpicadura crece con la velocidad de caída |
| Sacudida de cámara | `[camera_shake]` | Explosiones y rayos cercanos |
| Filtro de audio | `[audio]` | Solo está la base (low-pass de OpenAL): no la usa ningún efecto todavía |
| Lluvia en paredes | `[block_side_rain]` | Gotas que resbalan por las caras laterales |
| Charcos | `[rain_puddles]` | Se forman con la lluvia y se secan después |
| Mariposas | `[butterflies]` | De día cerca de flores, se posan y huyen |
| Pájaros | `[birds]` | Gorriones, mirlos y gaviotas en bandadas. Huyen a 8 bloques si estás quieto, 11 andando hacia ellos y 14 corriendo. Con lluvia o de noche se van |
| Luciérnagas | `[fireflies]` | De noche sobre la hierba, brillan también con shaders |
| Hojas que caen | `[falling_leaves]` | Caen de las copas, se quedan en el suelo o flotan en el agua |
| Polen | `[pollen]` | Motas al sol que se mueven con el viento |
| Viento | (interno) | Lo comparten las hojas y el polen |
| God rays y DoF | `[god_rays]`, `[depth_of_field]`, `[post_effects]` | Post-proceso. Solo sin shaderpack: con Complementary se apagan solos |
| Editor .shinepack | `config/shineport/packs/` | Partículas propias en JSON (ver abajo) |

**Editor .shinepack (desde la 1.8.0):** los archivos `*.shinepack` van en `config/shineport/packs/` y los PNG en
`config/shineport/textures/`. Se recarga con F3+T o con `/shineport reload|list|on|off`. La lista de campos y los
ejemplos están en `~/Minecraft/aprendizaje/shinepack/` (`LEEME-shinepack.txt`); no cambiaron entre la 1.8.1 y la 1.8.4.

## Probadores automáticos

Los cuatro montan su escena junto al jugador, a la altura real del suelo, así que sirven en cualquier mundo.
Al terminar dejan el juego abierto y te devuelven al modo de juego que tenías. Todo lo que miden va a `latest.log`.

| Probador | Valor | Qué hace |
|---|---|---|
| ambiente | `ambiente` | Explanada con dos copas de árbol y una charca. Capturas de día (hojas y polen), con lluvia y de noche (luciérnagas). Escribe `shinepack id=count` |
| pájaros | `pajaros` | Allana 73x51 bloques (usa un mundo de pruebas). Saca capturas de cada especie y prueba la huida caminando de verdad con la tecla de avanzar (no con /tp) desde 18 hasta 4 bloques. Después pone lluvia. Dura unos 80 s. No toques el teclado mientras camina |
| mariposas | `mariposas` | Prado de flores a mediodía. Capturas, huida y lluvia. Recuento por estado cada 5 s |
| charcos | `true` | Explanada bajo la lluvia. Capturas con cada modo de dibujo y a varias horas |

**En el PC (pack completo):** en Prism, instancia `WIP-Cliente-para-PC` → Editar → Ajustes → Java → argumentos de Java,
añade `-Dshineport.autotest=<valor>` (por ejemplo `-Dshineport.autotest=pajaros`) y entra en un mundo de pruebas.
Al terminar, quita el argumento.

**En desarrollo (`forge-port`):**
`./gradlew runClient -PquickPlay=lluvia-test -PautoTest=<valor>`. Para charcos basta con `-PautoTest` sin valor.
Añade `-PconMods` para cargar Oculus y Complementary. `lluvia-test` es un mundo plano (suelo en y=-60).
Ojo: esto lanza el juego, así que en el portátil no.

Lecciones de estas pruebas:
- Si un probador necesita la velocidad del jugador, tiene que hacerlo caminar con teclas: con `/tp` la velocidad sale 0.
- En el cliente, el heightmap `MOTION_BLOCKING_NO_LEAVES` no sirve (solo existe en el servidor).
- Los comandos del probador corren desde el servidor: `gamemode` necesita el nombre del jugador y para mover al jugador hay que usar `tp`.

## Aparcado: polen y luciérnagas como .shinepack (1.9.0)

Está en `~/Minecraft/desarrollo/shine-port/ideas-migracion-shinepack/`. Hay un `LEEME.txt`, el código completo en `src/`
(hecho sobre la 1.8.1, **no sobre la 1.8.4**) y `shineport-1.9.0-borrador.jar`, que compila pero no se ha probado.

- **Hecho:** `polen.shinepack` y `luciernagas.shinepack` con los mismos valores que el código, más los interruptores
  `use_shinepack` en `[pollen]` y `[fireflies]` del toml. También hay muchos campos nuevos para el editor:
  horas, luz, lluvia, anclaje "ground", densidad por bioma, día y noche, wobble y drift, fundidos, núcleo brillante, etc.
- **Falta:**
  1. Pasar los cambios de la 1.8.2 a la 1.8.4 (TestSite, la huida al andar y el probador que camina) al código del borrador, o rehacer el borrador sobre la 1.8.4.
  2. Documentar los campos nuevos en `LEEME-shinepack.txt`.
  3. Comprobar que los .shinepack cargan sin avisos. Fuera del juego no se puede, porque falla el Bootstrap de Forge.
  4. Comparar en el PC, con el probador `ambiente`, cómo se ven contra la versión hecha en código.
