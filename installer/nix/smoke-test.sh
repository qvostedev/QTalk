#!/usr/bin/env bash
set -euo pipefail
launcher="$1"
export HOME="$(mktemp -d)"
export XDG_CONFIG_HOME="$HOME/.config"
export XDG_DATA_HOME="$HOME/.local/share"
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
export LIBGL_ALWAYS_SOFTWARE=1
"$launcher" > build/nix-release/qtalk-nix-startup.log 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; cat build/nix-release/qtalk-nix-startup.log' EXIT
for attempt in $(seq 1 45); do
    kill -0 "$pid" || exit 1
    if xdotool search --onlyvisible --name '^QTalk$' >/dev/null 2>&1; then
        sleep 3
        kill -0 "$pid"
        if grep -E 'UnsatisfiedLinkError|NoClassDefFoundError|Exception in thread' build/nix-release/qtalk-nix-startup.log; then exit 1; fi
        echo 'Native Nix QTalk opened a window without bundled Java or DEB installation'
        exit 0
    fi
    sleep 1
done
exit 1
