# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-050a — a rounding on a rule row (D-076); W-050 PAUSED after its step 1 (plans/W-050.md, approved D-076)
Stage           : NOT_STARTED
Plan            : — (plans/W-050a.md once written)
Approved        : —
Steps done      : — (W-050: 1)
Next step       : READ the W-050a row of 03-WORK-ITEMS.md (and plans/W-050.md A1, D-076); write plans/W-050a.md on the W-049a pattern (RuleRounding(Money toNearest), RuleVersion.rounding after relief, ShapeJson.rounding, RuleJson must-carry rule, 13 sites); STOP for approval
Last verify     : ./mvnw clean test -pl backend -Punit → 1536 pass, 0 fail, 0 Checkstyle violations (W-049b)
Waiting on      : nothing for W-050a; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-049b: mark the rebate sections MODELLED and catalogue the rebate — W-049's missed standing rules 1–2 (D-077)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
