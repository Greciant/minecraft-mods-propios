# Keo: revisión del 2026-10-03

Sigue a `keo-videos-2026-10-02.md`.

## Estado de cada fuente
- **TikTok @elkeomc: SIN REVISAR.** Claude in Chrome no estuvo conectado durante esta revisión; lo intenté 4 veces. Queda pendiente mirar los TikToks posteriores a "curvas de hielo" (7691900190084582677, del 01-10).
- **YouTube @elkeomc:** no hay nada nuevo. Lo revisé sin navegador, leyendo /videos y /shorts. El último video largo sigue siendo P1yedJEQ-Mw (Modular Backpacks) y el último short, sfof2gRlJV8 (semillas); los dos ya están anotados.
- **Modrinth, colección "mods pa videos" de gokukeo** (https://modrinth.com/collection/H5b9NfaM): tiene 153 proyectos y se actualizó por última vez el **2026-09-30**. La API no dice cuándo se agregó cada mod, así que no puedo saber cuáles son "los recientes". Guardé una foto de la lista en `modrinth-mods-pa-videos-2026-10-03.json` para comparar en la próxima revisión. Mientras tanto, revisé los proyectos más nuevos, ordenados por fecha de publicación.

## De la colección, lo útil para Forge 1.20.1 (que no esté en el pool)
| Mod | Qué hace | Forge 1.20.1 | Lado | Nota |
|---|---|---|---|---|
| **Fast Noise** (`zfastnoise`) | Optimiza la generación de mundo vanilla. Tiene 7,5 M de descargas. | ✅ 1.0.13+1.20+forge (2026-07-09) | solo servidor | **Es el más interesante para el servidor.** Hay que probar que sea compatible con el worldgen del pack (Tectonic/Lithosphere, etc.) en una copia, antes de usarlo en el servidor real. |
| **lumaLight** | Iluminación de colores, contornos, bloom y atmósfera "cartoon" sin shaderpacks. | ✅ 1.0.0-forge-1.20.1 (2026-08-14) | solo cliente | Es otra alternativa ligera a shaders, como Luxium. Es una opción, no algo para todos. |
| **Picture Mode** | Vista isométrica para sacar fotos del mundo. | ✅ 1.1.7 (2026-09-15) | solo cliente | Opcional, para capturas o videos. |
| **Fangs 'n Claws** | Enemigos nuevos con comportamientos únicos. | ✅ 1.2.4 (2026-08-11) | cliente y servidor | Candidato a contenido. |
| **Natural Camera** | Cámara en primera persona más realista. | ✅ 0.0.4-a (alfa) | solo cliente | Es una alfa; podría chocar con First-person Model o Punchy. |
| Otros con Forge 1.20.1 | Walking Crops, Enchanted Fishing Line, FTB Quests UI Overhaul, Riftbound Jewelry, Easy Macros, Ping System, Tellus, Petite Inventory, Wilder Flowers, Realm RPG: Treasure Balloons | ✅ | — | No los revisé en detalle. |
