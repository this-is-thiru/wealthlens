# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-060 — ComponentPartitionNode
Stage           : NOT_STARTED
Plan            : — (plans/W-060.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-060 row (DR 06 G1.1 step 1; "unknown code = error, never a silent addend") and the D-089 erratum (no component ships until its first consuming rule); read EnteredFacts (what components the inputs carry — none yet?), the inputs.components pipeline node, ComponentSet if it exists; decide whether W-060 has anything to partition before W-064 — write plans/W-060.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1855 pass, 0 fail, 0 Checkstyle violations (W-059)
Waiting on      : nothing for W-060; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-059: add the component registry to the timeline — each component arrives with the first rule that reads it (D-089)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
