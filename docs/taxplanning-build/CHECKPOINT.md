# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334c7c in progress (rows 1 – 2 go; row 3 sign-off + wipe question asked) — W-334c7b3 done (D-823) — W-350 done: the 18 held trades were one TEST_USER account's (codes A/B/C, no request kept); the whole account removed on dev's copy; fingerprint dev-after-c7b3.txt
Stage           : W-334c7b3 committed and pushed with W-334c7c's plan and its rows 1 – 2 (one commit); WL-A-2 republished; awaiting the owner's sign-off (row 3) and wipe answer
Done session 110: D-821 (61 refused rows left), D-822 (W-334c7b2, pushed 7b1ca4e2), D-823 (W-334c7b3)
Board           : W-334c7b1 D, W-334c7b2 D, W-334c7b3 D, W-350 D, W-334c7c E, W-348a · / W-348b · (the other two users' wait), W-351 ·, W-334d ·, W-334e ·
Next step       : W-334c7c approved; rows 1 – 2 go (§3 as expected; 3.5 2/49 now counted, the other users' unrebuilt rows + owner's BMF sales); row 3 sign-off summary handed; W-334c7b3 still uncommitted (owner's word)
Open to owner   : the other two users' segment declarations (both still there: the removed test account held no outcome rows); W-337 engine and place vs M2; runner token in git history (repo PUBLIC); 13 BMF rows (§3.5)
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; rehearsal files ~/wealthlens-runs/w334c (c7b/ holds personal-data backups, mode 700); scripts c7b2-*, c7b3-* there; DNS timeouts on the owner's network — the 3-try loops hold; never cd inside a $ command
Last verify     : no code changed this session; scripts tried on throwaway mongo:8.0.4 (results in the plans)
Last item commit: taxplanning W-334c7b3: remove the test account holding prod's 18 unredrivable held trades on its copy — codes A / B / C, no request kept, so a redrive could never replay them and the account stayed blocked
Files touched   : none
Updated         : 2026-11-20, session 110
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
