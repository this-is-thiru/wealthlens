# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-075c1 DONE)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : run the resume check; then WAIT for the owner. Backlog order: W-073c (needs S.O. 1046(E)), W-075c2 (loan interest), W-075c3 (Agniveer), W-075c4 (80GG), W-075c6 (80G), W-075d (sourcing), W-075a3, W-076; W-075c5 after W-079. Proposed (not placed): an 80D catalogue entry
Last verify     : ./mvnw clean test -pl backend -Punit → 2422 pass, 0 fail, 0 Checkstyle violations (W-075c1, 2026-10-06)
Waiting on      : the owner — next pick. OQ-19, OQ-20 open. OQ-18 open. OQ-16, OQ-17 open. OQ-15 open. OQ-14 open. OQ-10 (whom the car is a perquisite for), OQ-11 (CII divisor before 2023-24), OQ-12 (club exclusions, TY 2026-27), OQ-13 (Sch. III Sl. 10 literal reading) open. Review notes: W-071c wear and tear by months, Sl. 2(b) without records; W-071e2 interest paid on a reimbursed medical loan; W-071b: the medical perquisite reaches income under Salaries, not gross salary. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105)
Files touched   : —
Last item commit: taxplanning W-075c1: add the disability and specified-disease deductions — the assessee's own, a disabled dependant's, and treatment with the senior citizen's limit, TY 2026-27 (D-137, D-138)
Updated         : 2026-10-06, session 11
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
