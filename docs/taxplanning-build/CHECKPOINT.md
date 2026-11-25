# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337f (port taxplanning to JPA/Postgres) DONE — D-951 (choices), D-952 (close-out); board D; committed and pushed.
Stage           : 6 — DONE. Nothing in flight.
Last item commit: "taxplanning W-337f: port taxplanning module to JPA with V10 DDL — D-951, D-952" — matches `git log --grep`.
Next step       : W-337g (the copier) — write its plan (§2 template, probe before plan); check the pipeline run of the push first.
Flags for owner : inert @Field on WorkbookImport/DraftStaleness (W-337j); AuditMetadata/AuditableEntity/Mongo CreatedWithIdSaved possibly
  dead; GazetteCursor never fills created*; native lease acquire leaves audit columns null; portfolio findByEmailAndTransactionDateBetween
  inclusive-Between question (W-337e) still open.
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only.
Updated         : 2026-11-25 (session 121, W-337f committed and pushed)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
