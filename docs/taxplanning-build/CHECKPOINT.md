# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u — size and SRP (review S4)
Stage           : READY — not started
Plan            : none yet; WI row W-088u
Approved        : —
Steps done      : —
Next step       : on "start W-088u": read, write plans/W-088u.md, STOP
Last verify     : unit clean -Punit 3083 run, 0 fail; integration 252/0 (2026-10-19, W-088t)
Waiting on      : the owner — "start W-088u"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088t: derive a date's tax year in one place — TaxYearId.containing from the type's own boundaries, HousePropertyInterest's inline April rule removed (D-269)
Updated         : 2026-10-19, session 24 — W-088t approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
