#!/usr/bin/env bash
F="$(dirname "$0")/console.fifo"
[ -p "$F" ] || { echo "sin consola: arranca con ./start-consola.sh"; exit 1; }
printf '%s\n' "$*" > "$F"
