# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045k — the section catalogue (D-042)
Stage           : NOT_STARTED
Plan            : — (plans/W-045k.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045k row and only the sections it names; write plans/W-045k.md; STOP for approval. Row carries a ⚠ size risk. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1456 pass, 0 fail, 0 Checkstyle violations (W-045j)
Waiting on      : nothing for W-045k; D-054 startup wiring UNOWNED. OQ-1..OQ-3 open
Files touched   : —
Last item commit: taxplanning W-045j: add the coverage register and its build checks — seeded with the sections the timeline cites (D-059)
Updated         : 2026-09-24, session 5
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
