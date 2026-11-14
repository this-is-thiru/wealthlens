# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-117b (claim catalogue, employer-optional entries) — APPROVED C1a C2a C3a C4a + draft content, board E, NOT built (plans/W-117b.md); planning ahead: W-118 plan in progress
Stage           : APPROVED, awaiting a build session (owner: plans from Fable, building by Opus); W-117a follow-up committed and pushed, pipeline pending
Done today      : session 97: W-326b follow-up committed 384a893b, pipeline 43619226337 success (D-697); W-117a built (D-699)
Board           : W-117 D (split); W-117a D, W-117b E, W-117c – d ·; W-118 – W-123 ·; W-324 ·; W-113a ·; Phase 10: W-299, W-294 ·
Next step       : (1) record the W-117a follow-up's pipeline in D-699. (2) BUILD W-117b from plans/W-117b.md (read ss.123 – 126, Sch. XV, r.278 in full first; coverage step in the plan's header). (3) planning ahead: finish plans/W-118.md — probe so far: no Form 16 data exists; measure a claim's tax effect by a second run withholding its rules at NodeContext.inRegime (20 of 76 node classes ask it)
Owner actions   : after deploy: §3.24 then redrive per listed user (W-316); §3.22 then §2.12 per listed user (W-313); §3.23 (W-312); §3.21 (W-304); §3.20 (W-303); §3.18, §3.19, then §2.14 (W-297, once; backup first) and its per-user §2.12 → §2.11; §3.17 → §2.13 → per user §2.12 → §2.2 → §2.11; §3.16; Q-1 (PS-8), Q-2 (AMC periods); rotate the Mongo password and JWT key pasted in session 82
Last verify     : W-117a follow-up: ClaimCatalogueTest 24/0, validate green, 1 red check; W-117a pipeline 43621547774 FAILURE (coverage gate 0.84, D-699)
Files touched   : none uncommitted but this file
Last item commit: taxplanning W-117a: test the claim catalogue's parts refusing to be built without what they are for
Updated         : 2026-11-14, session 97 — W-117a follow-up committed; W-117b approved, not built
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
