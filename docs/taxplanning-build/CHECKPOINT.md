# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u7 — correct stored trade_outcomes rows an earlier rebuild wrote with double-counted buy charges (D-276)
Stage           : READY — not started
Plan            : plans/W-088u7.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088u7": read the runner, TradeOutcomeEntity's fields, W-086a's startup migration (the precedent for rewriting rows); write plans/W-088u7.md — first how to count the affected rows per environment (read-only), the backup, then the correction; STOP. Ask the owner which environments exist and whether the rebuild ever ran there (cannot be known from the repo)
Last verify     : unit clean -Punit 3094 run, 0 fail; integration 252/0 (2026-10-20, W-088u6)
Waiting on      : the owner — "start W-088u7"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u6: allocate the trade_outcomes rebuild's buy charges through LotChargeAllocator — a lot sold across sells is charged once, not again on each later sell; stored rows placed as W-088u7 (D-276)
Updated         : 2026-10-20, session 25 — W-088u6 done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
