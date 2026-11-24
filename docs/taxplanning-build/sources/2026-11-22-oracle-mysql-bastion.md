# Oracle Cloud: MySQL HeatWave Always Free and Bastion — what the docs say (read 2026-11-22, session 118)

> **Not used (D-931, 2026-11-22):** Oracle dropped for PostgreSQL on Aiven. Kept as the record.

For W-337h (D-929). Every line is from a page read that day (HTTP 200); "not stated" means read and absent.

## MySQL versions
- docs.oracle.com/en-us/iaas/mysql-database/doc/mysql-server-versions.html: "All Always Free DB systems are created with the latest
  version. They are also upgraded to the latest version in each maintenance cycle." Tables run to 8.4.13 LTS, 9.7.4 LTS and
  26.10.0 Innovation (October 20, 2026). Which of them "latest" is for Always Free: not stated — read `SELECT VERSION()`.
- docs.oracle.com/iaas/releasenotes/mysql-database/heatwave-970-849-8046.htm (April 29, 2026): "New DB systems are now based on
  version 8.4.9, by default."

## Always Free DB system (…/mysql-database/doc/creating-always-free-db-system.html)
- On the **DB systems** list page, **Create DB system** → **Always Free**; **Create in compartment**, **Name**, **Description**;
  **Username**, **Password**, **Confirm password** ("The Administrator has a specific set of privileges that differs from the
  `root` user"); **Standalone** only; **Virtual cloud network**, **Subnet** ("Select the private subnet of your VCN"); shape
  MySQL.Free, HeatWave.Free; storage 50 GiB; backups fixed: enabled, retention 1 day, PITR disabled, window by Oracle, soft
  delete disabled; **Create**. No hostname or port field.

## VCN (…/mysql-database/doc/creating-virtual-cloud-network.html)
- Networking → Virtual cloud networks → **Start VCN wizard** → **Create VCN with internet connectivity** (VCN, public and private
  regional subnets, internet, service and NAT gateways); fields VCN name, Compartment, VCN IPv4 CIDR block, public and private
  subnet CIDR (examples 10.0.0.0/24, 10.0.1.0/24); "It is recommended to accept the default values".

## Ingress rule (…/mysql-database/doc/adding-ingress-rules-compute-instance-bastion-session-or-vpn-connection.html)
- Security list of the **private** subnet → **Add ingress rules**: Stateless not selected; Source type CIDR; Source CIDR "the CIDR
  of the _public_ subnet" (narrower allowed); IP protocol TCP; source port blank; destination "3306" (X protocol 33060).

## Admin privileges (…/mysql-database/doc/default-mysql-privileges.html)
- The administrator holds global `CREATE`, `CREATE USER`, `CREATE ROLE`, `SHOW DATABASES` … `WITH GRANT OPTION`.

## Bastion
- create-bastion.htm (…/Content/Bastion/Tasks/): **Bastion name** (alphanumeric); target VCN; target subnet; optional FQDN /
  SOCKS5; **CIDR block allowlist** (at most 20); Advanced → **Maximum session time-to-live** 30 – 180 minutes. "The VCN needs a
  service gateway with a route rule."
- create-session-port-forwarding.htm: **SSH port forwarding session**, target **IP Address** and port (MySQL 3306), **Add SSH key**
  (public key; connect with its private key), session TTL 30 – 180 min.
- connect-port-forwarding.htm: Actions → **Copy SSH command**, replace `<privateKey>` and `<localPort>`; then connect MySQL to
  127.0.0.1 on the local port. The host name pattern is not stated.
- bastionoverview.htm: "Bastions have service limits, but do not incur costs." Always Free page: "Bastion is free for both free
  and paid accounts." servicelimits.htm: "Maximum of 5 bastions per region", "Maximum of 20 sessions per bastion".

## Not read / open
- Whether Oracle's free-tier terms allow one person several Always Free accounts (staging in a second account): not read.
- MySQL transactions and foreign keys across schemas on one server (for the later schema-per-module row): to prove, not read.
