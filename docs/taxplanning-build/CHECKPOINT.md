# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-059 — components.json for both years (Phase 7 opens)
Stage           : NOT_STARTED
Plan            : — (plans/W-059.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-059 row (DR 10 E4, DR 15 F-part; "every code has cashNature") and any erratum on it; check what the pack already holds for components (the lock's components layer, ComponentCode, existing timeline files) before proposing any; write plans/W-059.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1850 pass, 0 fail, 0 Checkstyle violations (W-058)
Waiting on      : nothing for W-059; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-058: add the engine's metamorphic properties — bounded marginal cost is what catches a missing relief (D-088)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
