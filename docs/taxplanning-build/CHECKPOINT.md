# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-066a — retirement and termination
Stage           : NOT_STARTED
Plan            : — (plans/W-066a.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-066a row (03-WORK-ITEMS.md; D-096): RetirementNode, RET-* rules and components, 2025 s.18; write plans/W-066a.md; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 2062 pass, 0 fail, 0 Checkstyle violations (W-069b)
Waiting on      : nothing for W-066a; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7 open (OQ-7 handled per D-102). Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-069b: add the transport allowance for a disabled employee — the monthly limit for each month covered, in every regime (D-102)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
