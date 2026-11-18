# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334 docs committed and pushed with this checkpoint (runbook §6, D-780, D-781; WL-A-2 published; W-335 = WL-48); execution not started
Stage           : W-334 R; next: plan W-334a (merge + tag) and W-334b (preflight) as checklist items, stop for approval
Done session 107: W-258, W-258a, the W-258 fix 92cc59b5, all pushed at 9973913a (green); W-334 built, uncommitted
Board           : W-334 R; W-335 B; every other open item waits (W-299, W-294, W-113a, W-324, W-131i, W-126d, W-146a)
Next step       : owner (2026-11-19): deployment work as crisp, checklist-driven items, run interactively, no compromise. First: split W-334 into W-334a merge+tag, W-334b preflight (keys, tier, tools, fingerprint twice on prod), W-334c prod rehearsal on dev, W-334d staging, W-334e prod, W-334f dev fresh — each plan = numbered checklist with exact command, expected result, stop condition, way back; owner approves each; one item at a time; then commit W-334 docs
Open to owner   : W-334 review; then §6's runs; runner token in git history (aws/ removed); host chmod 666 docker.sock
Owner actions   : runbook §6 (the interim run); §0.8 and §1.r are inside it
Env notes       : -Dtest=Class skips @Nested classes — use quoted Class*; integration tests need DOCKER_HOST=unix:///Users/pthirumalayadav/.orbstack/run/docker.sock; never cd inside a $ command
Last verify     : W-334 doc tests 78/0 (+1 integration 1/0); validate green
Last item commit: taxplanning W-334: write the interim run's ordered, gated sheets for dev, staging and prod — the app runs locally and three databases need the merged build once
Files touched   : none
Updated         : 2026-11-19, session 107
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
