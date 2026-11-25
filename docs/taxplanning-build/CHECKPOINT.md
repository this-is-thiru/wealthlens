# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2 choices approved — D-970 (C2a typed JSON walk, C3a parent+children per transaction, C4a time table,
  C5a runbook entry in g2c); board W-337g2 —, sub-items g2a1 – g2c ·. Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2: record the copier's embedded-data choices — owner approved C2a – C5a; D-970" — matches `git log --grep`.
Next step       : build W-337g2a1 (plans/W-337g2.md, row W-337g2a1): child tables for refresh_tokens (refresh_token_factors),
  insurances (insurance_policy_details, FK insurance_id_fk, encrypted fields carried as ciphertext), privacy_breach_notices
  (privacy_breach_notice_affected, searchable entries); CollectionCopy gains child mappings; MongoCopier writes parent + children
  in one transaction, children only when the parent was written (C3a); child counts checked; MongoCopierIntegrationTest cases
  incl. a part-way failure between parent and children; CollectionCopiesTest's unspecified set shrinks. Check the pipeline first.
Flags for owner : W-337f flags unchanged (inert @Field, GazetteCursor created*, lease audit nulls, findByEmailAndTransactionDateBetween).
Env notes       : NO Claude attribution; integration needs OrbStack + DOCKER_HOST=unix://$HOME/.orbstack/run/docker.sock; full tiers on the
  pipeline only. Tests run in UTC (argLine); time tests pin a fixed Asia/Kolkata clock. Next Flyway version: V14.
Updated         : 2026-11-25 (session 122 end)
```
## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch -> stop and report both outputs. Match -> do **Next step** and nothing else.
