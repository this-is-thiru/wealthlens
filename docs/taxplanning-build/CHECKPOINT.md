# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088k2 — portfolio publishes each capital-gains bucket's transfer date (D-302)
Stage           : READY — not started
Plan            : plans/W-088k2.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088k2": read its WI row, D-302, D-303 (sources/2026-10-21-holding-period.md "For W-088k2": the bonus ex-date gap), CapitalGainsBucket, CapitalGainsSummaryService.BucketKey, the adapter, TradeOutcomeEntity sell_date; decide which asset facts k3 needs (listing for equity) and the bonus-date choice (fact vs stated approximation); write plans/W-088k2.md, STOP
Last verify     : unit clean -Punit 3197 run, 0 fail (2026-10-21, W-088k1); integration 269/0 (W-088j2f)
Waiting on      : the owner — "start W-088k2"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088k1: source the holding-period law for both years — 1961 s.2(42A) as amended by FA (No. 2) 2024, s.50AA, the 2025 Act s.2(101) and s.76; the boundary ruled calendar months; W-088k split k1–k3 (D-302, D-303)
Updated         : 2026-10-21, session 27 — W-088k1 done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
