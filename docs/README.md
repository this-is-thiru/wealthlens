# WealthLens docs — index

Start here. Every kept doc is listed with what it is for. Anything removed is in a git tag: **`docs-history-m1`** (W-357a), **`docs-history-m2`** (the old deploy runbook, W-430), **`docs-history-m3`** (finished plans, reviews and work-item rows, W-432), **`docs-history-m4`** (the old README, `taxplanning-context/`, the design `PROGRESS.md`, W-434) or **`docs-history-m5`** (the status board, work items, parked register and runbook extras, W-435):
`git show docs-history-m1:<path>` prints a removed file, `git ls-tree -r --name-only docs-history-m1 docs/` lists them.

## Working on the build (`docs/taxplanning-build/`)

| Doc | What it is |
|---|---|
| [`00-START-HERE.md`](taxplanning-build/00-START-HERE.md) | the process: how an item is planned, built, verified and closed |
| [`CHECKPOINT.md`](taxplanning-build/CHECKPOINT.md) | where the work stands now — read first when resuming |
| [`DECISIONS.md`](taxplanning-build/DECISIONS.md) | every decision (D-001 …) and blocker (B-001 – B-004); the code cites these rows |
| [`BACKLOG.md`](taxplanning-build/BACKLOG.md) | every open item with its status, phase and, if parked, its gate; an item not in it is finished (W-435). The old board, work items and parked register are in tag `docs-history-m5` |
| [`plans/`](taxplanning-build/plans/) | plans of items not yet finished (and two the rule packs cite) |
| [`01-PRD.md`](taxplanning-build/01-PRD.md), [`02-TECH-SPEC.md`](taxplanning-build/02-TECH-SPEC.md) | product requirements and the tax-planning technical spec |
| [`04-PROMPTS.md`](taxplanning-build/04-PROMPTS.md) | the session prompts |
| [`post-deployment-runbook.md`](taxplanning-build/post-deployment-runbook.md) | the deploy runbook for every module (also YouTrack WL-A-2); its run log starts fresh after W-334 |
| [`sources/`](taxplanning-build/sources/) | the Gazette and official sources every legal figure is taken from (cited by the rule packs) |
| [`reviews/`](taxplanning-build/reviews/) | the pack review `PackReviewTest` checks, the law-in-code audit CLAUDE.md §4 refers to, and the W-092 cell table the ITR forms cite; the rest in tag `docs-history-m3` |
| [`tools/`](taxplanning-build/tools/) | scripts: rule-pack relock, Act parsers, the WL-A-2 publisher |

## Design

| Doc | What it is |
|---|---|
| [`taxplanning-design/`](taxplanning-design/) | the tax design — the cited source for figures and structure (rule R1); `00-BRIEF.md` first. Written before the build: where it differs from the code, the code and `docs/modules/` are current (W-434) |

## Modules (`docs/modules/`)

One current doc per module, written from the code and citing it (W-357b, D-901; the template is `plans/W-357b.md` (tag `docs-history-m3`) C2a). Written so far:

| Module | Doc |
|---|---|
| `finance` — loan and SIP calculators | [`modules/finance.md`](modules/finance.md) |
| `insurance` — stored policies, no routes | [`modules/insurance.md`](modules/insurance.md) |
| `helper` — the upload template and file answers | [`modules/helper.md`](modules/helper.md) |
| `auth` — accounts, sign-in, tokens | [`modules/auth.md`](modules/auth.md) |
| `corporate` — corporate actions, recorded then applied per user and broker | [`modules/corporate.md`](modules/corporate.md) |
| `brokercharges` — rate cards, the charge engine, what each trade cost | [`modules/brokercharges.md`](modules/brokercharges.md) |
| `portfolio` — trades, holdings, outcomes and reports | [`modules/portfolio.md`](modules/portfolio.md) |
| `shared` — the privacy duties, and what every module stands on | [`modules/shared.md`](modules/shared.md) |
| `taxengine` — income tax as data, and the engine | [`modules/taxengine.md`](modules/taxengine.md) |
| `taxplanning` — profiles, drafts, computations, documents and tax operations (boundary, profiles, workspaces, drafts, salary import, assembly, portfolio bridge, computation, advisory views, report, documents and isolation, protection, erasure, jobs and the Gazette watch) | [`modules/taxplanning.md`](modules/taxplanning.md) |

Every module has its doc (W-357b complete).

`charges-engine/` and `trade-ledger/`, the charge engine's and the trade ledger's design notes, were deleted by W-357b6b (D-909) and
W-357b7c (D-913): what the code shows is in `modules/brokercharges.md` and `modules/portfolio.md`, each of which also says what the
design names the code cites mean (§10). Plans, reviews and D-rows that cite either folder read it at tag `docs-history-m1`:
`git show docs-history-m1:docs/charges-engine/<file>` (or `…/trade-ledger/<file>`).
