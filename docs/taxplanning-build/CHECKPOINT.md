# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088k3 — the engine decides the term (D-302)
Stage           : READY — not started
Plan            : plans/W-088k3.md not yet written
Approved        : —
Steps done      : —
Next step       : on "start W-088k3": read its WI row, D-302 – D-304, sources/2026-10-21-holding-period.md ("For W-088k3", Q1 calendar months), CapitalGainsInput (Term, BucketFacts), CapitalGainsNode + CapitalGainsStt, CapitalGainsAdapter, RS-CG-PART rows' capitalgains.term; design RS-CG-TERM (listing from asset + exchange, 12/24 months, ex-date note for bonus), the step, the adapter change; write plans/W-088k3.md (split if > §7), STOP
Last verify     : unit clean -Punit 3201 run, 0 fail; integration 269/0 (2026-10-21, W-088k2)
Waiting on      : the owner — "start W-088k3"; OQ-32, OQ-33 open
Files touched   : (none)
Last item commit: taxplanning W-088k2: publish each capital-gains bucket's transfer date — transferredOn from sell_date, in the bucket key and the summary's check, so the engine can count the holding period; W-088k4 placed (D-304)
Updated         : 2026-10-21, session 27 — W-088k2 done
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
