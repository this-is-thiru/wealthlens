# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-164 — a Gold Bond's maturity not defaulted (audit T5, D-315) — NOT STARTED
Stage           : — (session 37 PAUSED at the owner's session-limit notice; all committed and pushed)
Plan            : — (write plans/W-164.md on "start W-164")
Approved        : —
Steps done      : —
Next step       : on "start W-164": READ the WI row + AssetRequestParser:179, write plans/W-164.md, STOP for approval
Last verify     : unit -Punit 3783/0; integration 310/0 (mongo:8.0.4); CI green run 40719965059 (c24c8846)
Waiting on      : the owner — "start W-164"; open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-163c: cite each statutory rate to the law — STT Sl. 5, stamp duty Art. 56A, the SEBI turnover fee and GST heading 9971 read, all agree; the four cards carry source and verified date; W-163 closed (D-384)
Updated         : 2026-10-28, session 37 — PAUSED (owner shutting down); session note in STATE "Notes for resume"; branch pushed, origin = local
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
