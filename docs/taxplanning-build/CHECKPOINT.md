# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334c7b2 done (D-822): W-348a + W-348b for the owner's account on dev's copy with m1.3, every row go; fingerprint dev-after-c7b2.txt
Stage           : DONE — committed and pushed on "commit it and push"; WL-A-2 republished
Done session 110: D-821 (the 61 refused rows left as they are, years filed; W-352, W-348c not taken), D-822 (owner's segment 448 → DELIVERY; 90 rows rebuilt, compared, −16.00 double-counted buy charges; lots 298; P&L 3 years)
Board           : W-334c7b2 D, W-334c7b3 ·, W-334c7c ·, W-348a · / W-348b · (owner's done; the other two users' wait for declarations), W-348c —, W-350 ·, W-351 ·, W-352 —, W-334d ·, W-334e ·
Next step       : on 'resume': resume check; then plan W-334c7b3 (W-350, the 18 held trades) on dev as it stands (dev-after-c7b2.txt)
Open to owner   : the other two users' segment declarations; W-337 engine and place vs M2; runner token in git history (repo PUBLIC); 13 rows with exchange BMF (§3.5 term refused) — noted, years filed
Env notes       : jars in ../wealthlens-m1.3 and ../wealthlens-m0; rehearsal files ~/wealthlens-runs/w334c (c7b/ holds personal-data backups, mode 700); scripts c7b2-01/02/05/08/11 there; database tools need GODEBUG=netdns=cgo + a dscacheutil lookup + 3 tries; never cd inside a $ command
Last verify     : no code changed this session; scripts tried on throwaway mongo:8.0.4 (results in plans/W-334c7b2.md)
Last item commit: taxplanning W-334c7b2: rebuild the owner's outcome rows, lots and P&L on prod's copy with m1.3 — every old row compared first, −16.00 double-counted buy charges gone; the 61 refused rows left as they are, their years filed
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
