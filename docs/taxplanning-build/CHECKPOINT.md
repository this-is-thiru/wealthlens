# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088i — the house-reinvestment exemption (S2 s.86 / 1961 s.54F, D-248)
Stage           : 5 of 6 (REPORTED) — built, verified and committed e5233041; awaiting the review
Plan            : plans/W-088i.md — approved as written: "W-088i approved, place W-088i2 and W-088i3"
Sourcing        : sources/2026-10-31-reinvestment-exemption.md — 2025 s.86 (Gazette 265620 pp.130–131),
                  1961 s.54F (thc pp.292–293), FA 2023 s.30 the only post-2019 amendment, nine other
                  Acts of 2019–2026 searched with no hit
Approved        : yes — the plan as written; OQ-38's TRANSFER_EXPENDITURE-only reading of net
                  consideration, Risk 3's entered-fact-per-transfer shape and Risk 4's same exempt
                  amount off both measures are the decisions to record at close-out
Board           : `W-088i R  W-088i2 ·  W-088i3 ·  W-088i4 ·`
Built           : HouseReinvestment, ExemptReinvestment, ReinvestmentExemption, the row
                  CG-EXEMPT-RESIDENTIAL-HOUSE (both versions), coverage MODELLED, SEC-CAPITAL-GAINS-
                  REINVESTED-HOUSE, the 40th EnteredFacts component, RuleDefinition's permit
Departed        : recorded in the plan's Build record — the golden is ₹4,51,750 not ₹5,03,750 (the
                  nil-band shift), the cap was ₹100 crore and is ₹10 crore, and the row serves EQUITY
                  only because AssetType.LAND_OR_BUILDING cannot say a house from a plot (W-088i4)
Next step       : the owner's review; then close-out — D-428, the WI clause, board `D`, and the
                  decision row for OQ-38's reading, Risk 3 and Risk 4
Last verify     : unit tier 3866 / 0 / 0 (BUILD SUCCESS); ReinvestmentExemptionTest 17 / 0 / 0
Files touched   : nothing uncommitted — e5233041 holds the 64 files; STATE.md and this file alone moved since
Last item commit: e5233041 — taxplanning W-088i: take a long-term gain put into a residential house out of the charge (2025 s.86; 1961 s.54F)
Updated         : 2026-10-31, session 43 — W-088i committed e5233041; awaiting the review
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
