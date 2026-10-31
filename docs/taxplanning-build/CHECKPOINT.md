# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-182 — built and verified; the owner's commit word is what is waited on
Stage           : 4 of 6 (VERIFIED); report written, STOP before the commit
Plan            : plans/W-182.md — built as C1a (all 17 blocks), C1c-a (three TODO ids),
                  C2a (three comment-only classes), C3a (orphaned methods and local), C4a (a
                  commented-code check proposed, not built)
Approved        : "W-182 approved C1a C1c-a C2a C3a C4a" (2026-10-31)
Steps done      : the whole build — 17 blocks in 10 files, three classes deleted, two orphaned
                  methods and one local deleted, three TODO ids; D-419; board `E` → `D`; the WI
                  done note; the plan's decisions; STATE and this file
Next step       : nothing until the owner says commit. Then stage exactly the files named under
                  "Files touched", commit with the subject below, and open W-183
Last verify     : clean install green (Checkstyle, Spotless); unit 3828 / 0 / 0; integration
                  323 / 0 / 0 (OrbStack); both commented-code greps read hit by hit; git diff
                  hunk by hunk; doc guards 9 / 0
Waiting on      : the owner — the commit word. Still open from before: the resume check's grep
                  wording; OQ-32, OQ-33, OQ-34; the BSE segments for W-208 and the plan
                  mechanism for W-210
Files touched   : 11 under backend/src/main/java — CorporateActionService, PortfolioController
                  (M), AssetEntity, PortfolioService, ProfitAndLossService, TransactionService,
                  MongoDBConfig, TLocalDateTime (M), QueueService, TemporaryService,
                  RabbitMQConfig (deleted); STATE.md; 03-WORK-ITEMS.md; plans/W-182.md (new);
                  this file. Staged at close-out: all of the above
Last item commit: taxplanning W-182: remove the seventeen commented-out blocks and the three comment-only classes — seven of them named types or fields that no longer exist, so nothing could be compiled back, and deleting the dead split methods makes W-213 and the three untracked TODOs visible instead of restoring anything (D-419)
Updated         : 2026-10-31, session 43 — W-182 built and verified, stopped for the commit word
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
