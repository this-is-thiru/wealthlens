# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-073b — the accretion on the excess employer contribution (D-125)
Stage           : EXPLAINED — plans/W-073b.md (C1–C5), awaiting approval
Plan            : plans/W-073b.md
Approved        : —
Steps done      : —
Next step       : RESUME HERE (session 11): run the resume check; then WAIT for "W-073b approved …" and build. Alternatives the owner may pick instead: "start W-073c" (sourced, Round 5; plan must read 1961 Sch. IV Pt A r.6 + the "salary" for 12%), "start W-074" (T4 goldens).
Last verify     : ./mvnw clean test -pl backend -Punit → 2350 pass, 0 fail, 0 Checkstyle violations (W-073a)
Waiting on      : the owner — approval of W-073b (or a different pick). OQ-10 (whom the car is a perquisite for), OQ-11 (CII divisor before 2023-24), OQ-12 (club exclusions, TY 2026-27), OQ-13 (Sch. III Sl. 10 literal reading) open. Review notes: W-071c wear and tear by months, Sl. 2(b) without records; W-071e2 interest paid on a reimbursed medical loan; W-071b: the medical perquisite reaches income under Salaries, not gross salary. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105)
Files touched   : — (all committed: 69bd66f docs, W-073b plan)
Last item commit: taxplanning W-073a: add the aggregate employer contribution — PF, NPS and superannuation together beyond seven lakh fifty thousand, salary on a line of its own (D-126)
Updated         : 2026-09-28, session 10 (paused at the owner's request — session limit)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
