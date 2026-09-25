# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-055 — surcharge marginal-relief parameterisations
Stage           : NOT_STARTED
Plan            : — (plans/W-055.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-055 row (Phase 6 table + D-084 erratum: uses ThresholdLadderCeiling from W-048; W = U + V, one implementation, four parameterisations) and DR 06 G3.2, rulebook RATE-SUR-MR-001 (FA 2026 Part I-A/I-B Para F Table 2; s.2(5); s.3(5) Table Sl. 6) — read those tables; write plans/W-055.md with the D-043 standing-rules line; STOP for approval. Known: the rows carry a relief (W-049a) with no node until W-056 → join NOT_YET_EVALUATED (D-084); thresholds are the ladders' band lower bounds per D-050 — decide whether the relief rows restate them or W-056 derives them
Last verify     : ./mvnw clean test -pl backend -Punit → 1712 pass, 0 fail, 0 Checkstyle violations (W-054)
Waiting on      : nothing for W-055; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-054: add the surcharge rows for both regimes and years — two ladders per regime, as the Act tests two incomes (D-084)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
