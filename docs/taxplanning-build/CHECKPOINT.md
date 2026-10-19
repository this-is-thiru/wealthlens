# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088s — shipped years as a manifest
Stage           : READY — not started
Plan            : none yet; WI row W-088s (review E6)
Approved        : —
Steps done      : —
Next step       : on "start W-088s": read, write plans/W-088s.md, STOP
Last verify     : unit clean -Punit 3074 run, 0 fail; integration 252/0 (2026-10-19, W-088r)
Waiting on      : the owner — "start W-088s"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088r: state a rule version's cap as Optional, never null — RuleVersion.cap Optional<CapShape> like its siblings, RuleJson.capOrNull and the readers' null checks gone (D-267)
Updated         : 2026-10-19, session 24 — W-088r approved and committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
