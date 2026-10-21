# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-151 — s.516 rounding as rule data (D-313; audit P1)
Stage           : READY — not started
Plan            : written when taken (plans/W-151.md)
Approved        : —
Steps done      : —
Next step       : on "start W-151": read its WI row, the audit file § W-088l1 P1, node/RoundingStep, rules/money/Money.roundToNearestTenPerSection516, rules/rounding/RuleRounding, the rounding rows (ROUND-*) and their notes; write plans/W-151.md, STOP
Last verify     : targeted 443/0; unit -Punit 3242/0; integration 281/0 (2026-10-21, W-088o2)
Waiting on      : the owner — "start W-151"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088l3: audit portfolio, corporate, brokercharges, shared and the rest for law in code — eight findings placed as W-160 – W-167, CLAUDE.md §4 names the audit file; W-088l closed (D-315)
Updated         : 2026-10-21, session 28 — W-088l3 committed, W-088l closed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
