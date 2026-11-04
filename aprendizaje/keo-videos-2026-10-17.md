# Links que mandó el usuario: revisión del 2026-10-17

Revisados con Claude in Chrome. No instalé nada.

## TikTok https://vt.tiktok.com/ZSgFJduxv/ — Keo (7697051481094114581, 16-10): "el mejor mod de animaciones"
**Touch'n Motion**, de "callejón" (victorkozhokin). Página: https://www.curseforge.com/minecraft/mc-mods/touch-and-motion. Licencia **GPLv3**; va en cliente y servidor.
- **Existe para Forge 1.20.1:** `touch_n_motion_forge_1.20.1_1.0.4.jar` (16-10). Así que **no hace falta portarlo**.
- Qué hace: el jugador interactúa físicamente con el mundo. Equilibrio sobre vallas con los pies fijados, se apoya al chocar contra un bloque, estira el brazo hacia palancas y botones, toca los cultivos al pasar, acomoda los pies en superficies irregulares, rema en el bote, mira a los mobs que lo miran y reacciona a los rayos. Se puede configurar por categoría.
- Necesita:
  - **Entity Model Features** → ya está en el pack (`entity_model_features-3.3.5-1.20.1-forge`). Hay que comprobar si la 1.0.4 pide una versión más nueva.
  - **Entity Texture Features** → ya está (7.2.1).
  - **EMF Compat: Core** (mismo autor, GPLv3) → existe para Forge 1.20.1: `emf_compat_core_forge_1.20.1_2.3.2.jar`. No está en el pack.
  - Un resourcepack con modelo de jugador para EMF: **Fresh Animations: Player Extension** (FreshLX, ARR, pero se puede usar tal cual; https://modrinth.com/resourcepack/fa-player-extension). El pack ya trae FreshAnimations v1.10.4, pero **no** el Player Extension, que tiene que ir encima de Fresh Animations en la lista de resourcepacks.
- El mismo autor tiene más mods "EMF Compat" para que las animaciones de Fresh Animations funcionen con Better Combat, Iron's Spells, parkour, etc. Vale la pena mirarlos si se agrega esto.
- **Riesgos a probar en el PC:** que no choque con Punchy, First-person Model o Better Combat (animaciones del jugador), ni con Spatial GUI o ReAnimated. Es muy nuevo (1 semana, ~8,8k descargas).

## Los 5 links de Instagram: no son de Minecraft
Son reels sobre IA y Claude, no sobre mods:
- DekR5ejxTnj (stefo.curo): usar Haiku para tareas chicas y Opus solo para lo difícil, para que no se acabe el límite de uso.
- DehkcW6vl6a (sebastiankra.ia): "¿cuántos agentes IA tienes?" (sin contenido técnico).
- DeIFvoSPh6m (sebastiankra.ia): armar un asistente propio (Hermes Agent, memoria Honcho, etc.).
- DeHdXeWsgph (donimas): enrutar cada tarea al modelo más barato que la resuelva y subir de nivel si falla.
- DectlCLPpXo (sebastiankra.ia): 4 complementos para Claude (Ponytail, OmniRoute, Graphify, Agent Skills).
Encajan con la regla de la memoria "uso rentable de modelos". No son para el pack.

## Candidatos a porteo
Ninguno nuevo: Touch'n Motion ya existe para Forge 1.20.1.
