# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-056 — SurchargeNode (+ SlabLadder band lookup)
Stage           : IMPLEMENT — plan approved (D-086); W-056a committed
Plan            : plans/W-056.md
Approved        : "approved — proceed as recommended" — 2026-09-26 (D-086)
Steps done      : 1, 2 (W-056a)
Next step       : step 3 — SlabLadder.bandFor(Money) → Optional<Band> (lowerExclusive < income ≤ upperInclusive) + SlabLadderTest
Last verify     : ./mvnw clean test -pl backend -Punit → 1741 pass, 0 fail, 0 Checkstyle violations (W-056a)
Waiting on      : nothing; W-052d due before Phase 9; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : plans/W-056.md, 03-WORK-ITEMS.md (W-056's; the W-056a row there lands with W-056's commit)
Last item commit: taxplanning W-056a: add NodeContext.recomputed — a node recomputes a slot at another value by re-running the nodes between (D-086)
Updated         : 2026-09-26, session 8
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
