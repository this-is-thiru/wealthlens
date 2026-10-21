# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088k4b — correct bonus lots processed before W-088k4a (D-306 C2a)
Stage           : READY — not started
Plan            : plans/W-088k4b.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088k4b": read its WI row, D-306, D-307, runbook §2 entries and their endpoints (W-088u7/u8c pattern: SUPER_USER, dryRun default, idempotent, integration test), D-280, D-286 (Phase 15 removal row, YouTrack child under WL-28), how an existing action gets its allotment_date (endpoint vs runbook command), which lots/transactions belong to a bonus action (corporate_actions list, corporate_action_type BONUS, ex-date); write plans/W-088k4b.md, STOP
Last verify     : unit clean -Punit 3218 run, 0 fail; integration 273/0 (2026-10-21, W-088k4a)
Waiting on      : the owner — "start W-088k4b"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088k4a: date a bonus lot from its allotment — corporate_action.allotment_date, performing a bonus without it refused, the engine's caveat narrowed to older lots; W-088k4 split k4a/k4b (D-306, D-307)
Updated         : 2026-10-21, session 27 — W-088k4a done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
