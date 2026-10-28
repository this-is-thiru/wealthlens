# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-163b — DONE (D-382); next W-163c (cite each statutory rate to the law, not started)
Stage           : DONE
Plan            : plans/W-163.md (parent); W-163c plan not yet written
Approved        : "W-163b approved" (session 37, 2026-10-28)
Steps done      : —
Next step       : republish WL-A-2 if not yet read back; then W-163c READ → EXPLAIN only on the owner's word
Last verify     : unit -Punit 3778/0; integration 310/0 (2026-10-28, W-163b)
Waiting on      : the owner — next item; open: CLAUDE.md "backfill" 503 wording (no such endpoint); api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-163b: move STT, stamp duty, the SEBI fee and GST to four statutory cards — same figures, the law in one place per scope; a scope with no statutory card is a gap; charge coverage reviewed, W-177 – W-179 placed (D-382, D-383)
Updated         : 2026-10-28, session 37 — W-163b done and committed (not pushed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
