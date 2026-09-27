# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-069 — allowances (W-069a done)
Stage           : IN_PROGRESS — waiting on step 2 (r.2BB as in force for AY 2026-27, from the owner / delegated agent)
Plan            : plans/W-069.md
Approved        : "W-069 approved — proceed as recommended" (D-101; covers W-069a)
Steps done      : 1, 3 (W-069a committed); 2 pending
Next step       : when the owner returns the r.2BB sourcing: verify each quote against its source; record it in plans/W-069.md; then step 4 (components, rows — TY 2025-26 Sl. 13 only if sourced, C1a). If unsourced: TY 2025-26 Sl. 13 not authored, claims refused
Last verify     : ./mvnw clean test -pl backend -Punit → 2027 pass, 0 fail, 0 Checkstyle violations (W-069a)
Waiting on      : nothing for W-069; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-069a: add the allowance inputs — what was spent, or the children and months covered (D-101)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
