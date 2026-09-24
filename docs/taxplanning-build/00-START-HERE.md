# 00 — START HERE

You are building the `taxplanning` module of WealthLens. This directory is the **execution** artifact
set. `docs/taxplanning-design/` (25 files) is the **design** — reference it, never re-derive it.

**Read this file completely before doing anything.**

> **Amendment (D-033, 2026-09-23):** read `docs/taxplanning-design/00-BRIEF.md` **first** — the
> founding brief: who this is for (any assessee, individual first, HUF second), the concept model,
> the manager catalogue and the coverage register. Where any document disagrees with it, the brief
> wins.

---

## 1. The loop — one work item at a time, two stop points

Every work item goes through six stages. **You stop twice and wait for a human.**

```
  ┌─ 1. READ ──────── read the item, and ONLY the doc sections it names
  │
  ├─ 2. EXPLAIN ───── write the plan (§2). ████ STOP. WAIT FOR APPROVAL. ████
  │                   status → EXPLAINED
  │
  ├─ 3. CODE ──────── implement exactly what was approved. Nothing more.
  │                   status → IN_PROGRESS
  │
  ├─ 4. VERIFY ────── run the item's Verify command, then the unit tier
  │
  ├─ 5. REPORT ────── summarise + paste verify output (§3).
  │                   status → AWAITING_REVIEW.  ████ STOP. WAIT FOR REVIEW. ████
  │
  └─ 6. REVIEW ────── human approves → status DONE, commit, take the next item
                      human requests changes → back to stage 3
```

Two more edges exist and are easy to miss:

```
  stage 2 → stage 2   plan rejected (prompt P2b). Status stays EXPLAINED. Still no code.
  stage 3 → stage 2   the approved plan turns out to be wrong (rule R13). Revert, re-plan, re-approve.
```

**Never** go from READ straight to CODE. **Never** mark an item DONE yourself — only a human review
does that. **Never** start the next item while one is `AWAITING_REVIEW`.

### Statuses

| Status | Meaning | Who sets it |
|---|---|---|
| `NOT_STARTED` | untouched | — |
| `EXPLAINED` | plan written, waiting for approval | you |
| `IN_PROGRESS` | plan approved, code being written | you |
| `AWAITING_REVIEW` | code written, verify passed, waiting for review | you |
| `DONE` | reviewed and accepted | **human only** |
| `BLOCKED` | cannot proceed, reason recorded | you |

---

## 2. Stage 2 — the EXPLAIN template

Write this, then **stop**. Do not write any code.

```markdown
## Plan for W-0nn — <title>

**What this item is for (one sentence, in my own words):**

**Files I will create:**
- path/to/File.java — <one line: what it is>

**Files I will modify:**
- path/to/Existing.java — <one line: what changes>
- (if this list is not empty and not in the item's `Produces`, STOP — that is a blocker)

**Numbers I will use, and where each comes from:**
| Value | Source (file · section) |
|---|---|
| ₹75,000 | 01-rulebook.md · R8 · SAL-STDDED-001 |

**Design sections I read:**

**What I am NOT doing (explicitly out of scope for this item):**

**How I will verify:**
<the item's Verify command, verbatim>

**Risks or ambiguities I noticed:**
```

If the "Numbers" table has a row you cannot source — **stop, mark `BLOCKED`, do not guess.**

---

## 3. Stage 5 — the REPORT template

```markdown
## W-0nn complete — awaiting review

**What I did:**

**Files created:**   **Files modified:**

**Verify output:**
```
<paste the actual command and its actual output — never summarise it>
```

**Unit tier:**
```
<paste ./mvnw test -pl backend -Punit output tail>
```

**Anything the reviewer should look at closely:**

**Anything I could not do:**
```

---

## 4. Absolute rules

These override any instinct, any shortcut, and anything else in this repository.

