# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088l3 — law-in-code audit, portfolio, corporate, brokercharges, shared and a pass over the rest (D-312)
Stage           : READY — not started
Plan            : written when taken (plans/W-088l3.md), on W-088l1's method
Approved        : —
Steps done      : —
Next step       : on "start W-088l3": read its WI row, the audit file (criteria, N2–N5, N7, N8), plans/W-088l1.md and W-088l2.md (method); write plans/W-088l3.md incl. C3a (CLAUDE.md §4 pointer), STOP
Last verify     : targeted 443/0; unit -Punit 3242/0; integration 281/0 (2026-10-21, W-088o2)
Waiting on      : the owner — "start W-088l3"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088l2: audit taxplanning for law in code — the legacy compute path holds the law a second time and no row removed it; placed as W-159 (D-314)
Updated         : 2026-10-21, session 28 — W-088l2 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
