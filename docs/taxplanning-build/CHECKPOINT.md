# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-071e — other benefits: Table IV / r.3(7) (D-110)
Stage           : NOT_STARTED
Plan            : — (plans/W-071e.md once written)
Approved        : —
Steps done      : —
Next step       : Ask the owner first whether to place an item lifting W-071a C5a (the CII cap for continued accommodation — all four indices READ, U3; G.S.R. 636(E) covers leased too). Then READ the W-071e row (DR 01 PERQ-OTHER-2026, PERQ-LOAN-2026; S4 r.15(5) Table IV; r.3(7) — sources V4), write plans/W-071e.md, STOP
Last verify     : ./mvnw clean test -pl backend -Punit → 2250 pass, 0 fail, 0 Checkstyle violations (W-071c)
Waiting on      : nothing for W-071e. OQ-10 (whom the car is a perquisite for) open. Review notes: W-071c wear and tear by months, Sl. 2(b) without records; W-071b: the medical perquisite reaches income under Salaries, not gross salary. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105); the CII cap for continued accommodation (D-111 C5a); the TY 2025-26 CII proviso covers leased too (G.S.R. 636(E))
Files touched   : — (Rule-3_2025-12-13_12-11-50_d5ea0b_en.pdf in the repo root is the owner's download: never commit)
Last item commit: taxplanning W-071c: add the motor car and the specified employee — Table II per calendar month by engine, the employer's car only above four lakh of salary in money (D-113)
Updated         : 2026-09-28, session 10
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
