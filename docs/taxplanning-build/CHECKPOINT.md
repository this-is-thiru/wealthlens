# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-089 — T13-1, the rebate with special-rate income (DR 13 R-5). Done (D-427),
                  committed f7140cfe
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-089.md — built as approved; its Build record carries the one departure (the
                  cap-bypass probe failed on RebateNode's overlap guard, not on a nil bill) and its
                  Decisions (D-427) C1 – C4
Approved        : "W-089 approved" (2026-10-31), then "W-089 approved, place W-089e", then "commit it"
Steps done      : all six, plus the close-out and both commits
Next step       : the owner's choice of the next item — W-089e (the un-limited rebate, placed this
                  session) or W-088i (the house-reinvestment exemption, S2 s.86 / 1961 s.54F); W-088h
                  stays unreachable until an unlisted-securities asset kind exists
Last verify     : ./mvnw test -pl backend -Punit → 3849 / 0 / 0, Checkstyle and Spotless green in
                  validate; run before the commit, whose pre-commit gate passed on the same tree
Waiting on      : the owner's choice of the next item — nothing else is outstanding
Files touched   : this file and STATE.md — nothing under backend/; the item commit f7140cfe carries the
                  six paths (the test, the runner method, the plan, the WI rows, the board and D-427)
Last item commit: taxplanning W-089: run the T13-1 case end to end — a ₹12,00,000 income that is almost all long-term gains pays 87,750 rather than nil, because s.156(3) caps the rebate at the slab-rate tax, which is zero (D-427)
Updated         : 2026-10-31, session 43 — W-089 committed f7140cfe, this the post-commit state line
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
W-089's (`f7140cfe` against this line).
