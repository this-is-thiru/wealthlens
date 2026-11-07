#!/usr/bin/env bash
# Blocks `git commit` from a Claude Code session unless the unit tier passes. The unit tier's
# `validate` phase runs Checkstyle and spotless:check, so this one call enforces CLAUDE.md
# "Engineering standards" as far as the build can (decision D-045, item W-045a).
set -uo pipefail

command=$(jq -r '.tool_input.command // empty')
case "$command" in
    *"git commit"*) ;;
    *) exit 0 ;;
esac

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
if ! cd "$root" 2>/dev/null; then
    echo "Commit blocked: pre-commit-gate could not find the project root ('$root')." >&2
    exit 2
fi

if output=$(./mvnw -q test -pl backend -Punit 2>&1); then
    exit 0
fi

{
    echo "Commit blocked by .claude/hooks/pre-commit-gate.sh (CLAUDE.md Engineering standards, W-045a)."
    echo "'./mvnw -q test -pl backend -Punit' failed. Fix the cause; do not bypass the gate."
    echo "--- last 60 lines ---"
    echo "$output" | tail -60
} >&2
exit 2
