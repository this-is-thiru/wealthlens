# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045g2 — the provider serves packs compiled from the timeline (D-054)
Stage           : NOT_STARTED
Plan            : — (plans/W-045g2.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045g2 row, the W-045f and W-045g errata on it, and only the sections it names; write plans/W-045g2.md; STOP for approval. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw test -pl backend -Punit → 1446 pass, 0 fail, 0 Checkstyle violations (W-045g)
Waiting on      : B4 — who owns startup wiring + JAR loading (UNOWNED, D-054). OQ-1..OQ-3 open
Files touched   : —
Last item commit: taxplanning W-045g: compile a year's rule pack from the timeline and pin it with a lock — equal to today's 2026-27 pack (D-054)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
