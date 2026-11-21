# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-359a BUILT (D-853), awaiting 'commit it' (api-collection: commit inside the submodule, then stage its pointer); PR #70 pipeline 44887511915 SUCCESS; next W-358b
Stage           : prod and staging on m1.4, signed off; m1.4 stopped; MongoDB tools REMOVED (node autoremoved too — owner told); kept: ~/wealthlens-runs/archives/ (prod-after-m14 1,635 / staging-after-m14 1,406 docs, mode 600, delete by 2027-11-21 or after W-337) + ~/wealthlens-runs/fingerprint.js; everything else of the run deleted
Done session 112: staging finished (D-842, D-843); prod: split (D-844), e1 (D-845), e2 (D-846, D-847), e3 (D-848, D-849); W-355, W-356 placed
Board           : W-358a D, W-357a D, W-359a D (built, uncommitted); W-359 E, W-358b E, W-358c E, W-359b E; W-357b ·, W-358d ·, W-355 ·, W-356 ·, W-337 ·
Next step       : after 'commit it': commit api-collection (2 files) inside the submodule, then the item commit with its pointer; push to PR #70; then build W-358b per plans/W-359.md
Open to owner   : 'commit it' for W-359a; PR #70 not to merge until the owner says (pipeline 44887511915 success); api-collection Buy Stock.yml:68 doc proposal
Env notes       : no MongoDB tools on this machine now (reinstall: brew tap mongodb/brew && brew install mongodb-database-tools); prod host cluster0.x6trgen.mongodb.net, staging it-staging.6fu8nvy.mongodb.net
Last verify     : W-359a — 15 classes green (ControllerAdviser 23 red-first 22/23, Actuator 3 proven by removing the dependency, RouteAccess 5, Auth 17); validate clean; doc tests green
Last item commit: taxplanning W-357a: remove the docs history and keep what is cited — decisions in their own file, an index, a tag for the rest
Files touched   : ControllerAdviser, ControllerAdviserTest, AuthConfig, pom, application.yaml, ActuatorIntegrationTest (new), RouteAccessIntegrationTest, 9 integration tests; removed ResponseWrapperAdvice, ApiResponse, ErrorResponse; CLAUDE.md; DECISIONS, STATE, 03-WORK-ITEMS, plans/W-359.md (new); api-collection 2 files (plus this file)
Updated         : 2026-11-21, session 112 (W-359a built)
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
