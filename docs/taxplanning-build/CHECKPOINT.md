# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-408 DONE — D-976 (StoredAssetRequest mix-in). Committed and pushed with the D-975 placement records.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-408: keep an uploaded trade's execution time in the stored request — a redrive recorded no order time; D-976" — matches `git log --grep`.
Next step       : check this commit's pipeline; then BUILD W-337g2b2 (approved C1a – C3a, D-975; plans/W-337g2b2.md): the walk's
  snake_case + _id matching (check m1.6.1's nested-id key with MappingMongoConverter first), StoredAssetRequest mix-in in the walk's
  mapper, EmbeddedTimes local-held case, CollectionCopy.localHeldKeys; board · → P.
  Note for W-337g2c: the runbook copies before seeding the catalogue (the copier refuses a seeded target).
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock. Next Flyway version: V14.
Updated         : 2026-11-26 (session 123)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
