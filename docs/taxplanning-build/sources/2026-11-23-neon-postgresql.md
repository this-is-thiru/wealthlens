# Neon Postgres project `wealthlens` — console paste and Neon's own limits (read 2026-11-23)

For W-400 and the open comparison against Azure SQL (D-933, D-934; `sources/2026-11-22-azure-sql-free-tier.md`). Console facts
are from the owner's paste of the project's Branch overview page that day ("not shown" means the page did not state it);
plan-limit facts are from Neon's own docs, read by a fetch agent the same day.

## The project (owner's paste)
- Project `wealthlens` (id `long-rain-88433262`), branch `production` (id `br-long-pond-a1rl5gr4`), created
  2026-11-23 08:53:02, expires "Never".
- Region: AWS Asia Pacific 1 (Singapore, `ap-southeast-1`).
- Plan: Free plan. Postgres version: 18.
- Default compute: 0.25 ↔ 2 CU (autoscaling range).
- History retention: 6 hours.
- Usage since 2026-11-01: Compute 0 CU-hrs, Storage 0 kB, History 0 kB, Network transfer 0 kB, Object storage 0 kB — nothing
  written yet.
- Other services the account's page advertises (not used here): Better Auth, Object storage, Functions, AI Gateway, Realtime.

## Free plan limits (`neon.com/docs/introduction/plans`, "Last updated: 2026-11-18"; `/introduction/limits` returns the
same document, so it is not a second source)
- Storage: "limited to 1 GB of Postgres storage per project, and to 20 GB in total across all your projects" — continuous,
  not reset monthly.
- Compute: "100 CU-hours/project/month (enough to run a 0.25 CU compute in a project for 400 hours/month)". A CU "allocates
  approximately 4 GB of RAM, along with associated CPU and local SSD resources" (0.25 CU = 1 GB RAM … 2 CU = 8 GB RAM, the
  Free autoscaling ceiling); no vCPU count stated.
- Projects: 100. Branches: "10 branches/project", counting the root; "Extra branches are not available on the Free plan.
  Delete branches or upgrade if you need more." Read replicas: 3 per project.
- Idle: "5 min inactivity; cannot disable."
- When a limit is hit: CU-hours exhausted → compute suspended until the next billing period or upgrade, connections then fail
  (SQLSTATE 53000); storage cap exceeded → writes (insert/update/delete) fail until space is freed or upgraded, but compute
  stays up and reads/`pg_dump` still work; network transfer (5 GB/project/month, shared with Object Storage and Functions)
  exhausted → same suspend-until-next-period. "None of these limits delete your data."
- History / point-in-time restore: "Up to 6 hours, limited to 1 GB of change history per project" — hours, not days.
- Branching mechanics: "Child branches share data with the root until changes are made" — a new branch "initially consumes
  no storage", then "a delta is recorded from the point of branch creation", billed on the minimum of the change size or the
  logical data size. Whether that delta counts against the 1 GB per-project cap is **not stated**.
- Backups: 1 manual snapshot, no scheduled backups on Free. Monitoring retention 1 day. Support: Community. No uptime SLA.
- Credit card at sign-up, which Postgres version(s) are offered, and the region list: **not stated** on either page read (the
  owner's own project is on version 18, Singapore — from the console paste above, not the docs).

## Azure SQL free tier, for comparison (already recorded; D-933, D-934; `plans/W-337h.md` rows 1 – 3 already run against a
live server per `CHECKPOINT.md`)
- Engine SQL Server, not Postgres. 100,000 vCore-seconds / 32 GB data / 32 GB backup per database per month, for the
  subscription's lifetime, up to 10 free databases; auto-pause after 15 min idle (fixed), or — if "Continue using database"
  is chosen instead — stays up at General Purpose rates, and "can't switch back" to auto-pause; no SLA; region fixed per
  subscription once picked (owner chose Central India).
- D-934 (approved, in progress): the owner's current public IP only; collation `Latin1_General_100_CS_AS_SC_UTF8`; one admin
  SQL login `wealthlens_admin`.
