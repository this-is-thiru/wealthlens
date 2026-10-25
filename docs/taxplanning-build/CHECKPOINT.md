# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160d2 — the P&L's pair and map go (D-366; T1 P6; an API change)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160d2.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160d2 (ProfitAndLossService pair/map writes, :279–286 and isShortTermCapitalGain, its HoldingPeriodService/TradeClassifier; RealisedProfits, RealisedProfitsResponse; AnalyticsService:55–64 moves to the split (W-160d1 C1a); tests and api-collection Req3), write the plan, STOP
Last verify     : unit -Punit 3732/0; integration 288/0 (2026-10-25, W-160d1)
Waiting on      : nothing for W-160d2; open for later: per-bucket term audit record (near W-107/W-160g), keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160d1: write the P&L split by asset type and segment beside the pair — both families, the lot's asset type when the sale has none, the response carries it; W-160d split d1 – d3, W-169 placed (D-366; D-367)
Updated         : 2026-10-25, session 35 — W-160d1 done and committed; resume at W-160d2 (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
