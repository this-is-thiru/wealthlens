# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-179 — DONE as a decision item; W-208 – W-212 placed; awaiting the owner's commit word
Stage           : 6 of 6; closed out
Plan            : plans/W-179.md — D1a, D2a, D3a, D4a, D5a taken as recommended (D-417)
Approved        : "W-179 approved D1a D2a D3a D4a D5a" (2026-10-31)
Steps done      : W-208 (BSE) · W-209 (AMC tiers) · W-210 (plan) · W-212 (buyback fee) placed after W-179,
                  W-211 (HUF / NRI) placed not scheduled; D-417 in STATE; the WI row's Done note; board `E` → `D`
                  with the five ids appended; this file
Next step       : the owner's word on the commit of these four files; then start W-181 at the EXPLAIN stage
                  (the next open item in board order): the resume check, plans/W-181.md, board `·` → `E`,
                  update this file, STOP
Last verify     : doc guards `FinishedPlanTest,CoverageBacklogTest` 9/0 (2026-10-31); the tree carries only
                  plans/W-179.md, STATE.md, 03-WORK-ITEMS.md and this file — no code, no card, no rule pack
Waiting on      : the owner — the commit; whether the resume check's grep should change; OQ-32, OQ-33, OQ-34
                  open. Theirs to name when W-208 and W-210 start: the BSE segments traded, and the plan
                  mechanism (a `planCode` on the trade request, or an account lookup)
Files touched   : plans/W-179.md (new), STATE.md, 03-WORK-ITEMS.md, CHECKPOINT.md — the last item commit is
                  866f3dd2 (W-186) and its post-commit state line is f461363c
Last item commit: taxplanning W-186: price Fyers' clearing charge on its four F&O cards and make CLEARING deductible by its side — Fyers prints and taxes a clearing charge the catalogue had no code for, so those notes understated by Rs 500 per crore of notional and Rs 9,000 per crore of premium, and a computation carrying the charge would have been refused (D-416)
Updated         : 2026-10-31, session 43 — W-179 done and placed; awaiting the commit word
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* after a docs-only post-commit state line, the grep returns that state-line commit
rather than the item commit, because the state lines carry the item prefix too. Compare the **item** commit
named above; the tree, not the grep, is what decides. It fired for W-179 (`f461363c` against `866f3dd2`) and
twice before (`5c1b9f89` against `93fb1569`).
