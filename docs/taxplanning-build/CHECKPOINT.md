# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-067 — creates ExemptionNode: HRA
Stage           : NOT_STARTED
Plan            : — (plans/W-067.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-067 row (DR 06 G1.3; "per period; metro % per period; limitedBy names the limb") with rulebook R9 SAL-HRA-001…006, R-11, OccupationPeriods moved here (D-097), SalaryBases/SAL-SALARY-FOR-HRA (W-062), Days + apportion (W-066), TemporallyApportionedCap (W-066b), salary.income gains salary.exemptions (D-096), OQ-5; write plans/W-067.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1965 pass, 0 fail, 0 Checkstyle violations (W-066b)
Waiting on      : nothing for W-067; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-066b: apply a rule that changed inside the year as it declares — per segment, latest, earliest, or refuse (D-098)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
