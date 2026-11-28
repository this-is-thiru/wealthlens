# WealthLens — deploy runbook

**What a database still needs, and how to check it.** One runbook for every module (W-176, D-379; CLAUDE.md §6). Rewritten by
W-430 (D-1041, D-1042) and cut by W-435 (D-1051) to what the next release needs: the environment, the pending steps and the
start checks. The steps for switched-off features and the recurring operations are in tag `docs-history-m5`
(`git show docs-history-m5:docs/taxplanning-build/post-deployment-runbook.md`, its §3 and §4). **Everything earlier** — the MongoDB era, the move to PostgreSQL, every retired one-time step and the
192 runs logged until 2026-11-28 — is in git tag `docs-history-m2`
(`git show docs-history-m2:docs/taxplanning-build/post-deployment-runbook.md`); older still, `docs-history-m1`. Closed plans and
D-rows cite that text's section numbers.

Each entry is keyed to the work item and decision that introduced it, and says when it runs and who runs it; the exact command,
with a read-only check before and after; whether a second run is safe; and the way back.

**The rule (R15, D-278, D-279, D-379, CLAUDE.md §6).** A change that needs a seed, a data correction, a startup outcome to check, a
flag or a precheck adds its entry here **in its own commit**, and its plan names it under "Post-deployment actions". A session
never runs any of it against a real database: it writes the command, the owner runs it, and the result goes into the run log.
**Every operation is idempotent (D-282)** — a second run, including after one that failed part-way, completes the work or changes
nothing — and **a one-time operation is removed after use** (a `BACKLOG.md` row, YouTrack parent **WL-28**, D-286).

**Every file a step saves is personal data, and is deleted (W-237f C3a):** a dump or a CSV taken as a step's way back is the
owner's own copy, which Act s.8(7)(a) reaches as it reaches the database. Delete it once its step's result is in the run log
and checked, or once the longest backup retention (§4.8 in tag `docs-history-m5`) has passed, whichever is first; the run-log line says so.

`$BASE` is the app's base URL, `$ADMIN_TOKEN` a `SUPER_USER` JWT and `$TOKEN` any user's JWT. Everything runs locally; nothing is
hosted. Dev, staging and prod run `m1.9` (W-428b, D-1038).

| § | What | When |
|---|---|---|
| 0 | Before a start: the environment, how to run SQL | every start |
| 1 | **Pending** | once per database, then the entry goes |
| 2 | What every start's log must show | every start |

---

## 0. Before a start
### 0.1 Environment · owner · **every start**

| # | Check | Why |
|---|---|---|
| 0.1.1 | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (W-427), `JWT_SECURITY_KEY`, `PRIVACY_CONTACT` are set | The app does not start without the JWT key, deliberately: a defaulted signing key is a forged-token vulnerability. Nor without the privacy contact (W-235, W-237a): who answers questions about the processing of personal data, returned in every erasure receipt (DPDP Rules, 2025 r.9). A build from W-428c needs no tax keys (D-1040); `m1.9` and earlier also need `TAX_DATA_KEY`, `TAX_DIGEST_KEY` |
| 0.1.2 | A `SUPER_USER` account exists | Seeding, publishing rate cards, the AMC cycle and every admin correction are `SUPER_USER`-only. Registration makes only `USER` accounts (W-255), so grant the role in that database: register through `POST /api/v1/public/auth/register`, then `PSQL -v email='<owner email>' <<< "UPDATE wealthlens.user_details SET roles = 'SUPER_USER' WHERE email = :'email' AND roles = 'USER';"` (§0.2), then sign in again (a token carries the role read at sign-in). A second run updates nothing. Way back: the same update with the two roles swapped |

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

### 1.3 W-440b (D-1055, D-1056) — the instrument registry: V15 at the start, then the owner's aliases · owner · **every database · the start once; the aliases before the first broker import (W-440c), again for each new instrument**

