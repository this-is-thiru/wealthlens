# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-052b — from the runner's result to a TaxComputation
Stage           : NOT_STARTED
Plan            : — (plans/W-052b.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-052b section of 03-WORK-ITEMS.md (STATE Q3; PipelineRunner.Result from W-042, TaxComputation from W-043) and only what it names; write plans/W-052b.md with the D-043 standing-rules line; STOP for approval. Known: the row names NodeIds.TOTAL_INCOME / TAX_BY_BASIS / NET_PAYABLE, but the generated constants are TOTALINCOME / TAX_BYBASIS / TAX_NETPAYABLE (check which slots the anchors really are — e.g. round.totalincome vs totalincome, round.netpayable vs tax.netpayable)
Last verify     : ./mvnw clean test -pl backend -Punit → 1585 pass, 0 fail, 0 Checkstyle violations (W-052)
Waiting on      : nothing for W-052b; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-052: add NetPayableNode — the whole tax after rebate plus the cess, untraced as a sum of traced figures (D-080)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
