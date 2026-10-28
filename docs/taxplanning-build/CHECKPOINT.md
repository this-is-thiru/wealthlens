# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-180 — unpin the integration tier's Mongo image (D-385) — NOT STARTED (next in order after W-164)
Stage           : — (W-164 DONE, D-386, committed)
Plan            : — (write plans/W-180.md on "start W-180")
Approved        : —
Steps done      : —
Next step       : on "start W-180": READ the WI row + AbstractIntegrationTest + cicd.yaml, write plans/W-180.md, STOP for approval
Last verify     : W-164 — AssetRequestParserTest 4/0; unit 3787/0; integration 310/0
Waiting on      : the owner — "start W-180"; place or dismiss: V1/V2 accept GOLD_BOND without maturity (W-164 C3a); open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-164: refuse an uploaded Gold Bond row with no maturity date instead of assuming eight years — a date given on any other type is kept, not dropped (D-386)
Updated         : 2026-10-28, session 38 — W-164 DONE
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
