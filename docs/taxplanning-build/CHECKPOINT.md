# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-066b — TemporallyApportionedCap (applying a mid-year change)
Stage           : NOT_STARTED
Plan            : — (plans/W-066b.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-066b row (DR 18 V5 :205-245; "nothing currently applies a mid-year change"; test each MidYearCombination branch; UNDETERMINED throws; path corrected to rules/temporal by D-097) and W-030's MidYearCombination/validation; use W-066's ProrationBasis (units/apportion); write plans/W-066b.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1946 pass, 0 fail, 0 Checkstyle violations (W-066)
Waiting on      : nothing for W-066b; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2, OQ-5 open. Carried: TY 2025-26 goldens PROVISIONAL (W-062); DIS-S202-2A owed; W-066a before W-070; W-064a after W-070
Files touched   : —
Last item commit: taxplanning W-066: add the proration bases — days, calendar months and completed months, so no caller divides (D-097)
Updated         : 2026-09-27, session 9
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
