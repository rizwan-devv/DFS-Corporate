# MySQL setup guide (DFS Corporate)

Default local DB is now **MySQL** (`dfs_corporate`). Schema is owned by **Flyway** (`db/migration/V1` … `V9`). You do **not** hand-write the full schema in Workbench — create an empty database, then let Flyway build tables.

Local defaults in `application.yml`: user `dfs`, password `change-me`, DB `dfs_corporate`.

## 1. Install / open MySQL

- MySQL Server 8.x + MySQL Workbench (or CLI)
- Note root password

## 2. Create empty database + user (Workbench or CLI)

```sql
CREATE DATABASE dfs_corporate
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'dfs'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON dfs_corporate.* TO 'dfs'@'localhost';
FLUSH PRIVILEGES;
```

Remote app host? Use `'dfs'@'%'` (tighten later).

## 3. Backend already has the driver

`pom.xml` includes `mysql-connector-j` and `flyway-mysql`. Profile file: `application-mysql.yml`.

## 4. Connect the app

From `backend/`:

```bash
# Windows PowerShell example
$env:MYSQL_HOST="localhost"
$env:MYSQL_PORT="3306"
$env:MYSQL_DATABASE="dfs_corporate"
$env:MYSQL_USER="dfs"
$env:MYSQL_PASSWORD="change-me"

mvn spring-boot:run "-Dspring-boot.run.profiles=mysql"
```

Or:

```bash
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=mysql --MYSQL_PASSWORD=change-me"
```

On first start Flyway creates all tables (`parties`, `accounts`, `partner_app_users`, …) including **account provision** columns from `V8`.

## 5. Verify in Workbench

```sql
USE dfs_corporate;
SHOW TABLES;
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

You should see migrations `1` … `8` as success.

Seed admin (if `DataInitializer` ran): `admin@dfscorporate.local` / `Admin@123`.

## 6. Switch back to H2

Omit the `mysql` profile (default `application.yml`). H2 and MySQL data are separate.

## 7. Production tips

- Set strong `MYSQL_*` and `JWT_SECRET` via env
- Prefer SSL (`useSSL=true`) on managed MySQL
- Backups: dump `dfs_corporate` regularly
- Never set `spring.jpa.hibernate.ddl-auto=update` in prod — keep `validate` + Flyway

## 8. Common errors

| Error | Fix |
|--------|-----|
| Access denied | User/password/host grants |
| Unknown database | Run `CREATE DATABASE` |
| Public Key Retrieval | Keep `allowPublicKeyRetrieval=true` for local MySQL 8 |
| Flyway checksum mismatch | Don’t edit old `V*.sql` after apply; add a new `V9__…` |
| Dialect / Flyway MySQL | Ensure `flyway-mysql` dependency is present (already added) |

## What you create vs what Flyway creates

| You create | Flyway creates |
|------------|----------------|
| Empty schema `dfs_corporate` | All tables, indexes, seed doc matrix |
| DB user + grants | Column adds (`V8` account provision, etc.) |

When you later integrate the **DFS Account API**, no MySQL change is required for the stub fields — they already exist on `parties`.
