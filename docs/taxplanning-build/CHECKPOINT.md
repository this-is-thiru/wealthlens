# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156f — gift kinds by rule data (D-343; R8)
Stage           : NOT_STARTED — W-156e done and committed (D-350)
Plan            : plans/W-156f.md (to write)
Approved        : W-156 split (D-343); W-156a – e done (D-344 – D-350)
Steps done      : —
Next step       : on the owner's go: READ W-156f (03-WORK-ITEMS.md, plans/W-156.md R8), write plans/W-156f.md, STOP for approval. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding (STATE Notes for resume, session 31)
Last verify     : targeted 307/0; LeaveTravelExemptionTest 25/0 after case 3; unit -Punit 3653/0; integration 281/0 (2026-10-23, W-156e)
Waiting on      : the owner — go for W-156f; placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156e: take salary's employer fund, employer kind, journey mode and per-child allowances from rows — employer.fund, employer.kind and journey.mode conditions, SAL-INCLUDES-PENSION-CONTRIBUTION and CapShape.countsRead, so the salary nodes hold no kind test (D-350)
Updated         : 2026-10-23, session 32 — W-156e committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
