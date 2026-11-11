#!/usr/bin/env bash
# Runs the unit tier at low priority on half the cores, so a run by hand does not take over the laptop (W-260, D-522).
# Extra arguments go to Maven. The commit gate no longer calls this (W-267, D-534).
set -uo pipefail

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
cd "$root" || exit 2

nice -n 19 ./mvnw test -pl backend -Punit -Djunit.jupiter.execution.parallel.config.dynamic.factor=0.5 "$@"
