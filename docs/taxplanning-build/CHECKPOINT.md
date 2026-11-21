# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-358b DONE (D-854), committed with api-collection pointer and pushed to PR #70; next W-358c (not started)
Stage           : prod and staging on m1.4, signed off; m1.4 stopped; MongoDB tools REMOVED; kept: ~/wealthlens-runs/archives/ (delete by 2027-11-21 or after W-337) + ~/wealthlens-runs/fingerprint.js
Done session 113: W-358b built — resource server, 15-min access, 14-day rotating refresh tokens (fixed from sign-in), cross-user path now 403
Board           : W-358a D, W-357a D, W-359a D, W-358b D; W-359 E, W-358c E, W-359b E; W-357b ·, W-358d ·, W-355 ·, W-356 ·, W-337 ·
Next step       : check PR #70's pipeline for the W-358b push (fix if red); then build W-358c per plans/W-359.md commit 3 (zoned /api/v1 URLs, /me, UserValidator removed), red first; publish WL-A-2 if not confirmed
Open to owner   : PR #70 not to merge until the owner says; refresh expiry fixed 14 d from sign-in (D-854, owner may prefer sliding); httpBasic still on (proposal in D-854); 5 Dependabot alerts on master; api-collection Buy Stock.yml:68 doc proposal
Env notes       : NO Claude attribution anywhere; no MongoDB tools on this machine; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock
Last verify     : W-358b — RefreshToken 12 (red 12/12 first), Auth 17, RouteAccess 5, AccountErasure 8 + 10 more classes green; 3 probes failed as expected and restored; validate clean
Last item commit: taxplanning W-358b: read tokens with Spring's resource server and add rotating refresh tokens — a stolen access token lasts 15 minutes, a reused refresh token ends the sign-in
Files touched   : (none) (plus this file)
Updated         : 2026-11-21, session 113 (W-358b committed and pushed)
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
