#!/usr/bin/env bash
# Runs the unit tier at low priority on half the cores, and records the working tree it passed on, so that the commit
# gate does not run it again on the same files (W-260, D-522). Extra arguments go to Maven (the gate passes -q).
#
# The tree id is taken before and after the run: a file edited while the tests ran was not what they tested, so no
# id is recorded. A failed run removes the record.
set -uo pipefail

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
cd "$root" || exit 2
# shellcheck source=working-tree.sh
source "$root/.claude/hooks/working-tree.sh"

# The test seam: unit-tier-stamp-test.sh points this at a stand-in, so the stamp logic is tested without a build.
mvnw="${UNIT_TIER_MVNW:-./mvnw}"
stamp=$(unit_tier_stamp)
before=$(working_tree_id)

nice -n 19 "$mvnw" test -pl backend -Punit -Djunit.jupiter.execution.parallel.config.dynamic.factor=0.5 "$@"
status=$?

after=$(working_tree_id)
if [ "$status" -eq 0 ] && [ -n "$before" ] && [ "$before" = "$after" ]; then
    echo "$after" > "$stamp"
else
    rm -f "$stamp"
fi
exit "$status"
