# WealthLens — deploy runbook

**What a database still needs, and how to check it.** One runbook for every module (W-176, D-379; CLAUDE.md §6). Rewritten by
W-430 (D-1041, D-1042) to hold only what is pending, what is run when a switch is turned on, and what recurs. **Everything earlier** — the MongoDB era, the move to PostgreSQL, every retired one-time step and the
192 runs logged until 2026-11-28 — is in git tag `docs-history-m2`
(`git show docs-history-m2:docs/taxplanning-build/post-deployment-runbook.md`); older still, `docs-history-m1`. Closed plans and
D-rows cite that text's section numbers.

Each entry is keyed to the work item and decision that introduced it, and says when it runs and who runs it; the exact command,
with a read-only check before and after; whether a second run is safe; and the way back.

**The rule (R15, D-278, D-279, D-379, CLAUDE.md §6).** A change that needs a seed, a data correction, a startup outcome to check, a
flag or a precheck adds its entry here **in its own commit**, and its plan names it under "Post-deployment actions". A session
never runs any of it against a real database: it writes the command, the owner runs it, and the result goes into the run log.
**Every operation is idempotent (D-282)** — a second run, including after one that failed part-way, completes the work or changes
nothing — and **a one-time operation is removed after use** (a Phase 15 backlog row, YouTrack parent **WL-28**, D-286).

**Every file a step saves is personal data, and is deleted (W-237f C3a):** a dump or a CSV taken as a step's way back is the
owner's own copy, which Act s.8(7)(a) reaches as it reaches the database. Delete it once its step's result is in the run log
and checked, or once the longest backup retention in §4.8 has passed, whichever is first; the run-log line says so.

`$BASE` is the app's base URL, `$ADMIN_TOKEN` a `SUPER_USER` JWT and `$TOKEN` any user's JWT. Everything runs locally; nothing is
hosted. Dev, staging and prod run `m1.9` (W-428b, D-1038).

| § | What | When |
|---|---|---|
| 0 | Before a start: the environment, how to run SQL | every start |
| 1 | **Pending** | once per database, then the entry goes |
| 2 | What every start's log must show | every start |
| 3 | When a switch is turned on: the jobs, erasure, the Gazette watch, documents | when the owner turns it on |
| 4 | Recurring and standing operations | on their cadence |

---

## 0. Before a start
### 0.1 Environment · owner · **every start**

| # | Check | Why |
|---|---|---|
| 0.1.1 | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (W-427), `JWT_SECURITY_KEY`, `PRIVACY_CONTACT` are set | The app does not start without the JWT key, deliberately: a defaulted signing key is a forged-token vulnerability. Nor without the privacy contact (W-235, W-237a): who answers questions about the processing of personal data, returned in every erasure receipt (DPDP Rules, 2025 r.9), §3.2. A build from W-428c needs no tax keys (D-1040); `m1.9` and earlier also need `TAX_DATA_KEY`, `TAX_DIGEST_KEY` |
| 0.1.2 | A `SUPER_USER` account exists | §4 needs one: seeding, publishing rate cards, the AMC cycle and every admin correction are `SUPER_USER`-only. Registration makes only `USER` accounts (W-255), so grant the role in that database: register through `POST /api/v1/public/auth/register`, then `PSQL -v email='<owner email>' <<< "UPDATE wealthlens.user_details SET roles = 'SUPER_USER' WHERE email = :'email' AND roles = 'USER';"` (§0.2), then sign in again (a token carries the role read at sign-in). A second run updates nothing. Way back: the same update with the two roles swapped |

### 0.2 Running SQL · owner · **every SQL step here**

Every SQL step runs `psql` from the `postgres:18` image, in a folder of your own, which a step's backup files are written to
(`/w` inside). One host per database (W-337h, D-936, D-980); each has one role used by you, Flyway and the app, over TLS.
Passwords are in your password manager only, never in the repo. The app works in schema `wealthlens` and never makes it (W-413,
D-989).

| Database | Host | Database, role | TLS |
|---|---|---|---|
| prod | Neon project `wealthlens`, branch `production`: `ep-holy-meadow-a1f4tyxx.ap-southeast-1.aws.neon.tech` | `neondb`, `neondb_owner` | `sslmode=require&channel_binding=require` |
| staging | Aiven project `aaaaaw`: `wealthlens-staging-aaaaaw.e.aivencloud.com:18042` | `defaultdb`, `avnadmin` | `sslmode=require` |
| dev | Aiven service `wealthlens-dev`: `wealthlens-dev-wealthlens-dev.d.aivencloud.com:13196` | `defaultdb`, `avnadmin` | `sslmode=require` |

```zsh
set -a; source ~/wealthlens-runs/<env>.env; set +a
PSQL() { docker run --rm -i -v "$PWD":/w -e PGPASSWORD="$DATABASE_PASSWORD" postgres:18 psql "host=<host> port=<port> dbname=<database> user=<role> sslmode=require" -v ON_ERROR_STOP=1 "$@"; }
```

`<env>` is `dev`, `staging` or `prod`; the host, port, database and role are the table's (prod: no `port=`, and
`channel_binding=require` added). `-v email='<email>'` reaches a query as `:'email'`; `-v id=…` likewise. A step's SQL goes in as
`<<< "…"` or `<<'EOF' … EOF` (`-c` does not substitute variables). Afterwards `unset DATABASE_PASSWORD JWT_SECURITY_KEY APP_PASSWORD`.

---

## 1. Pending — once per database, then the entry goes

An item that needs something at the next start adds its entry here in its own commit; the entry goes once its run is logged
for dev, staging and prod.

### 1.1 W-428c (D-1039, D-1040) — encryption removed: precheck, the start, the keys out · owner · **every database · once · before the first start of the release carrying W-428c**

**Why.** That release has no cipher and no keys. At its first start Flyway applies `V14__plain_personal_data.sql`: `date_of_birth`
back to `DATE` on `tax_profiles` and `tax_family_members`, `pan_digest` / `tan_digest` dropped, a PAN unique among profiles not
held for erasure and a TAN unique within a profile. A date not in `yyyy-MM-dd` form or two active profiles with one PAN would stop
that start, so a read-only check comes first. Running SQL is §0.2 step 4 (`PSQL`), from a folder of your own; the env file is the
database's own (`~/wealthlens-runs/<env>.env`).

1. **Backup first** — the migration changes column types: `docker run --rm -v "$PWD":/w -e PGPASSWORD="$DATABASE_PASSWORD" postgres:18 pg_dump "<the connection of §0.2>" -n wealthlens -Fc -f /w/<env>-pre-w428c.dump`,
   then `ls -l <env>-pre-w428c.dump`; stop if it is missing or empty.
