# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-337g2a1 DONE — D-971 (child tables for refresh_tokens, insurances, privacy_breach_notices). Committed and pushed.
Stage           : between items. Nothing in flight.
Last item commit: "taxplanning W-337g2a1: copy refresh_tokens, insurances and breach notices with their child tables — parent and children in one transaction; D-971" — matches `git log --grep`.
Next step       : plan W-337g2a2 (row in 03-WORK-ITEMS.md: charge_accounts, user_charges, corporate_action — child tables, ordered
  lines, the amounts map, demerger columns); EXPLAIN template, board E, stop for approval. Check the pipeline first.
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
