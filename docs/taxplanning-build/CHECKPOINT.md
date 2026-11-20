# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : session 109 paused (owner: session limit). Dev holds prod's copy on m1.3 with §1 and all §2 done (dev-after-c7b1.txt)
Stage           : W-334c7b2 · — to plan: W-348a (owner's segment = all delivery, D-815) + W-348b (owner's rows rebuilt with an old-vs-new compare before any delete, then §2.12, §2.11)
Done session 109: D-793 – D-820 — W-341, m1.2, m1.3 (72d7fd4e), W-334c4a – d, c5, c6, c7a, c7b1, W-339, W-340, W-342 – W-347, W-349; W-348 ruled; W-351 placed (M2 review); M2 without hosting (D-820)
Board           : W-334c7b1 D, W-334c7b2 ·, W-334c7b3 ·, W-334c7c ·, W-348a ·, W-348b ·, W-350 ·, W-351 ·, W-334d ·, W-334e ·
Next step       : on 'resume': read this file, run the resume check, then plan W-334c7b2; then c7b3 (W-350), the other two users' rebuild, c7c, W-334d, W-334e (~11 – 15 h hands-on)
Open to owner   : the other two users' declarations; W-337 engine and place vs M2; runner token in git history (repo PUBLIC)
Owner actions   : ask the other two users whether their prod trades were all delivery (W-348a); nothing is running — app stopped, port free
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; rehearsal files ~/wealthlens-runs/w334c (c7b/ holds personal-data backups, mode 700); database tools need GODEBUG=netdns=cgo + a dscacheutil lookup + 3 tries; never cd inside a $ command
Last verify     : W-340 rule red on 4 sites then green: Architecture 26/0, StoredFieldEncryption 9/0 + 1/0, StoredIdentifierEncryption 3/0, TaxJobs 21/0, TaxJobEndpoint 3/0, TaxJobDirectory 5/0
Last item commit: taxplanning D-820: Milestone 2 without a hosted deployment — the prod run is the deployment; M2 is the deploy gate, the removals and the W-351 review
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
