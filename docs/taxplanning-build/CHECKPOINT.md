# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160b — capital-gains buckets by facts (D-362; T1 P4)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160b.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160b (CapitalGainsSummaryService, CapitalGainsBucket and their readers; keep the transfer venue in the key), write the plan, STOP
Last verify     : unit -Punit 3723/0; integration 281/0 (2026-10-24, W-160a)
Waiting on      : nothing for W-160b; still open: nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160a: let engine rows decide which buckets the capital-gains head takes — CG-SPECULATIVE and CG-DERIVATIVE refused, CG-DEPOSIT left out, the adapter reads no classification (W-160 split D-362; D-363)
Updated         : 2026-10-24, session 34 — PAUSED at the owner's session-limit notice; everything committed; resume at W-160b (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
