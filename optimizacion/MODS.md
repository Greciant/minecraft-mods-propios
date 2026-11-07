# Cambios en la lista de mods

## Agregar (cliente y servidor)
| Mod | Versión | Archivo | Dependencias | Por qué |
|---|---|---|---|---|
| Lithium | mc1.21.1-0.15.4-neoforge (estable) | `lithium-neoforge-0.15.4+mc1.21.1.jar` (Modrinth) | ninguna | MSPT −28 %, p95 −32 %, generación −5 % (medido). Reescribe las búsquedas de POI, que eran el 27 % del tick. |

Va junto con `config/lithium.properties` (carpeta `servidor/config/`, que también va en el cliente). Desactiva la caché de temperatura de biomas de Lithium, porque ModernFix ya la hace, y así no hay conflicto de mixins.

Lithium es compatible con todo lo demás del pack: se probó con los 170 mods de servidor (C2ME, ModernFix, FerriteCore, Create, Tectonic, Awakened, Grand Line, Legendary Monsters, etc.). En el cliente convive con Sodium e Iris, porque son del mismo equipo (CaffeineMC).

## Actualizar
| Mod | Antes | Ahora | Por qué |
|---|---|---|---|
| wipspowers | 3.3.0 | **3.4.0** | Arreglo de la daga de Legendary Monsters; `WipsTerrain` con cola ordenada; PNG recomprimidos sin pérdida (−20 %). |

## Evaluados y NO agregados
| Mod | Resultado |
|---|---|
| Noisium 2.3.0 | Generación 177,6 → 176,1 s (−0,8 %, ruido). No compensa otro mod más. |
| ScalableLux 0.3.0 | Está en alpha y la iluminación no salió como cuello de botella en el perfil. No se instala. |
| Saturn | El heap vivo es de solo 1,3 GB; no hay problema de memoria que justifique otro mod. |

## Para probar en el PC (solo cliente: aquí no se pueden medir FPS)
Agrégalos **de a uno** y mide con `cliente/PROTOCOLO-FPS.md`. Quédate con el que suba el 1 % low sin cambiar cómo se ve.
| Mod | Versión | Qué hace |
|---|---|---|
| MoreCulling | 1.0.11 (NeoForge 1.21.1) | Deja de dibujar caras de bloques que no se ven (hojas, cristal, bloques de modelos). Compatible con Sodium. |
| BadOptimizations | 2.4.1 | Cachea el cálculo del lightmap y del cielo; sin cambio visual. |

## Solo cliente (ojo si abres un servidor dedicado)
- `sandeviscraft-neoforge-1.21.1-1.1.1.jar` y `insight-chests-neoforge-1.21.1-1.0.5.jar` **crashean un servidor dedicado** al arrancar. En un solo jugador o LAN están bien; en un servidor dedicado van solo en el cliente.
