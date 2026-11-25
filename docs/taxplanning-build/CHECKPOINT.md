# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g1 DONE — D-955 (close-out), D-953/D-954 (choices); board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-337g1: copy MongoDB's flat collections and document bytes into PostgreSQL, times in UTC — D-953, D-954, D-955" — matches `git log --grep`.
Next step       : plan W-401 (corporate-action JSON audit emails encrypted), then W-402 (store every time in UTC, before W-337i), then
  W-337g2. Check the pipeline run of this push first. Owner question open: the zone the JVM that wrote dev/staging/prod ran in.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only.
Updated         : 2026-11-25 (session 122, W-337g1 committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
