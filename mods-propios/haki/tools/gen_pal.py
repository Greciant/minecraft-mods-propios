#!/usr/bin/env python3
"""
Genera la animacion de la pose de lanzamiento del Haki para Player Animation Library (PAL):
  src/main/resources/assets/haki/player_animations/cast.json   animacion (formato GeckoLib/Bedrock de PAL), en bucle
  art/haki_cast_pal.bbmodel                                      proyecto de Blockbench con la rig de PAL y la animacion

Los valores son los mismos que POSE_* de HakiVisuals (la pose del Mixin de respaldo): mantenerlos en sincronia.
Valores en grados. Para brazos y piernas el Z de PAL tiene el mismo signo que el zRot de Java en este generador
(derecho +, izquierdo -). Se abre en Blockbench (plugin "GeckoLib Animation Utils"); Animation > Export Animation
escribe encima de cast.json. El fundido de entrada/salida lo hace el codigo (replaceAnimationWithFade), no la animacion.

Uso:  python3 tools/gen_pal.py
"""
import json
import math
import os
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = math.degrees

# ---- pose (radianes, = HakiVisuals.POSE_*) ----
ARM_SPREAD, ARM_FORWARD, HEAD_BACK, BODY_LEAN, LEG_SPREAD, RUMBLE = 1.05, -0.2, -0.28, -0.10, 0.12, 0.035
LENGTH = 1.2          # segundos del ciclo de temblor
STEPS = 8             # claves por ciclo


def U(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "haki/pal/" + name))


def tremble(phase, amp):
    return [amp * math.sin(2 * math.pi * k / STEPS + phase) for k in range(STEPS + 1)]  # ultimo = primero: cierra el ciclo


bones = {}


def key(bone, t, v):
    bones.setdefault(bone, {}).setdefault("rotation", {})[str(round(t, 3))] = [round(c, 4) for c in v]


for k in range(STEPS + 1):
    t = LENGTH * k / STEPS
    r1, r2, r3, r4 = tremble(0.0, RUMBLE)[k], tremble(1.7, RUMBLE)[k], tremble(0.4, RUMBLE)[k], tremble(2.2, RUMBLE)[k]
    key("right_arm", t, [D(ARM_FORWARD + r3), D(r1 * 0.5), D(ARM_SPREAD + r1)])
    key("left_arm", t, [D(ARM_FORWARD + r4), D(r2 * 0.5), D(-ARM_SPREAD + r2)])
    key("head", t, [D(HEAD_BACK + r1 * 0.3), 0, 0])
    key("torso", t, [D(BODY_LEAN), 0, 0])
    key("right_leg", t, [0, 0, D(LEG_SPREAD)])
    key("left_leg", t, [0, 0, D(-LEG_SPREAD)])

anim = {"format_version": "1.8.0", "animations": {"cast": {
    "loop": True, "animation_length": LENGTH,
    "player_animation_library": {"name": "Haki cast", "description": "Pose de lanzamiento del Haki", "author": "Benjamin Greciet",
                                 "applyBendToOtherBones": True},
    "bones": bones}}, "geckolib_format_version": 2}
out = os.path.join(ROOT, "src/main/resources/assets/haki/player_animations/cast.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(anim, open(out, "w"), indent=1)

# Animacion vacia a la que se funde la pose al soltar (duracion = FADE_OUT_TICKS de HakiPal: 8 ticks = 0.4 s)
end = {"format_version": "1.8.0", "animations": {"cast_end": {"loop": False, "animation_length": 0.4, "bones": {}}}}
json.dump(end, open(os.path.join(os.path.dirname(out), "cast_end.json"), "w"), indent=1)

# ---- bbmodel con la rig de PAL (mismos huesos y pivotes que usa aura-chad) ----
PAL_PARTS = [
    ("body", None, None, None, None, [0, 12, 0]),
    ("torso", "body", [-4, 12, -2], [8, 12, 4], [16, 16], [0, 24, 0]),
    ("head", "torso", [-4, 24, -4], [8, 8, 8], [0, 0], [0, 24, 0]),
    ("right_arm", "torso", [4, 12, -2], [4, 12, 4], [40, 16], [5, 22, 0]),
    ("left_arm", "torso", [-8, 12, -2], [4, 12, 4], [32, 48], [-5, 22, 0]),
    ("right_leg", "body", [0, 0, -2], [4, 12, 4], [0, 16], [2, 12, 0]),
    ("left_leg", "body", [-4, 0, -2], [4, 12, 4], [16, 48], [-2, 12, 0]),
]
els, nodes, guid = [], {}, {}
for name, parent, o, sz, uv, pv in PAL_PARTS:
    gu = U(name)
    guid[name] = gu
    node = {"name": name, "origin": pv, "rotation": [0, 0, 0], "color": 0, "uuid": gu, "export": True, "isOpen": True,
            "locked": False, "visibility": True, "autouv": 0, "children": []}
    if o:
        eu = U(name + "_cube")
        els.append({"name": name + "_cube", "box_uv": True, "rescale": False, "locked": False, "type": "cube", "uuid": eu,
                    "from": o, "to": [o[0] + sz[0], o[1] + sz[1], o[2] + sz[2]], "autouv": 0, "color": 0, "origin": pv,
                    "uv_offset": uv, "faces": {f: {"uv": [0, 0, 0, 0], "texture": 0} for f in ("north", "east", "south", "west", "up", "down")}})
        node["children"].append(eu)
    nodes[name] = node
roots = []
for name, parent, *_ in PAL_PARTS:
    (nodes[parent]["children"] if parent else roots).append(nodes[name])
animators = {}
for b, chs in bones.items():
    kfs = []
    for ch, ks in chs.items():
        for t, v in ks.items():
            kfs.append({"channel": ch, "data_points": [{"x": str(v[0]), "y": str(v[1]), "z": str(v[2])}], "uuid": U("%s/%s/%s" % (b, ch, t)),
                        "time": float(t), "color": -1, "interpolation": "linear"})
    animators[guid[b]] = {"name": b, "type": "bone", "keyframes": kfs}
bb = {"meta": {"format_version": "4.10", "model_format": "bedrock", "box_uv": True},
      "name": "haki_cast_pal", "model_identifier": "haki_cast_pal", "visible_box": [1, 1, 0], "variable_placeholders": "",
      "bedrock_animation_mode": "entity", "resolution": {"width": 64, "height": 64}, "elements": els, "outliner": roots,
      "textures": [],
      "animations": [{"uuid": U("anim"), "name": "cast", "loop": "loop", "override": False, "length": LENGTH, "snapping": 20,
                      "selected": True, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                      "animators": animators}]}
os.makedirs(os.path.join(ROOT, "art"), exist_ok=True)
json.dump(bb, open(os.path.join(ROOT, "art/haki_cast_pal.bbmodel"), "w"), indent=1)
print("ok", out)
