# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088o2 — CapitalGainsPart an open code (D-309)
Stage           : READY — not started
Plan            : plans/W-088o.md build plan, row W-088o2; its own plan written when taken
Approved        : —
Steps done      : —
Next step       : on "start W-088o2": read its WI row, plans/W-088o.md (build plan, W-088o2; risks 1–2), D-309, D-310, the readers of CapitalGainsPart (EnumMap / values() in CapitalGainsHead, HeadsAfterSetOff, TaxByBasisNode; the two orders; DefinitionJson) and PackValidator's window walk; write plans/W-088o2.md, STOP
Last verify     : targeted 308/0; unit -Punit 3228/0; integration 281/0 (2026-10-21, W-088o1)
Waiting on      : the owner — "start W-088o2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088o1: declare the capital-gains parts as rule data — CG-PARTS states each part's term and whether a long-term loss may reach it (s.108(2)(b)), so the set-offs read the row and isLongTerm() is gone; W-088o split o1 / o2 (D-309, D-310)
Updated         : 2026-10-21, session 28 — W-088o1 committed
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
