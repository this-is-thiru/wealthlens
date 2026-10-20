# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088j2b — venue facts on the capital-gains read-model (D-293)
Stage           : READY — not started
Plan            : plans/W-088j2b.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088j2b": read the WI row, D-293 (D2a), D-294 + sources row 8 (what chargeability needs: exchange, delivery, s.113A IFSC/NPS), TradeOutcomeEntity exchange_name/segment/broker per side, the three writers, CapitalGainsBucket/BucketKey; write plans/W-088j2b.md (incl. a read-only distinct count of exchange_name/segment for the owner; R15), STOP
Last verify     : unit clean -Punit 3154 run, 0 fail; integration 269/0 (2026-10-20, W-088j)
Waiting on      : the owner — "start W-088j2b"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j2a: source the Finance (No. 2) Act, 2004, Chapter VII — 2019–2026 amendments read, the base text accepted as reported, and an exchange delivery trade's STT ruled paid for s.198 (D-293, D-294)
Updated         : 2026-10-20, session 26 — W-088j2a done (D-294); next W-088j2b
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
