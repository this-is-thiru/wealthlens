# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-158d — a gift for consideration and pre-construction interest on another use, from rows (D-356; S5, S7)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-158d.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-158d (GiftReceived:33, OS-GIFT rows; HousingLoan:47, the pre-construction rows, HousePropertyInterest:160), write the plan, STOP
Last verify     : targeted 202/0; unit -Punit 3708/0; integration 281/0; dump identical; probes 3/3 bite (2026-10-24, W-158c)
Waiting on      : nothing for W-158d; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33
Files touched   : (none)
Last item commit: taxplanning W-158c: take which facts an employer vehicle may carry from Table II — VehicleFacts refuses who meets the private running where it routes no path, and a chauffeur, cost, charge or higher official use the path's computation does not read; the engine kept as fact consistency (D-359)
Updated         : 2026-10-24, session 34 — W-158c done (D-359)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
