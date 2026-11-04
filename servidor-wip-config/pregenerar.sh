#!/usr/bin/env bash
# Pregenera chunks en todas las dimensiones de dimensiones-pregen.txt.
# Secuencial a proposito: el servidor es un portatil, 20 a la vez lo mata.
set -u
cd "$(dirname "$0")"
LISTA=dimensiones-pregen.txt
HECHAS=pregen-hechas.txt
LOG=logs/latest.log
REG=pregen.log
MIN_LIBRE_GB=10

say(){ printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" | tee -a "$REG"; }
cmd(){ ./cmd.sh "$@" >/dev/null 2>&1; }
libre_gb(){ df -BG --output=avail . | tail -1 | tr -dc '0-9'; }

# --- arrancar el servidor si hace falta ---
if ! ss -ltn 2>/dev/null | grep -q ':25567'; then
    say "servidor apagado, arrancando con consola..."
    rm -f console.fifo .fifo_holder.pid
    nohup ./start-consola.sh > arranque.log 2>&1 &
    for i in $(seq 1 180); do
        grep -aq 'Done (' "$LOG" 2>/dev/null && ss -ltn 2>/dev/null | grep -q ':25567' && break
        sleep 2
    done
    ss -ltn 2>/dev/null | grep -q ':25567' || { say "NO arranco. Mira arranque.log"; exit 1; }
    say "servidor arriba"
else
    say "servidor ya estaba arriba"
fi
[ -p console.fifo ] || { say "sin consola fifo: arranca con ./start-consola.sh"; exit 1; }

cmd chunky quiet 30

total=0
while read -r dim radio; do
    case "$dim" in ''|'#'*) continue;; esac
    [ "${radio:-0}" -eq 0 ] 2>/dev/null && { say "salto  $dim (radio 0)"; continue; }
    # ya hecha en una pasada anterior? el servidor se reinicia mas de lo que uno quiere
    if [ -f "$HECHAS" ] && grep -qxF "$dim" "$HECHAS"; then say "salto  $dim (ya hecha)"; continue; fi

    if [ "$(libre_gb)" -lt "$MIN_LIBRE_GB" ]; then
        say "PARO: quedan menos de ${MIN_LIBRE_GB} GB libres"
        exit 1
    fi

    say "=== $dim  radio ${radio}  (~$(( (2*radio/16)**2 )) chunks)"
    marca=$(wc -l < "$LOG")
    cmd chunky world "$dim"
    sleep 1
    cmd chunky center 0 0
    sleep 1
    cmd chunky radius "$radio"
    sleep 1
    cmd chunky start
    sleep 3

    # por si Chunky pide confirmar porque ya habia una tarea guardada
    if tail -n +"$marca" "$LOG" | grep -qa 'was already started for this world'; then
        say "  habia tarea guardada, continuo"
        cmd chunky continue
        sleep 2
    fi

    # esperar a que termine esta dimension
    inicio=$SECONDS
    while true; do
        if tail -n +"$marca" "$LOG" | grep -qaF "Task finished for $dim"; then
            dur=$(( SECONDS - inicio ))
            echo "$dim" >> "$HECHAS"
            say "  hecho en $((dur/60)) min  |  mundo: $(du -sh world 2>/dev/null | cut -f1)  |  libre: $(libre_gb) GB"
            total=$((total+1)); break
        fi
        if ! ss -ltn 2>/dev/null | grep -q ':25567'; then
            say "  el servidor se cayo durante $dim. Vuelve a lanzar este script: Chunky continua donde iba."
            exit 1
        fi
        sleep 20
    done
done < "$LISTA"

say "TERMINADO: $total dimensiones pregeneradas. Mundo: $(du -sh world | cut -f1)"
say "El servidor sigue arriba. Parar con: ./cmd.sh stop"
