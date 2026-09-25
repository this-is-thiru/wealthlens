# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-046b — the old-regime age ladders and ladder selection (D-071)
Stage           : NOT_STARTED
Plan            : — (plans/W-046b.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-046b section of 03-WORK-ITEMS.md and only the sections it names (01-rulebook.md R3 RATE-SLAB-AGE-001/002; D-071 B1–B3); write plans/W-046b.md; STOP for approval. Source B2 (age "at any time during the tax year") and B3 (RNOR as resident) from the Act before proposing them; ladders from FA 2026 First Schedule Part I-B Para A (II)/(III) p.85 and Part I-A for TY 2025-26
Last verify     : ./mvnw clean test -pl backend -Punit → 1495 pass, 0 fail, 0 Checkstyle violations (W-046)
Waiting on      : nothing for W-046b; owner to itemise the backlog gaps (STATE); D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-046: add TaxByBasisNode — tax at slab rates from the regime's own rate table, one traced line (D-071)
Updated         : 2026-09-25, session 6
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
