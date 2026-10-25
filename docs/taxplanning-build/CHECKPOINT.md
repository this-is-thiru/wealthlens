# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160d — the P&L by facts (D-362; T1 P6; R15)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160d.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160d (RealisedProfits short/long pair and gains_by_classification, ProfitAndLossService:279–286 and its writers/readers, RealisedProfitsResponse, V1/V2 P&L writes; the reshape/rebuild R15 operation), write the plan, STOP
Last verify     : unit -Punit 3721/0; integration 287/0 (2026-10-25, W-160c)
Waiting on      : nothing for W-160d; open for later: per-bucket term audit record (near W-107/W-160g), keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160c: stop storing portfolio's term on trade outcomes — no writer sets capital_gains_type or classification_reason; a one-time SUPER_USER removal of the stored values, runbook §2.5, W-168, WL-35 (D-365)
Updated         : 2026-10-25, session 35 — W-160c done and committed; resume at W-160d (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
