# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045e — the three status axes (D-036)
Stage           : NOT_STARTED
Plan            : — (plans/W-045e.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045e row in 03-WORK-ITEMS and only the sections it names; write plans/W-045e.md; STOP for approval. (W-045d2, docs only, may be taken instead if the owner says so)
Last verify     : ./mvnw test -pl backend -Punit → 1412 pass, 0 fail, 0 Checkstyle violations (W-045d)
Waiting on      : nothing. OQ-1..OQ-3 open (OQ-3 gates L2 deletion); OQ-4 decided (D-051)
Files touched   : —
Last item commit: taxplanning W-045d: rename rule IDs and regime codes to concepts from the Acts — OQ-4 (D-051)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