2. **Check before (read-only)** — every count must be `0`:
   ```zsh
   PSQL <<'EOF'
   SELECT (SELECT count(*) FROM wealthlens.tax_profiles WHERE date_of_birth IS NOT NULL AND date_of_birth !~ '^\d{4}-\d{2}-\d{2}$') AS profile_dates,
          (SELECT count(*) FROM wealthlens.tax_family_members WHERE date_of_birth IS NOT NULL AND date_of_birth !~ '^\d{4}-\d{2}-\d{2}$') AS family_dates,
          (SELECT count(*) FROM (SELECT pan FROM wealthlens.tax_profiles WHERE erasure_requested_at IS NULL AND pan IS NOT NULL GROUP BY pan HAVING count(*) > 1) d) AS shared_pans,
          (SELECT count(*) FROM (SELECT profile_id, tan FROM wealthlens.tax_employers GROUP BY profile_id, tan HAVING count(*) > 1) d) AS shared_tans,
          (SELECT count(*) FROM wealthlens.user_details WHERE email LIKE 'k1:%') AS sealed_logins;
   EOF
   ```
   A non-zero count: stop and paste it.
3. **The start:** the release as usual. Expect Flyway "Successfully applied 1 migration … now at version v14", then `Started`, and
   the owner's sign-in `200`.
4. **Check after (read-only):** `PSQL <<< "SELECT data_type FROM information_schema.columns WHERE table_schema = 'wealthlens' AND table_name IN ('tax_profiles', 'tax_family_members') AND column_name IN ('date_of_birth', 'pan_digest');"`
   → two rows, both `date` (no `pan_digest`).
5. **The keys out of the env file** (after step 4 on that database; backup first):
   ```zsh
   cp -p ~/wealthlens-runs/<env>.env ~/wealthlens-runs/<env>.env.pre-w428c
   sed -i '' -E '/^(export )?TAX_(DATA|DIGEST)_KEY=/d' ~/wealthlens-runs/<env>.env
   grep -c TAX_ ~/wealthlens-runs/<env>.env
   ```
   → `0`. **Keep both values in your password manager** while `dev-pre-w428a.dump` and `prod-pre-w428a.dump` are kept: those
   dumps hold ciphertext, readable only with them and the `m1.9` build.

**Second run:** steps 2 and 4 are read-only; Flyway applies V14 once; step 5 finds nothing to remove. **Way back:** stop the app,
restore `<env>-pre-w428c.dump` (`pg_restore --clean --if-exists -n wealthlens`), put back `<env>.env.pre-w428c`, start `m1.9`.
**Removed after use:** this section, once logged for dev, staging and prod (WL-51's last step).

---

### 1.2 W-358d3 (D-885) — passwords rehashed as Argon2id at sign-in: the count a week after · owner · **read-only, due 2026-11-29, per database**

**read-only, a week after m1.6.1 (due 2026-11-29), per database:** §1.2's count. On 2026-11-22 `upgraded` was 1 on each (the owner's sign-in at W-366b / c row 4); a week after, read in SQL on PostgreSQL (`SELECT count(*) AS users, count(*) FILTER (WHERE password LIKE '{argon2}%') AS upgraded FROM wealthlens.user_details;`, D-1007 C2a, D-1013 C1a): the accounts that signed in or registered since. Going back to `m1.5` needs a password reset for each upgraded account (§1.2).

Nothing to run. Each successful sign-in rewrites that account's stored hash in `user_details.password` as `{argon2}$argon2id$…` when it
was older (every hash stored before W-358d3 is unlabelled BCrypt, `$2a$10$…`, or `$2y$` from `htpasswd`); registration and a password
change store Argon2id from the start. An account nobody signs in to keeps its BCrypt hash, which still signs in. A plain-text
(`{noop}`) or digest (`{MD5}`, `{SHA-1}` …) value never signs anyone in.

**Read-only, before the deploy and a week after** (`PSQL`, §0.2, one query):

```sql
SET search_path = wealthlens;
SELECT count(*) AS users, count(*) FILTER (WHERE password LIKE '{argon2}%') AS upgraded,
  count(*) FILTER (WHERE password NOT LIKE '{argon2}%' OR password IS NULL) AS bcrypt FROM user_details;
```

Expected before: `upgraded` 0. A week after: `upgraded` the accounts that signed in or registered since, `upgraded` + `bcrypt` =
`users`. A second run changes nothing (it only reads).

**Way back (D-885 C3a: no copy of the hashes is kept).** `m1.5` and earlier cannot read a labelled hash, so after going back an
upgraded account is refused until its password is reset: `htpasswd -bnBC 10 "" '<password>' | tr -d ':\n'` gives a BCrypt hash;
`PSQL -v email='<email>' -v hash='<hash>' <<< "UPDATE wealthlens.user_details SET password = :'hash' WHERE email = :'email';"` (`UPDATE 1`). The
query above (`upgraded`) counts the accounts this concerns.

---

## 2. On every start — automatic; confirm in the log

| # | Item / decision | What happens at startup | Check | If it fails |
|---|---|---|---|---|
| 2.1 | **W-079a** (D-210), **W-088s** (D-268) | `TaxEngineConfig` compiles every year in `rulepacks/shipped-years.json` (**2025-26, 2026-27**) and verifies each against its lock (`rulepacks/<year>.lock.json`) | The app starts. There is no database step: the rule packs ship in the JAR | **The boot stops** with a lock mismatch. Nothing reaches a database. Deploy the previous build; never edit a lock in place |
| 2.2 | Charge engine (ADR-27) | `ChargeReadinessAuditor` counts the rate cards when the engine is pricing | Grep the log for `NO RATE CARDS`. Expected on a database never seeded (prod today) until the seed (`POST /api/v1/admin/charges/seed`; the full steps are §1B of `docs-history-m2`) runs; `Charges engine ready: N rate card(s)` otherwise | Seed before anyone trades (§1B of `docs-history-m2`) |
| 2.3 | Flyway (W-337b, D-963) | Applies any new script in `db/migration/<module>/` to schema `wealthlens`, then Hibernate checks the schema | The log names the version, `Current version of schema "wealthlens": <n>` or `Successfully applied … now at version v<n>` | **The start stops.** Read the error; a new script's precheck in §1 says what to look for |

---

## 3. When a switch is turned on

### 3.1 W-137 (D-449) — the background jobs · owner · **when the jobs run (off since W-336): after the first runs**

