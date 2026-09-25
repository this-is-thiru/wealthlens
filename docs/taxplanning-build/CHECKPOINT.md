# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-053 — T1 golden tests
Stage           : NOT_STARTED
Plan            : — (plans/W-053.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-053 section of 03-WORK-ITEMS.md incl. its amendments/errata (D-043: both years, I3 shrink-only ratchet; W-050: exactly two ROUNDING lines; W-052b: compare against round.netpayable) and DR 08 T1 (every case, every expected value); write plans/W-053.md with the D-043 standing-rules line; STOP for approval. Known: the shipped pipeline names inputs.components, salary.gross, totalincome — nodes not built — so a golden run must enter at totalincome or the pipeline needs a T1 entry; T23-4 (byte-identical TaxComputation) and T23-8 are also assigned here
Last verify     : ./mvnw clean test -pl backend -Punit → 1615 pass, 0 fail, 0 Checkstyle violations (W-052c)
Waiting on      : nothing for W-053; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-052c: put TY 2025-26 on the timeline for the Phase 5 rules — per-year versions where the instrument differs, a 2025-26 lock (D-082)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
