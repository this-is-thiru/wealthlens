# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : BUILD NEXT: W-119b. Approved, not built, in build order: W-119b, W-119c, W-119d, W-122a, W-121, W-123a, W-123b — each in plans/<id>.md; W-117e placed (after W-117c)
Stage           : W-119a DONE (D-706), committed and pushed on the owner's "commit and push"
Done today      : session 99: W-117b (D-704), W-118 (D-705, pipeline 43675269149 success), W-119a (D-706)
Board           : W-117e ·; W-118 D; W-119a D, W-119b – d E, W-119b1 ·; W-120 ·; W-121 E; W-122a E, W-122b – c ·; W-123a – b E; W-324 ·; W-113a ·; Phase 10: W-299, W-294 ·
Next step       : check W-119a's pipeline; then BUILD W-119b from plans/W-119b.md, stop at its report. Plans were written cold at d44536db — grep every path and name first. Open owner question: ClaimCatalogueEntry has 8 components (CLAUDE.md >7)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : StatutoryValidityTest 13 + golden/section/pack/architecture set 545 green; ./mvnw validate green; full tiers on the pipeline
Files touched   : none (the W-119a commit holds everything)
Last item commit: taxplanning W-119a: prove every forbidden claim stays out of the total
Updated         : 2026-11-15, session 99 — W-119a committed and pushed
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
