# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088q2 — one carried-loss mechanism
Stage           : READY — not started
Plan            : plans/W-088q.md (split); plans/W-088q2.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088q2": read, write plans/W-088q2.md, STOP
Last verify     : unit clean -Punit 3073 run, 0 fail; integration 252/0 (2026-10-19, W-088q1)
Waiting on      : the owner — "start W-088q2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088q1: state each carried loss's head, span and return condition as rule data — LossCarryForward on the two carried-loss rows, the nodes reading it; W-088q split (D-263, D-264)
Updated         : 2026-10-19, session 23 — W-088q1 approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
