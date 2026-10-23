# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-154f — asset transfer wear and tear by rule data (D-325; Q10)
Stage           : READ — W-154e3 committed (W-154e closed); plan for W-154f being written
Plan            : plans/W-154f.md (to write)
Approved        : —
Steps done      : —
Next step       : read W-154f WI row, plans/W-154.md Q10, AssetPerquisite, the PERQ-ASSET-TRANSFER-* rows, Rate.leftAfter / ofShare, ExpenseAndAssetPerquisiteTest transfer cases; write plans/W-154f.md; STOP
Last verify     : targeted 217/0; unit -Punit 3611/0; integration 281/0 (2026-10-23, W-154e3)
Waiting on      : — (writing W-154f plan); OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-154e3: state the several-cars proviso as rule data — PERQ-CAR-PATH-PARTLY / -OWNRUN / -PRIVATE conditions on vehicle.employer.cars.shared and .position, so MotorCarPerquisite holds no proviso of its own (D-333; W-154e closed)
Updated         : 2026-10-23, session 30 — W-154e3 done and committed; W-154e closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
