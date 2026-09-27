# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-066a done; W-066c, W-066d await placement by the owner, D-103)
Stage           : NOT_STARTED
Plan            : —
Approved        : —
Steps done      : —
Next step       : ASK the owner where W-066c (notified retirement limits) and W-066d (Payment of Gratuity Act, ID Act s.25F(b)) go; add their rows to 03-WORK-ITEMS.md and the status board as placed; then start the next item in order
Last verify     : ./mvnw clean test -pl backend -Punit → 2095 pass, 0 fail, 0 Checkstyle violations (W-066a)
Waiting on      : W-066c/d placement (owner). W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7 open (OQ-7 handled per D-102). Carried: TY 2025-26 goldens PROVISIONAL (W-062, W-066a); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-066a: add the retirement deductions — government gratuity, commutation and leave in full, voluntary retirement once to its limit (D-103)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
