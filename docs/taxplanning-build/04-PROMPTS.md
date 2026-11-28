# 04 — Prompts

Copy-paste these. They are written so a smaller model cannot drift.
`<W-0nn>` is the item id; replace it and nothing else.

---

## P0 — Before the first item, once

> Read `docs/taxplanning-build/DECISIONS.md`.
>
> Seven questions are open. **Q1** (ship TY 2025-26 at launch, or TY 2026-27 only?) and **Q3**
> (modulith `Type.CLOSED` or `OPEN`?) block `W-001`.
>
> For each of the seven, restate the question in one line and give the recommendation from
> `docs/taxplanning-design/16-getting-started.md` §S1 with its one-line reason. Then stop and wait
> for my answers.
>
> When I answer, record each in DECISIONS.md, appended to its table, in the form
> `D-00n | YYYY-MM-DD | <question> | <decision> | <who>`, tick its checkbox, and confirm which are
> still open. **Do not start `W-001` until Q1 and Q3 are recorded.**

---

## P1 — Start or resume a session

> Read `docs/taxplanning-build/00-START-HERE.md` completely, then read
> `docs/taxplanning-build/BACKLOG.md`.
>
> Run `git log --oneline --grep='^taxplanning W-' -1` and compare its **subject** with the
> **"Last checkpoint"** line in CHECKPOINT.md. Non-item commits (docs, tooling) are ignored.
> - **Matches** → normal. Proceed.
> - **Differs** → an item was committed outside this protocol. **Stop and tell me** what
>   `git log --oneline -10` shows. Do not proceed until I say so.
>
> Then tell me, in under ten lines:
> 1. the current position (item id and status);
> 2. whether anything is `IN_PROGRESS`, `AWAITING_REVIEW` or `BLOCKED`;
> 3. what the next action is according to `00-START-HERE.md` §5;
> 4. any open blockers.
>
> **Do not write any code. Do not start an item.** Wait for me.

---

## P1-quick — Resume after an interruption *(added by D-044)*

> Resume taxplanning: read `docs/taxplanning-build/CHECKPOINT.md`, run its resume check, and if it
> matches, do its **Next step** only. If a plan file is named, read it and continue at the first
> unticked step. Update `CHECKPOINT.md` before you report to me.

Use this after a rate limit or a closed session. Use the full **P1** at the start of a new week or
when the checkpoint check fails.

---

## P2 — Explain an item *(stage 2 — the first stop point)*

> Take work item **`<W-0nn>`** from `docs/taxplanning-build/BACKLOG.md`.
>
> Read: that item; the design sections it names under `docs/taxplanning-design/`; and only those.
> You may also read `02-TECH-SPEC.md` for any type it mentions.
>
> Now write the plan using the **exact template** in `00-START-HERE.md` §2.
>
> Hard rules:
> - Every number in the "Numbers" table must cite a file and a section. If you cannot source one,
>   **stop and mark the item BLOCKED** using the §8 shape.
> - If the item would require modifying a file not in its `Produces` list, **stop — that is a blocker.**
> - Do not invent a type, package or endpoint name that is not in `02-TECH-SPEC.md`.
>
> **Write no code.** Update BACKLOG.md: set `<W-0nn>` to `EXPLAINED`. Then stop and wait for my approval.

---

## P2b — Reject or amend a plan *(stage 2, when the plan is wrong)*

> The plan for **`<W-0nn>`** is **not approved**. Problems:
> 1. …
> 2. …
>
> Keep the status at `EXPLAINED`. Rewrite the plan addressing only these points, using the same
> §2 template. **Write no code.** Then stop and wait for approval again.

---

## P3 — Implement after approval *(stage 3–5)*

> My approval for **`<W-0nn>`**: **approved as planned** *(or: approved with these changes: …)*.
>
> Now implement exactly the approved plan.
>
> - Create and modify only the files in the item's `Produces` list.
> - Copy every number from the source you cited. Do not recompute one.
> - If an expected value in a test disagrees with what your code produces, the **code** is wrong.
>   Do not change the test. Mark `BLOCKED` and report both numbers.
> - Follow the repository conventions in `02-TECH-SPEC.md` T12.
> - **If you discover the approved plan cannot work**, stop immediately (rule R13): revert what you
>   wrote, set the item back to `EXPLAINED`, write a revised plan explaining what you found, and wait.
>   Do not improvise a different design.
>
> Then:
> 1. run the item's `Verify` command;
> 2. run `./mvnw test -pl backend -Punit`;
> 3. run `./mvnw spotless:apply`;
> 4. run `git status --short` and confirm nothing outside `Produces` changed.
>
> **If step 1 or step 2 fails, do this and nothing else:**
> - **Do not** loosen or delete a test. **Do not** change an expected value. **Do not** add a
>   `try/catch` to make it pass.
> - You may fix your own code **twice**. Paste the failure output each time.
> - If it still fails after the second attempt, **stop**, set `<W-0nn>` to `BLOCKED`, write the
>   blocker using `00-START-HERE.md` §8, and report — including the full failure output and what you
>   tried. Do not proceed to the report template.
>
> If both pass, report using the **exact template** in `00-START-HERE.md` §3, pasting real command
> output — never a summary of it. Update BACKLOG.md: set `<W-0nn>` to `AWAITING_REVIEW`.
>
> Then **stop**. Do not start the next item.

