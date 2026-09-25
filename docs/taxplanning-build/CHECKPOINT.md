# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045n — TY 2025-26 sources for the Phase 5 rules only (research, no code; D-041)
Stage           : NOT_STARTED
Plan            : — (plans/W-045n.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045n row and only the sections it names; write plans/W-045n.md; STOP for approval. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1481 pass, 0 fail, 0 Checkstyle violations; TaxPlanningControllerIntegrationTest 14/14 (W-045m)
Waiting on      : nothing for W-045n; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045m: add a known-errors notice to the old compute response — body shape kept, removed with the endpoint (D-063, D-064)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
