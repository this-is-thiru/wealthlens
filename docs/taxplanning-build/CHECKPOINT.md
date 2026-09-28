# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-074 — T4 golden tests (DR 08 T4-1 … T4-4)
Stage           : NOT_STARTED
Plan            : — (plans/W-074.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-074 row and DR 08 T4-1 … T4-4, write plans/W-074.md, STOP. W-073b/c BLOCKED: need Rules 1962 r.3B (AY 2026-27) and the notified recognised-PF interest rate (both years); W-079 needs r.9D and its Rules 2026 successor
Last verify     : ./mvnw clean test -pl backend -Punit → 2350 pass, 0 fail, 0 Checkstyle violations (W-073a)
Waiting on      : nothing for W-071e. OQ-10 (whom the car is a perquisite for), OQ-11 (CII divisor before 2023-24), OQ-12 (club exclusions, TY 2026-27), OQ-13 (Sch. III Sl. 10 literal reading) open. Review notes: W-071c wear and tear by months, Sl. 2(b) without records; W-071e2 interest paid on a reimbursed medical loan; W-071b: the medical perquisite reaches income under Salaries, not gross salary. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105)
Files touched   : —
Last item commit: taxplanning W-073a: add the aggregate employer contribution — PF, NPS and superannuation together beyond seven lakh fifty thousand, salary on a line of its own (D-126)
Updated         : 2026-09-28, session 10
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
