# 04 — Prompts

Copy-paste these. They are written so a smaller model cannot drift.
`<W-0nn>` is the item id; replace it and nothing else.

---

## P0 — Before the first item, once

> Read `docs/taxplanning-build/STATE.md` "Decisions taken during the build".
>
> Seven questions are open. **Q1** (ship TY 2025-26 at launch, or TY 2026-27 only?) and **Q3**
> (modulith `Type.CLOSED` or `OPEN`?) block `W-001`.
>
> For each of the seven, restate the question in one line and give the recommendation from
> `docs/taxplanning-design/16-getting-started.md` §S1 with its one-line reason. Then stop and wait
> for my answers.
>
> When I answer, record each in STATE.md under "Decisions taken during the build" in the form
> `D-00n | YYYY-MM-DD | <question> | <decision> | <who>`, tick its checkbox, and confirm which are
> still open. **Do not start `W-001` until Q1 and Q3 are recorded.**

---

## P1 — Start or resume a session

> Read `docs/taxplanning-build/00-START-HERE.md` completely, then read
> `docs/taxplanning-build/STATE.md`.
>
> Run `git log --oneline --grep='^taxplanning W-' -1` and compare its **subject** with the
> **"Last checkpoint"** line in STATE.md. Non-item commits (docs, tooling) are ignored.
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

## P2 — Explain an item *(stage 2 — the first stop point)*

> Take work item **`<W-0nn>`** from `docs/taxplanning-build/03-WORK-ITEMS.md`.
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
> **Write no code.** Update STATE.md: set `<W-0nn>` to `EXPLAINED`. Then stop and wait for my approval.

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
> output — never a summary of it. Update STATE.md: set `<W-0nn>` to `AWAITING_REVIEW`.
>
> Then **stop**. Do not start the next item.

---

## P4 — Record my review *(stage 6)*

**Approving:**

> Review of **`<W-0nn>`**: **approved.**
>
> 1. Update STATE.md: set `<W-0nn>` to `DONE` with today's date, append a line to "Completed items",
>    advance "Current position" to the next item, and add anything learned to "Notes for resume".
> 2. **Before committing**, set STATE.md's **"Last checkpoint"** line to
>    `W-0nn — "taxplanning W-0nn: <one-line title>"`. It must match the subject you are about to use.
> 3. Stage and commit **only** the files in this item's `Produces` list, plus STATE.md, in **one**
>    commit:
>    `git add <those paths> docs/taxplanning-build/STATE.md`
>    `git commit -F <message-file> -- <those paths> docs/taxplanning-build/STATE.md`
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

> Item **`<W-0nn>`** is blocked. Write the blocker in STATE.md using the exact shape in
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
> 1. update STATE.md — statuses, "Current position", "Last updated", "Notes for resume";
> 2. if an item is mid-flight, write in "Notes for resume" exactly which files exist, which compile,
>    and what remains;
> 3. run `git status --short` and list what is uncommitted;
> 4. confirm no file under `docs/taxplanning-design/` was modified.
>
> Then give me a five-line handover: where we are, what is next, what is blocked.

---

## P7 — Weekly health check

> Without changing any code, report:
> 1. items `DONE` / `AWAITING_REVIEW` / `BLOCKED` / `NOT_STARTED`;
> 2. which milestone in `03-WORK-ITEMS.md` we are between;
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
> 4. confirm the milestone row in `03-WORK-ITEMS.md` for this phase is actually true;
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
> **Do not revert, reopen or edit anything until I choose.** When I do, record it in STATE.md under
> "Decisions taken during the build" and set the status accordingly.

---

## P11 — Emergency: the design docs and the code disagree

> Stop. Do not change either side.
>
> Report: what the code does; what the design says, quoted with its file and section; which design
> documents are involved and whether one **amends** the other (later documents amend earlier ones —
> check the "Amends" line at the top of each).
>
> Then recommend which should change, and why. **I decide.**
