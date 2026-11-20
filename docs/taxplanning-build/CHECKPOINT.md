# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-354 done (D-832); next W-348a / W-348b
Stage           : W-354 committed and closed (board D); WL-A-2 republished, read back equal; not pushed. W-353 pushed, merged (PR #69, 2229df87), tagged m1.4 (D-833)
Done session 110: session 111: D-828 (table), D-829 (list in yaml, no asset type refused, §3.27, W-354), D-830 (W-353 built); prod counts (owner) in D-829 / plan
Board           : W-353 D, W-354 D, W-348a · / W-348b ·, W-351 ·, W-334d ·, W-334e ·, W-337 ·
Next step       : on 'resume': resume check; plan W-348a / W-348b for the other two users on a fresh copy of prod (their trades declared delivery, D-825); then W-334d (staging)
Open to owner   : push (W-354 committed, unpushed); api-collection Buy Stock.yml:68 doc proposal
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; ~/wealthlens-runs/w334c holds prod-before-m1.archive (mode 600), prod-reference.txt, allotment-dates.json and the query scripts only; zsh: wrap globs in (setopt null_glob; …); DNS timeouts — 3-try loops; never cd inside a $ command
Last verify     : W-354: rehearsal x2 on throwaway mongo:8.0.4 (results in D-832); docs only
Last item commit: taxplanning W-354: add runbook §2.17 to remove prod's last test entries — DUMMY3, its test login and the orphan ICICIBANK lot of "username" were the trades and lots with no asset type
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
