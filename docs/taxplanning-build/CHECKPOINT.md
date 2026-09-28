# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-071c — motor car and other conveyances, and the specified employee (D-110, D-112)
Stage           : BLOCKED — TY 2025-26 sourcing
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner: the Income Tax Department's consolidated Rules 1962 rule 3 for AY 2026-27 (incometaxindia.gov.in answers 403 here — a local download path will do; unblocks W-071c–e), and the CII FY 2023-24 (348) / 2025-26 (376) Gazette copies (lifts W-071a C5a). Then READ the W-071c row, write plans/W-071c.md, STOP. Or the owner re-places W-071c–e and a later item starts (R4)
Last verify     : ./mvnw clean test -pl backend -Punit → 2216 pass, 0 fail, 0 Checkstyle violations (W-071b)
Waiting on      : the owner — rule 3 sourcing or re-placement. Review note from W-071b: the medical perquisite reaches income under Salaries, not gross salary. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105); the CII cap for continued accommodation (D-111 C5a); the TY 2025-26 CII proviso covers leased too (G.S.R. 636(E))
Files touched   : —
Last item commit: taxplanning W-071b: add medical treatment abroad — treatment and stay beyond what the Reserve Bank permitted, travel when income before it exceeds eight lakh (D-112)
Updated         : 2026-09-28, session 10
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
