# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-172 — backfill V2-written P&L years' profit, brokerage and misc (D-372 C2a; R15)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-172.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-172 (how to tell a V2-written sale or year from a V1 one — trade_outcomes charge breakups, user_charges, P&L charge summary; the split after §2.6; W-160d3's operation as template), write the plan, STOP
Last verify     : unit -Punit 3708/0; integration 292/0 (2026-10-25, W-169)
Waiting on      : nothing for W-172; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-169: record a V2 sale's profit, brokerage and misc in the P&L — folded from the trade-outcome rows the recorder computes, recordSale replaces the sale path; W-172 backfill placed (D-372)
Updated         : 2026-10-25, session 35 — W-169 done and committed; resume at W-172 (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
