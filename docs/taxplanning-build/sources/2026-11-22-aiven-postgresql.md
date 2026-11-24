# Aiven PostgreSQL service `wealthlens` — what the console shows (read 2026-11-22, session 118)

For W-337h (D-931, D-932), W-337a and W-400. Every line is from the owner's paste of the service's Overview page that day;
"not shown" means the page did not state it.

## The service
- Project `aaaaaw`, service `wealthlens`, state Running, nodes 1.
- PostgreSQL 18.7; "EOL : 7 November 2030 : OK".
- Plan: Free tier — "1 CPU / 1 GB RAM / 1 GB storage"; connection limit **20**.
- Storage used 12.3% of 1 GB at creation, before any app data (memory used 75% over 60 min).
- The page's upgrade offer: "Scale up your PostgreSQL for only $5/month — Increase your storage, get Basic support, and
  **prevent automatic power-offs during inactivity**" — so the free service powers off when inactive (the idle period is not
  shown).
- Read replicas: "Not available on the Free tier plans". Service integrations: "only available on startup plans and higher".

## Connecting
- Host `wealthlens-aaaaaw.i.aivencloud.com`, port `18042`, database `defaultdb`, user `avnadmin`, SSL mode `require`;
  a CA certificate is offered ("Show").
- Deployment model "Public internet"; IP address allowlist "Open to all" (D-932 C4b keeps it).

## Cloud, backups, maintenance
- Cloud provider DigitalOcean, region `blr`; backup location `do-blr1`.
- Backups: latest and oldest "14 minutes ago", 33 MB stored. **Retention period: not shown** on the Overview.
- Maintenance window: "Tuesdays after 15:21:32 UTC".

## Prod MongoDB size for comparison (W-337h row 0, owner, IntelliJ console)
- `Cluster0`: 30 collections, 1,655 objects, avgObjSize 980 bytes (≈ 1.6 MB data), 73 indexes; dbStats at scale MB rounds
  dataSize, storageSize and indexSize to 1.
