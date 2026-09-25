# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-049 — RebateNode (+ RuleVersion carries a ReliefShape; ShapeJson reads it, per the W-048 erratum)
Stage           : NOT_STARTED
Plan            : — (plans/W-049.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-049 section of 03-WORK-ITEMS.md (incl. the W-048 erratum above it) and only the sections it names; write plans/W-049.md; STOP for approval. Known: REBATE-* concept names from the W-045d2 mapping (REBATE-RESIDENT-INDIVIDUAL, REBATE-NEW-TAX-REGIME, -EXCESS, -LIMIT); REBATE-OLD-001 figures SOURCE_CHECKED with residual (W-045n); NEW_TAX_REGIME.json `_pendingRules` note to update; lock bump expected
Last verify     : ./mvnw clean test -pl backend -Punit → 1514 pass, 0 fail, 0 Checkstyle violations (W-048)
Waiting on      : nothing for W-049; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-048: add the ReliefShape family — each relief formula once, amount to take off plus reason (D-073)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
