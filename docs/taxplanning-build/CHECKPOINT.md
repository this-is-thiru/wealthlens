# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334e3 done (D-849), W-334e and W-334 complete; close-out written, NOT committed (awaiting owner's "commit it"); then WL-A-2
Stage           : prod and staging on m1.4, signed off; m1.4 stopped; MongoDB tools REMOVED (node autoremoved too — owner told); kept: ~/wealthlens-runs/archives/ (prod-after-m14 1,635 / staging-after-m14 1,406 docs, mode 600, delete by 2027-11-21 or after W-337) + ~/wealthlens-runs/fingerprint.js; everything else of the run deleted
Done session 112: staging finished (D-842, D-843); prod: split (D-844), e1 (D-845), e2 (D-846, D-847), e3 (D-848, D-849); W-355, W-356 placed
Board           : W-334e —, W-334e1/e2/e3 D, W-348a D, W-348b D, W-355 ·, W-356 ·, W-337 ·
Next step       : on "commit it": stage the five named files, commit, push if asked, WL-A-2; then next by board order (owner's choice: W-337 MySQL/PostgreSQL move, or another)
Open to owner   : "commit it" for W-334e3; api-collection Buy Stock.yml:68 doc proposal; the owner's own upload of the trades Y2 listed
Env notes       : no MongoDB tools on this machine now (reinstall: brew tap mongodb/brew && brew install mongodb-database-tools); prod host cluster0.x6trgen.mongodb.net, staging it-staging.6fu8nvy.mongodb.net
Last verify     : FinishedPlanTest + ./mvnw validate (session 112, this close-out)
Last item commit: taxplanning W-334e2: run §2 on prod and remove its test entries — the rehearsal's counts on every row, two TEST_USER portfolios kept by the owner
Files touched   : docs/taxplanning-build/STATE.md, docs/taxplanning-build/03-WORK-ITEMS.md, docs/taxplanning-build/post-deployment-runbook.md, docs/taxplanning-build/plans/W-334e3.md (new) (plus this file)
Updated         : 2026-11-21, session 112 (W-334e3 run and closed out, uncommitted)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.

*Known false mismatch:* once a post-commit state line exists, the grep returns **that** commit rather than the
item commit named above, because state lines carry the item prefix too. Compare the **item** commit; the tree,
not the grep, is what decides. It has fired for W-179's planning (`f461363c` against `866f3dd2`), W-181's
(`c620ba99` against `c76dad9a`), W-182's (`592ff6a6` against `e494326f`), W-183's (`ac3a9fce` against
`ecf681cd`), W-184's (`235d2566` against `f511544a`, then `a0fe2665`), W-088g's (`c59d8223` against
`c030e04f`), W-088g1's (`1f975262` against `f3027724`), W-088g2's (`09ed07b7` against `927b2581`) and
W-089's (`f7140cfe` against the post-commit line `1b62fe5a`); W-131d2's (`d7a5b146` pointer against `86035a1c`).
