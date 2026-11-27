# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-410 DONE (D-986), committed; W-411 built, its records being written for its own commit.
Stage           : between W-410's and W-411's commits.
Last item commit: "taxplanning W-410: refuse a read of a workspace not started instead of creating it — a GET must not write; D-986"
Next step       : 1. On "commit it": W-410 board D + plan step 3; commit ONLY W-410's files (DraftService, DraftIntegrationTest,
  plans/W-410.md, DECISIONS, STATE, WORK-ITEMS, CHECKPOINT). 2. Then write W-411's D-987, W-412 row (C2a), board D, and commit
  W-411's files (ChargeScheduleRepository, ChargeScheduleService, ChargeScheduleServiceTest, ChargeRepositoryIntegrationTest,
  plans/W-411.md + docs). 3. Then plan W-412, then W-337i. Owner: every account's email for §0.10?
Files touched   : W-410: DraftService.java, DraftIntegrationTest.java, plans/W-410.md, DECISIONS.md, STATE.md, 03-WORK-ITEMS.md;
  W-411: ChargeScheduleRepository.java, ChargeScheduleService.java, ChargeScheduleServiceTest.java, ChargeRepositoryIntegrationTest.java, plans/W-411.md
Flags for owner : staging/dev readback unpasted (W-337h); §1.s Aiven retention not given. W-337f flags unchanged.
Env notes       : NO Claude attribution; OrbStack started this session; DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on
  pipeline only. Trial: m1.6.1 jar built in ../wealthlens-m1.6.1/backend/target, w-337 jar in backend/target.
Updated         : 2026-11-27 (session 124)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
