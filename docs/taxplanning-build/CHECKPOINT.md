# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334d3b done (D-843), close-out written, NOT committed (awaiting owner's "commit it"); then WL-A-2 republish
Stage           : staging FINISHED and signed off on m1.4 (660 transactions / 431 lots / 130 rows / 8 P&L / 14 logins); m1.4 stopped; staging copies deleted; kept: ~/wealthlens-runs/staging-after-d3b.txt (final fingerprint), prod-before-m14.archive + prod-reference-m14.txt (W-334e), s-*.js scripts
Done session 112: W-334d3a (D-842, 4d0a67a0) and W-334d3b (D-843): §2, test logins, ITS-22, W-348a/b, §3, compare, owner's look, sign-off, clean-up
Board           : W-334d —, W-334d1 D, W-334d2 D, W-334d3 —, W-334d3a D, W-334d3b D, W-348a · / W-348b · (done at W-334e), W-334e ·, W-337 ·
Next step       : on "commit it": stage the four named files, commit, push if asked, publish WL-A-2; then plan W-334e (prod) — carry D-843's three findings for prod
Open to owner   : "commit it" for W-334d3b; api-collection Buy Stock.yml:68 doc proposal
Env notes       : jars ../wealthlens-m1.4 (87,816,823) and ../wealthlens-m0 (74,739,166); staging host it-staging.6fu8nvy.mongodb.net; router DNS flaky (6 tries, 30 s apart; grep "records\?"); zsh
Last verify     : FinishedPlanTest + ./mvnw validate exit 0 (session 112, before 4d0a67a0)
Last item commit: taxplanning W-334d3a: run §2 on staging and remove its test logins and ITS-22 collections — every write after a backup equal to its count
Files touched   : docs/taxplanning-build/STATE.md, docs/taxplanning-build/post-deployment-runbook.md, docs/taxplanning-build/plans/W-334d3.md (plus this file)
Updated         : 2026-11-21, session 112 (W-334d3b run and closed out, uncommitted)
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
