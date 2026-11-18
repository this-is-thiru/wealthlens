# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-259 DONE (D-777), committed and pushed with this checkpoint; next by board order W-258 (agricultural income in the rates), not planned
Stage           : 6 (DONE); nothing in progress
Done session 106: W-131e/h/f/j, D-772, W-132, W-333, W-133a/b, W-259 (D-768 – D-777)
Board           : W-259 D; W-258 · next (waiting: W-126d, W-131i, W-299, W-294, W-113a, W-324, W-146a)
Next step       : 1) gh run list — confirm the last commits green; 2) plan W-258 (Gazette first: agricultural income's partial integration, the Act's provision); stop for approval
Open to owner   : runner token in git history (aws/ removed); host chmod 666 docker.sock
Owner actions   : runbook §0.8; §1.n, §1.p; §1.k, §1.l; §1.m only when the scan is on
Env notes       : integration tests need OrbStack + DOCKER_HOST; never cd inside a $ command; tool .txt files are not compiled (W-333 broke the relock spike)
Last verify     : W-259 targeted 95/0 (7 classes) + validate; break caught
Last item commit: taxplanning W-259: keep the synthetic pack's hand-written rows and locks on regeneration — a rerun of the generator deleted them
Files touched   : none
Updated         : 2026-11-18, session 106
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
