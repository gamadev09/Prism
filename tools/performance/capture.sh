#!/usr/bin/env bash
# Keep the launcher installed and selected. Preserve user data.
set -euo pipefail
package=${LIQUIDY_PACKAGE:-io.github.janhurn9400.liquidy}
mode=${1:-}
case "$mode" in
    reset)
        adb shell dumpsys gfxinfo "$package" reset >/dev/null
        ;;
    record)
        out=${2:?Usage: capture.sh record OUTPUT_DIR}
        mkdir -p "$out"
        adb shell dumpsys gfxinfo "$package" framestats > "$out/frames.txt"
        adb shell dumpsys meminfo "$package" > "$out/memory.txt"
        adb shell dumpsys cpuinfo > "$out/cpu.txt"
        adb shell dumpsys display > "$out/display.txt"
        adb shell getprop ro.build.fingerprint > "$out/device.txt"
        ;;
    startup)
        out=${2:?Usage: capture.sh startup OUTPUT_DIR}
        runs=${3:-10}
        mkdir -p "$out"
        for ((i=0; i<runs; i++)); do
            adb shell am start -W -S -n "$package/io.github.janhurn9400.liquidy.MainActivity" -a android.intent.action.MAIN -c android.intent.category.HOME
            sleep 1
        done > "$out/startup.txt"
        ;;
    *)
        echo "Usage: capture.sh reset | record OUTPUT_DIR | startup OUTPUT_DIR [RUNS]" >&2
        exit 2
        ;;
esac
