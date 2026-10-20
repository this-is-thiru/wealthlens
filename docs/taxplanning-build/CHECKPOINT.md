# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j2c — STT chargeability decided on the tax side from rule rows (D-293)
Stage           : READY — not started
Plan            : plans/W-088j2c.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088j2c": read the WI row, D-294 + sources row 8 (Sl. 1–3 conditions, s.113A, Q1), D-295 (TradeVenue), CapitalGainsNode / RS-CG-PART rows, BucketFacts, the adapter; design the engine step deriving each side's STT state from rule rows (not a condition repeated in every part row) and the exchange-string mapping as data; write plans/W-088j2c.md (likely a split; R15), STOP
Last verify     : unit clean -Punit 3161 run, 0 fail; integration 269/0 (2026-10-20, W-088j2b)
Waiting on      : the owner — "start W-088j2c"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j2b: record where and how each side of a disposal was traded — per-side exchange and segment on trade_outcomes from all three writers, a TradeVenue per side on the capital-gains read-model (D-295)
Updated         : 2026-10-20, session 26 — W-088j2b done (D-295); next W-088j2c
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