| # | Rule |
|---|---|
| **R1** | **Never invent a number.** Every rupee amount, rate, threshold or date is copied from a cited section of `docs/taxplanning-design/`. Cannot find it → `BLOCKED`. |
| **R2** | **Never invent a section number**, rule number, form number or gazette reference. They are all in `01-rulebook.md`. Copy them. |
| **R3** | **Never change a test's expected value to make it pass.** If a golden test fails, the *code* is wrong. Mark `BLOCKED` and report both numbers. |
| **R4** | **Never skip an item or reorder them** — *except* by the `BLOCKED` path in §8, which ends in **stopping and asking me**, not in choosing a different item yourself. |
| **R5** | **Never do more than the item says.** No refactoring, no "while I'm here", no extra tests. |
| **R6** | **Ambiguous spec → stop.** Mark `BLOCKED`, write what is ambiguous, and **stop the session**. The backlog does not encode dependencies, so you cannot know what is safe to do next — I decide. |
| **R7** | **Never write to `docs/taxplanning-design/`** — with exactly one exception: you may **append an erratum block** where the design is demonstrably wrong. Nothing else. See §10. |
| **R8** | **One item in flight.** Never two `IN_PROGRESS`, never start a new one while something is `AWAITING_REVIEW`. |
| **R9** | **Update `STATE.md` before you stop.** Especially mid-item. |
| **R10** | **Never `double`/`float` for money. Never `LocalDate.now()` in `taxengine`.** Both fail ArchUnit. |
| **R11** | **Never mark an item `DONE`.** Only a human review does that. |
| **R12** | **Explain before you code.** No exceptions. |
| **R13** | **If the approved plan turns out to be wrong while coding, stop.** Revert what you wrote, set the item back to `EXPLAINED`, write a revised plan saying what you found, and wait for approval again. Never improvise past an approved plan. |
| **R14** | **Approval must be explicit.** Only a message that names the item and says *approved* (or *changes requested*) counts. Silence, "ok", "looks good", or a new unrelated instruction are **not** approval — ask. |

> **R7 amended (D-046 B1, 2026-09-23):** `docs/taxplanning-design/00-BRIEF.md` is the one design
> document edited **in place**, with its own Changes log — like `01-PRD.md` and `02-TECH-SPEC.md`. The
> other design documents remain errata-only.

---

## 5. Resuming after an interruption

```
1. git log --oneline --grep='^taxplanning W-' -1
                                     → compare the SUBJECT with STATE.md "Last checkpoint"
                                       mismatch → STOP, report `git log --oneline -10`
                                       (non-item commits are ignored by this check)
2. git status --short                → uncommitted work?
3. Read STATE.md "Current position" and "Notes for resume"
4. Act on the status you find:
     EXPLAINED        → the plan is written. Ask for approval. Do NOT code yet.
     IN_PROGRESS      → check which of "Produces" exist and compile. Finish only what is missing.
     AWAITING_REVIEW  → ask for review. Do NOT start the next item.
     BLOCKED          → read the blocker. STOP and report it. Do NOT pick another item (R6).
```

**Do not restart a `DONE` item. Do not assume an `IN_PROGRESS` item was never started.**

