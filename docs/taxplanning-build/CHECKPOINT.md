# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-066d — retirement limits set by other Acts (D-103, D-106): Sl. 5 (Payment of Gratuity Act) and Sl. 10 whole (retrenchment)
Stage           : BLOCKED — OQ-9
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's answer to OQ-9 (read the repealed PGA/IDA references as the Codes' s.53 / s.70(b), or refuse until CBDT speaks); then READ the W-066d row + sources/2026-09-27-retirement-limits.md, write plans/W-066d.md, STOP. Or the owner places W-066d later and Phase 8 (W-071) starts (R4)
Last verify     : ./mvnw clean test -pl backend -Punit → 2152 pass, 0 fail, 0 Checkstyle violations (W-066c)
Waiting on      : the owner — OQ-9 or re-placement. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8, OQ-9 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread; HRA/LTA rows); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105)
Files touched   : —
Last item commit: taxplanning W-066c: add other gratuity and leave encashment — notified limits, the ten-month average, capped across employers and years (D-106)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
