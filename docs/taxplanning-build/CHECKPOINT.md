# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334c8c done (D-837); W-334c8 complete; next plan W-334d (staging)
Stage           : W-334c8c committed, closed and pushed; dev wiped; rehearsal files deleted
Done session 110: session 111: D-828 (table), D-829 (list in yaml, no asset type refused, §3.27, W-354), D-830 (W-353 built); prod counts (owner) in D-829 / plan
Board           : W-334c8 D, W-334c8a D, W-334c8b D, W-334c8c D, W-348a · / W-348b · (done at W-334e), W-351 ·, W-334d ·, W-334e ·, W-337 ·
Next step       : on 'resume': resume check; plan W-334d (staging) from its row and the m1.4 rehearsal (D-834 – D-837)
Open to owner   : W-334d plan (to be written); api-collection Buy Stock.yml:68 doc proposal
Env notes       : jars in ../wealthlens-m1.4, ../wealthlens-m1.3, ../wealthlens-m0; ~/wealthlens-runs/w334c holds prod-before-m1.archive + prod-before-m14.archive (mode 600), both references, allotment-dates.json and the query scripts only; dev wiped; zsh: (setopt null_glob; …); DNS: rerun the line
Last verify     : W-354: rehearsal x2 on throwaway mongo:8.0.4 (results in D-832); docs only
Last item commit: taxplanning W-334c8c: sign off the prod rehearsal on m1.4 and wipe dev — §3 as expected or explained, §3.27 178 outside the exchange table, all BMF
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
