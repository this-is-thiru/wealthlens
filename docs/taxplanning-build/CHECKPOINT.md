# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-167 — TJsonMapper without a silent null (D-315; audit T7; N7) — NOT STARTED
Stage           : — (W-166 DONE, D-389, committed)
Plan            : — (write plans/W-167.md on "start W-167")
Approved        : —
Steps done      : —
Next step       : on "start W-167": READ the WI row + TJsonMapper and its callers, write plans/W-167.md, STOP for approval
Last verify     : W-166 — CorporateAction+TemporaryTransaction IT 58/0; unit 3788/0; integration 312/0
Waiting on      : the owner — "start W-167"; push (parent + api-collection) if wanted; place: CorporateActionService:489 commented code, Checkstyle unused-field rule; 33 register rows (W-165 C3a scan); V1/V2 accept GOLD_BOND without maturity (W-164 C3a); open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-166: remove the corporate-action date nothing kept and the unused March constants — a request still carrying date is accepted; the collection stops sending it (D-389)
Updated         : 2026-10-28, session 38 — W-164 DONE; W-180 closed; W-165 DONE; W-166 DONE
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
