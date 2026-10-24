# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156g — source the 1961 "dependant" for s.80DD and s.80DDB (D-348; W-156c C4a)
Stage           : NOT_STARTED — W-156f2 done and committed (D-352)
Plan            : plans/W-156g.md (to write)
Approved        : W-156a – f2 done (D-344 – D-352)
Steps done      : —
Next step       : on the owner's go: READ W-156g (03-WORK-ITEMS.md, plans/W-156c.md C4), write plans/W-156g.md, STOP for approval. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding
Last verify     : targeted 492/0; unit -Punit 3660/0; integration 281/0 (2026-10-24, W-156f2)
Waiting on      : the owner — go for W-156g; placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156f2: measure each gift by its own limb's row — OS-GIFT as limb (i) and OS-GIFT-IMMOVABLE, -PROPERTY-FREE, -PROPERTY-BOUGHT on gift.kind and gift.received, each with its own GiftThreshold, so GiftIncome holds no kind test (D-352)
Updated         : 2026-10-24, session 33 — W-156f2 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