**From W-336 (D-783)** every shipped profile has `app.tax.jobs.enabled: false`: nothing is scheduled, and the owner runs each job through `POST /api/v1/admin/tax/jobs/{name}/run` (`SUPER_USER`) on §4.1's cadence. The crons — `auto-archive` at 03:00 each Sunday and `retention-purge` at 04:00 on the 1st, in the server's zone (`app.tax.jobs.*-cron`) — apply only where the switch is turned on. Each logs `Job <name> archived draft <id>` or `deleted draft <id> of tax year <year>` per draft — ids only. **W-138 (D-450):** `staleness-sweep` runs at every start where the switch is on, and on request where it is off (after every start of a new build, §4.1); it logs `Job staleness-sweep flagged N draft(s)`; after a deploy that changes a pack (a new `law` or `exec` version in a lock), expect N to be the ready and filed drafts computed under the old one, and 0 on the next run. **W-257 (D-487):** a row a job writes carries the job's name as its actor (`audit_metadata.updatedBy`, or `createdBy` on a new row: `auto-archive`, `staleness-sweep`, `retention-purge`), stored as it is; rows written before W-257 keep `unknown`. Information only — no step.

**After the first run of each** (read-only; `PSQL`, §0.2):

```sql
SET search_path = wealthlens;
SELECT job, subject, reason FROM tax_job_failures ORDER BY failed_at;
SELECT count(*) AS leases_held FROM tax_job_leases;
```

Expected: no failures, and `leasesHeld` 0 between runs. A failure naming a tax year means a filed draft of a year no shipped pack covers: it is **kept**, and the row stays until the owner decides (ship that year's pack, or a D-row). Nothing to run twice; the tables are Flyway's.

**From W-239 (D-505)** the monthly `retention-purge` also deletes **landlords and lenders** once `app.tax.retention.third-party-after` (P2Y, all three profile yamls) has passed since the end of the last tax year the record can support (`DR 14` O5). From W-239a (D-506) that year is the later of the latest tax year of a stored draft **naming** the record (`/thirdParties/landlordIds`, `/thirdParties/lenderIds` in its inputs) and the year the record was last entered or changed; a record no draft names supports only the year of its last change. Drafts stored before W-239a name nothing, so on the first run their records follow that second rule. Each deletion logs `Job retention-purge deleted landlord <id>` or `… deleted lender <id>`; a record with no stored date is **kept** and logged as a refusal in `tax_job_failures` (subject `landlord <id>` / `lender <id>`). Nothing to seed or run. **Before the first run carrying W-239, and after it** (read-only; `PSQL`, §0.2; the drop is the records past their period):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM tax_landlords) AS landlords, (SELECT count(*) FROM tax_lenders) AS lenders,
  (SELECT count(*) FROM tax_landlords WHERE created_at IS NULL AND updated_at IS NULL)
  + (SELECT count(*) FROM tax_lenders WHERE created_at IS NULL AND updated_at IS NULL) AS undated;
```

Expected after: the counts fall by the number of `deleted landlord|lender` log lines, and `undated` equals the third-party refusals. A second run deletes nothing more. **Way back:** restore the two tables from the backup taken before the deploy — deleting them is the point, so only if a record was deleted in error.

**Way back:** stop running the job (nothing is scheduled while `app.tax.jobs.enabled` is false, the shipped value from W-336); stored drafts stay as they are. A draft the purge deleted is gone: restore it from the database backup.

### 3.2 W-235 — erasure on request · owner · **after the first purge that erases**

A user's `POST /api/v1/me/tax/profiles/{profileId}/erasure` holds the profile at once (`erasure_requested_at`; its PAN may then be registered again, W-428c) and answers `202` with the erase date, one year later (`app.privacy.erasure-hold: P1Y` since W-237d, DPDP
Rules, 2025 r.8(3)). The monthly `retention-purge` (§3.1) then erases the profile and everything under it — drafts of every
status, revisions, computations, family, employers, landlords, lenders, bank accounts, workspaces, regime election history,
and the access records naming any of them — logging `Job retention-purge erased profile <id>`. Nothing to seed or correct.

**Before the first start:** `PRIVACY_CONTACT` set (§0.1) to the contact the owner publishes; the start is refused
without it. **After a purge that logged an erasure** (read-only; `PSQL -v id='<id>'`, §0.2):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM tax_family_members WHERE profile_id = :'id') + (SELECT count(*) FROM tax_employers WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_landlords WHERE profile_id = :'id') + (SELECT count(*) FROM tax_lenders WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_bank_accounts WHERE profile_id = :'id') + (SELECT count(*) FROM tax_year_workspaces WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_filing_drafts WHERE profile_id = :'id') + (SELECT count(*) FROM tax_regime_election_history WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_profiles WHERE id = :'id') AS left_rows,
  (SELECT count(*) FROM tax_profiles WHERE erasure_requested_at <= (now() AT TIME ZONE 'UTC') - interval '366 days') AS held_overdue;
```

Expected: `left` 0, `heldOverdue` 0 (a held profile past its year still present means the purge did not run or failed:
§3.1's failure check). A second purge changes nothing. **Way back:** none for an erased profile — erasure is the point;
before the hold ends, a held profile can be released only with the user's word: `PSQL -v id='<id>' <<< "UPDATE wealthlens.tax_profiles SET erasure_requested_at = NULL WHERE id = :'id';"`.
**Backups:** a host backup keeps erased data until it expires; when that is, and a restore, are in §4.8 (W-237f).

### 3.3 W-237d — account-wide erasure · owner · **after the first run that erases**

A user's `POST /api/v1/me/privacy/erasure` holds the whole account at once (`privacy_erasure_requests`: `requested_at`)
and answers `202` with `erasedFrom`, one year later (`app.privacy.erasure-hold: P1Y`, DPDP Rules, 2025 r.8(3)), and the
privacy contact. From then the account cannot sign in, its tokens are refused, the email cannot be registered again, AMC
billing skips its charge accounts, and each of its tax profiles is held as §3.2 holds one. The `account-erasure`
job (run on request on §4.1's cadence, W-336; 04:30 daily where the jobs are on; one of §3.1's jobs, same lease and failure records) erases every
module's data for each account past its year — portfolio, trades, P&L, charges, charge accounts, corporate-action
watermarks, insurance, tax profiles and everything under them, the older salary profiles and computations, the person's
access-log entries, grievances and breach intimations, their email in each breach notice's `affected` — the login last,
then sets `erased_at`. The request row stays a year after `erased_at` as the record that the erasure was done, then §3.4's
`log-retention` job erases it (W-237e C4a). The person's own processing-log rows go with the account; rows others wrote
about them stay until their year (§3.4). Nothing to seed or correct.

**Before the first start:** the hold moved from `app.tax.retention.erasure-hold` to `app.privacy.erasure-hold` (env var
`APP_PRIVACY_ERASUREHOLD`); the run log has no §3.2 run, so no environment carries the old name. **After a run that
logged `Account erasure <id> done`** (read-only; `PSQL -v email='<email>'`, §0.2):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM assets WHERE email = :'email') + (SELECT count(*) FROM transactions WHERE email = :'email')
  + (SELECT count(*) FROM trade_outcomes WHERE email = :'email') + (SELECT count(*) FROM profit_and_loss WHERE email = :'email')
  + (SELECT count(*) FROM user_charges WHERE email = :'email') + (SELECT count(*) FROM charge_accounts WHERE email = :'email')
  + (SELECT count(*) FROM lastly_performed_corporate_action WHERE email = :'email') + (SELECT count(*) FROM insurances WHERE email = :'email')
  + (SELECT count(*) FROM tax_profiles WHERE email = :'email') + (SELECT count(*) FROM tax_draft_computations WHERE email = :'email')
  + (SELECT count(*) FROM privacy_grievances WHERE email = :'email') + (SELECT count(*) FROM privacy_breach_intimations WHERE email = :'email')
  + (SELECT count(*) FROM user_details WHERE email = :'email') + (SELECT count(*) FROM tax_access_log WHERE principal = :'email')
  + (SELECT count(*) FROM privacy_processing_log WHERE principal = :'email')
  + (SELECT count(*) FROM privacy_breach_notice_affected WHERE affected = :'email') AS left_rows,
  (SELECT count(*) FROM privacy_erasure_requests WHERE erased_at IS NULL AND requested_at <= (now() AT TIME ZONE 'UTC') - interval '367 days') AS held_overdue;
