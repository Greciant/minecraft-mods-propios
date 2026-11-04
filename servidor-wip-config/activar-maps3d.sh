#!/usr/bin/env bash
# Pone maps3d en el servidor. SOLO despues de que el cliente lo tenga:
# el mod exige el canal de red en los dos lados o no puedes entrar.
set -e
cd "$(dirname "$0")"
J=~/Sync/Compartida/7-MAPAS/jars/maps3d_1.0.1-1.20.1-forge.jar
[ -f "$J" ] || { echo "no encuentro $J"; exit 1; }
ss -ltn 2>/dev/null | grep -q ':25567' && { echo "para el servidor primero: ./cmd.sh stop"; exit 1; }
cp "$J" mods/ && echo "puesto. Arranca con ./start-consola.sh"
