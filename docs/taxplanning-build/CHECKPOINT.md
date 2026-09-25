# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-052c — TY 2025-26 on the timeline for the Phase 5 rules
Stage           : NOT_STARTED
Plan            : — (plans/W-052c.md once written)
Approved        : —
Steps done      : —
Next step       : READ the W-052c section of 03-WORK-ITEMS.md (D-041; W-045n's per-rule table in plans/W-045n.md Findings; STATE's unverified list) and only what it names; write plans/W-052c.md with the D-043 standing-rules line; STOP for approval. Known: 10 Phase 5 rows now (2 slab + 2 age ladders, 3 rebates, 2 roundings, 1 cess) all TY 2026-27 only; 1961 ss.288A/288B already SOURCE_CHECKED (D-076); REBATE-RESIDENT-INDIVIDUAL's 2025-26 wording still unread per STATE (1961 s.87A main limb read in W-049's session); a 2025-26 lock and TaxYearRegistry entry are new
Last verify     : ./mvnw clean test -pl backend -Punit → 1592 pass, 0 fail, 0 Checkstyle violations (W-052b)
Waiting on      : nothing for W-052c; D-054 startup wiring UNOWNED. OQ-1, OQ-2 open
Files touched   : —
Last item commit: taxplanning W-052b: add ComputationProjector — a run becomes a TaxComputation from round.totalincome, tax.bybasis and tax.netpayable (D-081)
Updated         : 2026-09-25, session 7
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