```

Expected: `left` 0 and `heldOverdue` 0 on every database (an open request past its year means the job did not run or
failed: §3.1's failure check; the next run completes it). A second run changes nothing: each erase deletes by email.
**Way back:** none for an erased account. Before the hold ends, an account is released only with the user's word:
`PSQL -v email='<email>' <<< "BEGIN; UPDATE wealthlens.tax_profiles SET erasure_requested_at = NULL WHERE email = :'email' AND erasure_requested_at IS NOT NULL; DELETE FROM wealthlens.privacy_erasure_requests WHERE email = :'email' AND erased_at IS NULL; COMMIT;"`.
**Backups:** a host backup keeps erased data until it expires; when that is, and a restore, are in §4.8 (W-237f).

### 3.4 W-237e — logs of processing kept a year, then erased · owner · **monthly review; after the first run that erases**

Every request made by a named person — a signed-in user, or a login attempt for an email, failed ones included — writes
one row to `privacy_processing_log`: `principal`, `subject_email` (the path's `{email}`, else the principal), `method`,
`route` (the matched pattern, never the path's values, the query or the body), `status`, `remote_address`, `recorded_at`
(UTC). A request with no named person (no token, no login) is not recorded. The `log-retention` job
(run on request on §4.1's cadence, W-336; 04:45 daily where the jobs are on; one of §3.1's jobs, same lease and failure records) erases what is a year past
its last processing (`app.privacy.log-retention: P1Y`, env var `APP_PRIVACY_LOGRETENTION`; DPDP Rules, 2025 r.6(1)(e),
r.8(3)): processing-log rows and `tax_access_log` reads by their time, grievances by `answered_at` (an unanswered one is
kept), breach intimations by the latest of `posted_at`, `read_at`, `emailed_at`, erasure requests by `erased_at`. Breach
notices stay. **Console logs are not this log:** they live only as long as the container (each deploy's `docker rm -f wealthlens-container` replaces
it) and are diagnostics (W-237e C5a).

**The table** (`privacy_processing_log`) is Flyway's (`V3__privacy.sql`): nothing to check first, nothing to seed.

**Monthly review (read-only, safe to repeat, nothing to roll back; `PSQL`, §0.2)** — r.6(1)(c)'s "monitoring and review": failed logins
by email and address over the last 31 days, and any principal that reached more than one person's data:

```sql
SET search_path = wealthlens;
SELECT principal, remote_address, count(*) AS attempts FROM privacy_processing_log
  WHERE route = '/api/v1/public/auth/login' AND status <> 200 AND recorded_at >= (now() AT TIME ZONE 'UTC') - interval '31 days'
  GROUP BY principal, remote_address ORDER BY attempts DESC;
SELECT principal, count(DISTINCT subject_email) AS subjects FROM privacy_processing_log
  WHERE recorded_at >= (now() AT TIME ZONE 'UTC') - interval '31 days' AND principal IS DISTINCT FROM subject_email GROUP BY principal;
```

Expected: few failed logins per email and address, and `reachedOthers` naming only the owner. Anything else is a
possible unauthorised access: §4.9.

**After the first run that erases** (a year after this deploy; read-only; `PSQL`, §0.2):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM privacy_processing_log WHERE recorded_at <= (now() AT TIME ZONE 'UTC') - interval '367 days')
  + (SELECT count(*) FROM tax_access_log WHERE read_at <= now() - interval '367 days')
  + (SELECT count(*) FROM privacy_grievances WHERE answered_at <= (now() AT TIME ZONE 'UTC') - interval '367 days')
  + (SELECT count(*) FROM privacy_erasure_requests WHERE erased_at <= (now() AT TIME ZONE 'UTC') - interval '367 days') AS overdue;
```

Expected: `overdue` 0 on every database (more means the job did not run or failed: §3.1's failure check; the next run
completes it). A second run changes nothing: each step deletes by date. **Way back:** stop running it (nothing is scheduled while
`app.tax.jobs.enabled` is false, W-336); erased rows are gone — restore from the database backup. **Backups:** a host backup
keeps erased rows until it expires; when that is, and a restore, are in §4.8 (W-237f).

### 3.5 W-249 (D-480) — erasure once the purpose has lapsed · owner · **after the first run**

