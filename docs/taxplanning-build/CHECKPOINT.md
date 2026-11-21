# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-358c2 DONE (D-858), committed and pushed to PR #70
Stage           : prod and staging on m1.4, signed off; m1.4 stopped; MongoDB tools REMOVED; kept: ~/wealthlens-runs/archives/ (delete by 2027-11-21 or after W-337) + ~/wealthlens-runs/fingerprint.js
Done session 113: W-358b built — resource server, 15-min access, 14-day rotating refresh tokens (fixed from sign-in), cross-user path now 403
Board           : W-358a D, W-357a D, W-359a D, W-358b D, W-358c —, W-358c1 D, W-358c2 D; W-358c3 ·, W-359 E, W-359b E; W-357b ·, W-358d ·, W-355 ·, W-356 ·, W-337 ·
Next step       : check PR #70's run for the W-358c2 push (fix if red); then plan W-358c3 (REST shapes and pagination), §2 template, board E
Open to owner   : PR #70 not to merge until the owner says; refresh expiry fixed 14 d from sign-in (D-854, owner may prefer sliding); httpBasic still on (proposal in D-854); 5 Dependabot alerts on master; api-collection Buy Stock.yml:68 doc proposal
Env notes       : NO Claude attribution anywhere; no MongoDB tools on this machine; integration tier needs OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock
Last verify     : W-358c2 — ResponseContract red 20 → green, ResponseContractIntegration red (owner email on every card) → green, probe bites both; 26 mover classes green; validate clean
Last item commit: taxplanning W-358c2: answer every route with a response type, never a stored document — the owner's email stopped reaching users through rate cards
Files touched   : (none) (plus this file)
Updated         : 2026-11-21, session 113 (W-358c2 committed and pushed)
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
