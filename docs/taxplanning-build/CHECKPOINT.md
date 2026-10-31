# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : none in flight — W-088i is closed (D-428), committed e5233041
Stage           : 6 of 6 (DONE) — reviewed on "review approved, close it out"
Plan            : plans/W-088i.md — approved as written, built, and its Build record holds the three departures
Sourcing        : sources/2026-10-31-reinvestment-exemption.md — 2025 s.86 (Gazette 265620 pp.130–131),
                  1961 s.54F (thc pp.292–293), FA 2023 s.30 the only post-2019 amendment
Decisions       : OQ-38 answered — net consideration less the TRANSFER_EXPENDITURE charges only, STT out;
                  one entered fact per transfer matched by date and refused when not unique; the same
                  exempt amount leaves the gain and W-088g's indexed measure
Board           : `W-088i D  W-088i2 ·  W-088i3 ·  W-088i4 ·  W-089 D`
Built           : the exemption end to end (see D-428); unit tier 3866 / 0 / 0; WL-A-2 not republished —
                  the runbook did not change
Next step       : W-088i5 (D-429: session 43's review placed thirteen rows, W-088i5 first — a done item
                  computes a wrong figure), then the owner's choice. Unblocked behind W-088i: W-088i2 (the deposit
                  route), W-088i3 (the charge-backs), W-088i4 (a land or building as the original asset).
                  Not startable: W-088h (needs unlisted securities mapped). Proposed earlier: W-089e
Last verify     : unit tier 3866 / 0 / 0 (BUILD SUCCESS) at the close-out
Files touched   : this file — the post-commit state line, uncommitted
Last commit     : c3dfbf17 — taxplanning W-088i: close out the item (item build e5233041; state line ab0ddef9)
Updated         : 2026-10-31, session 43 — W-088i closed (D-428)
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
