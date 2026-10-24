# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156h — the self-disability-claim bar on a dependant by rule data (D-353; W-156g C3a)
Stage           : NOT_STARTED — W-156g done and committed (D-353)
Plan            : plans/W-156h.md (to write)
Approved        : W-156a – g done (D-344 – D-353)
Steps done      : —
Next step       : on the owner's go: READ W-156h (03-WORK-ITEMS.md, plans/W-156g.md C3, plans/W-156c.md), write plans/W-156h.md, STOP for approval. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding
Last verify     : targeted 148/0; unit -Punit 3660/0 (2026-10-24, W-156g)
Waiting on      : the owner — go for W-156h; placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156g: confirm the TY 2025-26 "dependant" from the 1961 s.80DD and s.80DDB Explanations — both quoted in DED-DEPENDANT's note and provenance, s.80DD's s.80U limb placed as W-156h (D-353)
Updated         : 2026-10-24, session 33 — W-156g committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
