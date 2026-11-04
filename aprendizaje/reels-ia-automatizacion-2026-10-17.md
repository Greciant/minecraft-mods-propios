# Reels de IA: ideas para automatizar y gastar menos (2026-10-17)

Fuente: 5 reels de Instagram que mandó el usuario. Leídos por su texto con Claude in Chrome, sin vidIQ.

## Qué dicen
1. stefo.curo, "portero": Opus para todo es ir en Ferrari a por el pan. Buscar archivos, revisar errores y cambios pequeños van a un ayudante con Haiku; lo difícil a Opus. En Claude Code es un subagente con `model: haiku`.
2. sebastiankra.ia, "deja de montar 1 agente por tarea": idea de la charla de Anthropic. Un solo agente con varias skills (recetas) en vez de un agente distinto por tarea.
3. sebastiankra.ia, "Jarvis por piezas": casa, cerebro, perfil, memoria, skills, herramientas, canales. Este proyecto ya tiene casi todo (portátil, PC, memoria, hilos).
4. donimas, "router": el más barato primero, verificar, y subir de modelo solo si falla. Para lo difícil suele salir más barato subir el esfuerzo del modelo medio que saltar al grande. Una especificación clara deja que el barato acierte más.
5. sebastiankra.ia, 4 complementos: buscar lo que ya existe antes de escribir, saltar de modelo al acabarse el límite, mapa del proyecto para no releerlo todo, skills para planear/probar/publicar.

## Qué choca con este proyecto hoy
- `~/.claude/settings.json` del portátil tiene `"model": "opus"`, `"enableWorkflows": true` y `"skipWorkflowUsageWarning": true`. Contradice la regla de [uso rentable de modelos] (Sonnet/Haiku por defecto, sin workflows salvo pedido).
- No hay subagentes propios (`~/.claude/agents/` no existe), así que las búsquedas y lecturas de logs las hace el modelo principal.
- No hay un mapa corto del proyecto: cada hilo nuevo explora `~/Minecraft` desde cero.

## Cambios propuestos
1. Modelo por defecto `sonnet`, esfuerzo `low`; Opus solo al empezar un port difícil.
2. Quitar `skipWorkflowUsageWarning` (y `enableWorkflows` si no se usan) para que un workflow caro siempre avise.
3. Crear subagente `portero` (Haiku) en `~/.claude/agents/portero.md` para buscar archivos, leer logs de crash/latest.log, listar jars y comparar carpetas. Igual en el PC.
4. Escalera para ports: Sonnet medium compila y prueba; si falla dos veces, Sonnet high; solo después Opus. El autotest/packtest es el "verificar".
5. Un `MAPA.md` en `~/Minecraft` (qué hay en cada carpeta, rutas de Compartida, pool canónico, flujo portátil→PC→pool) para que los hilos no relean todo.
6. Antes de portar, buscar si ya existe un port o fork en Modrinth/CurseForge para 1.20.1 (la idea de "buscar lo que existe").
7. Convertir pasos repetidos en skills: "probar port en el PC", "subir mod al pool y avisar a la web", "revisión diaria de Keo".

## Aplicado (2026-10-17, el usuario dijo "aplica todo")
- `~/.claude/settings.json`: `model: sonnet`, `effortLevel: low`, sin `skipWorkflowUsageWarning` (respaldo `settings.json.bak-2026-10-17`).
- Subagente `~/.claude/agents/portero.md` (Haiku, solo lectura). Pedido al coordinador que el hilo del PC cree el suyo.
- `~/Minecraft/MAPA.md` con rutas, flujo y escalera de modelos; `LEEME.txt` apunta a él.
- Memoria: escalera de modelos y regla de buscar port existente antes de portar.

## OmniRoute y Hermes (el usuario los quiere: voz y más modelos)
- OmniRoute: gateway local (localhost:20128/v1) que salta de proveedor al agotarse la cuota (suscripción → API key → barato → gratis). Hay varios forks; el más citado es diegosouzapw/OmniRoute. Para seguir trabajando tras el límite de Claude necesita claves de otros proveedores (OpenRouter, Gemini, etc.). Pasar la suscripción de Claude por un gateway de terceros puede chocar con los términos de Anthropic: usarlo solo con claves API.
- Hermes Agent (NousResearch/hermes-agent, MIT): agente con memoria persistente, cambia de modelo con `hermes model`, habla por Telegram/Discord/WhatsApp con `hermes gateway`. La voz es transcribir notas de voz entrantes; responder hablando requiere TTS de pago (OpenAI o Nous Portal). Con Claude usa clave API de pago por uso, no la suscripción.
- Instalarlos y darles claves lo tiene que hacer el usuario (cuentas y claves no las introduzco yo).
- Decisión 2026-10-17: el usuario NO los instala; solo paga el plan de Claude, sin claves API ni servicios de pago aparte.
