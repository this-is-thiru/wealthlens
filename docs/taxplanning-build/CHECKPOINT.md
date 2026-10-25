# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160d3 — the stored P&L documents (D-366; T1 P6; R15)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160d3.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160d3 (whether every P&L-recorded sale has a trade_outcomes row; how V1/V2 figures map onto a rebuilt split; corporate-action sells; the R15 operation shape on W-160c's template), write the plan, STOP
Last verify     : unit -Punit 3724/0; integration 286/0 (2026-10-25, W-160d2)
Waiting on      : nothing for W-160d3; open for later: per-bucket term audit record (near W-107/W-160g), keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160d2: drop the P&L's short / long pair and classification map — both families write the split only, the one-year tests go, the response loses the fields, analytics sums the split (D-368)
Updated         : 2026-10-25, session 35 — W-160d2 done and committed; resume at W-160d3 (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
