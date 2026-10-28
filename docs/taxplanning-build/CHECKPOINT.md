# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-166 — dead weight (D-315; audit T6; N5) — NOT STARTED
Stage           : — (W-165 DONE, D-388, committed)
Plan            : — (write plans/W-166.md on "start W-166")
Approved        : —
Steps done      : —
Next step       : on "start W-166": READ the WI row + each named site, write plans/W-166.md, STOP for approval
Last verify     : W-165 — coverage/catalogue/backlog 42/0; unit 3788/0; integration 310/0
Waiting on      : the owner — "start W-166"; place: 33 register rows (W-165 C3a scan); V1/V2 accept GOLD_BOND without maturity (W-164 C3a); open: CLAUDE.md "backfill" 503 wording; catalogue SEBI_FEE reference; api-collection term requests; per-bucket term audit record, keep-old-labels option; nil-line proposal, PaidOtherThanInCash, REG basis-set finding; OQ-32, OQ-33, OQ-34
Files touched   : (none)
Last item commit: taxplanning W-165: list the capital-gains set-off under 2025 s.108 / 1961 s.70, the section it cites, not the charging section — the catalogue route moves with it; house-property rows kept for limb (1) (D-388)
Updated         : 2026-10-28, session 38 — W-164 DONE; W-180 closed; W-165 DONE
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
