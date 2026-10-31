# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-089 — T13-1, the rebate with special-rate income (DR 13 R-5). Done (D-427)
Stage           : 6 of 6 (REVIEWED) — approved, closed out, NOT committed; nothing in flight
Plan            : plans/W-089.md — built as approved; its Build record carries the one departure (the
                  cap-bypass probe failed on RebateNode's overlap guard, not on a nil bill) and its
                  Decisions (D-427) C1 – C4
Approved        : "W-089 approved" (2026-10-31), then "W-089 approved, place W-089e"
Steps done      : all six, including the close-out: the WI row's built clause, board D, D-427, W-089e
                  placed (WI row at 03-WORK-ITEMS.md:1371 and board `W-089e ·`), STATE 7–8
Next step       : the owner's word to commit. Nothing else is outstanding; W-089d's sibling W-089e is
                  placed but not started, and W-088i remains the next substantive item on the board
Last verify     : ./mvnw test -pl backend -Dtest=T13RebateAndSpecialRateTest → 1 / 0 / 0;
                  ./mvnw test -pl backend -Punit → 3849 / 0 / 0, Checkstyle and Spotless green in validate
Waiting on      : the owner's "commit it" — the tree is staged-ready but uncommitted
Files touched   : backend/.../golden/T13RebateAndSpecialRateTest.java (new),
                  backend/.../golden/GoldenCaseRunner.java, plans/W-089.md (new), 03-WORK-ITEMS.md,
                  STATE.md (board, D-427, fields 7–8) and this file
Last item commit: taxplanning W-088g: cap the tax on land or building acquired before 23-7-2024 at the lesser of 12.5 % of the gain and 20 % of the gain on indexed cost — so a 2005 purchase sold for four times its cost pays 1,57,264.96 instead of 3,75,000, while a non-resident and a purchase on the cut-off day keep the flat rate because the qualifier's conditions, not a Java branch, decide (D-426)
Updated         : 2026-10-31, session 43 — W-089 closed out on the owner's approval; stopped before the commit
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
`c030e04f`), W-088g1's (`1f975262` against `f3027724`) and W-088g2's (`09ed07b7` against `927b2581`).
