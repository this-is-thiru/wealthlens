# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : PAUSED by the owner (2026-11-20, late). W-334d3 split (D-841); W-334d3a approved, scripts built and tried, no row run yet
Stage           : staging consistent after d2 (m1.4, encrypted, cards seeded, app stopped); nothing running; d3a row 1 not run (it creates d3/); kept in ~/wealthlens-runs/w334c: staging-before-m14.archive + staging-reference-m14.txt (the way back), d1/ (owner baseline), d2/, s-*.js (45 staging copies) + s-d3-*.js (5, tried), staging-allotment-dates.json
Done session 110: session 111: D-828 (table), D-829 (list in yaml, no asset type refused, §3.27, W-354), D-830 (W-353 built); prod counts (owner) in D-829 / plan
Board           : W-334c8 D, W-334d —, W-334d1 D, W-334d2 D, W-334d3 —, W-334d3a P, W-334d3b ·, W-348a · / W-348b · (done at W-334e), W-334e ·, W-337 ·
Next step       : on 'resume': resume check; then give W-334d3a row 1 (in the last session's message: env + m1.4 staging start + wl_token, files in d3/); then Part A batched — each step's read-only checks in one block, each write alone after its backup check and a go; plan plans/W-334d3.md, rows from plans/W-334c7b1.md A2 – E4 with s-*.js
Open to owner   : resume at W-334d3a row 1 (batched); api-collection Buy Stock.yml:68 doc proposal
Env notes       : jars ../wealthlens-m1.4 (87,816,823) and ../wealthlens-m0 (74,739,166); staging host it-staging.6fu8nvy.mongodb.net, set atlas-12c0g5-shard-0, profile --spring.profiles.active=staging, keys bbf4a26647f6 / c59064f277a6 / 5524e829976b; staging WARN `malware scan is off` expected; prod archives + references kept for W-334e; dev wiped; zsh (setopt null_glob; …); DNS: rerun the line
Last verify     : W-354: rehearsal x2 on throwaway mongo:8.0.4 (results in D-832); docs only
Last item commit: taxplanning W-334d3: plan staging's last part and split it into d3a / d3b — §2, the test logins, the held trade and the ITS-22 collections, then W-348, §3 and the sign-off
Files touched   : none
Updated         : 2026-11-20 late, session 111 (paused: session limit, owner shutting down)
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
