# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-071 — perquisite values, both years (Phase 8)
Stage           : NOT_STARTED
Plan            : — (plans/W-071.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-071 row (03-WORK-ITEMS.md; DR 01 R12; "TY 2025-26 values are NEEDS VERIFICATION"), Rules 1962 r.3 (as substituted by G.S.R. 615(E), 18-8-2023, CG-DL-E-18082023-248178; r.3(7)(iii) by G.S.R. 452(E)) and Rules 2026 r.15 (Gazette 271092, from p.1540); write plans/W-071.md; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 2166 pass, 0 fail, 0 Checkstyle violations (W-066d)
Waiting on      : nothing for W-071. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (F(No.2)A 2019, TLA 2019, interim FA 2024 unread; HRA/LTA rows; D-107 reading); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105)
Files touched   : —
Last item commit: taxplanning W-066d: add statutory gratuity and retrenchment — the employer's figure under the labour statute, the repealed Acts read as the Codes (D-109)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
