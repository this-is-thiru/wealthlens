# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-088g — the cap on the tax on land or building acquired before 23-7-2024
Stage           : 5 of 6 (REPORTED) — built and verified, awaiting the owner's review
Plan            : plans/W-088g.md — the second-pass section, its Build record and Decisions (D-426)
Approved        : "W-088g approved C1a C2a C3a C4a C5a C6a C7a" (2026-10-31)
Steps done      : all nine steps of the second-pass list — the CII rule, IndexedCost, the qualifying
                  row, the LeastOf cap, the measure and its carriage, TaxByBasisNode's two bases, the
                  seven tests, both relocks, the close-out (WI row, board D, D-426, STATE 7–8)
Next step       : on the owner's review — the commit, then the post-commit state line. The four
                  deliberate-violation cycles left the tree as built; nothing else is outstanding
Last verify     : ./mvnw test -pl backend -Punit → 3848 / 0 / 0 (3841 before, plus the 7 new tests),
                  Checkstyle and Spotless green in validate
Waiting on      : the owner's review of the built item — no commit is made without their word
Files touched   : backend/src/main/java/.../taxengine/rules/{definition/RuleDefinition.java,
                  definition/IndexedCost.java, json/DefinitionJson.java, generated/RuleIds.java,
                  generated/RuleSetIds.java}, node/{CapitalGainsNode, CapitalGainsIndexation,
                  CapitalGainsAbsorption, SetOffNode, TaxByBasisNode}.java,
                  pipeline/{CapitalGainsHead, HeadsAfterSetOff}.java; rulepacks/timeline/law/rules/
                  {RATE-CG-LONG-OTHER, RATE-CG-COST-INFLATION-INDEX, CG-INDEXED-COST-LAND-BUILDING}.json,
                  law/rule-sets.json, content/coverage.json, both locks; the three count tests
                  (PackJsonTest, RulePackLoadTest, TaxYear2025RulePackTest) and the two id lists
                  (TimelineReaderTest, GeneratedConstantsTest), LandOrBuildingCapTest; STATE,
                  CHECKPOINT, the WI row, plans/W-088g.md
Last item commit: taxplanning W-088g2: add the land-or-building asset kind with its twenty-four-month term and its part rows — so a land sale is charged at 12.5 % instead of refused, because the bucket meets the term's refusal before the part's and no STT condition can ever hold for it (D-425)
Updated         : 2026-10-31, session 43 — W-088g built, verified and closed out; nothing committed
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
`c030e04f`), W-088g1's (`1f975262` against `f3027724`) and W-088g's second pass (`09ed07b7` against
`927b2581`, where the grep's answer is W-088g2's own state line).