An account not signed in to for `app.privacy.purpose-lapse: P3Y` (the owner's ruling, D-480; not a figure from the law)
is erased by the `account-erasure` job (§3.3, same job and holders; run on §4.1's cadence), at once and with no hold, and recorded in
`privacy_erasure_requests` with `cause: "PURPOSE_LAPSED"`, `requested_at` the lapse moment, `erased_at` the run. Each
sign-in and each registration stores `user_details.last_signed_in_at`. An account without it (every account registered
before W-249) gets the first run's time: **nothing can lapse earlier than three years after the first run on a
database.** No command to run. The privacy notice (`GET /api/v1/public/privacy/rights`, `/privacy`) states the period.

**Before the first start:** an environment override `APP_PRIVACY_PURPOSELAPSE` must be longer than `erasure-hold` (P1Y),
or the app refuses to start naming `app.privacy.purpose-lapse`; none is expected. **Read-only (`PSQL`, §0.2), before the first run and
after it** (the day after the deploy):

```sql
SET search_path = wealthlens;
SELECT count(*) AS users, count(*) FILTER (WHERE last_signed_in_at IS NULL) AS not_counted,
  (SELECT count(*) FROM privacy_erasure_requests WHERE cause = 'PURPOSE_LAPSED') AS lapse_erasures,
  (SELECT count(*) FROM privacy_erasure_requests WHERE cause = 'PURPOSE_LAPSED' AND erased_at IS NULL) AS lapse_open FROM user_details;
```

Expected before: `notCounted` = `users`, `lapseErasures` 0. After the first run: `notCounted` 0 and `lapseErasures` 0
(the earliest lapse is three years away). From then, `lapseOpen` 0 the day after any run (an open one means the run
failed part-way: §3.1's failure check; the next run completes it). A second run changes nothing: the time is set only
where absent, and an account with a request open is left to it. **Way back:** before the first lapse, clearing
`last_signed_in_at` (`UPDATE wealthlens.user_details SET last_signed_in_at = NULL;`, run in W-337j3b's local proof only) loses nothing (the next run counts again from its own time). An erased account cannot be brought
back; after a restore, §4.8's reapply erases a lapse erasure again like a requested one.

### 3.6 W-236 — the Gazette watch · owner, `SUPER_USER` · **when the owner chooses to start it; each database once**

**Why.** A daily job reads new e-Gazette copies and records a lead for each that may change a shipped pack (it names
the Central Board of Direct Taxes, or the Income-tax Act with the Department of Revenue). It **never writes a pack**: a
lead is read by the owner against the Gazette before anything changes. It calls out to `egazette.gov.in`, so it ships
**off** (`app.tax.gazette-watch.enabled: false` in all three profile yamls) and needs a starting point it never guesses.

**The tables** (`tax_gazette_cursor`, `tax_gazette_leads` with its unique `url`) are Flyway's.

1. **Check before** (read-only): no cursor yet, no leads.

   ```bash
   curl -sS "$BASE/api/v1/admin/tax/gazette-leads" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.page.totalElements'
   ```

   Expect `0`. In SQL (`PSQL`, §0.2): `SELECT count(*) FROM wealthlens.tax_gazette_cursor;` answers `0`.
2. **Choose the starting number.** On egazette.gov.in, open a recent Extraordinary Gazette; its address ends
   `WriteReadData/<year>/<number>.pdf`. The watch reads the numbers **after** the one given, so give the latest one already
   looked at (a lower number only means a longer first run).
3. **Seed the cursor** (recorded with the caller; a year before 2009 or after this year, or a number not positive, is a
   400):

   ```bash
   curl -sS -X PUT "$BASE/api/v1/admin/tax/gazette-cursor" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{"year": <year>, "lastNumber": <number>}' | jq '.'
   ```

   Expect the cursor back with `updatedBy` your login.
4. **Turn it on:** set `app.tax.gazette-watch.enabled=true` for the environment (`APP_TAX_GAZETTEWATCH_ENABLED=true`)
   and restart. With the jobs off (every shipped profile from W-336) run it on request, on §4.1's cadence:
   `curl -s -X POST "$BASE/api/v1/admin/tax/jobs/gazette-watch/run" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'`
   → `{"job": "gazette-watch", "ran": true}`; where `app.tax.jobs.enabled` is true it also runs at 05:00 daily, server time
   (`app.tax.jobs.gazette-watch-cron`). Each run reads at most 400 numbers and stops after 20 missing in a row.
5. **After the first run** (read-only): the log holds `Job gazette-watch stopped at <year>/<number>`, one
   `Gazette lead <url> …` WARN line per lead, and no `Job gazette-watch refused`; then

   ```bash
   curl -sS "$BASE/api/v1/admin/tax/gazette-leads" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.content[] | {url, page, note, possiblyAffected}'
   ```

   **Unverified until this run:** whether `egazette.gov.in` serves the server's host daily. A failure in
   `tax_job_failures` naming an HTTP status, or no cursor movement, means it does not — turn the watch off and record it
   in the run log.

**Twice?** Safe: the cursor only moves forward past copies read, and a lead's URL is unique, so a rerun over the same
numbers adds nothing. Re-seeding sets the cursor back; the leads already recorded stay.

**Way back:** `app.tax.gazette-watch.enabled=false` and restart. The cursor and leads stay; they are read by nothing else.
Nothing to back up: the watch writes only its own two tables.

### 3.7 W-131a – W-131d (D-761 – D-766) — documents: the switch, parsing and retention · owner · **when the documents switch is turned on, then after the first `retention-purge`**

The tables (`tax_documents`, `tax_parse_attempts`, `tax_document_confirmations`) are new and nothing writes to them until W-131d's endpoint exists with `app.tax.documents.enabled` on (D-761 C3a), so there is nothing to check first, and a second start changes nothing.

**After the first `retention-purge`** (read-only): run §3.1's failures query. Expected: no failure whose subject begins `documents of tax year`. One means a stored document of a year no shipped pack covers; it is **kept**, and stays until the owner decides (ship that year's pack, or a D-row), as for drafts.

**From W-131b (D-763):** a document's bytes are kept in table `tax_document_bytes` (W-337f), as uploaded (W-428c). **Confirm** the three profile yamls hold `app.tax.documents.max-bytes: 20971520`, `max-pages: 50` and `orphan-after: P1D` (startup fails without them). After each `retention-purge`, a log line `deleted the bytes of N document(s) no longer stored` is expected only after a removal or upload was cut off part-way; it changes nothing on a second run.

**From W-131c (D-764):** the `parse-documents` job reads each stored document with its form's parser. **Confirm** the three profile yamls hold `app.tax.jobs.parse-documents-cron: "0 * * * * *"` and `app.tax.documents.parse-budget: PT30S` (startup fails without them where `app.tax.jobs.enabled` is true). It runs every minute where the jobs are on, and finds nothing until W-131d stores a document. With the jobs off (every shipped profile from W-336) it runs only on request, and an upload starts no run of its own (`ParseRequests` needs the schedule): run `parse-documents` after an upload, or decide when documents are turned on whether an upload starts its own run (W-336 C5a). Expected in the log after an upload: `recorded a SUCCESS|PARTIAL|FAILED attempt at document …`. A line `overran its budget` means a parse thread was abandoned (W-131f isolates it); a `refused document …` failure row means a document has no stored bytes. Nothing to run; a second run reads nothing already read by the same parser version.

**From W-131d1 (D-766):** the endpoints `POST /api/v1/me/tax/documents`, `GET …/documents/{id}/parse` and `POST …/documents/{id}/confirm` ship **off**: the three profile yamls hold `app.tax.documents.enabled: false`, and each answers 503 "Uploading documents is switched off". **Confirm** the yamls hold it, and `spring.servlet.multipart.max-file-size: 21MB` / `max-request-size: 22MB` in `application.yaml`. **Turning it on** is the owner's, only after W-131f (isolation) is done. W-131e's scan ships off in staging and prod by the owner's decision (W-131h, D-769), so §3.8 is needed only once the scan is turned on: set `enabled: true` in the profile's yaml and redeploy; then `POST` a known Form No. 130 to a test profile, `GET …/parse` within a minute (where `app.tax.jobs.enabled` is true; where it is off, after running `parse-documents`, W-336), and check the access log holds the read (`tax_access_log`, subject `DOCUMENT`). Off again is the same yaml change. The notice's `tax-planning` part now names uploaded certificates, so the deploy carrying W-131d1 publishes a new notice version (W-250, D-481): any client registering must send it.

**Way back:** while they are empty, nothing to undo; the four tables are Flyway's. Once documents are stored, profile erasure and retention delete them with their attempts and confirmations (`DocumentRemoval`); a document deleted that should not have been is restored from the database backup.

### 3.8 W-131e (D-768), W-131h (D-769) — the malware scanner beside the app · owner · **each host, when the scan is turned on (off in staging and prod)**

**From W-131h (D-769):** staging and prod ship `app.tax.documents.scan.enabled: false` (owner: traffic and budget). Uploads are then stored unscanned, each `tax_documents` row records `scanned: false`, and every start logs `The malware scan is off: uploads are stored unscanned (W-131h)`. **Confirm** the staging and prod yamls hold `scan.enabled: false` and `application.yaml` holds `true`. Nothing below runs until the owner turns the scan on. **Turning it on:** steps 1 – 6 below, then `scan.enabled: true` in the profile's yaml and redeploy, then W-131i's rescan of what was stored unscanned. Read-only count before and after: `SELECT count(*) FROM wealthlens.tax_documents WHERE scanned IS NOT TRUE;` (`PSQL`, §0.2).

From W-131e every upload is scanned by ClamAV's `clamd` before a byte is stored or the PDF is opened (`MalwareScan`, `DocumentBytes.store`). It fails closed: with no scanner an upload answers 503 "The malware scan could not be completed; nothing was stored", and a file it finds malware in answers 400 "This file failed the malware scan and was not stored". While `app.tax.documents.enabled` is `false` nothing calls it, so a deploy carrying W-131e needs nothing until the switch is turned on. **Confirm** the three profile yamls hold `app.tax.documents.scan.host: ${CLAMD_HOST:localhost}`, `port: 3310` and `timeout: PT60S`.

1. **Before** (read-only): `free -g` on the host. The `available` column must leave **3 GiB** for ClamAV beyond the app (ClamAV Docker docs: "Minimum: 3 GiB", "Preferred: 4 GiB"; it "uses upwards of 2.4 GiB of RAM for a short period each day when loading new signature definitions"). Less → stop; the switch stays off.
2. Once per host: `docker network create wealthlens` and `docker volume create clam_db`.
3. `docker run -d --name clamd --restart unless-stopped --network wealthlens --mount source=clam_db,target=/var/lib/clamav clamav/clamav:1.5`. No port is published: only containers on `wealthlens` reach it, so a document's bytes never leave the host.
4. **Check** (read-only): `docker inspect --format '{{.State.Health.Status}}' clamd` answers `healthy` (the image's own health check pings `clamd`). The first start loads the signatures; on CI it answered within 17 s (run 44328189271).
5. **Unverified for today's setup:** the steps above assume the app runs in a container on the `wealthlens` network (then
   `-e CLAMD_HOST=clamd`). It now runs as `java -jar` on your machine, which reaches the scanner only through a published port
   (`-p 127.0.0.1:3310:3310` in step 3, and `CLAMD_HOST` unset, so `localhost`, `application.yaml:174`). Re-plan before
   turning the scan on.
6. **After**, on staging with the switch on (§3.7): `SELECT count(*) FROM wealthlens.tax_documents;` (`PSQL`, §0.2), then upload the 68-byte EICAR test file from eicar.org: `curl -s -X POST "$BASE/api/v1/me/tax/documents" -H "Authorization: Bearer $TOKEN" -F file=@eicar.com -F profileId=<a test profile> -F taxYear=2026 -F documentType=FORM_130` → 400 as above, the app log holds `An upload failed the malware scan: Eicar-Test-Signature`, and the count is unchanged. Then `docker stop clamd`, upload a real Form No. 130 → 503 and the count unchanged; `docker start clamd`, wait for `healthy`, upload it again → 200 and the count one higher.

**A second run** is safe: step 2 reports the network and volume exist, and step 3 reports the name in use — `docker start clamd` instead. `freshclam` inside the container updates the signatures once a day; nothing to run. **Way back:** turn the switch off first (§3.7), then `docker rm -f clamd` and `docker volume rm clam_db`; the scanner stores nothing of ours.

---

## 4. Recurring and standing operations

### 4.1 Recurring operations · `SUPER_USER`

| Operation | Command | Cadence | If it is never run |
|---|---|---|---|
| **AMC cycle** | `POST /api/v1/admin/charges/amc/impose` | quarterly / annually, per account frequency | No maintenance charge is billed. Idempotent per account and period: running it twice bills once |
| **Rate verification** | `GET /api/v1/me/charges/charge-schedules/unverified` | every 90 days | Cards age silently. The endpoint lists anything older than 90 days; that is the reminder (ADR-26) |
| **Drift check** | `GET /api/v1/admin/charges/charge-schedules/drift` | every deploy | A card edited in the repository never reaches a database that already seeded it: the seeder skips it. Drift makes that visible |
| **Tax jobs** (W-336, D-783) | `curl -s -X POST "$BASE/api/v1/admin/tax/jobs/<name>/run" -H "Authorization: Bearer $ADMIN_TOKEN" \| jq '.'` | `account-erasure`, `log-retention`, `auto-archive` weekly; `retention-purge` monthly; `staleness-sweep` after every start of a new build; `gazette-watch` only once §3.6 turns it on; `parse-documents` after an upload, while documents are on | A held account, a year-old log or an expired draft stays stored until the next run: while the app is local the duties run on this cadence. `ran: false` means another run holds the lease: run again later. A second run finds nothing new. Log each run |
| **Publishing a rate change** | `POST /api/v1/admin/charges/charge-schedules` | when a broker changes rates | **Never edit a deployed card** (ADR-26). Publish a successor; it supersedes the incumbent in the same transaction. How publishing supersedes: `docs/modules/brokercharges.md` §4 |

### 4.2 Register demat accounts · the user · **only if AMC is wanted**

AMC is **not** billed for an account that is not registered: with no account there is nothing to bill, and nothing
says so.

```bash
curl -sS -X POST "$BASE/api/v1/me/charges/charge-accounts" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "brokerName":"ZERODHA","dematAccountId":"1208160000000001","accountHolder":"self",
    "amcFrequency":"ANNUALLY","openedOn":"2024-01-01"
  }'
```

### 4.3 History before the engine is not priced

Trades made before the engine priced them carry no computed charge, and **there is no way to give them one**. ADR-32
applies the cutover forward only; the endpoint that could re-price history was removed for that reason. A charge
report over an earlier period is sparse, which is correct. Pricing history, if wanted, is the
work of W-383, and needs historical rate cards first.

### 4.4 If something goes wrong

**Stop the engine without a deploy:** `app.charges.engine-enabled: false`. Simulate and the AMC cycle answer **503**,
and nothing is recorded. Reads keep working (charge history, gaps, the reconciliation report), which is what
you need to decide whether to turn it back on.

**Stop only cost-basis changes**, keeping the engine recording for comparison: `app.charges.authoritative: false`. New
buys go back to the client-supplied `brokerCharges`. Trades already written keep the cost basis they were given;
nothing rewrites a stored `AssetEntity`.

| Symptom | Where to look |
|---|---|
| Charges are zero | `GET /api/v1/admin/charges/user-charges/users/{email}/gaps`: it names the reason per trade |
| Startup warned about rate cards | Seed (§1B of `docs-history-m2`); grep the log for `NO RATE CARDS` |
| A charge looks wrong | `GET /api/v1/admin/charges/user-charges/users/{email}/transaction/{id}`: the contract note, line by line, with the `scheduleCode` that priced it |
| Figures disagree with the client's | `GET /api/v1/admin/charges/user-charges/users/{email}/reconciliation` |

### 4.5 Known limits — recorded, not defects

Full list in `docs/modules/brokercharges.md` §8.
- **A database with no cards prices nothing until it is seeded** (§1B of `docs-history-m2`).
- **Nothing before 2025-04-01 can be priced:** no card covers it.
- **Only two mutual-fund schemes have exit-load profiles.** Every other fund resolves `NO_INSTRUMENT_PROFILE` and
  accrues no exit load; recorded, never fatal (ADR-24).
- **A rate card written directly to the database is invisible** until the resolver cache is evicted. `POST
  /api/v1/admin/charges/charge-schedules`, the close endpoint and `POST /api/v1/admin/charges/seed` evict it. Go through the API.
- **Scheme profiles have no publishing endpoint:** they arrive by seeding, and a profile added by hand needs a
  restart or a seed run.
- **No `POST /charges/recompute`.** Amending a card that has already priced charges has no safe mechanism; designed in
  tech-spec §14.4, not built.
- **V1 `buyStock` / `sellStock` do not use the engine.** They are in live use and deliberately untouched. Only the V2
  endpoints (`/api/v1/me/portfolio/transaction/v2`) are priced.

### 4.6 The trade idempotency window (TL-7)

`app.portfolio.idempotency.window-seconds`, default 60. Two identical trades submitted inside it are collapsed into
one. A bulk import that must submit identical rows sends an `idempotencyKey` per row: a supplied key is exact and has
no window. Setting the window to 0 disables the derived-fingerprint fallback and leaves client keys working.

### 4.7 W-237b — grievances answered within the published period · owner, `SUPER_USER` · **weekly**

A user raises a grievance with `POST /api/v1/me/privacy/grievances` (`201`, a receipt naming `respondBy`: the receipt time plus
`app.privacy.grievance.response-days`, 30, published in `GET /api/v1/public/privacy/rights`). DPDP Rules, 2025 r.14(3) allow at most
ninety days (`longest-days-allowed: 90`; a start whose period exceeds it is refused). Nothing to seed; the
`privacy_grievances` table is Flyway's. The app sends no mail: **this check is the measure
that keeps answers in time.**

**Weekly (read-only, safe to repeat, nothing to roll back):** as a `SUPER_USER`,

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/admin/privacy/grievances"
```

Expected: the unanswered grievances, the one due soonest first. Answer each before its `respondBy`:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"text": "<the answer>"}' "$BASE/api/v1/admin/privacy/grievances/<id>/answer"
```

`200` with `answer.answeredAt`; the user reads it at `GET /api/v1/me/privacy/grievances`. A second answer to the same
grievance is refused (`400`), so a repeated call changes nothing. **Way back:** an answer given in error is corrected by
writing to the user from the privacy contact; the stored answer is not edited.

### 4.8 W-237f — erased data in the backups, and a restore · owner · **before the duties commence (about May 2027); on every restore**

The Fiduciary must cause its Data Processor to erase what it was given (Act s.8(7)(b)). Backups are one of the expected
safeguards (Rules r.6(1)(d)), and r.8(3) Case 2 has the host keep the data at least a year. Nothing in the law limits how
long a snapshot may be kept: that is the owner's choice. The one bound is set by the app: **no snapshot may be kept
longer than `app.privacy.log-retention` (P1Y)**. A restore relies on the erasure records, and they are kept only a year
after each erasure (§3.4), so a snapshot older than that could hold a person no record still names (W-237f C1a).

**When erased data leaves the backups:** at `erased_at` plus the longest retention below. Data is erased a year after the
request (§3.2, §3.3), so a snapshot taken during the hold holds it until that snapshot expires.

**The hosts (W-337h, D-980)**, from the sources notes; you fill in what is not given (the MongoDB-era record is in `docs-history-m2`):

| | prod (Neon, Free) | staging (Aiven, `wealthlens-staging`) | dev (Aiven, `wealthlens-dev`) |
|---|---|---|---|
| Snapshot schedule; retention; point-in-time window | 1 manual snapshot, no scheduled backups; history 6 hours (`sources/2026-11-23-neon-postgresql.md`) | *not given* — retention not shown on the old service's Overview (`sources/2026-11-22-aiven-postgresql.md`); this service not read | *not given* |
| Longest retention ≤ P1Y? (the check) | yes, 6 hours | *not given* | *not given* |
| Processor terms accepted: document and date | *not given* | *not given* | *not given* |

**The check (owner, read-only, safe to repeat):** read each PostgreSQL host's backup settings (Neon: history retention and
snapshots; Aiven: the service's backups), enter every retention figure in the table above, and confirm none exceeds one
year. A longer one is shortened on the host before the duties commence. If a host has no backup that can be set, that is
a re-plan of W-237f (its risk 2), not a row here.

**On every restore.** A restore puts the database back to the snapshot's moment, and every erasure since is lost from the
data. The records of those erasures are exported first and put back after, and then one call applies them again. It
reapplies only what was done at or after the snapshot's time. An erasure from before that time is already reflected in
the snapshot, and its email may since belong to a new account, which must not be touched.

1. **Before the restore, from the live database** (read-only; `PSQL`, §0.2):

   ```bash
   PSQL <<'EOF'
   COPY (SELECT * FROM wealthlens.privacy_erasure_requests) TO STDOUT WITH CSV HEADER \g /w/erasure-requests-before-restore.csv
   COPY (SELECT * FROM wealthlens.tax_profile_erasures) TO STDOUT WITH CSV HEADER \g /w/profile-erasures-before-restore.csv
   EOF
   ```

   Note the snapshot's time in UTC (`yyyy-MM-ddTHH:mm:ss`) as `S`.
2. **Restore** on the host (D-1022 C5a): prod (Neon) from its history, "up to 6 hours", or its one manual snapshot
   (`sources/2026-11-23-neon-postgresql.md`); staging and dev (Aiven) from a service backup, whose retention is *not given*
   (`sources/2026-11-22-aiven-postgresql.md`). The console steps are in neither note. Every PostgreSQL backup postdates the move
   (W-337i), so no restore needs a MongoDB-era build.
3. **Put the records back** (an upsert by `id`: a record the restored database already holds is replaced by its later state):

   ```bash
   PSQL <<'EOF'
   BEGIN;
   CREATE TEMP TABLE er (LIKE wealthlens.privacy_erasure_requests) ON COMMIT DROP;
   \copy er FROM '/w/erasure-requests-before-restore.csv' WITH CSV HEADER
   INSERT INTO wealthlens.privacy_erasure_requests SELECT * FROM er ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email,
     requested_at = EXCLUDED.requested_at, cause = EXCLUDED.cause, erased_at = EXCLUDED.erased_at, created_by = EXCLUDED.created_by,
     updated_by = EXCLUDED.updated_by, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at;
   CREATE TEMP TABLE pe (LIKE wealthlens.tax_profile_erasures) ON COMMIT DROP;
   \copy pe FROM '/w/profile-erasures-before-restore.csv' WITH CSV HEADER
   INSERT INTO wealthlens.tax_profile_erasures SELECT * FROM pe ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email,
     profile_id = EXCLUDED.profile_id, requested_at = EXCLUDED.requested_at, erased_at = EXCLUDED.erased_at, created_by = EXCLUDED.created_by,
     updated_by = EXCLUDED.updated_by, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at;
   COMMIT;
   EOF
   ```

   Expect `INSERT 0 n` twice, each `n` the rows its file holds.

4. **Apply them again** (`SUPER_USER`):

   ```bash
   curl -X POST "$BASE/api/v1/admin/privacy/erasure/reapply" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{"snapshotTakenAt": "<S>"}'
   ```

   The answer names `accountsErased` (erased at or after `S`, erased again; `erased_at` unchanged), `accountsHeld`
   (asked at or after `S`, not yet erased, held again) and `profiles` (single profiles erased or held again). A time
   in the future, or none, is refused with 400.
5. **Check after (read-only)**, with `S` as `-v s='<S>'` (`PSQL`, §0.2):

   ```sql
   SET search_path = wealthlens;
   SELECT (SELECT count(*) FROM user_details WHERE email IN (SELECT email FROM privacy_erasure_requests WHERE erased_at >= :'s'::timestamp))
     + (SELECT count(*) FROM assets WHERE email IN (SELECT email FROM privacy_erasure_requests WHERE erased_at >= :'s'::timestamp))
     + (SELECT count(*) FROM tax_profiles WHERE email IN (SELECT email FROM privacy_erasure_requests WHERE erased_at >= :'s'::timestamp)) AS accounts_back,
     (SELECT count(*) FROM tax_profiles WHERE id IN (SELECT profile_id FROM tax_profile_erasures WHERE erased_at IS NOT NULL)) AS profiles_back;
   ```

   Expected: `accountsBack` 0 and `profilesBack` 0 on every database; then §3.3's check for each email in
   `accountsErased`. More than 0 means the call was not made or failed part-way. Make it again: each step deletes by email
   or by profile id, so a second call completes the work or changes nothing (tested).
6. **Delete the two export files** once the run is logged ("export deleted").

**Way back:** none is needed for the call, because it erases only what had been erased before the restore. If the
import was wrong, import the exports again and repeat step 4. **Profile-erasure records** (`tax_profile_erasures`:
`email`, `profile_id`, `requested_at`, `erased_at`) are new with W-237f. They are written by each single-profile request
from that deploy on, and erased a year after `erased_at` by §3.4's `log-retention` job. Nothing to seed.

### 4.9 W-237c — on a personal data breach · owner, `SUPER_USER` · **whenever the owner becomes aware of one**

A breach is *"any unauthorised processing of personal data or accidental disclosure, acquisition, sharing, use,
alteration, destruction or loss of access to personal data"* that compromises its confidentiality, integrity or
availability (DPDP Act, 2023 s.2(u)). The clock starts **when you become aware** (Rules r.7). Write that time down
first. Steps, in order (`$BASE`, `$TOKEN` as a `SUPER_USER`; times UTC, `yyyy-MM-ddTHH:mm:ss`):

1. **Without delay: post the notice** to each affected user. It covers r.7(1)(a) – (d), in plain words; the app adds
   the privacy contact, (e). `scope` is `EVERY_REGISTERED_USER` or `LISTED` with `emails`:

   ```bash
   curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "$BASE/api/v1/admin/privacy/breach-notices" \
     -d '{"text": {"description": "<what, how much, when>", "consequences": "<what may follow for them>",
          "mitigation": "<done and being done>", "safetyMeasures": "<what they can do>"},
          "becameAwareAt": "<UTC>", "scope": "EVERY_REGISTERED_USER"}'
   ```

   `201` with `id`, `affected` and `board.reportDueAt` (awareness + `app.privacy.breach.board-report-hours`, 72).
2. **Without delay: tell the Board** the description, nature, extent, timing, location and likely impact (r.7(2)(a)),
   through **the channel the Board publishes** (not stated in the Gazette copies read for W-237c; owner to fill in
   here). Then record it: `POST $BASE/api/v1/admin/privacy/breach-notices/<id>/board-intimated` → `board.firstIntimatedAt`.
3. **Without delay: email each affected user** the same text from the privacy contact's mailbox (the account email is
   the only mode of communication users register). The text and addresses: `GET $BASE/api/v1/admin/privacy/breach-notices/<id>/report`.
   Record who was emailed: `POST …/<id>/emailed` with `{"emails": [...]}`. A user already recorded keeps their first
   time, so a repeat changes nothing.
4. **Within 72 hours of awareness, or by the later time the Board allows on a written request:** send the Board the
   detailed report, r.7(2)(b)(i) – (vi). Item (vi), the intimations given, is `GET …/<id>/report` (per user: posted,
   read in the account, emailed). If the Board allows longer: `POST …/<id>/board-extension` with
   `{"reportDueAt": "<UTC>", "reference": "<the Board's reference>"}`. When sent: `POST …/<id>/board-report-sent`.

**Read-only check, at any time:** `GET …/<id>/report`: `board.firstIntimatedAt` and `board.reportSentAt` set, the
latter before `board.reportDueAt`; every affected user with `emailedAt`. **Second runs:** each recording call refuses
a repeat (`400`) except `emailed`, which keeps first times. **Way back:** none needed: the calls record what was done
and change no user data. A notice posted in error stays on record; post a correcting notice.

---

---

## Run log — one line per run, per database

Every run before 2026-11-28 15:00 IST is in tag `docs-history-m2` (192 rows) and `docs-history-m1` (406 rows).

| Date | Database | Step | Who | Result |
|---|---|---|---|---|
