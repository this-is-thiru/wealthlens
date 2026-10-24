# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-158b — which facts employer accommodation may carry, from its rows (D-356; S2)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-158b.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-158b (EmployerAccommodation:60–61, :73–88; the provider rows' amountsRead / countsRead), write the plan, STOP
Last verify     : targeted 231/0; unit -Punit 3672/0; integration 281/0; dump identical; probes 3/3 bite (2026-10-24, W-158a)
Waiting on      : nothing for W-158b; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33
Files touched   : (none)
Last item commit: taxplanning W-158a: take which facts an expense or household benefit may carry from its rows — Condition.itemFactsRead and ReadFacts refuse a fact no row of the item's kind reads, expense.higher.official.use read by none, the two records' checks gone (W-158 split D-356; D-357)
Updated         : 2026-10-24, session 34 — W-158a done (D-357)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
