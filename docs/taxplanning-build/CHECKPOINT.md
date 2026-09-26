# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-058 — metamorphic property tests
Stage           : NOT_STARTED
Plan            : — (plans/W-058.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-058 row (DR 19 E4.1; "moreIncomeNeverMeansLessTax would have caught H-1 unaided") and DR 19 E4.1; check what property-test tooling the build has (jqwik or none) before proposing any; write plans/W-058.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1826 pass, 0 fail, 0 Checkstyle violations (W-057)
Waiting on      : nothing for W-058; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-057: add the T2 golden tests — every DR 08 surcharge case end to end in both years (D-087)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
