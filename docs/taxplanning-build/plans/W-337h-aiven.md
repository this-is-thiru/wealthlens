> **Stopped after row 1 (D-933, 2026-11-22):** engine changed to Azure SQL Database; this is the record of the Aiven rows (renamed from `plans/W-337h.md`). The Azure plan is `plans/W-337h.md`.

## Plan for W-337h — the Aiven PostgreSQL service `wealthlens`: roles, schema, TLS, allowlist (prod)

> Planned in session 118 (2026-11-22) after D-931 (Oracle dropped; the Oracle plan is `plans/W-337h1.md`, not taken). A
> deployment item ([[deployment-items-checklists]]): the owner runs each row in IntelliJ's Database tool and pastes the result;
> the session answers **go** or **stop** and logs it. **Approved (D-932):** owner, 2026-11-22.

**What this item is for (one sentence, in my own words):** turn the empty Aiven service the owner created into prod's database
for W-337: schema `wealthlens` in `defaultdb`, reached as `avnadmin` over Aiven's TLS, and the facts W-337a needs (version,
limits, backups) recorded.

### Already known (owner, 2026-11-22; not re-read here)

| Fact | Value | From |
|---|---|---|
| Service | `wealthlens`, Aiven project `aaaaaw` | owner's console link |
| Host, port | `wealthlens-aaaaaw.i.aivencloud.com`, `18042` | owner's connection string |
| Database, admin user | `defaultdb`, `avnadmin` | same |
| TLS | `sslmode=require` in Aiven's string | same |
| Version | PostgreSQL 18.7 | owner read |
| Size | 1 GB | owner, D-931 |
| Schema name, no joins, FKs kept, schema per module later | `wealthlens`; W-399 | D-929 (1) – (3) |

**Not known** (row 1 reads them): the plan's name and limits beyond 1 GB (connections, RAM), cloud and region, backup
retention, the allowed IP addresses as set now, prod MongoDB's size (row 0).

### Decisions (owner, D-932)

Owner: "c1, c2 - leave it / c4 allow all / c5 datagrip / prod only for now".

- **C1 — roles: left.** No roles are made; the app and Flyway connect as `avnadmin` (read as "leave it" = not done; C1a's
  split can return as its own row later).
- **C2 — TLS: left.** `sslmode=require` as Aiven's string gives (encrypted; the server's certificate is not checked).
- **C3 — database: C3a**, `defaultdb` as Aiven made it (owner: "defaultdb is fine").
- **C4 — who can connect: C4b, allow all.** The allowed IP addresses stay open (`0.0.0.0/0`); password and TLS only.
- **C5 — the tool: DataGrip.**
- **C6 — prod only: C6a.** Staging and dev placed as **W-400**.

### Build — the rows (prod)

Each row: what you do, what to paste, what I expect, when to stop. **Never paste a password.**

**Row 0 — you: prod MongoDB's size** (read-only; your IntelliJ console on prod `Cluster0`):
```javascript
db.getSiblingDB("Cluster0").runCommand({ dbStats: 1, scale: 1048576 })
```
- Paste: `dataSize`, `storageSize`, `indexSize`, `objects` (megabytes, by `scale`).
- Expect: `dataSize + indexSize` well under 1024. **Stop** if above ~500: PostgreSQL rows and indexes are usually larger
  than MongoDB's compressed storage, and 1 GB may not hold prod — we decide the plan size before going on.

**Row 1 — you: read the service page** (Aiven console, service `wealthlens`). Paste: the plan name and its limits (storage,
RAM, CPU, max connections), cloud and region, the backup retention shown (Backups tab), and the allowed IP addresses.
Expect: storage 1 GB (D-931); allowed IPs open (C4b — if they are narrower, paste them and leave them as they are).

**Row 2 — you: the DataGrip data source (C2, C5).** DataGrip → **+** → Data Source → PostgreSQL, name `aiven-prod`: Host
`wealthlens-aaaaaw.i.aivencloud.com`, Port `18042`, Database `defaultdb`, User `avnadmin`, the admin password (Save:
as DataGrip offers); **SSH/SSL** tab → Use SSL, Mode **Require**. Test Connection, then in a console:
```sql
SELECT version(), current_user, current_database(), (SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()) AS tls;
```
- Expect: `PostgreSQL 18.7 …`, `avnadmin`, `defaultdb`, `tls` = `true`. **Stop** if `tls` is false.

