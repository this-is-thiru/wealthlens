# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-131h DONE (D-769), committed and pushed with this checkpoint; next by board order W-131f (isolation), not planned
Stage           : 6 (DONE); nothing in progress
Done session 106: W-131e (8587649d, D-768); W-131h (D-769): scan off in staging and prod; W-131i (rescan) placed; WL-A-2 republished
Board           : W-131e D; W-131f ·; W-131h D; W-131i ·
Next step       : 1) gh run list — confirm 8587649d and the W-131h commit green; 2) plan W-131f (parse isolation, DR 14 O6) on the §2 template; stop for approval
Open to owner   : W-131g, W-333 yes/no; freshclam staleness alert (proposal, unplaced, moot while the scan is off)
Owner actions   : runbook §0.8 before the one deploy; §1.n, §1.p; §1.m only when the scan is turned on; documents switch OFF until W-131f
Env notes       : integration tests need OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; never cd inside a $ command; read surefire XML for failures (stdout gets cut)
Last verify     : W-131h targeted 67/0 (9 classes) + validate 0; red first; two deliberate breaks caught
Last item commit: taxplanning W-131h: let uploads be stored without a malware scan in staging and prod — the owner defers the scanner on traffic and budget
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
