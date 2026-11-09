#!/usr/bin/env bash
# Sourced by unit-tier.sh and pre-commit-gate.sh (W-260, D-522).
#
# The id of every file the unit tier could read: tracked, staged or untracked, ignored files left out. The gate tests
# the working tree, not the index, so the id must cover the working tree. It is a git tree id written through a
# throwaway index, so the real index is never touched.
working_tree_id() {
    local index status
    index=$(mktemp) || return 1
    cp "$(git rev-parse --git-dir)/index" "$index" 2>/dev/null
    GIT_INDEX_FILE="$index" git add -A >/dev/null 2>&1 && GIT_INDEX_FILE="$index" git write-tree
    status=$?
    rm -f "$index"
    return $status
}

# Where the id of the last working tree the unit tier passed on is kept; inside .git, so it is never committed.
unit_tier_stamp() {
    echo "$(git rev-parse --git-dir)/unit-tier-green"
}
