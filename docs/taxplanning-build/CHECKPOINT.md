# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-158 — input checks stating legal combinations (D-313; audit P8, borderline)
Stage           : NOT_STARTED — W-157 done and committed (D-355)
Plan            : plans/W-158.md (to write)
Approved        : W-157 done (D-355)
Steps done      : —
Next step       : on the owner's go: READ W-158 (03-WORK-ITEMS.md, the audit's P8 row; HouseProperty:59 and HousingLoan:47 from W-157), write plans/W-158.md, STOP for approval. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding
Last verify     : targeted 260/0; HousePropertyInterestTest 23/0; unit -Punit 3662/0; integration 281/0 (2026-10-24, W-157)
Waiting on      : the owner — go for W-158; placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-157: take house-property occupancy and loan use from rows — house.occupancy on HP-OWN-RESIDENCE and the interest rows, HP-OWN-RESIDENCE-HIGHER-LIMIT on loan.use and loan.lender.certificate, the s.130/s.131 match by loan.purpose; three sites accepted (D-355)
Updated         : 2026-10-24, session 33 — W-157 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
