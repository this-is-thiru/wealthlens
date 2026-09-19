# 00 — START HERE

You are building the `taxplanning` module of WealthLens. This directory is the **execution** artifact
set. `docs/taxplanning-design/` (25 files) is the **design** — reference it, never re-derive it.

**Read this file completely before doing anything.**

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

## 10. Errata — the only permitted write to the design

The design is a record of what was decided and why. When building reveals it is **wrong** — not
merely incomplete, and not a matter of taste — append an erratum **immediately below the wrong
text**, in exactly this shape:

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
reader in exactly the way it misled you.

---

## 9. The files here

| File | What | When |
|---|---|---|
| `00-START-HERE.md` | this protocol | start of every session |
| `01-PRD.md` | what the product must do | when an item cites a feature ID |
| `02-TECH-SPEC.md` | every type, package, collection, endpoint, invariant | when an item cites a type |
| `03-WORK-ITEMS.md` | the ordered backlog with checkboxes | every session |
| `04-PROMPTS.md` | copy-paste prompts for each stage | every session |
| `STATE.md` | **the only mutable file** | first and last thing, every session |
