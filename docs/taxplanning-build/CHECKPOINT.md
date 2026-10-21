# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088k4 — a bonus lot dated from its allotment (D-304 C2)
Stage           : READY — not started
Plan            : plans/W-088k4.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088k4": read its WI row, D-304, D-305, sources/2026-10-21-holding-period.md (bonus-date gap), CorporateActionEntity (record_date, ex_date, date), CorporateActionService:353–390 (bonus lot and transaction dated at ex_date), the corporate-action seed/entry path, all three trade_outcomes writers; design where the allotment date comes from and the runbook entry for existing actions; write plans/W-088k4.md, STOP
Last verify     : unit clean -Punit 3218 run, 0 fail; integration 269/0 (2026-10-21, W-088k3)
Waiting on      : the owner — "start W-088k4"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088k3: decide short-term or long-term in the engine — RS-CG-TERM's twelve-month row for equity sold on NSE or BSE, counted in calendar months; the input no longer carries portfolio's term (D-305)
Updated         : 2026-10-21, session 27 — W-088k3 done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
