#!/usr/bin/env bash
# Si el servidor o pregenerar.sh se caen de madrugada, los levanta otra vez.
# Chunky retoma donde iba y el script salta las dimensiones ya hechas.
cd "$(dirname "$0")"
LOG=vigilante.log
TOTAL=$(grep -vE '^\s*#|^\s*$' dimensiones-pregen.txt | awk '$2>0' | wc -l)
say(){ printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" >> "$LOG"; }
say "vigilante en marcha, $TOTAL dimensiones que hacer"
while true; do
    hechas=$( [ -f pregen-hechas.txt ] && wc -l < pregen-hechas.txt || echo 0 )
    if [ "$hechas" -ge "$TOTAL" ]; then
        say "TERMINADO: $hechas/$TOTAL dimensiones. Me retiro."
        exit 0
    fi
    vivo=0
    for p in /proc/[0-9]*; do
        cl=$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null) || continue
        case "$cl" in *pregenerar.sh*) case "$cl" in *vigilar*|*proc*) ;; *) vivo=1;; esac;; esac
    done
    if [ "$vivo" -eq 0 ]; then
        say "pregenerar.sh no esta ($hechas/$TOTAL hechas). Relanzo."
        rm -f console.fifo .fifo_holder.pid
        nohup ./pregenerar.sh > /dev/null 2>&1 &
        sleep 240
    fi
    sleep 120
done
