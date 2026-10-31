# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g — the cap on the tax on land or building acquired before 23-7-2024. Done
                  (D-426), committed 5484a3e9
Stage           : 6 of 6 (REVIEWED) — nothing in flight
Plan            : plans/W-088g.md — built as approved (C1a … C7a); its Build record carries the five
                  departures and its Decisions (D-426) the readings OQ-36 and OQ-37 now settle to
Approved        : "W-088g approved C1a C2a C3a C4a C5a C6a C7a" (2026-10-31), then "commit it"
Steps done      : all nine steps of the second-pass list, the close-out and the commit
Next step       : the owner's choice of the next item. The next board item is W-088h (a non-resident's
                  unlisted securities — unreachable until the asset is mapped); W-088i (the
                  house-reinvestment exemption, S2 s.86 / 1961 s.54F) is the one the board calls
                  reachable now
Last verify     : ./mvnw test -pl backend -Punit → 3848 / 0 / 0, Checkstyle and Spotless green in
                  validate; run before the commit, whose pre-commit gate passed on the same tree
Waiting on      : the owner's choice of the next item — nothing else is outstanding
Files touched   : this file and STATE.md — nothing under backend/; the item commit 5484a3e9 carries the 29
                  paths (engine, pack, locks, tests, the four documents)
Last item commit: taxplanning W-088g: cap the tax on land or building acquired before 23-7-2024 at the lesser of 12.5 % of the gain and 20 % of the gain on indexed cost — so a 2005 purchase sold for four times its cost pays 1,57,264.96 instead of 3,75,000, while a non-resident and a purchase on the cut-off day keep the flat rate because the qualifier's conditions, not a Java branch, decide (D-426)
Updated         : 2026-10-31, session 43 — W-088g committed 5484a3e9, this the post-commit state line
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
