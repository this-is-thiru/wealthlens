# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-163a — DONE (D-381); next W-163b (statutory rules move, not started)
Stage           : DONE
Plan            : plans/W-163.md (parent); W-163b plan not yet written
Approved        : "W-163a approved" (session 37, 2026-10-28)
Steps done      : —
Next step       : W-163b READ → EXPLAIN, only on the owner's word
Last verify     : unit -Punit 3773/0; integration 310/0 (2026-10-28, W-163a)
Waiting on      : the owner — next item; open: CLAUDE.md "backfill" 503 wording (no such endpoint); api-collection term requests (submodule, owner's call); per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-163a: let the engine read a statutory card — a card naming no broker is priced beside the broker's card in one ordered evaluation; a code on both refused, its card recorded; W-163 split a – c (D-380, D-381)
Updated         : 2026-10-28, session 37 — W-163a done and committed (not pushed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
