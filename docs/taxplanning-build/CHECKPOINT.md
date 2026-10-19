# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088q — one carried-loss mechanism (gains the set-off context, D-260)
Stage           : READY — not started
Plan            : not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088q": read, write plans/W-088q.md, STOP
Last verify     : unit clean -Punit 3067 run, 0 fail; integration 252/0 (2026-10-19, W-088p2)
Waiting on      : the owner — "start W-088q"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088p2: give the six remaining long parameter lists their types — s.133's donation groups, the node's context for HRA and retirement, KindRow for whole-deducted items, a Combination for joint elections (D-262)
Updated         : 2026-10-19, session 23 — W-088p2 approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
