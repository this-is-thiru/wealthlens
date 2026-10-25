# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160f — remove portfolio's duplicate holding-period law (D-362; T1 P1, P8; R15)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160f.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160f (holding/ package and every remaining caller — TradeClassifier in TradeOutcomeRecorder for the sub-class; HoldingPeriodSeeder and its endpoint; holding_period_policies collection; CapitalGainsType readers; R15 drop), write the plan, STOP
Last verify     : unit -Punit 3725/0; integration 292/0 (2026-10-25, W-160e)
Waiting on      : nothing for W-160f; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160e: give holdings days held instead of a one-year split — every holdings row carries daysHeld, the SHORT_TERM / LONG_TERM endpoints and their Excel go (D-370)
Updated         : 2026-10-25, session 35 — W-160e done and committed; resume at W-160f (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
