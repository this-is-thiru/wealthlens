# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-179 — DONE, committed as c76dad9a; next is W-181
Stage           : 6 of 6; closed out
Plan            : plans/W-179.md — D1a, D2a, D3a, D4a, D5a taken as recommended (D-417)
Approved        : "W-179 approved D1a D2a D3a D4a D5a" (2026-10-31)
Steps done      : W-208 – W-212 placed, D-417 in STATE, the board `D`, the plan, the WI row, this file
Next step       : the owner's word on the commit of these two state lines; then start W-181 at the
                  EXPLAIN stage (the next open item in board order): the resume check, plans/W-181.md,
                  the board `·` → `E`, update this file, STOP
Last verify     : doc guards `FinishedPlanTest,CoverageBacklogTest` 9/0 (2026-10-31); the item commit
                  carries 4 files (+272 / −21); no code, no card, no rule pack
Waiting on      : the owner — the commit of these two state lines; whether the resume check's grep should
                  change; OQ-32, OQ-33, OQ-34 open. Theirs to name when W-208 and W-210 start: the BSE
                  segments traded, and the plan mechanism (a `planCode` on the trade request, or a lookup)
Files touched   : only these two state lines — STATE.md (Last checkpoint) and CHECKPOINT.md; the item
                  itself is in c76dad9a, so the tree is otherwise clean
Last item commit: taxplanning W-179: place the five charge-coverage decisions as rows W-208 – W-212 — the coverage review left bonds, BSE, the Zerodha AMC tiers, the plan and buyback fees unpriced, so each decision now states what the code allows (one needs no data, one is data only, three need a published fact and a vocabulary entry) and what it costs (D-417)
Updated         : 2026-10-31, session 43 — W-179 committed as c76dad9a; next W-181
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after a docs-only post-commit state line, the grep returns that state-line commit
rather than the item commit, because the state lines carry the item prefix too. Compare the **item** commit
named above; the tree, not the grep, is what decides. It fired for W-179's planning (`f461363c` against
`866f3dd2`) and twice before (`5c1b9f89` against `93fb1569`).