---

## P4 — Record my review *(stage 6)*

**Approving:**

> Review of **`<W-0nn>`**: **approved.**
>
> 1. Update CHECKPOINT.md: set `<W-0nn>` to `DONE` with today's date, advance "Current position" to the next
>    item, and add anything learned to CHECKPOINT.md (a ruling as a D-row in DECISIONS.md).
> 2. **Before committing**, set CHECKPOINT.md's **"Last checkpoint"** line to
>    `W-0nn — "taxplanning W-0nn: <one-line title>"`. It must match the subject you are about to use.
> 3. Stage and commit **only** the files in this item's `Produces` list, plus BACKLOG.md, in **one**
>    commit:
>    `git add <those paths> docs/taxplanning-build/BACKLOG.md`
>    `git commit -F <message-file> -- <those paths> docs/taxplanning-build/BACKLOG.md`
>    Subject: `taxplanning W-0nn: <one-line title>`. Use the attribution lines this session was given.
>    *(Use `-F` with a file: a heredoc into `git commit -- <paths>` is read as paths, not a message.)*
> 4. Confirm with `git log --oneline -1` that the subject matches the checkpoint line. **Do not amend
>    to insert a hash** — a commit cannot contain its own hash.
> 5. Tell me the next item id and its one-line title. **Do not start it.**
>
> If the working tree contains changes outside the `Produces` list, **stop and tell me** — do not
> commit them.

**Requesting changes:**

> Review of **`<W-0nn>`**: **changes requested.**
> 1. …
> 2. …
>
> Set the status back to `IN_PROGRESS`, make only these changes, re-run the Verify command and the
> unit tier, and report again using the §3 template. Do not make any other change.

---

## P5 — When something is blocked

> Item **`<W-0nn>`** is blocked. Write the blocker in BACKLOG.md using the exact shape in
> `00-START-HERE.md` §8, set the item to `BLOCKED`, and then tell me:
> 1. what you needed and could not find;
> 2. the two or three options you can see, with the trade-off of each;
> 3. which you would choose and why — **as a recommendation, not a decision.**
>
> Then **stop the session**. Do not pick another item — the backlog encodes no dependencies, so you
> cannot know what is safe to start (rule R6). I will decide whether to unblock this item, reorder,
> or append a new one.

---

## P6 — Ending a session cleanly

> We are stopping. Before you finish:
> 1. update CHECKPOINT.md — statuses, "Current position", "Last updated"; CHECKPOINT.md;
> 2. if an item is mid-flight, write in CHECKPOINT.md exactly which files exist, which compile,
>    and what remains;
> 3. run `git status --short` and list what is uncommitted;
> 4. confirm no file under `docs/taxplanning-design/` was modified.
>
> Then give me a five-line handover: where we are, what is next, what is blocked.

---

## P7 — Weekly health check

> Without changing any code, report:
> 1. items `DONE` / `AWAITING_REVIEW` / `BLOCKED` / `NOT_STARTED`;
> 2. which milestone in `BACKLOG.md` we are between;
> 3. `./mvnw test -pl backend -Punit` — pass, and wall time (budget: 20 s);
> 4. how many golden (year × regime) cells are filled versus required;
> 5. every rule still marked `NEEDS VERIFICATION` or `STATUS UNCLEAR` that a **completed** item used;
> 6. anything in `00-START-HERE.md` §4 you had to work around.

---

## P8 — Verifying a completed phase

> Phase `<n>` is complete. Without writing code:
> 1. list every item in the phase with its status;
> 2. run each item's `Verify` command and paste the output;
> 3. run `./mvnw test -pl backend -Punit` and `-Dtest=ArchitectureTest`;
> 4. confirm the milestone row in `BACKLOG.md` for this phase is actually true;
> 5. list anything the phase was meant to deliver that is missing.
>
> Then stop. Do not start the next phase.

---

## P9 — The anti-hallucination self-check

Run this **before** approving any plan that touches a rupee value.

