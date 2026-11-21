# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334d3a done (D-842), close-out written, NOT committed (awaiting owner's "commit it"); then WL-A-2 republish (runbook changed)
Stage           : staging after d3a (m1.4 STOPPED; fingerprint d3/staging-after-d3a.txt: transactions 660, assets 431, trade_outcomes 129, P&L 9, user_details 14); kept in ~/wealthlens-runs/w334c: staging-before-m14.archive + staging-reference-m14.txt (the way back), d1/ (owner baseline), d2/, d3/ (backups, mode 600), s-*.js, s-d3-*.js (incl. new read-only s-d3-x1-dummy-stocks.js), staging-allotment-dates.json
Done session 112: FinishedPlanTest was red on HEAD (W-334d3 '—' + open step 4) — step 4 marked 'not taken under W-334d3', test + validate exit 0; W-334d3a rows 1 – Stop, all go; owner "i" (both test logins removed) and "a" (ASHOKLEYDUMMY left; its §2.8-unplaced sale folded by that user's W-348b + §2.11 in d3b)
Board           : W-334d3 —, W-334d3a D, W-334d3b ·, W-348a · / W-348b ·, W-334e ·, W-337 ·
Next step       : on "commit it": stage the four named files + this one, commit, publish WL-A-2; then W-334d3b: give its row 1 (env + m1.4 staging start + wl_token, files in d3/), then Part W (W-334c8b W1 – W11 with s-d3-w*.js; users from W1, test logins gone; expect 129 rows), X, Y, Z
Open to owner   : "commit it" for W-334d3a; api-collection Buy Stock.yml:68 doc proposal
Env notes       : jars ../wealthlens-m1.4 (87,816,823) and ../wealthlens-m0 (74,739,166); staging host it-staging.6fu8nvy.mongodb.net, set atlas-12c0g5-shard-0, profile --spring.profiles.active=staging, keys bbf4a26647f6 / c59064f277a6 / 5524e829976b; staging WARN `malware scan is off` expected; router DNS flaky (6 tries, 30 s apart; grep "records\?" — singular "record" for 1); zsh
Last verify     : W-354: rehearsal x2 on throwaway mongo:8.0.4 (results in D-832); docs only
Last item commit: taxplanning W-334d3: plan staging's last part and split it into d3a / d3b — §2, the test logins, the held trade and the ITS-22 collections, then W-348, §3 and the sign-off
Files touched   : docs/taxplanning-build/STATE.md, docs/taxplanning-build/post-deployment-runbook.md, docs/taxplanning-build/plans/W-334d3.md (plus this file)
Updated         : 2026-11-21, session 112 (W-334d3a run and closed out, uncommitted)
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
