# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-131f DONE (D-770), committed and pushed with this checkpoint; next by board order W-131j (the app as non-root), not planned
Stage           : 6 (DONE); nothing in progress
Done session 106: W-131e (D-768), W-131h (D-769), W-131f (D-770) pushed; probes 44328189271, 44334020550, 44338803065 (branches deleted); W-131i, W-131j placed
Board           : W-131e D; W-131f D; W-131h D; W-131i · (waits for the scan); W-131j ·; W-132 ·
Next step       : 1) gh run list — confirm the three item commits green; 2) plan W-131j on the §2 template; stop for approval
Open to owner   : W-131g, W-333 yes/no
Owner actions   : runbook §0.8; §1.n (documents switch: W-131e/f done, scan off by D-769), §1.p; §1.l (confirm log line, ~200 MB per concurrent read); §1.m only when the scan is on
Env notes       : integration tests need OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; never cd inside a $ command; read surefire XML for failures (stdout gets cut); -DskipTests still compiles tests (use -Dmaven.test.skip=true on a probe branch)
Last verify     : W-131f targeted 152/0 (16 classes) + validate 0; four deliberate breaks caught
Last item commit: taxplanning W-131f: read every uploaded PDF in a child process as nobody with no secrets — a hostile PDF was parsed inside the application
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
