# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-045o — coverage.json completed to every section of both Acts (research, no code; D-038)
Stage           : NOT_STARTED
Plan            : — (plans/W-045o.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-045o row and only the sections it names; write plans/W-045o.md; STOP for approval. Expect a size question: both Acts' tables of contents; the 1961 Act's site refused us in W-045n. (W-045d2 may be taken instead if the owner says so)
Last verify     : ./mvnw clean test -pl backend -Punit → 1481 pass, 0 fail (W-045m); W-045n was docs only
Waiting on      : nothing for W-045o; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-045n: source TY 2025-26 for the Phase 5 rules — old-regime rebate figures confirmed, 1961-side cess corrected to FA 2026 s.2(6) (D-065)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
