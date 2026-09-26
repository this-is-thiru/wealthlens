# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-056 — SurchargeNode (+ SlabLadder band lookup)
Stage           : NOT_STARTED
Plan            : — (plans/W-056.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-056 row + D-084 and D-085 errata (greater of the two ladders' rates; RATE-SUR-SPLIT-001 two buckets; relief thresholds from the -CEILING rows; Verify empties NOT_YET_EVALUATED) and DR 06 G3.2; write plans/W-056.md with the D-043 standing-rules line; STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1724 pass, 0 fail, 0 Checkstyle violations (W-055)
Waiting on      : nothing for W-056; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-055: add the surcharge marginal-relief rows for both regimes — the Act's Table 2 thresholds, restated (D-085)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
