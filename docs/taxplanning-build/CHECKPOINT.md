# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-131d2 DONE (D-767), W-131d complete. Next by board order: W-131e (the malware scan) — plan it
Stage           : committed and pushed (see Last item commit); nothing in progress
Done session 105: D-767 — W-131d2
Board           : W-131d2 D; W-131e (scan) ·; W-131f (isolation) ·
Next step       : check the pipeline for the W-131d2 push; "start next" = plan W-131e per 00-START-HERE §2; stop for approval
Open to owner   : W-131g (prompt for a PDF password, O6) yes/no; W-333 (ComputedTax 18 components, past 7) yes/no — neither placed
Owner actions   : runbook §0.8 before the one deploy; §1.n and §1.p on the deploy carrying W-131a – d2; documents switch stays OFF until W-131e + W-131f
Env notes       : integration tests need OrbStack (orb start) + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; never cd inside a $ command
Last verify     : W-131d2 targeted 64/0 (7 classes, 3 integration) + validate
Last item commit: taxplanning W-131d2: reconcile a draft's confirmed certificates with its computation
Updated         : 2026-11-18, session 105
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
