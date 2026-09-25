# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045l — the assessee family (D-039)
Stage           : NOT_STARTED
Plan            : — (plans/W-045l.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045l row and only the sections it names; write plans/W-045l.md; STOP for approval. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1476 pass, 0 fail, 0 Checkstyle violations (W-045k2)
Waiting on      : nothing for W-045l; D-054 startup wiring UNOWNED. OQ-1..OQ-3 open
Files touched   : —
Last item commit: taxplanning W-045k2: add the investments-deduction and HRA catalogue entries — every link NotModelled until W-076 and W-067 (D-061)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
