# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-169 — the V2 P&L records profit, brokerage and misc (D-366 C4a)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-169.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-169 (ProfitAndLossService V2 family: InternalContext writes purchase/sell only at :498–511 then; what V2 knows of charges — the ChargeComputation, BuyContext charges; AnalyticsService.totalRealizedProfit; W-160d3's reshape keeps V2 years without profit), write the plan, STOP
Last verify     : unit -Punit 3704/0; integration 292/0 (2026-10-25, W-160f)
Waiting on      : nothing for W-169; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160f: remove portfolio's duplicate holding-period law — the holding package, its policies, entity and CapitalGainsType go; runbook §2.7 drops the collection, W-171, WL-37; W-160 closed but for W-160g (D-371)
Updated         : 2026-10-25, session 35 — W-160f done and committed; resume at W-169 (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
