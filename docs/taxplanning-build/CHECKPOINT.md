# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (between items)
Stage           : W-088m1 done (D-252); next in order W-088m2 (conditions on the item)
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's next instruction ("take W-088m2" or another order)
Last verify     : unit -Punit (clean, Checkstyle on) → 3020 run, 0 fail; integration 252/0; I3 probe bit (2026-10-19)
Waiting on      : the owner — next item; OQ-32, OQ-33 open
Files touched   : —
Last item commit: taxplanning W-088m1: let the pack choose which rule row a node applies — nodes name rule sets, each row names its set, the six regime and person choices moved from Java to data (D-250 – D-252)
Updated         : 2026-10-19, session 22 — W-088m1 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
