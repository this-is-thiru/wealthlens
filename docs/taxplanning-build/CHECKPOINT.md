# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-361 DONE (D-865), W-360 DONE (D-864) — committed and pushed to PR #70 (session 114)
Stage           : prod and staging on m1.4, signed off; m1.4 stopped; MongoDB tools REMOVED; kept: ~/wealthlens-runs/archives/ (delete by 2027-11-21 or after W-337) + ~/wealthlens-runs/fingerprint.js
Done session 113: W-358b (D-854) + 401→403 fix; W-358c split (D-855); W-358c1 (D-856); W-358c2 (D-857, D-858); W-358c3 split (D-859); W-358c3a (D-860) + fix; W-358c3b (D-861, D-862); runbook "Next deploy" list
Board           : W-358a D, W-357a D, W-359a D, W-358b D, W-358c —, W-358c1 D, W-358c2 D, W-358c3 —, W-358c3a D, W-358c3b D; W-360 D, W-361 D, W-359 E, W-359b E; W-357b ·, W-358d ·, W-355 ·, W-356 ·, W-337 ·
Next step       : check the pipeline run for the push (fix if red); then plan W-206 (request DTOs, §2 template, probe first) → W-359b → merge PR #70 on the owner's word → deploy
Open to owner   : PR #70 merge after W-359b and a green run (owner proposal); after W-359b by board order W-358d, W-357b, W-355, W-356, W-351 (M2 review), W-337 (SQL, after deploying everything); past commits keep Claude co-author lines
Env notes       : NO Claude attribution anywhere; no MongoDB tools on this machine; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock
Last verify     : W-360 tree 1.85, Form130Parser 25 + DocumentBytes 7 green; W-361 red 200≠401 → green, probe bites, 9 classes green; validate clean
Last item commit: taxplanning W-361: turn HTTP Basic off — a password on every request skipped the sign-in record and the refresh flow
Files touched   : (none) (plus this file)
Updated         : 2026-11-21, session 114
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
