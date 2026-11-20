# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-348 ruled (D-812), committed and pushed; W-348a/b/c, W-334a3 placed
Stage           : W-334a3 E approved — plan committed; rows 1 – 7 run by the session
Done session 109: W-334c4 split (D-793); c4a rows 1 – 3, 4 (stop), 4a logged; W-341 built: embedded CA actors sealed, 3 red-first ITs, runbook §1.r embeddedActors; c2-05-plain-check.js widened
Board           : W-348 D, W-348a ·, W-348b ·, W-348c ·, W-334a3 ·, W-350 ·, W-334d ·, W-334e ·
Next step       : on 'commit and push': commit the plan, then rows 1 – 7 (PR, pipeline, merge, master pipeline, tag m1.3, worktree)
Open to owner   : the two test users' logins (remove or keep); W-337 engine and place vs M2; runner token in git history (repo PUBLIC)
Owner actions   : your own segment declaration for W-348a (were all your prod trades delivery?); the two test users' logins
Env notes       : database tools: GODEBUG=netdns=cgo AND a dscacheutil lookup + up to 3 tries; passwords via read -s; never cd inside a $ command
Last verify     : W-340 rule red on 4 sites then green: Architecture 26/0, StoredFieldEncryption 9/0 + 1/0, StoredIdentifierEncryption 3/0, TaxJobs 21/0, TaxJobEndpoint 3/0, TaxJobDirectory 5/0
Last item commit: taxplanning W-348: rule on prod's old outcome rows — rebuild them with today's code, segments from each user's declaration, unbroken charges recorded again (W-348a/b/c, m1.3)
Files touched   : none
Updated         : 2026-11-20, session 109
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
