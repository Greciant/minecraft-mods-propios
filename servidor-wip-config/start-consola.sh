#!/usr/bin/env bash
cd "$(dirname "$0")"
F=console.fifo; rm -f "$F"; mkfifo "$F"
sleep infinity > "$F" & echo $! > .fifo_holder.pid
exec ./start.sh < "$F"
