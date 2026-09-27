# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-071b — specified-employee and medical-travel thresholds (D-110)
Stage           : BLOCKED — TY 2025-26 sourcing
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's sourcing: the 2025 Income-tax Rules, 1962 amendment prescribing the s.17(2)(iii)(c) salary amount and the medical-travel gross total income after FA 2025 s.9 (from AY 2026-27 = TY 2025-26); the Department's consolidated Rules 1962 rule 3 for AY 2026-27 (unblocks W-071c–e too); the Cost Inflation Index notifications 2023-24 onward (lifts W-071a's continued-accommodation refusal). Then READ the W-071b row, write plans/W-071b.md, STOP. Or the owner re-places W-071b–e and a later item starts (R4)
Last verify     : ./mvnw clean test -pl backend -Punit → 2193 pass, 0 fail, 0 Checkstyle violations (W-071a)
Waiting on      : the owner — sourcing or re-placement. W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5, OQ-6, OQ-7, OQ-8 open. Carried: TY 2025-26 goldens PROVISIONAL; DIS-S202-2A, SAL-HRA-006, SAL-LTA-CHILD-001 owed; nil lines for LTA/allowances proposed (D-104); EmployerKind vs EmployerType proposed (D-105); the CII cap for continued accommodation (D-111 C5a)
Files touched   : —
Last item commit: taxplanning W-071a: add the perquisite node and residential accommodation — Table I by the 2011-census band, lease, hotel and furniture, on the accommodation salary (D-110, D-111)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
