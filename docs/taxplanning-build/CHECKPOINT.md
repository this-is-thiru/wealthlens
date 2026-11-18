# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-131j DONE (D-771), committed and pushed with this checkpoint; next by board order W-132 (T8 tests), not planned
Stage           : 6 (DONE); nothing in progress
Done session 106: W-131e (D-768), W-131h (D-769), W-131f (D-770), W-131j (D-771) pushed; five CI probes, branches deleted; W-131i placed (waits for the scan)
Board           : W-131e/f/h/j D; W-131i ·; W-132 ·; W-133 ·
Next step       : 1) gh run list — confirm the four item commits green; 2) plan W-132 on the §2 template; stop for approval
Open to owner   : aws/ removed (owner, session 106; staged, not committed) — its runner token stays in git history: owner to confirm it is expired or revoke it; chmod 666 docker.sock was a host step, check the host; W-131g declined, W-333 placed after W-132 (D-772, uncommitted)
Owner actions   : runbook §0.8; §1.n (documents switch), §1.p; §1.k, §1.l; §1.m only when the scan is on
Env notes       : integration tests need OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; never cd inside a $ command; read surefire XML for failures; -DskipTests still compiles tests; Linux-only tests via a throwaway CI branch
Last verify     : W-131j targeted 89/0 + validate 0; Linux run 44341679586 passed, break caught
Last item commit: taxplanning W-131j: run the application as wealthlens, not root, and make it non-dumpable — the app ran as root in its container
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
