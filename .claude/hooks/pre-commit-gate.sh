#!/usr/bin/env bash
# Blocks `git commit` from a Claude Code session unless Maven's `validate` phase passes: Checkstyle and
# spotless:check, the enforced half of CLAUDE.md "Engineering standards" (decision D-045, item W-045a). It runs no
# test: the pipeline runs every tier, and the owner does not want them run again on the laptop at each commit
# (W-267, D-534). A failing test is therefore found by the pipeline, not here.
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

# The test seam: pre-commit-gate-test.sh points this at a stand-in, so the gate is tested without a build.
mvnw="${COMMIT_GATE_MVNW:-./mvnw}"
if output=$("$mvnw" -q validate 2>&1); then
    exit 0
fi

{
    echo "Commit blocked by .claude/hooks/pre-commit-gate.sh (CLAUDE.md Engineering standards, W-045a)."
    echo "Checkstyle or spotless:check failed (./mvnw validate). Fix the cause; do not bypass the gate."
    echo "--- last 60 lines ---"
    echo "$output" | tail -60
} >&2
exit 2
