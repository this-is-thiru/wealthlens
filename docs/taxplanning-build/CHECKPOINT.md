# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-057 — T2 golden tests
Stage           : NOT_STARTED
Plan            : — (plans/W-057.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-057 row ("copy expected values"), DR 08 T2-1…T2-5 (T2-6 moved to W-088, D-086), W-053's T1 golden set-up (GoldenCase JSON, GoldenCaseRunner, T1SlabAndRebateTest); write plans/W-057.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1778 pass, 0 fail, 0 Checkstyle violations (W-056)
Waiting on      : nothing for W-057; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-056: add SurchargeNode — the greater ladder rate on the tax after rebate, less W = U + V priced by recomputation (D-086)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
