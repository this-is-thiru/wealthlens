# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045a — engineering standards enforced by the build (repo-wide, D-045)
Stage           : EXPLAINED              (NOT_STARTED | EXPLAINED | IN_PROGRESS | AWAITING_REVIEW | BLOCKED)
Plan            : docs/taxplanning-build/plans/W-045a.md
Approved        : —                      (owner's exact words + date, or —)
Steps done      : —
Next step       : ask the owner to approve plans/W-045a.md (or the W-045a/W-045a2 split); then step 1
Last verify     : ./mvnw test -pl backend -Punit → 1410 pass, 0 fail (2026-09-23)
Waiting on      : owner — approval of W-045a's plan; O-1, O-2, O-3 (00-BRIEF.md §12)
Files touched   : —
Last item commit: taxplanning W-043: the half that never moves
Updated         : 2026-09-23, session 4
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
