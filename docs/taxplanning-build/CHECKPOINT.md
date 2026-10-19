# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088q3 — the set-off context
Stage           : READY — not started
Plan            : plans/W-088q.md (split); plans/W-088q3.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088q3": read, write plans/W-088q3.md, STOP
Last verify     : unit clean -Punit 3073 run, 0 fail; integration 252/0 (2026-10-19, W-088q2)
Waiting on      : the owner — "start W-088q3"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088q2: set off every carried loss by one mechanism — CarriedLossSetOff over each ledger's portions, CapitalGainsAbsorption for the parts in order; CarriedCapitalLossSetOff removed (D-265)
Updated         : 2026-10-19, session 24 — W-088q2 approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
