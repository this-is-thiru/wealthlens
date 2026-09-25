# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-050 — RoundTotalIncomeNode and RoundNetPayableNode
Stage           : NOT_STARTED
Plan            : — (plans/W-050.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-050 section of 03-WORK-ITEMS.md and only the sections it names; write plans/W-050.md; STOP for approval. Known: its rows RATE-ROUND-002/-003 need concept names per the W-045d2 mapping; a new rule means a lock bump (law.8) and RuleIds regen; exactly two ROUNDING lines per pipeline
Last verify     : ./mvnw clean test -pl backend -Punit → 1536 pass, 0 fail, 0 Checkstyle violations (W-049)
Waiting on      : nothing for W-050; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-049: add RebateNode and the three s.156 rebate rows — each in its own regime, s.156(2) held to slab-rate tax (D-074)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
