#!/usr/bin/env bash
cd "$(dirname "$0")"
exec /usr/lib/jvm/java-21-openjdk-amd64/bin/java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.4.23/unix_args.txt --nogui "$@"
