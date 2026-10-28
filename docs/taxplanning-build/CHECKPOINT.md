# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-185 — IPFT counted twice on the 2026-03 cash cards (D-393; W-178 C3a) — NOT STARTED
Stage           : — (session 38 PAUSED at the owner's session-limit notice; all committed and pushed)
Plan            : — (write plans/W-185.md on "start W-185")
Approved        : —
Steps done      : —
Next step       : on "start W-185": READ the WI row + the seven 2026-03 cash cards + how the seeder treats a changed card (skip by code, drift) + golden cases dated ≥ 2026-03-01, write plans/W-185.md, STOP for approval
Last verify     : W-178 — charge tests 168/0; unit 3811/0; integration 312/0
Waiting on      : the owner — "start W-185"; open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-178: add equity futures and options cards for all three brokers and the law — the new STT from 30-3-2026, Finance Act 2026's assent; W-185 and W-186 placed (D-393)
Updated         : 2026-10-28, session 38 — PAUSED (owner shutting down); session note in STATE "Notes for resume"; branch and api-collection pushed, origin = local. Done: W-164 – W-167, W-177, W-178; W-180 closed; W-181 – W-186 placed; order W-185 → W-186 → W-179 → W-181 → … → W-184
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