> For item **`<W-0nn>`**, list every number you are about to write into code or JSON. For each, give:
> - the value;
> - the file and section it comes from;
> - the **exact quoted line** from that file.
>
> If you cannot quote a line for any of them, say so plainly and mark the item `BLOCKED`.
> Do not reason from memory about Indian tax law. Everything is in `01-rulebook.md`.

---

## P10 — An item already marked DONE turns out to be wrong

> **`<W-0nn>`** was accepted but is wrong. Do not start or continue any other item.
>
> 1. Report what is wrong, and how you found it.
> 2. Run `git log --oneline` and identify the commit for `<W-0nn>`.
> 3. List every **later** item that touched the same files, and say whether each would be affected.
> 4. Propose **one** of: a forward-fix item appended to the backlog; a revert of that commit; or
>    reopening `<W-0nn>`. Give the trade-off of each.
>
> **Do not revert, reopen or edit anything until I choose.** When I do, record it in BACKLOG.md under
> DECISIONS.md and set the status accordingly.

---

## P11 — Emergency: the design docs and the code disagree

> Stop. Do not change either side.
>
> Report: what the code does; what the design says, quoted with its file and section; which design
> documents are involved and whether one **amends** the other (later documents amend earlier ones —
> check the "Amends" line at the top of each).
>
> Then recommend which should change, and why. **I decide.**

---

## P12 — Periodic tax audit and rule-pack refresh *(was `05-TAX-AUDIT-MASTER-PROMPT.md`, reviewed 2026-11-28)*

Run in a fresh session: after a Finance Act is assented and published in the Gazette, or new Rules or ITR forms are notified, before a new tax year's pack ships; after a CII, perquisite, allowance or PFRDA / EPFO change; or before a release that touched `taxengine`, `taxplanning`, `portfolio` or `brokercharges`. Replace `<TRIGGER_AND_YEAR>` (e.g. `TY 2027-28 Finance Act 2027 refresh`) and `<YYYY-MM-DD>`, and nothing else.

