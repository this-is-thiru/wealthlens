# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-158c — which facts an employer vehicle may carry, from Table II's rows (D-356; S3)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-158c.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-158c (EmployerVehicle:62–90; MotorCarPerquisite; Table II rows' conditions and caps), write the plan, STOP
Last verify     : targeted 240/0; unit -Punit 3692/0; integration 281/0; dump identical + 4 new records; probes 3/3 bite (2026-10-24, W-158b)
Waiting on      : nothing for W-158c; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33
Files touched   : (none)
Last item commit: taxplanning W-158b: take which facts employer accommodation may carry from its rows — AccommodationFacts asks the provider, continued and furniture rows, PERQ-ACCOM-FURNITURE reaches Table I Sl. 1 and 2 only, a first year's amount no cap reads refused (D-358)
Updated         : 2026-10-24, session 34 — W-158b done (D-358)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
