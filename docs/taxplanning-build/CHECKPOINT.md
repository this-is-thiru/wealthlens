# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (between items)
Stage           : W-088m3 done (D-254); next in order W-088m4 (elections name rule sets)
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's next instruction ("take W-088m4" or another order)
Last verify     : unit -Punit (clean, Checkstyle on) → 3038 run, 0 fail; integration 252/0 (DOCKER_HOST=OrbStack socket) (2026-10-19)
Waiting on      : the owner — next item; OQ-32, OQ-33 open
Files touched   : —
Last item commit: taxplanning W-088m3: fail the build when a node names a rule row — no RuleIds, no RuleId from text, no map to a rule set in taxengine.node; the elections placed as W-088m4 (D-254)
Updated         : 2026-10-19, session 23 — W-088m3 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
