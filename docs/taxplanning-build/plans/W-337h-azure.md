> **Stopped after row 3 (D-935, 2026-11-23):** engine changed to PostgreSQL on Neon; this is the record of the Azure rows
> (renamed from `plans/W-337h.md`). The Neon plan is `plans/W-337h.md`. **Not torn down: the owner still has a live Azure SQL
> server `wealthlens-prod-1111.database.windows.net`, database `wealthlens`, login `wealthlens_admin` — delete both in the
> Azure portal when convenient (nothing in them but the empty schema from row 3); not required before building on Neon.**

## Plan for W-337h — the Azure SQL Database free-tier database for prod

> Planned in session 118 (2026-11-22) after D-933 (the Aiven plan is `plans/W-337h-aiven.md`, stopped after row 1; Oracle's
> is `plans/W-337h1.md`, not taken). A deployment item: the owner runs each row and pastes the result; the session answers
> **go** or **stop**. Facts from `sources/2026-11-22-azure-sql-free-tier.md` (Microsoft's page, read 2026-11-22).
> **Approved (D-934):** C1a C2a C3a, owner, 2026-11-22.

**What this item is for (one sentence, in my own words):** create prod's database on Azure SQL's free tier in Central India,
set so it can never charge (auto-pause when the month's free allowance ends), with schema `wealthlens` in it, proven to
fall asleep when nothing is connected.

### Already settled

| What | Value | From |
|---|---|---|
| Engine, offer | Azure SQL Database, free tier (100,000 vCore s, 32 GB data, 32 GB backup a month, per database) | D-933; sources note |
| Region | **Central India** — fixed for every free database in the subscription once chosen | owner, 2026-11-22; sources note |
| When the allowance runs out | **Auto-pause the database until next month** — never "Continue using database for additional charges" (cannot be undone) | sources note |
| Auto-pause | after 15 minutes with no connection, fixed | sources note |
| Schema; joins; FKs; per-module schemas later | `wealthlens`; none; kept; W-399 | D-929 |
| Who connects | one admin login for the owner, Flyway and the app — no separate roles (D-932 C1, carried) | D-932 |
| Prod's MongoDB size | ≈ 1.6 MB | W-337h-aiven row 0 |

### Decisions (for approval with this plan)

- **C1 — who may connect (the server firewall).** **C1a (recommended):** your current public IP only (the create wizard's
  "Add current client IP address": Yes; "Allow Azure services and resources to access this server": No). A changed home IP is
  one more firewall line in the portal. **C1b:** allow all (a rule 0.0.0.0 – 255.255.255.255), as you chose on Aiven.
- **C2 — collation (how text compares; set at creation, hard to change later).** **C2a (recommended):**
  `Latin1_General_100_CS_AS_SC_UTF8`: case-sensitive, so ciphertext, hashes and base64 (where `aB` ≠ `Ab`) are never
  matched or unique-indexed wrongly; UTF-8, so plain `VARCHAR` holds any name. **C2b:** Azure's default
  (`SQL_Latin1_General_CP1_CI_AS`, case-insensitive) and a case-sensitive collation on each such column in the Flyway
  scripts (W-337b) — easy to miss one.
- **C3 — sign-in method.** **C3a (recommended):** "SQL authentication" — a login name and password, which is what the app
  and DataGrip use. **C3b:** SQL plus Microsoft Entra (your Microsoft account as a second admin) — more to set up, nothing
  the app needs now.

### Build — the rows (prod)

**Row 1 — you: create the database** (portal; C1 – C3 as approved). Go to `aka.ms/azuresqlhub` → **Start free**. On
**Basics**:
- Subscription: yours. Resource group: **Create new** → `wealthlens-prod`.
- Database name: `wealthlens`.
- Server: **Create new** → Server name `wealthlens-prod-<4 letters or digits of your choice>` (must be unique across Azure);
  Location **(Asia Pacific) Central India**; Authentication method per C3 → Server admin login `wealthlens_admin`, a password
  you generate (`openssl rand -base64 24`) and keep in your password manager.
- Check the **free offer** line: the Cost summary shows **Estimated Cost/Month 0**, and the setting for when the free limit
  is reached is **Auto-pause the database until next month**. **Stop** if either is not so.
- **Networking** tab (C1): per C1a or C1b.
- **Additional settings** tab: Use existing data **None**; Collation per C2.
- **Review + create** → **Create**; wait for "Your deployment is complete".
- Paste: the server name, and a screenshot or the text of the Review page's cost and free-limit lines.

**Row 2 — you: connect from DataGrip and read it back.** DataGrip → **+** → Data Source → **Microsoft SQL Server**, name
`azure-prod`: Host `<server name>.database.windows.net`, Port `1433`, Authentication "User & Password", User
`wealthlens_admin`, the password, Database `wealthlens`. (Azure presents a proper certificate, so leave encryption on and
"trust server certificate" off.) Test Connection, then:
```sql
SELECT @@VERSION AS version, DB_NAME() AS db,
       DATABASEPROPERTYEX(DB_NAME(), 'Collation')        AS collation,
       DATABASEPROPERTYEX(DB_NAME(), 'Edition')          AS edition,
       DATABASEPROPERTYEX(DB_NAME(), 'ServiceObjective') AS objective,
       (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID) AS encrypted;
```
- Expect: `Microsoft SQL Azure …`, `wealthlens`, the C2 collation, edition `GeneralPurpose`, a serverless objective
  (`GP_S_…`), `encrypted` = `TRUE`. Paste the row. **Stop** on a different collation (the database is recreated —
  it is empty, so that costs nothing).

**Row 3 — you: the schema** (same console):
```sql
IF SCHEMA_ID('wealthlens') IS NULL EXEC('CREATE SCHEMA wealthlens');
ALTER USER dbo WITH DEFAULT_SCHEMA = wealthlens;
SELECT s.name, USER_NAME(s.principal_id) AS owner,
       (SELECT COUNT(*) FROM sys.tables t WHERE t.schema_id = s.schema_id) AS tables
FROM sys.schemas s WHERE s.name = 'wealthlens';
```
- Expect: one row, `wealthlens`, owner `dbo`, `tables` 0. A second run changes nothing.
- **Stop** if `ALTER USER dbo` is refused (the server admin may not be allowed to change `dbo`'s default schema;
  then W-337a names the schema in its settings instead — paste the error).

**Row 4 — you: prove it falls asleep.** Disconnect `azure-prod` in DataGrip (the red square), close any portal query
editor, wait **20 minutes**, then open the database's **Overview** in the portal.
- Paste: its **Status** line, and the free-allowance figures the Overview shows (remaining vCore seconds / storage).
- Expect: Status **Paused**. **Stop** if Online: something is still connected.

**Row 5 — you: the spending alert and credentials.** Confirm by name only: a budget alert exists on the subscription (Cost
Management → Budgets, e.g. ₹100 a month, email to you); the admin password is in your password manager and nowhere in the
repo or chat. Paste: "budget set, saved off-repo".

**Row 6 — you: sign-off.** "prod signed off".

### Keeping the free time (for W-337a, not this item)

The allowance is spent only while the database is awake, and any open connection keeps it awake. So the app's connection
pool must let go when idle: Spring Boot's `spring.datasource.hikari.minimum-idle: 0` and a short
`spring.datasource.hikari.idle-timeout` let the pool close every connection once the app is quiet, and the database pauses 15
minutes later. The scheduled tax jobs (`TaxJobSchedule.java:36` – `:69`) ship off while the app is local (W-336), so
nothing wakes it on a timer. Stop the app and disconnect DataGrip when done. W-337a sets these and measures the usage.

### Close-out (the session)

- Run-log lines in `post-deployment-runbook.md` for rows 1 – 6 and the Aiven rows 0 – 1; a D-row recording the server,
  database, region, collation, firewall choice and that it paused.
- `post-deployment-runbook.md` §2.19 (W-337h): what exists, rows 1 – 3 as the commands, a second run's effect, the way back
  (delete the database and server in the portal — nothing in them yet), "prod · once, done".
- §1.s's owner's-record table: a row for Azure's backups as read (retention not yet sourced — the free tier FAQ).
- STATE board `W-337h` to awaiting review; W-337a re-planned for SQL Server next.

**Files I will create:** none besides this plan. **Modify:** `post-deployment-runbook.md`, `DECISIONS.md`, `STATE.md`,
`CHECKPOINT.md`, the Azure sources note (row 1 – 4 readings). No code.

**Numbers I will use, and where each comes from:** 100,000 vCore s, 32 GB, 15 minutes, Central India's fixed-region rule,
the two exhaustion options and "can't switch back" — `sources/2026-11-22-azure-sql-free-tier.md`; port 1433 and
`.database.windows.net` are SQL Server / Azure SQL's standard endpoint (row 2 proves them); ₹100 budget is a suggestion,
your number. No rupee figures in the app, no legal section.

**Design sections I read:** D-928, D-929, D-932, D-933; the Azure sources note; `TaxJobSchedule.java`; CLAUDE.md §6.

**What I am NOT doing:** staging and dev (W-400); Flyway, tables, JPA, the app's settings (W-337a / W-337b); moving data
(W-337g); deleting the Aiven service (yours, whenever you like).

**How I will verify:** each row's paste against its "Expect"; row 4 proves the sleep that keeps it free; no code runs.

**Post-deployment actions (R15):** this item is the runbook's §2.19, written at close-out.

**Five roles:** architect — one schema in one database, real schemas for W-399 later; PM — nothing a user sees changes;
senior engineer — never-charge setting checked before Create, case-sensitive text so ciphertext can't collide, TLS
verified; automation tester — row 2 reads every setting back, row 4 proves the pause; manual tester — the owner runs each row.

**Risks or ambiguities:**
1. **No SLA** on the free tier (Microsoft's page); fine for one person's app.
2. **An always-running app spends the month's free time in about two days** (estimate from Microsoft's serverless billing rule,
   about 0.7 vCore billed while awake), then the database is paused until the 1st — not charged.
3. **Row 1's portal screens** may differ in wording from the page; stop and paste if a field named here is missing.
4. **Backup retention** is not on the free-tier page; read before §1.s is written.
5. **`ALTER USER dbo`** may be refused for the server admin (row 3's stop names the fallback).

### Steps

- [x] 1. Approval of C1 – C3 (owner) — D-934
- [x] 2. Rows 1 – 3 run (server/db/login created; version and collation read; schema created) — not taken further, D-935
- [ ] 3. Rows 4 – 6, close-out — not taken (engine changed, D-935)
