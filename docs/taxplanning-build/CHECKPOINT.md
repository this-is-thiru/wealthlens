# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154c — meals by rule data (D-325; Q5)
Stage           : READ — W-154b committed; plan for W-154c being written
Plan            : plans/W-154c.md (to write)
Approved        : —
Steps done      : —
Next step       : read W-154c WI row, plans/W-154.md Q5 and risks 3 – 4, MealPerquisite, the PERQ-MEAL* rows, MealAndGiftPerquisiteTest; write plans/W-154c.md; STOP
Last verify     : targeted 258/0; unit -Punit 3517/0; integration 281/0 (2026-10-23, W-154b)
Waiting on      : — (writing W-154c plan); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154b: choose each household benefit's Table III row and whom it reaches as rule data — RS-PERQ-HOUSEHOLD by household.kind, the airline / railways journey refused by the carrier row's condition, PERQ-SPECIFIED-EMPLOYEE gating by perquisite.arrangement (D-327)
Updated         : 2026-10-23, session 30 — W-154b done and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
