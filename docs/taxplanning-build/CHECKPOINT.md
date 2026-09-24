# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045j — the coverage register (D-038)
Stage           : NOT_STARTED
Plan            : — (plans/W-045j.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045j row and only the sections it names; write plans/W-045j.md; STOP for approval. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1447 pass, 0 fail, 0 Checkstyle violations (W-045i)
Waiting on      : nothing for W-045j; D-054 startup wiring UNOWNED. OQ-1..OQ-3 open
Files touched   : —
Last item commit: taxplanning W-045i: generate NodeIds from the timeline's node catalogue — answers STATE Q1 (D-058)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