> Act as a senior principal product manager working with a senior Indian chartered accountant. **Audit trigger:** `<TRIGGER_AND_YEAR>`. Audit `taxengine`, `taxplanning`, their rule packs and the `portfolio` bridge, and write one report with a roadmap for my review.
>
> **Ground rules**
> 1. **Gazette only.** A figure, section (1961 and 2025 Acts), rate, cap, threshold, holding period, window, carry-forward span or lock-in counts as verified only when read in a Gazette copy, or in a `SOURCE_CHECKED` rule under `backend/src/main/resources/rulepacks/timeline/` or a file in `docs/taxplanning-build/sources/`. A CBDT circular, a department page or a reprint counts only where I have said it may. Anything else is `[NEEDS GAZETTE VERIFICATION]`. Never recall law from memory; this prompt states no legal figure, so take none from it.
> 2. **Every name must exist.** Before citing a type, enum value, rule id, rule set, method, path or item id, find it (`rg` / read the file). Enumerations — asset types, other-source kinds, regimes — are read from their source file, never listed from memory.
> 3. **Run or read, stated per finding.** Each finding says **RUN** (the test's name, its inputs, and the surefire XML counts and output pasted) or **READ** (the `path:line` read, and that it was not executed). Nothing is "live-verified" without a RUN.
> 4. **Refusal or crash.** Classify every failure by how `shared/exception/ControllerAdviser` answers it: an `IllegalArgumentException` is a refusal (400); anything else reaches the caller as 500. Say which, from the exception type at the cited line.
> 5. **Existing before new.** Before marking anything `[NEW]`, search `BACKLOG.md`, `DECISIONS.md` and the `F` numbers of `01-PRD.md` for it; a tracked item is cited as `[EXISTING: W-nnn]` with its status, a decided question by its D-row.
> 6. **Facts, not rules (CLAUDE.md §4).** Flag any legal date, threshold, rate, city list or qualifying list in Java rather than in tax-year-versioned pack data.
> 7. **Local test budget.** Never run a full tier, `-Pbenchmark` or a package glob locally. Run only one temporary named class: `./mvnw test -pl backend -Punit -Dtest=SeniorCaPeriodicAuditTest -Dspotless.check.skip=true -o -Daether.syncContext.named.factory=noop -Daether.remoteRepositoryFilter.prefixes=false`.
> 8. **Read-only on repo state.** Do not change `docs/taxplanning-design/`, `BACKLOG.md`, `CHECKPOINT.md`, `DECISIONS.md` or any main source. The temporary test is copied to the session's scratchpad (so a RUN can be repeated) and removed from the tree before you stop.
> 9. No AI or model attribution in any file.
>
> **Stage 1 — Resume and inventory.** Read `CHECKPOINT.md`, `BACKLOG.md`, `rulepacks/shipped-years.json` and the latest review in `docs/taxplanning-build/reviews/`. With a script, read every file under `timeline/law/rules/`, `timeline/law/disallowances/`, `timeline/exec/components/` and `timeline/exec/funds/` (liquidity terms are a field in the last two), and `timeline/content/claim-catalogue.json`. Report: rules and tax years shipped; every version not `SOURCE_CHECKED`; validity or sanction windows ended or ending, a cost inflation index missing the current year, an ended LTA block; each figure that differs between shipped years; and each value of `portfolio`'s `AssetType` and the engine's `OtherSourceKind` (read from their files) that no row of `RS-CG-TERM`, `RS-CG-PART` or `RS-OS-INCOME` serves. **Tracking check:** every item open (`K`, `·`, `E`, `P`, `B`) on the board of the latest `docs-history-*` tag is either in `BACKLOG.md` or has a D-row that finished it; list any that is neither.
>
> **Stage 2 — Stress tests against `TaxCalculator`.** In `backend/src/test/java/com/thiru/wealthlens/taxengine/golden/SeniorCaPeriodicAuditTest.java`, run each shipped year and regime through: (1) **age boundary** — born 31 March, 1 April, 2 April of the year that makes 60 and 80, across slabs, the senior health and deposit-interest rows and the advance-tax age exemption; the 1-April ruling is `W-439`'s — report its state, do not assume the law; (2) **surcharge and marginal relief with deferment relief** — income just above each surcharge threshold, from salary alone and from salary plus gains dated after an instalment date, computed on the year's first day and after a due date; the special-rate cap as `RATE-SUR-*-LIMIT` states it; (3) **rebate** — at each rebate row's ceiling and just above it, with and without special-rate gains, both regimes; (4) **house property** — the own-residence caps and completion window, the pre-construction instalments, a let property's loss in each regime, a negative annual value, three houses not let (`HouseElection`); (5) **perquisites and allowances** — each `PERQ-*` and `SAL-ALW-*` row's boundary, the specified-employee gate, a second car, the employer-contribution limit and accretion; (6) **Chapter VIII** — each deduction row's cap and its conflicts (shared ceilings, mutual exclusions, the s.133 / s.134 refusal); (7) **capital gains** — every `AssetType` value as its file declares it, the land indexation choice, the long-term exempt band and the basic-exemption shift, the reinvestment exemption's windows, cap and bar, set-off and carry-forward; (8) **advance tax and interest** — deferment relief with full and short payment by the last instalment, an undated dividend, refund interest at `INT-REFUND`'s threshold; (9) **retirement** — each `RET-*` row's limit and once-only bar; (10) **ITR form choice** (`chooseForm`) at each form's bars and with an unanswered declaration; (11) **elections and optimiser** (`compareRegimes`, `optimise`, `priceDeductionRoom`, `priceRentRoom`) — and whether `HouseElection` and `SetOffOrderElection` have a production caller; (12) **the `taxplanning` API** — typed `@Valid` requests, the `Clock`, the portfolio bridge, carry-forward across years, documents and jobs posture, ITR export state. Every expected figure is worked by hand from the cited rule before the run (CLAUDE.md §5).
>
> **Stage 3 — Tax-saving levers.** For each lever the pack models, give its rule ids, 1961 and 2025 sections, annual cap, statutory years and windows, whether it is an action still open or a check of one already taken, the regimes it reaches, its liquidity terms and their source, its conflicts (mutual exclusion, shared ceiling, the same rupee twice) and its traps. Start from the latest review's groups if it has them; regroup where the pack says otherwise.
>
> **Stage 4 — Report.** Write `docs/taxplanning-build/reviews/<YYYY-MM-DD>-periodic-tax-audit.md` with: (1) what changed since the latest review; (2) the test matrix — inputs, outputs, RUN or READ, PASS / FAIL / REFUSED (400) / 500, `path:line`; (3) a roadmap in **my standing order** — the D-rows that order the work (D-781 A, D-904 and any later) first, then the rest by what gives a wrong figure, refuses a valid return, or answers 500 — each line `[EXISTING: W-nnn]` or `[NEW]`, with a backlog row drafted for every `[NEW]` (phase, item text, source, when); the shape of the roadmap follows from the findings; (4) the sourcing queue — every figure, lock-in, circular or amendment to be read before it is built; (5) numbered questions for my rulings.
>
> **Stage 5 — Clean and stop.** Copy `SeniorCaPeriodicAuditTest.java` to the scratchpad, remove it from the tree, run `git status --short` and confirm only the report is new. Point me to the report and list the questions waiting on me. Do not commit, and do not edit `BACKLOG.md` until I rule.
