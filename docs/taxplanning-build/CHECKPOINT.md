# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-132 DONE (D-773), committed and pushed with this checkpoint (+ api-collection); next by board order W-333, not planned
Stage           : 6 (DONE); nothing in progress
Done session 106: W-131e/h/f/j (D-768 – D-771), D-772, W-132 (D-773); aws/ removed
Board           : W-132 D; W-333 · next; W-133 ·
Next step       : 1) gh run list — confirm the last commits green; 2) plan W-333 (check first whether ComputedTax is serialized or stored as-is); stop for approval
Open to owner   : runner token in git history (aws/ removed); host chmod 666 docker.sock
Owner actions   : runbook §0.8; §1.n, §1.p; §1.k, §1.l; §1.m only when the scan is on
Env notes       : integration tests need OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; never cd inside a $ command; read surefire XML; Linux-only tests via a throwaway CI branch; an unused import makes Checkstyle stop a break run before its tests
Last verify     : W-132 targeted 110/0 + golden 5/0 on the real engine + validate 0; breaks (a sentence, the question, the regime) caught
Last item commit: taxplanning W-132: the T8 golden cases on the shipped layout and the engine, and each reason in words — a report gave codes the user could not read
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
