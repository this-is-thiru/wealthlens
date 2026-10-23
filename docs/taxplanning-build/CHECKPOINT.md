# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-156d — Chapter VIII's other kinds by rule data (D-343; R6)
Stage           : NOT STARTED — plan to write
Plan            : (none yet) — ChapterEightNode :99 (NPS fund), :193–198 (life premium pay line, verdict), :64–65 (verdict); ScheduleXvDeduction :123–134 (Government employer); LoanInterestDeduction :82 (EV loan row)
Approved        : W-156 split (D-343); W-156a – c done (D-344 – D-348)
Steps done      : —
Next step       : read the R6 sites whole, write plans/W-156d.md, explain it, ask approval (do NOT code). Also open: nil-line proposal and PaidOtherThanInCash (W-156b2), the REG basis-set finding
Last verify     : targeted 220/0; unit -Punit 3641/0; integration 281/0 (2026-10-23, W-156c)
Waiting on      : the owner — placements: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-156c: take disability's severity row and who is a dependant from rows — disability.severity conditions on the four severity rows and DED-DEPENDANT for s.127 and s.128, so DisabilityDeduction holds no severity test or relationship switch (D-348)
Updated         : 2026-10-23, session 31 — W-156c approved and committed; next W-156d
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
