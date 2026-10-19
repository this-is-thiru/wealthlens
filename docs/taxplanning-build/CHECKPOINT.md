# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088r — no null across a boundary
Stage           : READY — not started
Plan            : none yet; WI row W-088r (review S3)
Approved        : —
Steps done      : —
Next step       : on "start W-088r": read, write plans/W-088r.md, STOP
Last verify     : unit clean -Punit 3073 run, 0 fail; integration 252/0 (2026-10-19, W-088q3)
Waiting on      : the owner — "start W-088r"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088q3: give this year's set-offs one context and the head's own set-off the shared absorption — SetOffContext in place of seven-parameter lists, CapitalGainsNode on CapitalGainsAbsorption and ctx.traceLine (D-266)
Updated         : 2026-10-19, session 24 — W-088q3 approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
