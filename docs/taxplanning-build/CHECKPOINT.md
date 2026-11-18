# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : PAUSED. W-131e planned and APPROVED C1a – C4a (plans/W-131e.md, board E); not built
Stage           : nothing in progress; code at d7a5b146 (pushed); this pause commit holds the plan + board + checkpoint
Done session 105: D-767 — W-131d2 (86035a1c + pointer d7a5b146, api 107fed2); W-131e planned + approved
Board           : W-131d2 D; W-131e E (approved); W-131f (isolation) ·
Next step       : 1) gh run list — confirm d7a5b146 green (run 44236362337: {"conclusion":"success","status":"completed"}); 2) ask the owner the three open points: C5 (local ClamAV probe — it was denied by the permission classifier, do not retry without a yes), EC2 host memory (≥ 3 GiB for ClamAV), scan timeout PT60S adopt or measure; 3) build W-131e per plan steps; stop for review
Open to owner   : C5 / host memory / timeout (above); W-131g (PDF password prompt, O6) yes/no; W-333 (ComputedTax 18 components, past 7) yes/no — neither placed
Owner actions   : runbook §0.8 before the one deploy; §1.n and §1.p on the deploy carrying W-131a – d2; documents switch stays OFF until W-131e + W-131f
Env notes       : integration tests need OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; never cd inside a $ command; read surefire XML for failures (stdout gets cut)
Last verify     : W-131d2 targeted 64/0 (7 classes, 3 integration) + validate
Last item commit: taxplanning W-131d2: reconcile a draft's confirmed certificates with its computation
Files touched   : none beyond this pause commit
Updated         : 2026-11-18, session 105 (paused; owner shutting down)
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
