# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-050 — RoundTotalIncomeNode and RoundNetPayableNode (D-076); W-049b and W-050a landed
Stage           : BUILD
Plan            : plans/W-050.md
Approved        : 2026-09-25 — "approved — A1–A6 as recommended" (D-076)
Steps done      : 1, 1b
Next step       : step 2 of plans/W-050.md — RoundingNodesTest (fails); then step 3's rows (RATE-ROUND-TOTAL-INCOME, RATE-ROUND-PAYABLE, rounding {toNearest "10"}), lock → law.8 + RuleIds, nodes (refuse toNearest ≠ 10), coverage s.516/288A/288B MODELLED + catalogue entry; the RuleRounding wiring in step 3 is already done by W-050a
Last verify     : ./mvnw clean test -pl backend -Punit → 1548 pass, 0 fail, 0 Checkstyle violations (W-050a)
Waiting on      : nothing; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-050a: carry a rounding on a rule version — the step as data, at most one of cap, relief and rounding (D-078)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
