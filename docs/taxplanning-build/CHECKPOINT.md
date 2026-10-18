# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : — (W-080a3 DONE; between items)
Stage           : between items
Plan            : —
Approved        : —
Steps done      : —
Next step       : WAIT for the owner's pick. Next in order: W-079e2 (TY 2025-26 interest allowance shared by dividends and units, OQ-26 Q2: by income share), then W-084, W-084a, W-085. Open OQs: OQ-30 (W-088). UNVERIFIED: 0. Unplaced proposals: none
Last verify     : unit -Punit → 2910 run, 0 fail, 0 Checkstyle (W-080a3, 2026-10-18)
Waiting on      : the owner — next pick
Files touched   : none
Last item commit: taxplanning W-080a3: rename the test-only GoldenCaseRunner.SalaryRun to ShippedPipelineRun — it holds house-property and other-sources runs too, so the old name misled (D-233)
Updated         : 2026-10-18, session 21 — W-080a3 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
