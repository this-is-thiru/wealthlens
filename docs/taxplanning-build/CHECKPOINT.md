# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-162b — `portfolio` stops storing deductibility (D-375; T4)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-162b.md (to write); split plans/W-162.md
Approved        : —
Steps done      : —
Next step       : READ W-162b (TradeOutcomeRecorder:179, :198, ChargeDeductibilityService's last reader; TradeOutcomeEntity / TradeOutcomeContext deductible_* fields; ChargeViewAssembler:127–128, AssetCharges.deductibleSellCharges and its tests / export; runbook §2.9 shape from §2.5), write the plan, STOP
Last verify     : unit -Punit 3771/0; integration 303/0 (2026-10-27, W-162a)
Waiting on      : nothing for W-162b; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-162a: let the engine net the capital gain — RS-CG-CHARGE rows decide by year which charges are cost, transfer expenditure or not deducted; portfolio publishes amounts, not a gain (W-162 split D-375; D-376)
Updated         : 2026-10-27, session 36 — W-162a committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
