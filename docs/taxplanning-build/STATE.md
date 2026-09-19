# STATE — the only mutable file

**Read this first, write it last, every session.**

```
Last checkpoint           : W-001 — "taxplanning W-001: create the four module skeletons"
Last updated              : 2026-09-19 (session 1 — W-001 DONE)
Current position          : W-001
Session count             : 1
```

**"Last checkpoint"** records the **item id and the commit subject** of the last accepted item — not
a hash. A commit cannot contain its own hash, so storing one here is self-referential and was found
to be unworkable in session 1.

Prompt **P1** compares this line against the most recent **item** commit:

```
git log --oneline --grep='^taxplanning W-' -1
```

- the subject **matches** → the tree is where the protocol left it. Proceed.
- the subject **differs** → an item was committed outside the protocol. Stop, report
  `git log --oneline -10`, and wait.

Commits that do **not** start `taxplanning W-` — documentation, tooling, unrelated repository work —
are ignored by the check. Only item commits move the checkpoint.

This survives amends, rebases and cherry-picks, which a hash does not.

---

## Current position

| | |
|---|---|
| **Next item** | `W-002` — Amend the money ArchUnit rule |
| **Status** | `NOT_STARTED` |
| **Phase** | 0 — Make the build accept the design |
| **Next milestone** | `W-053` — a correct slab-and-rebate calculator, no database |

---

## Status board

Legend: `·` not started · `E` explained, awaiting approval · `P` in progress ·
`R` awaiting review · `D` done · `B` blocked

```
Phase 0    W-001 D  W-002 ·  W-003 ·
Phase 1    W-004 ·  W-005 ·  W-006 ·  W-007 ·  W-008 ·  W-009 ·  W-010 ·  W-011 ·  W-012 ·
Phase 2    W-013 ·  W-014 ·  W-015 ·  W-016 ·  W-017 ·  W-018 ·  W-019 ·  W-020 ·  W-021 ·  W-022 ·
Phase 3    W-023 ·  W-024 ·  W-025 ·  W-026 ·  W-027 ·  W-028 ·  W-029 ·  W-030 ·  W-031 ·  W-032 ·  W-033 ·  W-034 ·
Phase 4    W-035 ·  W-036 ·  W-037 ·  W-038 ·  W-039 ·  W-040 ·  W-041 ·  W-042 ·  W-043 ·  W-044 ·  W-045 ·
Phase 5    W-046 ·  W-047 ·  W-048 ·  W-049 ·  W-050 ·  W-051 ·  W-052 ·  W-053 ·
Phase 6    W-054 ·  W-055 ·  W-056 ·  W-057 ·  W-058 ·
Phase 7    W-059 ·  W-060 ·  W-061 ·  W-062 ·  W-063 ·  W-064 ·  W-065 ·  W-066 ·  W-067 ·  W-068 ·  W-069 ·  W-070 ·
Phase 8    W-071 ·  W-072 ·  W-073 ·  W-074 ·  W-075 ·  W-076 ·  W-077 ·  W-078 ·  W-079 ·  W-080 ·
Phase 9    W-081 ·  W-082 ·  W-083 ·  W-084 ·  W-085 ·  W-086 ·  W-087 ·  W-088 ·  W-089 ·
Phase 10   W-090 ·  W-091 ·  W-092 ·  W-093 ·  W-094 ·  W-095 ·
Phase 11   W-096 ·  W-097 ·  W-098 ·  W-099 ·  W-100 ·  W-101 ·  W-102 ·  W-103 ·  W-104 ·  W-105 ·  W-106 ·  W-107 ·  W-108 ·  W-109 ·  W-110 ·  W-111 ·
Phase 12   W-112 ·  W-113 ·  W-114 ·  W-115 ·  W-116 ·  W-117 ·  W-118 ·  W-119 ·  W-120 ·  W-121 ·  W-122 ·  W-123 ·
Phase 13   W-124 ·  W-125 ·  W-126 ·  W-127 ·  W-128 ·  W-129 ·  W-130 ·  W-131 ·  W-132 ·  W-133 ·
Phase 14   W-134 ·  W-135 ·  W-136 ·  W-137 ·  W-138 ·  W-139 ·  W-140 ·  W-141 ·  W-142 ·  W-143 ·  W-144 ·  W-145 ·  W-146 ·
```

Counts — done **1** / 146 · in flight **0** · blocked **0**

---

## Completed items

<!-- One line per DONE item, appended in order. Never edit an existing line.
     Format:  W-0nn | YYYY-MM-DD | <one-line summary> | <verify command that passed> -->

