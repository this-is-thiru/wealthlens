# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-160e — holdings by facts (D-362; T1 P7; an API change)
Stage           : NOT_STARTED — plan to write
Plan            : plans/W-160e.md (to write)
Approved        : —
Steps done      : —
Next step       : READ W-160e (PortfolioService one-year holdings split and LocalDate.now(), the /assets/holding/{holdingType} endpoints and their Excel, readers and tests), write the plan, STOP
Last verify     : unit -Punit 3724/0; integration 296/0 (2026-10-25, W-160d3)
Waiting on      : nothing for W-160e; open for later: per-bucket term audit record (near W-107/W-160g), keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-160d3: move the P&L's stored short / long pair into its split — figures unchanged, placed by the year's trade-outcome facts, unplaceable sides listed; runbook §2.6, W-170, WL-36 (D-369)
Updated         : 2026-10-25, session 35 — W-160d3 done and committed (W-160d closed); resume at W-160e (plan to write)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
