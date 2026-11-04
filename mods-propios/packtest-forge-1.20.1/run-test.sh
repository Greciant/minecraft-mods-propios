#!/bin/bash
# Lanza runClient con el guion de autoprueba, con el candado compartido.
cd "$(dirname "$0")"
flock /tmp/claude-1000/-home-grec-Minecraft/9f5d726e-38b9-41e0-883f-81a3a32aa7d5/scratchpad/runclient.lock timeout 1200 ./gradlew runClient --no-daemon -q > run-client.out 2>&1
echo "salida=$?" >> run-client.out