**Why.** The release carrying W-440b applies `portfolio/V15__instrument_aliases.sql` at its first start: a new, empty table
`instrument_aliases`, nothing else changed. A broker import (W-440c) finds each row's instrument only through it, and refuses a
row whose ISIN, scrip code or symbol it lacks. Nothing is seeded (C15a): the list is your holdings, so it is posted from your own
files, never kept in the repo.

**At the start (automatic).** §2.3: the log shows `Successfully applied 1 migration … now at version v15`. Read-only check after
(`PSQL`, §0.2): `SELECT count(*) FROM wealthlens.instrument_aliases;` → `0`.

**Register (any time after the start; `$BASE`, `$ADMIN_TOKEN` as above).** One file `aliases.json` in a folder of your own, each
value copied from your broker file exactly as it is written (Fyers' `="…"` removed), the code the one your trades are stored under.
An ISIN names no broker; `SCRIP_CODE` and `BROKER_SYMBOL` name one of `ZERODHA`, `FYERS`, `UPSTOX`:

```zsh
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/portfolio/instrument-aliases?size=200"
curl -sS -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' --data @aliases.json "$BASE/api/v1/admin/portfolio/instrument-aliases"
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/portfolio/instrument-aliases?size=200"
```

`aliases.json` is `{"aliases": [{"kind": "ISIN", "brokerName": null, "value": "<isin>", "stockCode": "<code>"}, …]}`. The first and last
calls only read: `page.totalElements` after = before + the POST's `created` count. **A second run is safe:** an alias already
naming the same code is listed under `skipped` and nothing is written. A refusal (400) writes nothing at all and names the value:
a value already naming another code, two codes for one value in the file, an ISIN not of its shape, a broker missing or extra, a
space around a value. Delete `aliases.json` once the run is logged (it lists your holdings).

**Way back.** Going to a release without W-440b leaves the table unused (Flyway does not undo V15; the older app ignores it).
To remove aliases posted by mistake, back up first, then delete by the time of the run (`PSQL`, §0.2):

```zsh
PSQL <<< "\copy (SELECT * FROM wealthlens.instrument_aliases) TO '/w/instrument-aliases-backup.csv' CSV HEADER"
PSQL -v since='<yyyy-mm-dd hh:mm:ss UTC of the run>' <<< "DELETE FROM wealthlens.instrument_aliases WHERE created_at >= :'since';"
```

---

## 2. On every start — automatic; confirm in the log

| # | Item / decision | What happens at startup | Check | If it fails |
|---|---|---|---|---|
| 2.1 | **W-079a** (D-210), **W-088s** (D-268) | `TaxEngineConfig` compiles every year in `rulepacks/shipped-years.json` (**2025-26, 2026-27**) and verifies each against its lock (`rulepacks/<year>.lock.json`) | The app starts. There is no database step: the rule packs ship in the JAR | **The boot stops** with a lock mismatch. Nothing reaches a database. Deploy the previous build; never edit a lock in place |
| 2.2 | Charge engine (ADR-27) | `ChargeReadinessAuditor` counts the rate cards when the engine is pricing | Grep the log for `NO RATE CARDS`. Expected on a database never seeded (prod today) until the seed (`POST /api/v1/admin/charges/seed`; the full steps are §1B of `docs-history-m2`) runs; `Charges engine ready: N rate card(s)` otherwise | Seed before anyone trades (§1B of `docs-history-m2`) |
| 2.3 | Flyway (W-337b, D-963) | Applies any new script in `db/migration/<module>/` to schema `wealthlens`, then Hibernate checks the schema | The log names the version, `Current version of schema "wealthlens": <n>` or `Successfully applied … now at version v<n>` | **The start stops.** Read the error; a new script's precheck in §1 says what to look for |

---

## Run log — one line per run, per database

Every run before 2026-11-28 15:00 IST is in tag `docs-history-m2` (192 rows) and `docs-history-m1` (406 rows).

| Date | Database | Step | Who | Result |
|---|---|---|---|---|