**Row 3 — you: the schema** (same console):
```sql
CREATE SCHEMA IF NOT EXISTS wealthlens;
SELECT nspname, pg_get_userbyid(nspowner) AS owner,
       (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'wealthlens') AS tables
FROM pg_namespace WHERE nspname = 'wealthlens';
```
- Expect: one row, `wealthlens`, owner `avnadmin`, `tables` **0**. A second run changes nothing (`IF NOT EXISTS`).
- **Stop** if `tables` is not 0 (something already lives there; tell me first).

**Row 4 — you: credentials off the repo.** Confirm by name only: the `avnadmin` password is in your password manager (or
DataGrip's store) and nowhere in this repo or chat. Paste: "saved off-repo".

**Row 5 — you: sign-off.** "prod signed off" once rows 0 – 4 are pasted and answered **go**.

### Close-out (the session)

- Run-log lines in `post-deployment-runbook.md` for rows 0 – 5; a D-row recording the service, plan, region, version,
  backups and the allowlist as read.
- `post-deployment-runbook.md` §2.19 (W-337h, D-row from the close-out): what exists (service, schema `wealthlens`), row 3
  as the command, its second-run effect, the way back (`DROP SCHEMA wealthlens;` as `avnadmin` — it holds nothing yet),
  marked "prod · once, done".
- §1.s's owner's-record table: a row for the Aiven service with row 1's backup retention (read, not assumed). MongoDB stays
  primary until W-337i; the reapply steps are rewritten there (D-928 C5a), not here.
- `sources/2026-11-22-aiven-postgresql.md` with the facts above and row 1's reading, for W-337a and W-400.
- STATE board `W-337h` to awaiting review.

**Files I will create:** `sources/2026-11-22-aiven-postgresql.md` (close-out).
**Files I will modify:** `post-deployment-runbook.md` (§2.19, §1.s row, run log), `DECISIONS.md`, `STATE.md`,
`CHECKPOINT.md`. No code.

**Numbers I will use, and where each comes from:** host, port, database, admin user and TLS mode from the owner's connection
string; 18.7 from the owner's read; 1 GB from D-931; prod's MongoDB name `Cluster0` from `application-prod.yaml:10`; the
"~500 MB" stop in row 0 is my margin, not a sourced figure — row 0's numbers decide it with you. No rupee figures, no legal
section.

**Design sections I read:** D-928, D-929, D-931, D-932; `plans/W-337h1.md` (shape); CLAUDE.md §6; `post-deployment-runbook.md`
§1.s and §2 numbering (last entry §2.18).

**What I am NOT doing:** separate roles (C1 left), CA verification (C2 left), an allowlist (C4b); staging and dev (W-400);
Flyway, tables, JPA, the app's datasource settings (W-337a, W-337b); moving data (W-337g); §1.s's reapply steps (W-337i).

**How I will verify:** each row's paste against its "Expect" line; no code or test runs.

**Post-deployment actions (R15):** this item **is** the runbook's §2.19, written at close-out.

**Five roles:** architect — one schema, as D-929 named it, ready for Flyway; PM — nothing a caller sees changes; senior
engineer — the owner's choices recorded as such (admin login for the app, open allowlist, TLS without host check), no secret
in the repo or chat; automation tester — rows 2 and 3 each read back what they claim; manual tester — every row run and
pasted by the owner.

**Risks or ambiguities:**
1. **1 GB** may not hold prod (row 0 decides).
2. **The app holds the admin password** (C1 left) and the service accepts any address (C4b): a leaked `avnadmin` password
   is enough to reach and drop everything. Owner's choice, recorded in D-932; a split role is a later row if wanted.
3. **Version moves:** Aiven may offer upgrades; W-337a pins 18.7 and re-reads before each later sub-item.
4. **Free plan limits** (connections, idle power-off, if any) are unknown until row 1; W-337a sizes the connection pool from
   them.

### Steps

- [x] 1. Approval of C1 – C6 (owner) — D-932
- [ ] 2. Rows 0 – 5, signed off "prod signed off"
- [ ] 3. Close-out: run log, D-row, runbook §2.19 and §1.s row, sources note, STATE board
