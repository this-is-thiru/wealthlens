# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334c7c done (D-824) — the prod rehearsal on m1.3 signed off; dev wiped (0 collections); rehearsal files deleted (archive, reference, scripts kept)
Stage           : DONE — committed and pushed with D-825 – D-827 and W-353; WL-A-2 republished
Done session 110: D-821, D-822 (W-334c7b2, pushed 7b1ca4e2), D-823 (W-334c7b3, pushed 553b99df), D-824 (W-334c7c)
Board           : W-334c7a – c7c D, W-350 D, W-348a · / W-348b · (the other two declared delivery, D-825 — rehearse on a fresh copy), W-353 · (new: exchange check), W-351 ·, W-334d ·, W-334e ·
Next step       : on the owner's yes to the OFF_EXCHANGE table, plan W-353 (before W-334e); then W-334d (staging) and the other two users' W-348a / W-348b on a fresh copy
Open to owner   : W-337 MySQL on Oracle Always Free before M2 (D-827); W-353 ruled NSE / BSE only for EQUITY and MUTUAL_FUND (D-826) — before W-334e (owner); OFF_EXCHANGE proposal (BOND/GOLD_BOND NSE/BSE/OFF_EXCHANGE, FD/INSURANCE OFF_EXCHANGE, refuse when absent) awaiting a yes; §3.5 closes with the other users' rebuild except BMF sales
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; ~/wealthlens-runs/w334c holds prod-before-m1.archive (mode 600), prod-reference.txt and the query scripts only; zsh: a glob matching nothing aborts the command — use (setopt null_glob; …); DNS timeouts — 3-try loops; never cd inside a $ command
Last verify     : no code changed this session; scripts tried on throwaway mongo:8.0.4 (results in the plans)
Last item commit: taxplanning W-334c7c: sign off the prod rehearsal on m1.3 and wipe dev — §3 as expected on prod's copy, the rehearsal files deleted, prod's archive kept for W-334e
Files touched   : none
Updated         : 2026-11-20, session 110
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
