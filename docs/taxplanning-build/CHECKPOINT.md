# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088i5 — one house claim measured once over every bucket of its sale
Stage           : 5 of 6 (AWAITING_REVIEW) — built and verified; STOP for the owner's review
Plan            : plans/W-088i5.md — approved C1a C2a; Build record holds two wrong predictions
Sourcing        : sources/2026-10-31-reinvestment-exemption.md rows 1-2 (no new source)
Decisions       : D-430 — the claim's buckets measured together, losing buckets included, the exempt amount
                  shared by gain with the last taking the remainder
Board           : `W-088g2 D  W-088i5 R  W-088g3 ·`
Built           : ReinvestmentExemption.chargeableGainsOf + Measure; CapitalGainsNode.takenBuckets; +7 cases
Next step       : the owner's review; on "commit it", commit the named paths below
Last verify     : class 24/0/0; unit tier 3873 / 0 / 0; integration tier 323 / 0 / 0; Checkstyle 0
Files touched   : ReinvestmentExemption.java, CapitalGainsNode.java, HouseReinvestment.java,
                  ReinvestmentExemptionTest.java, plans/W-088i5.md (new), 03-WORK-ITEMS.md, STATE.md, this file
Last commit     : ecc14100 — docs(taxplanning-build): place session 43's review as thirteen backlog rows (D-429)
Updated         : 2026-10-31, session 44 — W-088i5 built, awaiting review
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
W-089's (`f7140cfe` against the post-commit line `1b62fe5a`).
