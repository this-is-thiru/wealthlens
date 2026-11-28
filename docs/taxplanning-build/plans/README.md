# Plans

One file per item, `W-0nn.md`, written at stage 2 (EXPLAIN) using the template in
`00-START-HERE.md` §2, so an approved plan survives the session that wrote it.

Two additions to that template, at the end of the file:

```markdown
## Approval
<owner's exact words> — <date>          ← filled in at approval; blank means not approved

## Steps
- [ ] 1. <one file or one change>        ← ticked as each is finished, with CHECKPOINT.md updated
- [ ] 2. …
- [ ] n. run Verify + the unit tier
```

Each step is small enough to finish in one go: one file, or one tightly-coupled change. A session
that stops between steps resumes at the first unticked one.

The plan file is committed **with its item**, as part of the item's single commit.

Only plans of open items stay here, with `W-078f.md` and `W-112b.md`, which the rule packs cite. Every finished plan is in
git tag `docs-history-m3` (W-432, D-1045): `git show docs-history-m3:docs/taxplanning-build/plans/<id>.md`.
