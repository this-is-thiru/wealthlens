# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j2c2 — the engine decides STT chargeability (D-296)
Stage           : READY — not started
Plan            : plans/W-088j2c2.md not yet written (design in plans/W-088j2c.md)
Approved        : —
Steps done      : —
Next step       : on "start W-088j2c2": read the WI row, D-296, D-297 + sources/2026-10-20-recognised-exchanges.md (NSE, BSE; IFSC names), sources/2026-10-20-securities-transaction-tax.md row 8, CapitalGainsNode/BucketFacts/adapter, RS-CG-PART rows; write plans/W-088j2c2.md (rows, codes, trace, tests, bite probes; R15), STOP
Last verify     : unit clean -Punit 3161 run, 0 fail; integration 269/0 (2026-10-20, W-088j2b)
Waiting on      : the owner — "start W-088j2c2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j2c1: source the recognised stock exchanges and the IFSC exchanges — SCRA s.2(f), s.4, s.5, s.29-B read; NSE and BSE accepted as recognised for both years; India INX and NSE IFSC in IFSC (D-296, D-297)
Updated         : 2026-10-20, session 26 — W-088j2c1 done (D-297); next W-088j2c2
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