> **Amendment (D-044, 2026-09-23) — §5b, checkpoints for sessions that can end at any moment.**
> A session may be cut off by a rate limit at any point, including mid-message. Three rules:
>
> 1. **`CHECKPOINT.md` is the resume point** — about 20 lines, overwritten (never appended). Update
>    it at **every stage change** and **after every plan step**, and always **before** reporting to
>    the owner. Resume reads it first; `STATE.md` is read only when the checkpoint points there.
> 2. **The plan lives in a file**, `plans/W-0nn.md`, not only in the chat. Approval is recorded in it
>    (the owner's exact words). The plan's **Steps** list is ticked as work proceeds; a resumed session
>    continues at the first unticked step and redoes nothing ticked.
> 3. **No work-in-progress commits.** Uncommitted files survive on disk between sessions; the item
>    is still one commit (its `Produces`, its plan file, `STATE.md` and `CHECKPOINT.md`). The resume
>    check compares `git status --short` with the checkpoint's "Files touched".
>
> The short resume prompt is **P1-quick** in `04-PROMPTS.md`.

---

## 6. Definition of done — every item, no exceptions

- [ ] The item's `Verify` command passes
- [ ] `./mvnw test -pl backend -Punit` passes
- [ ] `./mvnw spotless:apply` has been run
- [ ] **The only files changed are:** the item's `Produces` list, **plus `docs/taxplanning-build/STATE.md`**, **plus** any file `spotless:apply` reformatted with no semantic change
- [ ] If `spotless` touched a file that is **not** in `Produces`, `git checkout --` that file and say so in the report — formatting an unrelated file is someone else's change, not yours
- [ ] `STATE.md` updated
- [ ] **A human has reviewed and approved**
- [ ] The item is **committed** in **one** commit — its `Produces` files plus `STATE.md` — whose
      subject is `taxplanning W-0nn: <title>`, and STATE.md's "Last checkpoint" names that item and
      subject **before** the commit is made

Eight of eight, or it is not done. The commit is part of the item, not an afterthought — the resume
check in §5 depends on it.

⚠ **Write the checkpoint line before committing, not after.** A commit cannot contain its own hash,
which is why the checkpoint is an item id and a subject rather than a hash.

---

## 7. Sizing

Each item is **one file or one small tightly-coupled set**, completable in one session, and under
roughly 300 lines of new code.

**If an item is larger than that, it was mis-sized. Do not expand it and do not split it yourself.**
Say so in your **EXPLAIN** (stage 2), under "Risks or ambiguities", with your estimate and a proposed
split. That is a decision for me, taken at the plan stage — never discovered halfway through the code.

---

## 8. Escalation — mark `BLOCKED` and stop when

- a number you need is not in the design docs;
- two design docs disagree;
- a golden test's expected value and your computation disagree;
- the item depends on something already `BLOCKED`;
- a build gate fails for a reason the item did not anticipate;
- you would have to modify a file outside `Produces`;
- **the design docs and the existing code disagree** — use prompt **P10**, do not change either side;
- **the item is mis-sized** (§7) — raise it in the EXPLAIN, not mid-code.

Record it in `STATE.md` in exactly this shape:

```
### B-00n  (W-0nn)
What I needed:
What I looked at:
What is ambiguous or missing:
What I would need to proceed:
```

---

## 10. Errata — the only permitted write to the design *or the backlog*

> **Amendment (D-046 B1, 2026-09-23):** `01-PRD.md` and `02-TECH-SPEC.md` are **no longer
> append-only**. They state the current truth and are edited in place; every change is logged in
> their **Changes** section, and the prior text stays in git. The errata rule below still governs
> the 25 design documents and `03-WORK-ITEMS.md`.

The design is a record of what was decided and why, and `03-WORK-ITEMS.md` is a record of what was
planned. When building reveals either is **wrong** — not merely incomplete, and not a matter of
taste — append an erratum **immediately below the wrong text**, in exactly this shape:

```markdown
> **Erratum (W-0nn, YYYY-MM-DD):** <what is wrong, in one sentence> — <the correction>.
> Verified by: <command or file:line>.
```

Rules for an erratum:

| | |
|---|---|
| **Never edit or delete the original text.** | The error and its correction must both be visible. A silently corrected document teaches nothing |
| **Only where you have proof.** | A command that failed, or a `file:line` you read. Not a suspicion |
| **One block, immediately after the wrong statement.** | Not a footnote, not a separate errata page |
| **Record it in `STATE.md` too**, under "Notes for resume" | so a resumed session sees it without re-reading 25 documents |
| **Nothing else in that file may change.** | `git diff` on a design doc must show additions only, all of them erratum blocks |
| **It is still part of the item's report.** | The reviewer sees it before it is committed |

This exception exists because `W-002` found `19-engineering-foundations.md` E1 quoting an ArchUnit
rule **without** its `ChargeRounding` exemption — implementing from that quote alone would have
broken the charges engine. A design that cannot be corrected is a design that misleads the next
reader in exactly the way it misled you. Extended to `03-WORK-ITEMS.md` by decision **D-009**, after
`W-006` found a work item citing the wrong test.

---

## 9. The files here

| File | What | When |
|---|---|---|
| `00-START-HERE.md` | this protocol | start of every session |
| `01-PRD.md` | what the product must do | when an item cites a feature ID |
| `02-TECH-SPEC.md` | every type, package, collection, endpoint, invariant | when an item cites a type |
| `03-WORK-ITEMS.md` | the ordered backlog with checkboxes | every session |
| `04-PROMPTS.md` | copy-paste prompts for each stage | every session |
| `STATE.md` | decisions, blockers, status board, history | when the checkpoint points there |
| `CHECKPOINT.md` | **the resume point** — ~20 lines, overwritten | first and last thing, every session (§5b) |
| `plans/W-0nn.md` | an item's plan, approval and ticked steps | while that item is in flight |
| `reviews/` | dated reviews of the build documents | when a decision cites one |

---

## 11. ID glossary — what every identifier means, and how to cite it

Added by `W-045b` (2026-09-23), from a scan of every file in `docs/taxplanning-design/` and
`docs/taxplanning-build/`. **Many letters mean different things in different documents** — `T1`,
`I8`, `R1`, `F4`, `A1`, `C1`, `E4`, `P1` each have two or three meanings. So:

> **Citation rule.** Inside its own document an ID may stand alone. **From any other document, prefix
> it**: design documents as `DR nn` (e.g. `DR 23 A3`, `DR 08 T1-N1`, `DR 13 R-5`); the build documents
> as `PRD`, `TS`, `WI` (work items), `STATE`, `BRIEF`. A bare collision-prone ID in a new line of text
> is a defect.

### Build documents (`docs/taxplanning-build/`)

| ID | Means | Home | Cite as |
|---|---|---|---|
| `W-0nn`, `W-0nnx` | work item; suffixed ids are splits/insertions, never renumbered | `03-WORK-ITEMS.md` | `W-045b` |
| `B-00n` | a blocker | `STATE.md` | `B-002` |
| `D-0nn` | a build decision (three digits) | `STATE.md` | `D-046` |
| `B1`, `B2` | backlog rules (B1: an item carries its own test; B2: a `permits` clause lists only what exists) | `03-WORK-ITEMS.md` | `WI rule B2` |
| `R1`–`R14` | the absolute rules | this file §4 | `START-HERE R7` |
| `P0`–`P11`, `P1-quick` | prompts | `04-PROMPTS.md` | `prompt P11` |
| `F1`–`F41` | product features; `F2.9` = a criterion | `01-PRD.md` | `PRD F2.9` |
| `N1`–`N12` | non-functional requirements | `01-PRD.md` | `PRD N12` |
| `P1`–`P5` | the five properties | `01-PRD.md` §6 | `PRD P3` |
| `PR-1`–`PR-5` | personas | `01-PRD.md` | `PR-5` |
| `T1`–`T12` | tech-spec sections | `02-TECH-SPEC.md` | `TS T5` |
| `I1`–`I32` | invariants | `02-TECH-SPEC.md` T5 | `TS I8` |
| `A1`–`A7`, `B1`–`B6`, `C1`–`C5` (in a review) | review findings / decisions | `reviews/<date>-…md` | `review 2026-09-23 B4` |
| `Q1`–`Q3` | session-3 questions (answered by D-043) | `STATE.md` | `STATE Q2` |

### The brief (`docs/taxplanning-design/00-BRIEF.md`)

| ID | Means | Cite as |
|---|---|---|
| `BP-1`–`BP-9` | the brief's principles | `BRIEF BP-5` |
| `M-01`–`M-28` | managers | `BRIEF M-08` |
| `OQ-1`–`OQ-4` | the owner's open questions | `OQ-4` |
| `SC-1`–`SC-5` | section-catalogue invariants | `BRIEF SC-3` |

### Design documents (`docs/taxplanning-design/`) — cite with `DR nn`

| ID | Means | Home | ⚠ Collides with |
|---|---|---|---|
| `D1`–`D7` | the design's scope decisions | `00-SUMMARY.md` | `D-0nn` build decisions |
| `D-WORD` (`D-PURE`, `D-NOSILENT`, `D-TEMPORALKEY` …) | design rules | `02-lessons.md` and later | — |
| `D-1`–`D-18` | defects found by the third pass | `15-review-findings.md` | `D-001`… (`D-17` ≠ `D-017`) |
| `S1`–`S11` | sources (the documents that were opened) | `01-rulebook.md` | `16-getting-started.md` S1, S2 sections |
| `R1`–`R24` | rulebook groups | `01-rulebook.md` | START-HERE `R1`–`R14` |
| `H-1`–`H-16` | hypotheses about the placeholder | `01-rulebook.md` R24 | — |
| `O-1`–`O-36` | observations of the placeholder's mistakes | `02-lessons.md` (from the context pack) | `14-operational-design.md` `O1`–`O5` sections |
| `L1`, `L2` | lessons sections | `02-lessons.md` | `17-regime-lifecycle.md` `L1`–`L5` |
| `K-n` | ideas kept from the placeholder | `02-lessons.md` | — |
| `A1`–`A4` | advisory sections | `03-advisory.md` | `23` `A1`–`A12` |
| `P1`, `P2`; `JTBD-n` | product-spec sections; jobs to be done | `04-product-spec.md` | PRD `P1`–`P5`, prompts |
| `M1`–`M7` | domain-model sections | `05-domain-model.md` | BRIEF `M-01`… |
| `G1`–`G7` | algorithms sections | `06-algorithms.md` | `23` `G-1`–`G-12` |
| `X1`–`X5` | architecture sections | `07-architecture.md` | — |
| `T1`–`T23`, `Tn-m` (`T1-N1`) | golden suites and cases | `08-golden-tests.md` | TS `T1`–`T12` |
| `B1`, `B2` | build-plan sections | `09-build-plan.md` | WI rules B1/B2; `15` B1–B5 |
| `E1`–`E8` | extensibility sections | `10-extensibility.md` | `19` `E1`–`E10` |
| `F1`–`F10` | filing-drafts sections | `11-filing-drafts.md` | PRD `F1`–`F41`; `15` F1–F6; `20` F29… |
| `C1`–`C6` | claim-integrity sections | `12-claim-integrity.md` | `15` C1–C5; `22` C1–C9; review C1–C5 |
| `R-1`–`R-15`; `T13-n` | reframed decisions; edge cases | `13-edge-cases.md` | rulebook `R1`… |
| `O1`–`O5` | operational-design sections | `14-operational-design.md` | `02` `O-1`… |
| `Part A`–`Part I` (top level) | review-findings parts — e.g. Part E is *`Condition`, resolved* | `15-review-findings.md` | cite as `DR 15 Part E` |
| `B1`–`B5`, `C1`–`C5`, `F1`–`F6` | sections inside those parts | `15-review-findings.md` | many — always `DR 15` |
| `S1`, `S2` | getting-started sections | `16-getting-started.md` | rulebook sources |
| `L1`–`L5` | regime-lifecycle sections | `17-regime-lifecycle.md` | `02` L1, L2 |
| `V1`–`V8`, `V-n` | temporal-versioning sections and cases | `18-temporal-versioning.md` | — |
| `E1`–`E10`, `TL-n` | engineering-foundations sections; trade-ledger references | `19-engineering-foundations.md` | `10` E1–E8 |
| `F29`–`F40` | product-gap features (now PRD features) | `20-product-gaps.md` | same numbers in the PRD — same meaning |
| `I1`–`I8` | ITR-forms sections | `21-itr-forms.md` | TS invariants `I1`–`I32` |
| `C1`–`C9` | claim-catalogue sections | `22-claim-catalogue.md` | see C above |
| `A1`–`A12`, `G-1`–`G-12`, `T23-n` | consolidation sections, defects, tests | `23-architecture-consolidation.md` | `03` A1–A4; `06` G1–G7 |

### Identifiers that are values, not references

| Shape | Means | Rule |
|---|---|---|
| `SAL-HRA-001`, `RATE-SLAB-…` | a `RuleId` | names a concept — no year, no "old/new", no section (D-035, OQ-4) |
| `NEW_TAX_REGIME` | a `RegimeCode` | the default regime, named from the 2025 Act's s.202 heading (D-051); was `S202_DEFAULT` until `W-045d` |
| `ITR-1` … `ITR-UN` | an `ItrFormCode` | the form's official code |
| `salary.gross` | a `NodeId` | lower-case, dot-separated |
| `TXC-2026-…` | a computation receipt | `PRD N9` |

