# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154b — household benefits by rule data (D-325; Q2 – Q4)
Stage           : READ — W-154a committed; plan for W-154b being written
Plan            : plans/W-154b.md (to write)
Approved        : —
Steps done      : —
Next step       : read W-154b WI row, plans/W-154.md Q2 – Q4 and risk 1, HouseholdBenefitPerquisite, SpecifiedEmployee, the Table III rows; write plans/W-154b.md; STOP
Last verify     : targeted 183/0; unit -Punit 3482/0; integration 281/0 (2026-10-23, W-154a)
Waiting on      : — (writing W-154b plan); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154a: state which accommodation the continued cap reaches as rule data — PERQ-ACCOM-CONTINUED's accommodation.provider condition, so PerquisiteNode holds no provider test (W-154 split D-325; D-326)
Updated         : 2026-10-23, session 30 — W-154a done and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
