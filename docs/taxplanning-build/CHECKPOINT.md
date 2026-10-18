# CHECKPOINT — where to pick up

**Overwrite this file, never append.** Keep it under ~25 lines. Update it **before** telling the
owner anything, so a session cut off mid-message loses nothing. Rules: `00-START-HERE.md` §5b.

```
Item            : W-082a — the election core and the set-off order (W-082 split, D-220)
Stage           : DONE — "W-082a approved" (2026-10-18); committing
Plan            : plans/W-082.md (C0a split; OQ-28 may choose, OQ-29 default kept — D-221)
Approved        : "W-082 approved — proceed as recommended" (2026-10-18)
Steps done      : 1–8
Next step       : WAIT for the owner's review. On "W-082a approved": commit the named paths below (one commit, subject "taxplanning W-082a: …"), STATE Last checkpoint first. Next in order then: W-082b
Last verify     : unit -Punit → 2876 run, 0 fail, 0 Checkstyle (W-082a, 2026-10-18)
Waiting on      : —
Files touched   : backend main: taxengine/election/ (6 new), input/EnteredFacts, node/SetOffNode, rules/definition/AbsorptionOrder; backend test: election/ElectionResolverTest, golden/{SetOffOrderElectionPipelineTest (new), GoldenCaseRunner, SalaryHeadPipelineTest}, input/{InputsFingerprintTest, RegimeElectionSnapshotTest}, node/*Test (31 files, EnteredFacts arg); docs: 02-TECH-SPEC, 03-WORK-ITEMS, STATE, plans/W-082.md, sources/2026-10-18-set-off-order.md
Last item commit: taxplanning W-081c: close out the set-off chapter's coverage — S2 ss.108–110 / 1961 ss.70, 71, 71B modelled with catalogue entries, capital gains to W-088, the rest deferred, s.71A spent (D-219)
Updated         : 2026-10-18, session 21 — W-082a awaiting review
```

## Resume check (run these, compare, then act on "Next step")

```
git log --oneline --grep='^taxplanning W-' -1   # must equal "Last item commit"
git status --short                              # must list exactly "Files touched" (plus this file)
```

Mismatch → stop and report both outputs. Match → do **Next step** and nothing else.
