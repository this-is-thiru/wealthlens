# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-064a — entertainment allowance
Stage           : NOT_STARTED
Plan            : —
Approved        : —
Steps done      : —
Next step       : READ the W-064a row (03-WORK-ITEMS.md; D-094, D-095): SAL-ENTMT-001, 1961 s.16(ii), Government employer type, ENTERTAINMENT_ALLOWANCE, the salary measure, SalaryDeductionNode's least-of; TY 2025-26 old regime only; write plans/W-064a.md; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 2110 pass, 0 fail, 0 Checkstyle violations (W-070)
Waiting on      : nothing for W-064a. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062, W-066a); DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; W-066c, W-066d after W-064a (blocked on sourcing); nil lines for LTA and allowances in an excluding regime proposed (D-104)
Files touched   : —
Last item commit: taxplanning W-070: add the T3 golden tests — every HRA case end to end, the new regime's nil stated, TY 2025-26 reported apart (D-104)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
