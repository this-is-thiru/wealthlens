# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334a1 DONE (D-790): m1.1 = 6b5412db, worktree ../wealthlens-m1.1 built; next: re-plan W-334c2 for m1.1
Stage           : W-334c2 B → to re-plan: row 0 reset dev's copy (drop, restore archive, gate IDENTICAL), m1.1 jar everywhere, then rows 1 – 9 again
Done session 108: plans/W-334a.md, plans/W-334b.md written; W-334 plan step 3 ticked; STATE lines 7, 8, 65 updated
Board           : W-334a E, W-334b E, W-334c – f ·; W-335 B; every other open item waits (W-299, W-294, W-113a, W-324, W-131i, W-126d, W-146a)
Next step       : on the owner's commit word close W-334a1; then update plans/W-334c2.md for the rerun (reset dev, m1.1 jar), board W-334c2 B → E, stop for approval
Open to owner   : approve W-334a, W-334b; commit of these planning docs (needs the owner's word); runner token in git history (repo is PUBLIC); host chmod 666 docker.sock
Owner actions   : W-334a rows 1 – 6, then W-334b rows 1 – 9
Env notes       : database tools: prefix GODEBUG=netdns=cgo (router DNS times out for Go's resolver); passwords via read -s only; -Dtest=Class skips @Nested; never cd inside a $ command
Last verify     : W-338: 5 affected ITs 37/0, StoredStringId 6/0 (red 6/6 with a bare collection name), Architecture+Modulith green, validate green
Last item commit: taxplanning W-334a1: log the merge of PR #66 as 6b5412db and the tag m1.1 — every database now runs the interim run from the build that encrypts prod's rows
Files touched   : none
Updated         : 2026-11-19, session 108
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
