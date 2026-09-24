# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045g3 — remove the per-year copy rulepacks/2026-27/ (D-055)
Stage           : NOT_STARTED
Plan            : — (plans/W-045g3.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045g3 row and the W-045g2 erratum; write plans/W-045g3.md; STOP for approval. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw test -pl backend -Punit → 1447 pass, 0 fail, 0 Checkstyle violations (W-045g2)
Waiting on      : nothing for W-045g3; D-054 startup wiring UNOWNED. OQ-1..OQ-3 open
Files touched   : —
Last item commit: taxplanning W-045g2: load every year from the timeline, refused unless it matches its lock — per-year test packs migrated (D-055)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
