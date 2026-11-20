# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-343 (D-800) and W-345 (D-801) done, committed and pushed; pipeline on the next PR
Stage           : next W-334c4b — waits for the owner's four allotment dates; owner may reopen D-798 given W-343
Done session 109: W-334c4 split (D-793); c4a rows 1 – 3, 4 (stop), 4a logged; W-341 built: embedded CA actors sealed, 3 red-first ITs, runbook §1.r embeddedActors; c2-05-plain-check.js widened
Board           : W-343 D, W-344 D, W-345 D, W-334c4b ·, W-334c4c ·, W-334c5 ·, W-334d ·, W-334e ·
Next step       : commit both (separate commits); owner decides whether W-343 reopens D-798 (§2.1 might now pair on a build carrying it); W-334c4b once dates are in
Open to owner   : approve W-334c6; W-340, W-339 before W-334d; W-337; runner token in git history (repo PUBLIC)
Owner actions   : allotment dates from each company's announcement: HDFCBANK (rec 2025-08-27), ASHOKLEY (2025-07-17), MSUMI (2025-07-19), RELIANCE (2024-10-28)
Env notes       : database tools: GODEBUG=netdns=cgo AND a dscacheutil lookup + up to 3 tries; passwords via read -s; never cd inside a $ command
Last verify     : W-343 rebuild 6/0, §2.1 6/0 + 5/0 (red first); W-345 gap audit 62/0, CA 48/0, split 7/0, Architecture 25/0, Modulith, Checkstyle (red first)
Last item commit: portfolio W-345: refuse a stock code with a leading or trailing space on every entry — a spaced code holds apart from its clean twin
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
