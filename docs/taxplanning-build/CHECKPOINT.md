# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-159 — retire the legacy taxplanning compute path (D-314; audit Q1; D-063)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-159.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-159 (WI row; Appendix E group L2; D-063, D-314) — the plan first checks taxengine serves what the legacy path serves, and stops if not; write the plan, STOP
Last verify     : targeted 437/0; unit -Punit 3714/0; integration 281/0; dump identical; probes 3/3 bite (2026-10-24, W-158d)
Waiting on      : nothing for W-159; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-158d: take a gift for consideration and pre-construction interest on another use from rows — gift.received on OS-GIFT, loan.use on the pre-construction rows picked per loan, OQ-34 raised; W-158 closed (D-360)
Updated         : 2026-10-24, session 34 — W-158d done, W-158 closed (D-360)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
