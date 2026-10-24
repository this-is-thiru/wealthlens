# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156f2 — gift measure by limb (D-351; W-156f C2a)
Stage           : NOT_STARTED — W-156f done and committed (D-351)
Plan            : plans/W-156f2.md (to write)
Approved        : W-156a – f done (D-344 – D-351)
Steps done      : —
Next step       : on the owner's go: READ W-156f2 (03-WORK-ITEMS.md, plans/W-156f.md C2), write plans/W-156f2.md, STOP for approval. Also open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding
Last verify     : targeted 441/0; unit -Punit 3655/0; integration 281/0 (2026-10-24, W-156f)
Waiting on      : the owner — go for W-156f2; placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156f: take gifts' grounds of exclusion from rows — OS-GIFT-EXCLUSION on gift.exclusion and gift.kind, and gift.exclusion on OS-GIFT-COVID-DEATH, so GiftIncome holds no ground test (D-351)
Updated         : 2026-10-24, session 33 — W-156f committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
