# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2a2 DONE — D-972 (charge_accounts, user_charges, corporate_action). Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2a2: copy charge_accounts, user_charges and corporate_action — ordered lines, the amounts map and the embedded demerger detail; D-972" — matches `git log --grep`.
Next step       : check the pipeline (1ed914e0's run, then this commit's); then plan W-337g2a3 (row in 03-WORK-ITEMS.md:
  tax_regime_election_history, tax_document_confirmations, tax_gazette_leads); EXPLAIN template, board E, stop for approval.
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
