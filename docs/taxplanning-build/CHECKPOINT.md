# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160c — trade outcomes stop storing a term (D-362; T1 P3; R15)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160c.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160c (TradeMatchingService, TradeOutcomeRecorder, BonusAllotmentDateCorrection writes of capital_gains_type / classification_reason; readers left after W-160b; R15 unset operation, Phase 15 row, YouTrack child under WL-28), write the plan, STOP
Last verify     : unit -Punit 3722/0; integration 281/0 (2026-10-25, W-160b)
Waiting on      : nothing for W-160c; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160b: group capital-gains buckets by facts alone — the bucket, its key and the summary reference carry no stored term; X1.3 erratum (D-364)
Updated         : 2026-10-25, session 35 — W-160b done and committed; resume at W-160c (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
