# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-163c — DONE (D-384); W-163 closed; next W-164 (Gold Bond maturity default, not started)
Stage           : DONE
Plan            : —
Approved        : "W-163c approved" (session 37, 2026-10-28)
Steps done      : —
Next step       : republish WL-A-2 if not yet read back; then W-164 READ → EXPLAIN only on the owner's word
Last verify     : unit -Punit 3778/0; integration 310/0 (2026-10-28, W-163c)
Waiting on      : the owner — next item; catalogue SEBI_FEE reference (found, not changed); open: CLAUDE.md "backfill" 503 wording (no such endpoint); api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-163c: cite each statutory rate to the law — STT Sl. 5, stamp duty Art. 56A, the SEBI turnover fee and GST heading 9971 read, all agree; the four cards carry source and verified date; W-163 closed (D-384)
Updated         : 2026-10-28, session 37 — W-163c done and committed (not pushed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
