# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-066d — retirement limits set by other Acts (D-103, D-106, D-107): Sl. 5 (Payment of Gratuity Act) and Sl. 10 whole (retrenchment)
Stage           : NOT_STARTED
Plan            : — (plans/W-066d.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-066d row, D-107 (OQ-9: read PGA s.4 / IDA s.25F(b) as CSS s.53 / IRC s.70(b)) and sources/2026-09-27-retirement-limits.md; write plans/W-066d.md; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 2153 pass, 0 fail, 0 Checkstyle violations (W-063a)
Waiting on      : nothing for W-066d. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread; HRA/LTA rows); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105)
Files touched   : —
Last item commit: taxplanning W-063a: cite the Taxation Laws (Amendment) Act 2025 for the TY 2025-26 new-regime standard deduction — the proviso extended to s.115BAC(1A)(iii) (D-108)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
