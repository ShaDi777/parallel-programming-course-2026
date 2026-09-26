#!/usr/bin/env bash
set -euo pipefail

javac --add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
      -d out \
      $(find src/main/java -name "*.java")

echo "=== BENCHMARK ==="
java -cp out org.labs.Main benchmark plain 1
for C in synchronized synchronizedEmpty lockStriping threadLocal doubleBuffer; do
    for T in 1 2 4 8 16; do
        java -cp out org.labs.Main benchmark "$C" "$T"
    done
done

echo "=== STRESS ==="
for C in lockStriping threadLocal doubleBuffer doubleBufferWeak; do
    java -cp out org.labs.Main stress "$C" 4
done

read -p "Press Enter to continue..."