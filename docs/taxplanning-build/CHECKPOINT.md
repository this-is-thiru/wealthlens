# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-134a committed and closed (D-448)
Stage           : next by board order: W-137 (TaxJob + JobLease + 6 jobs, with W-136 retention), not started
Done today      : session 47: planned, approved C1a – C7a, built; 4440 / 0 / 0; four probes bit and reverted
Board           : W-134 D, W-134a —, W-135 D, W-136 —, W-235 ·
Next step       : read W-137 and its decision rows (DR 19 E8, D-447 C6a), source what they need (Gazette only), write its plan, STOP
Owner actions   : runbook §0.5 (keys; clear eleven tax_* collections) and §0.6 (count legacy salary data; deploy held until W-159 D), plus §1.x, §3.7 – §3.9, §0.4 at the deploy; push review/taxplanning when ready (W-217 check first); `orb start` before the integration tier
Last verify     : unit + integration 4440 / 0 / 0; Checkstyle 0; Spotless clean (at the W-134 family commit)
Files touched   : none
Last item commit: taxplanning W-134a: merge the legacy salary data into W-159 and hold the deploy until it is done
Updated         : 2026-11-06, session 47 — W-134a closed
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
W-089's (`f7140cfe` against the post-commit line `1b62fe5a`).
