# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-353 done (D-830); next W-354
Stage           : W-353 committed and closed (board D); WL-A-2 republished, read back equal; not pushed
Done session 110: session 111: D-828 (table), D-829 (list in yaml, no asset type refused, §3.27, W-354), D-830 (W-353 built); prod counts (owner) in D-829 / plan
Board           : W-353 D, W-354 ·, W-348a · / W-348b ·, W-351 ·, W-334d ·, W-334e ·, W-337 ·
Next step       : on 'resume': resume check; plan W-354 (prod's 5 trades with no asset_type: read who / what / how, no email printed; owner runs the query), §2 template; then W-348a / W-348b on a fresh copy; then W-334d (staging)
Open to owner   : push (W-353 committed, unpushed); api-collection Buy Stock.yml:68 doc proposal
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; ~/wealthlens-runs/w334c holds prod-before-m1.archive (mode 600), prod-reference.txt, allotment-dates.json and the query scripts only; zsh: wrap globs in (setopt null_glob; …); DNS timeouts — 3-try loops; never cd inside a $ command
Last verify     : targeted: PortfolioServiceTest 36/0/0, TradeExchangePropertiesTest 3/0/0, TradeExchangeRefusalIntegrationTest 2/0/0, CostBasis 9/0/0, CollisionRetry 7/0/0, Idempotency 12/0/0, Modulith 1/0/0, Architecture 26/0/0; validate green; tiers on the pipeline after push
Last item commit: taxplanning W-353: refuse a trade whose exchange its asset type does not list — BMF, nse, NSE and none reached the tax side, which refused the term or settled no STT
Files touched   : none
Updated         : 2026-11-20, session 111
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
