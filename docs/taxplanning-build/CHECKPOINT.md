# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088l — repo-wide audit: law in code and rule-derived fields (D-249)
Stage           : READY — not started
Plan            : written when taken (plans/W-088l.md)
Approved        : —
Steps done      : —
Next step       : on "start W-088l": read its WI row and every note placed for it (D-302 D2a HoldingPeriodService, D-306 CorporateActionDto.date, review 'Out of scope' RestructuringEngine:198–200, W-088o1's coverage placement of SETOFF-WITHIN-CAPITAL-GAINS); write plans/W-088l.md (a split if > §7), STOP
Last verify     : targeted 443/0; unit -Punit 3242/0; integration 281/0 (2026-10-21, W-088o2)
Waiting on      : the owner — "start W-088l"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088o2: make CapitalGainsPart an open code — the parts are only what CG-PARTS declares, checked by the build's pack validation, so a new special-rate part is rule data alone; W-088o closed (D-311)
Updated         : 2026-10-21, session 28 — W-088o2 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
