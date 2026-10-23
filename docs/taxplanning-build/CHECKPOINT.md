# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-155c2 — which facts a retirement payment may carry, from its rows (D-339; R6)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — R6: a CapShape base walk (nine shapes), the CommutedPensionShare definition, RS-RET-BAR's item facts; RetirementPayment:50–65 and :62; the nine RetirementPaymentTest refusals move to the node
Approved        : W-155c split (D-339); W-155c1 done (D-340)
Steps done      : —
Next step       : read W-155c2's row and plans/W-155c.md, write plans/W-155c2.md, explain it, ask approval (do NOT code). Also still open: where to place the REG basis-set finding (STATE Notes for resume, session 30)
Last verify     : targeted 352/0; unit -Punit 3626/0; integration 281/0 (2026-10-23, W-155c1)
Waiting on      : the owner — placement of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-155c1: take the notified entries' bases from the facts given and B from rows — RS-RET-AGGREGATE-HISTORY's conditions-only rows over a per-entry ledger, so NotifiedRetirementLimits holds no entry test (W-155c split D-339; D-340)
Updated         : 2026-10-23, session 31 — W-155c1 approved and committed; next W-155c2
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
