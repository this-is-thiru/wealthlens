# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-153d — dividend income for the surcharge by rule data (D-318; K4)
Stage           : READY — not started
Plan            : written when taken (plans/W-153d.md)
Approved        : —
Steps done      : —
Next step       : on "start W-153d": read its WI row, SurchargeNode:100 (DIVIDEND_KINDS) and dividendIncome / interestAllowedOnDividends, DividendInterestAllowance:78 (receivedOnDividends), D-200, D-229, D-234, OQ-30 (units in the 15% pool?); source S2 s.2(40) (Gazette pp.10–11 read in W-153c) and the surcharge provision's "dividend" words; write plans/W-153d.md, STOP
Last verify     : targeted 523/0; unit -Punit 3398/0; integration 281/0 (2026-10-22, W-153c2)
Waiting on      : the owner — "start W-153d"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-153c2: deduct each other-sources item's share by the row reaching its kind — RS-OS-SHARE, every item income line then share then reached expenses, the kind switch and paths gone, OS-DEEMED-DIVIDEND folded into the reach rows (D-323; W-153c closed)
Updated         : 2026-10-22, session 29 — W-153c2 committed, W-153c closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
