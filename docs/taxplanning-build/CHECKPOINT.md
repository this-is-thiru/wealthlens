# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154e — cars by rule data (D-325; Q8, Q9, Q4)
Stage           : READ — W-154d committed; plan for W-154e being written
Plan            : plans/W-154e.md (to write; may propose e1 / e2)
Approved        : —
Steps done      : —
Next step       : read W-154e WI row, plans/W-154.md Q8 – Q9 and risk 2, MotorCarPerquisite, EmployerVehicle, the PERQ-CAR-* rows, MotorCarPerquisiteTest; write plans/W-154e.md; STOP
Last verify     : targeted 240/0; unit -Punit 3577/0; integration 281/0 (2026-10-23, W-154d)
Waiting on      : — (writing W-154e plan); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154d: choose each expense benefit's row and exclusion as rule data — RS-PERQ-EXPENSE and RS-PERQ-EXPENSE-EXCLUDED (new PERQ-CREDIT-CARD-OFFICIAL, PERQ-TELEPHONE; the club exclusions) by expense.kind and three facts, so ExpenseBenefitPerquisite holds no kind switch or club year test (D-329)
Updated         : 2026-10-23, session 30 — W-154d done and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
