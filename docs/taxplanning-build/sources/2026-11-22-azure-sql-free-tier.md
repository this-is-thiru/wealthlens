# Azure SQL Database free tier — what Microsoft's page says (read 2026-11-22, session 118)

For W-337h and W-337a (D-933). Page: learn.microsoft.com/en-us/azure/azure-sql/database/free-tier?view=azuresql&tabs=azure-portal
("Build and test apps with the Azure SQL Database free tier"; ms.date 2026-10-30, updated 2026-11-18). Link given by the owner;
read through a fetch agent. Quotes are the page's; "not stated" means read and absent.

## Allowance
- "For each database, you get 100,000 vCore seconds, 32 GB of data, and 32 GB of backup storage free per month for the
  lifetime of your subscription."
- "Each Azure subscription allows you to create up to 10 free tier General Purpose databases."
- Templates: `maxSizeBytes` 34359738368 (32 GiB), `requestedBackupStorageRedundancy` "Local", examples at capacity 2 (Gen5).
- Min / max vCores and how vCore seconds are counted: **not stated**. Open connections "can prevent idle auto-pause and
  continue to consume vCore seconds."

## Prerequisites
- "You need an Azure account and subscription. Unlike the previous Azure free account offer, the free tier is available with
  most Azure subscription types. Microsoft Azure for Students Starter isn't supported."
- "You can't restore or convert an existing database to the free tier." Credit cards: **not stated** on this page.

## When the allowance runs out
- "Auto-pause the database until next month": no charge; inaccessible until the allowance resets at the start of the next
  calendar month.
- "Continue using database for additional charges": stays online, overage at General Purpose serverless rates. "You can't
  switch back to the auto-pause option."
- Created "with default settings, including the option to pause the database when the free limits are reached."

## Behaviour and limits
- "The free tier databases automatically pause after 15 minutes of inactivity. The auto-pause delay is fixed and can't be
  changed."
- "The free tier has no service-level agreement (SLA), and feature and configuration restrictions apply."
- Region: "If you use Advanced configuration, the region selected for the first free tier database applies to all free tier
  databases in that subscription and can't be changed."
- Point-in-time restore retention, elastic pools, failover groups, geo-replication, collation: **not stated** here (the page
  points to the free tier FAQ and a feature comparison page). The 7-day PITR figure in D-933 came from a search summary.

## Portal steps (as the page gives them)
1. aka.ms/azuresqlhub → "Create a database" → **Start free**.
2. Basics: subscription and resource group preselected (changeable); database name generated or typed; server preselected or
   new; Cost summary "Estimated Cost/Month" zero.
3. Optional Additional settings → Use existing data → Sample (we leave it empty).
4. **Review + create** → **Create**.
