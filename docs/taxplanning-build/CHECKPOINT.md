# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-162c — the charge catalogue loses its deductibility flag (D-375, D-377; T4)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-162c.md (to write); split plans/W-162.md
Approved        : —
Steps done      : —
Next step       : READ W-162c (ChargeCatalogueEntity:54–70, ChargeCodes.requireDeductibilityDeclared, ChargeSeederService:150, :163–182 backfillDeductibility, ChargeCatalogueRepository's Javadoc, charge-catalogue.json key, ChargeSeederServiceTest:954, :997; the stored charge_catalogue field — op vs owner mongosh, R15), write the plan, STOP
Last verify     : unit -Punit 3766/0; integration 309/0 (2026-10-27, W-162b)
Waiting on      : nothing for W-162c; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-162b: stop storing which charges are deductible on trade outcomes — the recorder, entity and holdings view drop deductible_*, ChargeDeductibilityService removed; runbook §2.9 unsets the stored fields, W-174, WL-39; W-160g widened (D-377)
Updated         : 2026-10-27, session 36 — W-162b committed; WL-A-2 republished after it
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
