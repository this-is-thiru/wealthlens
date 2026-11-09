#!/usr/bin/env bash
# Proves the commit gate's skip (W-260, D-522) in a throwaway repository, with a stand-in for ./mvnw that counts its
# runs: a tree the unit tier passed on is not run again, and any change to it — edited, added, or edited during the
# run — is, as is a tree whose run failed. Run: .claude/hooks/unit-tier-stamp-test.sh (exit 0 = all cases pass).
set -uo pipefail

hooks="$(cd "$(dirname "$0")" && pwd)"
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
failures=0

mkdir -p "$repo/.claude/hooks"
cp "$hooks/pre-commit-gate.sh" "$hooks/unit-tier.sh" "$hooks/working-tree.sh" "$repo/.claude/hooks/"
cat > "$repo/fake-mvnw" <<'FAKE'
#!/usr/bin/env bash
echo run >> "$FAKE_RUNS"
[ -n "${FAKE_EDIT:-}" ] && echo edited-during-run >> "$FAKE_EDIT"
exit "${FAKE_STATUS:-0}"
FAKE
chmod +x "$repo/fake-mvnw"
cd "$repo" || exit 2
git init -q . && git config user.email t@example.invalid && git config user.name t
echo one > a.txt && git add -A && git commit -q -m init

export CLAUDE_PROJECT_DIR="$repo" UNIT_TIER_MVNW="$repo/fake-mvnw" FAKE_RUNS="$repo/.git/fake-runs"
: > "$FAKE_RUNS"

runs() { wc -l < "$FAKE_RUNS" | tr -d ' '; }
gate() { echo '{"tool_input":{"command":"git add a.txt && git commit -m x"}}' | .claude/hooks/pre-commit-gate.sh 2>/dev/null; }
expect() {  # name, expected gate exit, expected total runs
    local name=$1 want_exit=$2 want_runs=$3 got_exit
    gate; got_exit=$?
    if [ "$got_exit" = "$want_exit" ] && [ "$(runs)" = "$want_runs" ]; then
        echo "PASS $name"
    else
        echo "FAIL $name: exit $got_exit (want $want_exit), runs $(runs) (want $want_runs)"
        failures=$((failures + 1))
    fi
}

expect "first commit on a tree runs the unit tier" 0 1
expect "the same tree again is not run twice" 0 1
echo two > a.txt
expect "an edited tracked file forces a run" 0 2
echo new > b.txt
expect "an untracked file forces a run" 0 3
git add b.txt
expect "staging alone changes nothing the tests read" 0 3
echo three > a.txt
FAKE_STATUS=1 expect "a failing run blocks the commit" 2 4
expect "after a failure the same tree runs again" 0 5
echo four > a.txt
FAKE_EDIT="$repo/a.txt" expect "a file edited during the run is run again next time" 0 6
expect "and that next run is recorded" 0 7
expect "so the tree after it is skipped" 0 7

[ "$failures" -eq 0 ] && echo "all cases pass" || echo "$failures case(s) failed"
exit "$failures"
