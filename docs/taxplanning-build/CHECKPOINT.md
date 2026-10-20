# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u4 — taxengine methods over 40 lines (W-088u split, D-270; C2a adds two)
Stage           : READY — not started
Plan            : plans/W-088u.md (split); plans/W-088u4.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088u4": re-measure (tools/method_lengths.py 40 over taxengine), read each method, write plans/W-088u4.md, STOP — proof: all-nodes dump; TimelineReader/RulePackCompiler by the canonical pack dump (memory)
Last verify     : unit clean -Punit 3084 run, 0 fail; integration 252/0 (2026-10-20, W-088u3)
Waiting on      : the owner — "start W-088u4"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u3: split ShapeJson per concern — the definition readers to DefinitionJson, the rupee and percentage readers to PackJson (D-273)
Updated         : 2026-10-20, session 25 — W-088u3 done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
