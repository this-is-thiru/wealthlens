# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334e2 done (D-847), close-out written, NOT committed (awaiting owner's "commit it"); then WL-A-2; then plan W-334e3
Stage           : prod after §2, test entries removed (788 / 518 / 12 logins), m1.4 STOPPED; 163 rows still without segments / venues (W-348, e3); kept in ~/wealthlens-runs/w334c: prod-before-m14.archive + prod-reference-m14.txt (the way back), e1/ (baseline-owner-*, before-1r), e2/ (every §2 backup, mode 600), p-*.js (31)
Done session 112: staging finished (D-842, D-843); W-334e split (D-844); W-334e1 (D-845); W-334e2 (D-846, D-847)
Board           : W-334e —, W-334e1 D, W-334e2 D, W-334e3 ·, W-348a · / W-348b ·, W-337 ·
Next step       : on "commit it": stage the four named files, commit, push if asked, WL-A-2; then plan W-334e3: W-348a/b for owner + test6 + test10 (W-334c8b Part W rows with p- copies of s-d3-w1/w3/w5/w8/w8b), §3 (c5-01, w342, w344, c8c-01 as p-), compare with e1 baseline, owner's look, sign-off, clean-up (both prod archives + references, e1/ e2/, MongoDB tools per D-786 C1a)
Open to owner   : "commit it" for W-334e2; api-collection Buy Stock.yml:68 doc proposal
Env notes       : prod host cluster0.x6trgen.mongodb.net, db Cluster0, set atlas-24ynrh-shard-0, profile prod, keys 8d910c6ebabf / f9bf756780af / af38366a5db3; WARN malware scan off expected; router DNS flaky (6 tries); zsh
Last verify     : FinishedPlanTest + ./mvnw validate exit 0 (session 112, before f9f11c67)
Last item commit: taxplanning W-334e1: bring prod to m1.4 up to the charge engine — unchanged since the rehearsal, encrypted under its own keys, rate cards seeded
Files touched   : docs/taxplanning-build/STATE.md, docs/taxplanning-build/post-deployment-runbook.md, docs/taxplanning-build/plans/W-334e2.md (new) (plus this file)
Updated         : 2026-11-21, session 112 (W-334e2 run and closed out, uncommitted)
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
