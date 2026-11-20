# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334c7a done (D-817), committed and pushed; dev = dev-after-c7a.txt
Stage           : next — plan W-334c7b (§2 + W-348a/b + W-350 + §2.16 step 5); the other two users' declarations still needed for W-348b
Done session 109: W-334c4 split (D-793); c4a rows 1 – 3, 4 (stop), 4a logged; W-341 built: embedded CA actors sealed, 3 red-first ITs, runbook §1.r embeddedActors; c2-05-plain-check.js widened
Board           : W-334c7a D, W-334c7b ·, W-334c7c ·, W-348a ·, W-348b ·, W-350 ·, W-351 ·, W-334d ·, W-334e ·
Next step       : commit; plan W-334c7b
Open to owner   : the other two users' declarations; W-337 engine and place vs M2; runner token in git history (repo PUBLIC)
Owner actions   : obtain the other two users' segment declarations (W-348a); the owner's own given: all delivery (D-815)
Env notes       : database tools: GODEBUG=netdns=cgo AND a dscacheutil lookup + up to 3 tries; passwords via read -s; never cd inside a $ command
Last verify     : W-340 rule red on 4 sites then green: Architecture 26/0, StoredFieldEncryption 9/0 + 1/0, StoredIdentifierEncryption 3/0, TaxJobs 21/0, TaxJobEndpoint 3/0, TaxJobDirectory 5/0
Last item commit: taxplanning W-334c7a: log the reset, encryption and charge engine on prod's copy with m1.3 — all go; §2.1 now pairs one user with unexplained changes, so W-348b compares before it deletes
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
