# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088u2 — ChapterEightNode per concern (W-088u split, D-270)
Stage           : READY — not started
Plan            : plans/W-088u.md (split); plans/W-088u2.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088u2": read, write plans/W-088u2.md, STOP — its proof uses tools/dump_all_nodes.py (+ dump_normalise.py, dump_mask_doubling_stub.py); capture the HEAD dump before any code
Last verify     : unit clean -Punit 3084 run, 0 fail; integration 252/0 (2026-10-19, W-088u1)
Waiting on      : the owner — "start W-088u2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088u1: split OtherSourcesNode per concern — gifts to GiftIncome, the s.93 allowance to DividendInterestAllowance, the trace writer to OtherSourcesLines; W-088u split (D-270, D-271)
Updated         : 2026-10-19, session 24 — paused at the session limit; everything committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
