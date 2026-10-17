# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-078j7b3 — r.280's two versions settled on the owner's ruling (D-216)
Stage           : EXPLAIN written — STOPPED for approval (W-078j7b2 committed)
Plan            : plans/W-078j7b3.md (both TY 2026-27 versions SOURCE_CHECKED; relock BUMP=1 → 2026-27.law.70; two AllowanceExemptionTest cases flip; UNVERIFIED 2 → 0)
Approved        : D-216 (the ruling); the plan awaits "W-078j7b3 approved — proceed as recommended"
Steps done      : —
Next step       : WAIT for "W-078j7b3 approved — proceed as recommended"
Last verify     : unit -Punit → 2836 run, 0 fail, 0 Checkstyle (W-078j7b2, 2026-10-17); UNVERIFIED 2
Waiting on      : the owner — W-078j7b3 approval. Open OQs: OQ-25, OQ-26, OQ-27
Files touched   : plans/W-078j7b3.md (untracked)
Last item commit: taxplanning W-078j7b2: source-check eight TY 2025-26 introducedBy from the FA 1995 thc.nic.in copy and the annual Acts of the Parliament volumes — UNVERIFIED 10 → 2 (D-214, D-215)
Updated         : 2026-10-17, session 20 (W-078j7b2 committed)
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
