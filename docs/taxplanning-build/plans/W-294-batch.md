## Plan for a batch of two run-log retirements — W-294 (runbook §2.13) and W-299 (runbook §2.14 and the segment correction)

> Planned in session 115 (2026-11-22), D-888's order (W-365 → this batch → W-173, W-254 → merge PR #71 → W-366). Named
> `W-294-batch` so `FinishedPlanTest` reads it against no single item (session 114 trap). One plan, one approval; each item its own
> D-row, YouTrack issue and commit. **Stop point: nothing is built until the owner answers C1 – C2.**

**What this batch is for (one sentence, in my own words):** both one-time corrections have now run on every database, so their runbook
steps are marked "do not run" and the segment correction's route and code are deleted, leaving nothing that should never run again one
call away.

### Gates (read in the run log at tag `docs-history-m1`)

| Item | Retires / removes | prod `Cluster0` | staging `it-staging` | YouTrack |
|---|---|---|---|---|
| W-294 | §2.13 (bonus transactions' copied charges and identifiers; no code) | W-334e2 C4 – C9: 10 found, backup 10, cleared 10 / 10, `copied` 0; step 5 per user in W-334e3 W9 – W10 (§2.12, §2.11) | W-334d3a C4 – C7: 6 found, backup 6, cleared 6 / 6, `copied` 0; step 5 in W-334d3b W9 – W10 | WL-45 |
| W-299 | §2.14, and `POST /api/v1/admin/portfolio/transactions/segment/correct` | W-334e2: §3.18 / §3.19 `[]`, dry run 0 / 0 — nothing to write | W-334d3a C8 – C9: `[]` / `[]`, dry run 0 / 0, mixed 0 — nothing to write | WL-46 |

Dev has held nothing since D-837. A fresh database (W-337) needs neither step, because each one corrects data that older code wrote.

### The code (measured, and a probe run)

- **W-299 deletes 4 files:** `portfolio/migration/SegmentCorrection.java` (184 lines), `portfolio/controller/SegmentCorrectionController.java`,
  `portfolio/dto/SegmentCorrectionReport.java` and `integration/SegmentCorrectionIntegrationTest.java`. It also deletes the api-collection request
  `Transactions/Correct Segments.yml`. `SegmentCorrection` uses only `mongoTemplate`'s `findAll`, `find` and `updateFirst`, which other code
  also calls, so no method is left unused.
- **Probe** (files deleted, then restored; `git status` clean afterwards): ArchitectureTest 26, WealthLensModulithTest 1, UnusedPrivateFieldTest 2
  and StoredStringIdIntegrationTest 15 all pass. **ApiCollectionContractIntegrationTest fails**, naming `Transactions/Correct Segments.yml: POST
  …/segment/correct — no such operation`. That is the evidence the route is gone, and it is why the collection request is deleted in the same commit.
- **No 404 case:** the owner removed the retired-route cases (`RetiredTestRoutesIntegrationTest`, D-884: "remove in next item"). Nothing is
  added back.

### Decisions (for approval with this plan)

- **C1 — how a step is retired.** **C1a (recommended):** as §2.1 – §2.10 were (D-874 – D-881): the heading becomes "**retired by W-xxx
  (D-…) · do not run**", a "Retired." banner names the gate rows, and the body stays below as the record. **C1b:** delete both sections.
- **C2 — YouTrack.** **C2a (recommended):** WL-45 and WL-46 are set Done at each item's commit (REST, the owner's token), as D-874 C2a did.
  **C2b:** the owner sets them.

### Files (if C1a, C2a)

**W-294 (docs only):**
- §2.13 heading and banner.
- The runbook's summary row "2.1 – 2.14" (`post-deployment-runbook.md:68`) gains 2.13.
- §3.17's "§2.13 corrects the transactions" notes that §2.13 is retired.
- "Next deploy" item 8, shared by both items: nothing to run.

**W-299:**
- Delete the 4 files and the collection request. The submodule commit is pushed, and its pointer is staged in the item commit.
- §2.14 heading and banner, and its mention at `:1587`.
- §3.18 and §3.19's "§2.14 (W-297) corrects it" notes that §2.14 is retired.
- Summary row `:68`.

**Numbers I will use:** only the run-log counts in the gate table.
**Design sections read:** the W-294 and W-299 rows (`03-WORK-ITEMS.md:1476`, `:1469`); runbook §2.13 and §2.14 (`:1658` – `:1737`); D-874 – D-884.

**What I am NOT doing:**
- Changing `StoredStringIdIntegrationTest`'s Javadoc list. It already names steps retired before (§2.1 – §2.5, §2.9), and the Phase 15 batch
  left it as it is.
- The historical W-334c4 rows that name §2.13 and §2.14.
- W-173 and W-254 (next).

**How I will verify:**
- Per item: `validate`, ArchitectureTest, WealthLensModulithTest and UnusedPrivateFieldTest.
- W-299 also: ApiCollectionContractIntegrationTest green once the request is gone, and RouteAccessIntegrationTest (the route list shrinks).
- The pipeline after the push.

**Post-deployment actions (R15):** nothing to run. "Next deploy" item 8: §2.13 and §2.14 are retired, and
`…/transactions/segment/correct` is no longer served.

**Five roles:**
- **Architect:** the one-time code leaves the build; no module edge changes.
- **PM:** no user-facing change. An admin route that must never run again goes.
- **Senior engineer:** dead weight removed, with no orphaned methods (checked).
- **Automation tester:** the probe shows the contract check catches a stale request. A deletion has no red-first test: the owner removed the 404
  cases (D-884).
- **Manual tester:** the api-collection no longer lists "Correct Segments". The spec at `/api/v1/admin/docs/openapi` has no `segment/correct`.

**Risks or ambiguities:** none found. Both gates read as complete for both databases.

### Steps

- [x] 1. Approval (owner: "batch approved C1a C2a", 2026-11-22)
- [x] 2. W-294 build + commit (D-890)
- [x] 3. W-299 build + commit (D-891)
