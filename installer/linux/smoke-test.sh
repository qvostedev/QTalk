#!/usr/bin/env bash
set -euo pipefail
export HOME="$(mktemp -d)"
export XDG_CONFIG_HOME="$HOME/.config"
export XDG_DATA_HOME="$HOME/.local/share"
export LIBGL_ALWAYS_SOFTWARE=1
/usr/bin/qtalk > /tmp/qtalk-startup.log 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; cat /tmp/qtalk-startup.log' EXIT
for attempt in $(seq 1 45); do
    kill -0 "$pid" || exit 1
    if xdotool search --onlyvisible --name '^QTalk$' > /dev/null 2>&1; then
        sleep 3
        kill -0 "$pid"
        if grep -E 'UnsatisfiedLinkError|NoClassDefFoundError|Exception in thread' /tmp/qtalk-startup.log; then exit 1; fi
        echo "QTalk window opened with the packaged JVM and native libraries"
        exit 0
    fi
    sleep 1
done
echo "QTalk window did not appear"
exit 1
