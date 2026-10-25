# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-161 — the April – March year in one place (D-315; audit T2; N2)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-161.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-161 (TLocalDate.financialYear and its callers, CapitalGainsSummaryService:48/:64, YearlyChargeSummary:42, the new reshape/backfill uses of financialYear; TaxYearId.containing vs one shared type — the plan's choice, D-269), write the plan, STOP
Last verify     : unit -Punit 3708/0; integration 303/0 (2026-10-25, W-172)
Waiting on      : nothing for W-161; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-172: backfill the V2 sales' profit, brokerage and misc into the P&L split — from the recorder's rows written before the deploy, each counted once; runbook §2.8, W-173, WL-38 (D-373)
Updated         : 2026-10-25, session 35 — W-172 done and committed; resume at W-161 (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
