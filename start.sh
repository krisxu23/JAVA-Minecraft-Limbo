#!/bin/bash
# JAVA-Minecraft-Limbo — Adaptive startup script
# Automatically tunes JVM heap to leave enough native memory for proxy services.

set -e

JAR="build/libs/server.jar"
if [ ! -f "$JAR" ]; then
  echo "Building..."
  ./gradlew clean shadowJar
fi

# --- Auto-detect memory ---
if [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
  # cgroup v1 (Docker, LXC)
  CGROUP_MEM=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
elif [ -f /sys/fs/cgroup/memory.max ]; then
  # cgroup v2
  CGROUP_MEM=$(cat /sys/fs/cgroup/memory.max)
fi

if [ -n "$CGROUP_MEM" ] && [ "$CGROUP_MEM" -gt 0 ]; then
  TOTAL_MEM=$((CGROUP_MEM / 1048576))
elif command -v free >/dev/null 2>&1; then
  TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
else
  TOTAL_MEM=512
fi

# Heuristic: leave enough native memory for sing-box + cloudflared
#   <512MB  → heap 40%  (tight, bare minimum)
#   512-1G  → heap 50%
#   1G-4G   → heap 60%
#   >4G     → heap 70%
if   [ "$TOTAL_MEM" -lt 512 ];  then HEAP_PCT=40
elif [ "$TOTAL_MEM" -lt 1024 ]; then HEAP_PCT=50
elif [ "$TOTAL_MEM" -lt 4096 ]; then HEAP_PCT=60
else                                 HEAP_PCT=70
fi

HEAP_MB=$((TOTAL_MEM * HEAP_PCT / 100))

echo "=========================================="
echo " Total memory : ${TOTAL_MEM}MB"
echo " Heap limit   : ${HEAP_MB}MB (${HEAP_PCT}%)"
echo " Native left  : $((TOTAL_MEM - HEAP_MB))MB"
echo " JVM args     : -Xmx${HEAP_MB}M $(echo ${JVM_ARGS:-})"
echo "=========================================="

# Use ZGC on JDK 17+, fallback to G1GC on older JDKs
JAVA_VERSION=$(java -version 2>&1 | head -1 | sed 's/[^0-9.]//g' | cut -d. -f1)
if [ "$JAVA_VERSION" -ge 17 ]; then
  GC_ARGS="-XX:+UseZGC -XX:+ZGenerational"
else
  GC_ARGS="-XX:+UseG1GC"
fi

exec java \
  -Xmx${HEAP_MB}M -Xms64M \
  $GC_ARGS \
  -XX:+AlwaysPreTouch \
  -XX:+DisableExplicitGC \
  -XX:+ExitOnOutOfMemoryError \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=./oom-dump.hprof \
  -XX:-OmitStackTraceInFastThrow \
  -Djava.awt.headless=true \
  ${JVM_ARGS:--server} \
  -jar "$JAR" "$@"
