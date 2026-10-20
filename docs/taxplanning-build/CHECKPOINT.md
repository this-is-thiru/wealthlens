# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u6 — the migration's double-counted buy charge (D-275)
Stage           : READY — not started
Plan            : plans/W-088u6.md not yet written (defect read in plans/W-088u5.md)
Approved        : —
Steps done      : —
Next step       : on "start W-088u6": read TradeMatchingService.matchSellToLots / figures, LotChargeAllocator (B-8, V1's fix) and the migration; write plans/W-088u6.md with a failing-first test (a lot sold twice: buy charges sum to what was paid), STOP
Last verify     : unit clean -Punit 3092 run, 0 fail; integration 252/0 (2026-10-20, W-088u5)
Waiting on      : the owner — "start W-088u6"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u5: cut the four portfolio trade-path methods into named steps — characterization tests first, the figures in TradeFigures; the double-counted buy charge placed as W-088u6 (D-275)
Updated         : 2026-10-20, session 25 — W-088u5 done, W-088u closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
