# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (between items)
Stage           : W-088f done (D-249); next in order W-088f2 (rate basis and S.O. 5054(E)'s waiver as rule data)
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's next instruction ("take W-088f2" or another order)
Last verify     : unit -Punit (clean) → 3004 run, 0 fail; integration 252/0; acquiredOn probe bit (2026-10-19)
Waiting on      : the owner — next item; OQ-32, OQ-33 open
Files touched   : —
Last item commit: taxplanning W-088f: answer OQ-31 from S.O. 5054(E) and publish how and when each lot was acquired as facts — no law in code, the waiver to rule data in W-088f2 (D-249)
Updated         : 2026-10-19, session 22 — W-088f committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
