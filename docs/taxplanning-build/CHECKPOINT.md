# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156 — deduction, exemption and gift kinds by rule data (D-313; audit P6; N1; likely split when planned)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — read the W-156 row, audit P6 (reviews/2026-10-21-law-in-code-audit.md § W-088l1)
Approved        : W-155 closed (D-336 – D-342)
Steps done      : —
Next step       : read W-156's row and audit P6, write plans/W-156.md (likely a split), explain it, ask approval (do NOT code). Also still open: where to place the REG basis-set finding (STATE Notes for resume, session 30)
Last verify     : targeted 599/0; unit -Punit 3635/0; integration 281/0 (2026-10-23, W-155d)
Waiting on      : the owner — placement of the REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-155d: put OtherSourcesNode.reaches onto RowReach.holdsFor — the item-condition test written once for the perquisite gate, the retirement rows and other sources (D-342; W-155 closed)
Updated         : 2026-10-23, session 31 — W-155d approved and committed, W-155 closed; next W-156
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
