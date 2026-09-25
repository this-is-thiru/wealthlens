# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-049 — RebateNode + the three s.156 rebate rows (plan approved, D-074)
Stage           : BUILD
Plan            : plans/W-049.md
Approved        : 2026-09-25 — "approved — A1–A4 as recommended" (D-074)
Steps done      : 1, 2 (W-049a built and committed, D-075)
Next step       : step 3 of plans/W-049.md — RebateNodeTest (fails); rows; lock → 2026-27.law.7 + RuleIds; RebateNode
Last verify     : ./mvnw clean test -pl backend -Punit → 1524 pass, 0 fail, 0 Checkstyle violations (W-049a)
Waiting on      : nothing; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : none
Last item commit: taxplanning W-049a: carry a relief on a rule version — read like a cap, its slab-rate limit always stated (D-075)
Updated         : 2026-09-25, session 6 (ended at the session limit, tree clean)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
