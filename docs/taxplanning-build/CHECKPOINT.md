# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088k — the term decided on the tax side (D-249)
Stage           : READY — not started
Plan            : plans/W-088k.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088k": read its WI row, plans/W-088f.md Revision 1 F2, D-249, D-237 / R-10, portfolio HoldingPeriodService and capital_gains_type writers, CapitalGainsAdapter, CapitalGainsInput.Term; source the holding-period law for both years (S2 / 1961 s.2(42A) and its provisos) before any rule row; write plans/W-088k.md (a split if mis-sized), STOP
Last verify     : unit clean -Punit 3197 run, 0 fail; integration 269/0 (2026-10-21, W-088j2f)
Waiting on      : the owner — "start W-088k"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088j2f: derive Sl. 1 only for a purchase on or after Chapter VII's commencement — 1-10-2004 under S.O. 1058(E), sourced from the Lok Sabha record and accepted; a pre-commencement purchase settles under S.O. 5054(E) (D-301)
Updated         : 2026-10-21, session 27 — W-088j2f done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
