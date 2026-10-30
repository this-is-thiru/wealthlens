# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-203 — NOT STARTED (the seeders drop unknown keys silently; next on the board after W-202)
Stage           : PAUSED at the session limit (session 40) — W-202 done (D-406), committed; everything pushed (api-collection main first, then this branch)
Plan            : — (write plans/W-203.md on "start W-203")
Approved        : —
Steps done      : —
Next step       : resume: Resume check; then on "start W-203": board order (R4: W-202 D  W-203 ·  W-204 ·); READ the W-203 row; write plans/W-203.md; STOP. FinishedPlanTest fails a DONE item with an open plan step; request bodies are strict (D-406)
Last verify     : W-202 — targeted 48/0; probe (property off → both strictness tests fail); unit 3817/0; integration 314/0 (2026-10-30)
Waiting on      : the owner — "start W-203"; OQ-32, OQ-33, OQ-34 open
Files touched   : (none)
Last item commit: taxplanning W-202: refuse a request body property the DTO does not have with a 400 — fail-on-unknown-properties on, D-389's date tolerance superseded, the collection's four stale fields fixed; W-203 and W-204 placed (D-406)
Updated         : 2026-10-30 ~23:50, session 40 — PAUSED at the session limit; W-187 … W-202 done (D-396 – D-406), all committed and pushed; next W-203
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
