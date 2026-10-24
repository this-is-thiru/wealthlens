# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-157 — house-property occupancy (D-313; audit P7, borderline)
Stage           : NOT_STARTED — W-156h done and committed; W-156 closed (D-354)
Plan            : plans/W-157.md (to write)
Approved        : W-156 closed (a – h, D-344 – D-354)
Steps done      : —
Next step       : on the owner's go: READ W-157 (03-WORK-ITEMS.md, the audit's P7 row), write plans/W-157.md, STOP for approval. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding
Last verify     : targeted 222/0; unit -Punit 3661/0; integration 281/0 (2026-10-24, W-156h)
Waiting on      : the owner — go for W-157; placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156h: take the bar on a dependant who claims their own disability deduction from a row — DED-DEPENDANT-OWN-CLAIM on dependant.claims.own.disability, so DisabilityDeduction holds no s.127(8) test; W-156 closed (D-354)
Updated         : 2026-10-24, session 33 — W-156h committed, W-156 closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
