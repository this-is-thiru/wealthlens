# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-165 — coverage register placement (D-315; audit T8; N8) — NOT STARTED
Stage           : — (W-180 closed without a change, D-387, committed)
Plan            : — (write plans/W-165.md on "start W-165")
Approved        : —
Steps done      : —
Next step       : on "start W-165": READ the WI row + content/coverage.json :70 :111 :644 :692, write plans/W-165.md, STOP for approval
Last verify     : W-164 — AssetRequestParserTest 4/0; unit 3787/0; integration 310/0 (W-180 changed no code)
Waiting on      : the owner — "start W-165"; place or dismiss: V1/V2 accept GOLD_BOND without maturity (W-164 C3a); open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-180: close without a change — keep the integration tier on mongo:8.0.4; no published 8.0 image starts on the Ubuntu 26 runner and 8.0.4 is green there (D-387)
Updated         : 2026-10-28, session 38 — W-164 DONE; W-180 closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
