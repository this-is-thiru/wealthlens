# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-155d — OtherSourcesNode.reaches onto RowReach.holdsFor (D-339; W-155b proposal)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — OtherSourcesNode.reaches (:192) has RowReach.holdsFor's body; callers by RuleId
Approved        : W-155c closed (D-339 – D-341)
Steps done      : —
Next step       : read W-155d's row, write plans/W-155d.md, explain it, ask approval (do NOT code). Also still open: where to place the REG basis-set finding (STATE Notes for resume, session 30)
Last verify     : targeted 155/0; unit -Punit 3635/0; integration 281/0 (2026-10-23, W-155c2)
Waiting on      : the owner — placement of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-155c2: decide which facts a retirement payment may carry from its rows — CapShape.amountsRead and EntryFacts read the entry row's bases, definition and bar rows, so RetirementPayment holds no entry test (D-341; W-155c closed)
Updated         : 2026-10-23, session 31 — W-155c2 approved and committed, W-155c closed; next W-155d
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
