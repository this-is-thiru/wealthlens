# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-181 — DONE, committed as e494326f; next is W-182
Stage           : 6 of 6; closed out
Plan            : plans/W-181.md — C1a, C2a, C3a taken, recorded as D-418
Approved        : "W-181 approved C1a C2a C3a" (2026-10-31)
Steps done      : the guard and four tests; the probe; unit 3828 / 0 / 0; integration 323 / 0 / 0;
                  D-418; the board `D`; the WI row; the plan; STATE; the item commit
Next step       : start W-182 at the EXPLAIN stage (the next open item in board order): the resume
                  check, plans/W-182.md, the board `·` → `E`, update this file, STOP
Last verify     : unit 3828 / 0 / 0 (`-Punit`); integration 323 / 0 / 0 (OrbStack); doc guards 9/0;
                  the item commit carries 6 files (+359 / −22)
Waiting on      : the owner — the commit word for these state lines. Still open: whether the resume
                  check's grep should change; OQ-32, OQ-33, OQ-34; the BSE segments and the plan
                  mechanism when W-208 and W-210 start
Files touched   : none — the tree is clean at resume once these state lines are committed; they are
                  STATE.md and this file. The item is e494326f. If the grep below returns a later
                  state-line commit, that is the known artifact (see note)
Last item commit: taxplanning W-181: require a Gold Bond's maturity on the JSON trade paths — addTransaction and addTransactionV2 stored a GOLD_BOND with no maturity date while the Excel upload refused the same row, so the rule now sits in the one validator both paths pass (D-418)
Updated         : 2026-10-31, session 43 — W-181 committed as e494326f; next W-182
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
`866f3dd2`), for W-181's planning (`c620ba99` against `c76dad9a`) and twice before (`5c1b9f89` against
`93fb1569`).
