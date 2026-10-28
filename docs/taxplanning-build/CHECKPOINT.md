# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-177 — missing broker cards (D-383; coverage G1 – G3) — NOT STARTED (the W-164 – W-167 chain is done; W-177 → W-178 → W-179 is the remaining chain after W-163c)
Stage           : — (W-167 DONE, D-390, committed)
Plan            : — (write plans/W-177.md on "start W-177")
Approved        : —
Steps done      : —
Next step       : on "start W-177": READ the WI row + reviews/2026-10-28-charge-coverage.md + the existing broker cards, write plans/W-177.md, STOP for approval
Last verify     : W-167 — TJsonMapper+P&L mapping 8/0; unit 3792/0; integration 312/0
Waiting on      : the owner — "start W-177"; push (parent + api-collection) if wanted; place: CorporateActionService:489 commented code, Checkstyle unused-field rule; 33 register rows (W-165 C3a scan); V1/V2 accept GOLD_BOND without maturity (W-164 C3a); open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-167: make TJsonMapper fail on nothing to read instead of answering null — a null source refused, the two uncalled methods deleted (D-390)
Updated         : 2026-10-28, session 38 — W-164, W-165, W-166, W-167 DONE; W-180 closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
