# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088p2 — the other long parameter lists
Stage           : READY — not started
Plan            : plans/W-088p.md (split); plans/W-088p2.md not yet written
Approved        : —
Steps done      : —
Next step       : on "take W-088p2" / "start W-088p2": read, write plans/W-088p2.md, STOP
Last verify     : unit -Punit 3065 run, 0 fail; integration 252/0 (2026-10-19, W-088p1)
Waiting on      : the owner — "start W-088p2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088p1: write trace lines from the node's context — NodeContext.traceLine under the context's own node with the governing version's provenance, the 13 node line(...) helpers removed; W-088o moved, W-088p split (D-259, D-260, D-261)
Updated         : 2026-10-19, session 23 — W-088p1 approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
