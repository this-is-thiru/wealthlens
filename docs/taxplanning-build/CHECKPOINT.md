# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-063 — SalaryDeductionNode
Stage           : NOT_STARTED
Plan            : — (plans/W-063.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-063 row ("professional tax only in the opted-out regime"; DR 01 R8), rulebook R8 SAL-STDDED-*/SAL-PTAX-*, D-089 (W-063 brings PROFESSIONAL_TAX with SAL-PTAX-001, both years, cashNature sourced); write plans/W-063.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1898 pass, 0 fail, 0 Checkstyle violations (W-062)
Waiting on      : nothing for W-063; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open. Carried from W-062: TY 2025-26 goldens log PROVISIONAL (two UNVERIFIED salary-base versions); commission-in-salary-base is W-064's data decision
Files touched   : —
Last item commit: taxplanning W-062: add the salary bases — basic, and dearness allowance where the terms provide, per employer (D-092)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
