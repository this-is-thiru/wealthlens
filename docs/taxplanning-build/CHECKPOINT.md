# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-163 — statutory charges on each broker's card (D-315; audit T3)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-163.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-163 (the audit's T3 row; data/charges/*-equity-*.json — STT, STAMP_DUTY, GST, SEBI_FEE repeated per card; ChargeScheduleResolver / ChargeEngine for how a card's rules are evaluated), write the plan putting both readings to the owner (a shared statutory card the broker cards reference, or accepted as the contract note's bill), STOP
Last verify     : unit -Punit 3760/0; integration 309/0 (2026-10-27, W-162c)
Waiting on      : nothing for W-163; open: api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-162c: remove the charge catalogue's deductibility flag — field, seed gate, backfill and JSON key go, RS-CG-CHARGE is the one place the law lives; runbook §2.10 unsets the stored flag, W-175, WL-40; W-162 closed (D-378)
Updated         : 2026-10-27, session 36 — W-162c committed, W-162 closed; WL-A-2 republished after it
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
