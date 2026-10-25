# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-162 — deductibility of a charge as tax-side rule data (D-315; audit T4)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-162.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-162 (ChargeCatalogueEntity.deductible_for_capital_gains :69–70, ChargeDeductibilityService and its readers — TradeOutcomeRecorder's deductible buy/sell charges, CapitalGainsSummaryService's gain; the engine's s.48 rows if any; the charge catalogue seed JSON), write the plan, STOP
Last verify     : unit -Punit 3715/0; integration 303/0 (2026-10-25, W-161)
Waiting on      : nothing for W-162; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-161: put the April – March year in one place — shared FinancialYear replaces TLocalDate.financialYear and every hand-built window outside the engine; TaxYearId kept (D-374)
Updated         : 2026-10-25, session 35 — W-161 done and committed; resume at W-162 (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
