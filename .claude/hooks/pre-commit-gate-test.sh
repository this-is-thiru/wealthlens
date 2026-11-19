#!/usr/bin/env bash
# Proves the commit gate (W-267, D-534) in a throwaway directory, with a stand-in for ./mvnw that records the goals it
# is given: a command that is not a commit is not checked; a commit runs `validate` and nothing that runs a test; a
# failing `validate` blocks the commit. Run: .claude/hooks/pre-commit-gate-test.sh (exit 0 = all cases pass).
set -uo pipefail

hooks="$(cd "$(dirname "$0")" && pwd)"
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
failures=0

cat > "$repo/fake-mvnw" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_GOALS"
exit "${FAKE_STATUS:-0}"
FAKE
chmod +x "$repo/fake-mvnw"

export CLAUDE_PROJECT_DIR="$repo" COMMIT_GATE_MVNW="$repo/fake-mvnw" FAKE_GOALS="$repo/goals"
: > "$FAKE_GOALS"

gate() { echo "{\"tool_input\":{\"command\":\"$1\"}}" | "$hooks/pre-commit-gate.sh" 2>/dev/null; }
expect() {  # name, command, expected gate exit, expected goals recorded so far
    local name=$1 want_exit=$3 want_goals=$4 got_exit got_goals
    gate "$2"; got_exit=$?
    got_goals=$(paste -sd'|' "$FAKE_GOALS")
    if [ "$got_exit" = "$want_exit" ] && [ "$got_goals" = "$want_goals" ]; then
        echo "PASS $name"
    else
        echo "FAIL $name: exit $got_exit (want $want_exit), goals '$got_goals' (want '$want_goals')"
        failures=$((failures + 1))
    fi
}

expect "a command that is not a commit is not checked" "git status" 0 ""
expect "a commit runs validate and no test goal" "git add a.txt && git commit -m x" 0 "-q validate"
FAKE_STATUS=1 expect "a failing validate blocks the commit" "git commit -m x" 2 "-q validate|-q validate"
expect "and the next commit is checked again" "git commit -m x" 0 "-q validate|-q validate|-q validate"

[ "$failures" -eq 0 ] && echo "all cases pass" || echo "$failures case(s) failed"
exit "$failures"