W-001 | 2026-09-19 | Four modulith modules declared, all Type.CLOSED; taxplanning OPEN→CLOSED | `./mvnw test -pl backend -Dtest=WealthLensModulithTest`

---

## Blockers

<!-- Use the exact shape from 00-START-HERE.md §8. Never delete a blocker; mark it RESOLVED. -->

*(none)*

---

## Decisions taken during the build

<!-- Anything the design did not settle and a human decided. Format:
     D-00n | YYYY-MM-DD | <question> | <decision> | <who> -->

Recorded on the design's own recommendations (`docs/taxplanning-design/16-getting-started.md` §S1).
**All are reversible at zero cost today; none has been built on yet.**

| ID | Date | Question | Decision | Who |
|---|---|---|---|---|
| D-001 | 2026-09-19 | Q1 — ship TY 2025-26 at launch, or TY 2026-27 only? | **TY 2026-27 first.** It is fully verified; TY 2025-26 detail rules are `NEEDS VERIFICATION` and would put Slice 0 on the critical path | provisional — on recommendation |
| D-002 | 2026-09-19 | Q2 — four modules or one? | **Four** — `taxrules`, `taxengine`, `taxdocs`, `taxplanning` | provisional — on recommendation |
| D-003 | 2026-09-19 | Q3 — modulith `Type.CLOSED` or `OPEN`? | **CLOSED.** Greenfield has no flat-layout legacy to accommodate; CLOSED is what makes "the engine cannot reach a repository" enforced rather than conventional | provisional — on recommendation |
| D-004 | 2026-09-19 | Q4 — advisor path within 12 months? | **Seam only** — `AccessPolicy` from day one with a trivial body | provisional — on recommendation |
| D-005 | 2026-09-19 | Q5 — document ingestion in release 1? | **Fast-follow**, behind `app.taxplanning.documents.enabled=false` | provisional — on recommendation |
| D-006 | — | Q6 — who signs off a rule-pack change? | **OPEN — needs a human name.** Does not block any item before `W-032` | — |
| D-007 | 2026-09-19 | Q7 — `SAL-ALW-NEW-SUBSET-001` per section or per r.280(3)? | **Per the section**, flagged visibly. A rule cannot override its parent section | provisional — on recommendation |

⚠ **D-003 is the only one that changes what `W-001` writes.** D-006 must be answered before `W-032`
(the first rule pack).

---

## Rules used that are not fully verified

<!-- Append whenever a completed item relies on a NEEDS VERIFICATION or STATUS UNCLEAR rule.
     Format:  <RuleId> | <status> | <item that used it> | <what to check> -->

*(none yet)*

---

## Notes for resume

Session 1. Decisions D-001…D-007 recorded on the design's recommendations; **D-006 (Q6) is still
open** and blocks nothing before `W-032`. The plan for `W-001` is written and is **awaiting
approval** — no code has been written.

`W-001` is **DONE** and committed. Next item is `W-002` — amend the money ArchUnit rule. It has not
been started; run prompt **P2** for it.

Learned in this session:
- `taxplanning` moving `OPEN` → `CLOSED` was the risk in `W-001`; `modulith verify()` passes, so
  nothing outside the module reaches into it. Decision **D-003** stands.
- The three marker classes (`TaxRules`, `TaxEngine`, `TaxDocs`) may be unnecessary — `package-info`
  alone might suffice for module detection. Not tested either way; they are harmless and each
  carries a note to delete it once the module has real public types.
- Baseline before any taxplanning work: **730 unit tests green, 7.5 s**. NFR N12 budgets 20 s.
- **Protocol gap found while committing.** The design and build docs were untracked, so `STATE.md`
  could not be committed without either orphaning it or dragging in the whole specification. Handled
  with a **baseline commit** of `docs/taxplanning-design/` and `docs/taxplanning-build/` immediately
  before the `W-001` commit. `P4` should say so; it currently assumes the spec is already tracked.
- **Second protocol gap.** `P4` says commit `STATE.md`, *then* write the new hash into it — which
  dirties the tree again. Resolved by committing, reading the hash, updating `STATE.md`, then
  `git commit --amend`. `P4` should spell that out.

Reminders that matter most:
- `W-002` exists because the design would otherwise **fail the existing build on the first commit** —
  `ArchitectureTest` forbids `BigDecimal.setScale` outside `..shared.util.money..`.
- `W-032` (the first pack JSON) is the highest-risk item in Phase 3. Slab bands are `400000`, **not**
  `400001`.
- `W-049` (the rebate node) contains the single most dangerous line in the engine: the rebate must
  read `TaxByBasis.atSlabRates` **only**.
