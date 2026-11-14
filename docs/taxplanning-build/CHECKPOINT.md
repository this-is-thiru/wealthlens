# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-327b2 done (D-682), board D; W-327b complete — next by board order W-327c (Chapter VIII and other sources, not yet planned)
Stage           : DONE (session 95, 2026-11-14) — committed and pushed
Done today      : session 95: W-325d (D-676), W-327 split (D-677), W-327a (D-678), W-327a1 (D-679), W-327b split (D-680), W-327b1 (D-681), W-327b2 (D-682)
Board           : W-115, W-116, W-325, W-325a – d, W-327, W-327a, W-327a1, W-327b, W-327b1, W-327b2 D; W-327c – e ·, W-326 ·; W-324 · (after AIS / 26AS); W-113a · (after F12); Phase 10: W-299, W-294 · (wait run logs)
Next step       : confirm the W-327b2 pipeline run and record it in D-682; then plan W-327c (chapterviii + othersources sites, incl. "not met" at DisabilityDeduction:97)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-327b2 local: unit (taxengine, taxplanning, architecture) 4504/0, DraftComputationIntegrationTest + Modulith 61/0, validate green
Files touched   : none beyond this file after the W-327b2 commit
Last item commit: taxplanning W-327b2: publish why for perquisites as facts and word it in content
Updated         : 2026-11-14, session 95 — W-327b2 committed and pushed
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
