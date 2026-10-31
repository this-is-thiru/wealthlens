# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-181 — DONE, staged and verified; awaiting the owner's commit word. Next is W-182
Stage           : 5 of 6 (REPORTED); no code change outstanding
Plan            : plans/W-181.md — C1a, C2a, C3a taken (owner's approval), recorded as D-418
Approved        : "W-181 approved C1a C2a C3a" (2026-10-31)
Steps done      : the guard + four tests; the probe; unit and integration tiers; D-418; board `D`;
                  the WI row; the plan; STATE; this file; the six paths staged
Next step       : on "commit it", commit the six staged paths with the subject below, then the
                  post-commit state lines; after that, start W-182 at the EXPLAIN stage
Last verify     : unit 3828 / 0 / 0 (`-Punit`); integration 323 / 0 / 0 (OrbStack, `-Pintegration`);
                  the four new tests 4/0 with the guard and 2 failures (200 not 400) without it
Waiting on      : the owner's commit word. Still open — whether the resume check's grep should
                  change; OQ-32, OQ-33, OQ-34; the BSE segments and the plan mechanism when W-208 and
                  W-210 start
Files touched   : backend PortfolioService.java, PortfolioIntegrationTest.java;
                  docs plans/W-181.md (new), STATE.md, 03-WORK-ITEMS.md, CHECKPOINT.md — all staged
Last item commit: none yet for W-181; the last is W-179's c76dad9a. W-181's subject:
                  taxplanning W-181: require a Gold Bond's maturity on the JSON trade paths — addTransaction and addTransactionV2 stored a GOLD_BOND with no maturity date while the Excel upload refused the same row, so the rule now sits in the one validator both paths pass (D-418)
Updated         : 2026-10-31, session 43 — W-181 built, verified, staged; stopped for the commit
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
