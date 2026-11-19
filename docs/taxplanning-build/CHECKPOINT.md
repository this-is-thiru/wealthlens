# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-334c3 DONE (D-792); dev holds prod's copy, encrypted and seeded (dev-after-c3.txt); next W-334c4 (§2.1 – §2.14) — to plan
Stage           : W-334c4 · — plan it: read runbook §2.1 – §2.14 fully; per step a backup before each write; m1.1; tools with lookup + retries; possibly split
Done session 108: plans/W-334a.md, plans/W-334b.md written; W-334 plan step 3 ticked; STATE lines 7, 8, 65 updated
Board           : W-334a E, W-334b E, W-334c – f ·; W-335 B; every other open item waits (W-299, W-294, W-113a, W-324, W-131i, W-126d, W-146a)
Next step       : plan W-334c4 — runbook §2.1 – §2.14 on dev's copy with m1.1 (read each section fully; prod-only 2.1, 2.3 included; a backup before each write; full copy-paste blocks; likely split into 2 – 3 items); stop for approval. Nothing is running; prod and staging untouched
Open to owner   : approve W-334c4's plan; W-340 and W-339 before W-334d; W-337 (MongoDB → MySQL/PostgreSQL) engine and place vs M2; runner token in git history (repo PUBLIC)
Owner actions   : W-334a rows 1 – 6, then W-334b rows 1 – 9
Env notes       : database tools: GODEBUG=netdns=cgo AND a dscacheutil lookup + up to 3 tries (router DNS still times out); passwords via read -s; never cd inside a $ command
Last verify     : W-338: 5 affected ITs 37/0, StoredStringId 6/0 (red 6/6 with a bare collection name), Architecture+Modulith green, validate green
Last item commit: taxplanning W-334c3: log §1A and §1B on prod's copy with m1.1 — the charge engine seeds 39 cards and prices a known trade at 119.67
Files touched   : none
Updated         : 2026-11-19, session 108
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
W-089's (`f7140cfe` against the post-commit line `1b62fe5a`); W-131d2's (`d7a5b146` pointer against `86035a1c`).
