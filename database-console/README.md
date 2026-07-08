# Cosmic Database Console

The Console combines the existing `cosmic` game database with WZ XML catalog data and a separate
`cosmic_database_console` schema for Database Console authentication, audit history, drafts, catalog indexes, and queued
server operations.

## Services

- `web`: Next.js database interface.
- `api`: Spring Boot API, Liquibase migrations, catalog importer, and game database access.
- Cosmic live bridge: private endpoints hosted by the game server under `/internal/admin`.

## Local configuration

Copy `.env.example` to `.env` and set local credentials. Never commit `.env`.

```powershell
Copy-Item database-console\.env.example database-console\.env
```

Set both database passwords in `database-console/.env`, then launch both services:

```powershell
.\database-console\start-database-console.cmd
```

Stop them with `.\database-console\stop-database-console.cmd`.

The launcher uses a system Node.js 22 installation when available and otherwise uses the Codex
bundled Node runtime already installed on this workstation. Docker deployments include both runtimes.

The live Cosmic bridge starts by default on loopback (`127.0.0.1`) so local
console edits can reach online character memory without disconnecting the
character. The bridge still requires a bearer token. Set a local token before
starting both the game server and the Console:

```powershell
$env:COSMIC_BRIDGE_TOKEN = "change-this-local-token"
```

Use the same `COSMIC_BRIDGE_TOKEN` value in `database-console/.env`. If the
bridge is not enabled, the Console still works for offline database edits and
shows the live bridge as offline.

To disable the bridge for a local server run, set:

```powershell
$env:COSMIC_DATABASE_CONSOLE_BRIDGE_ENABLED = "false"
```

Current live-write scope is intentionally narrow: online appearance changes
and equipped/cash-equipped slot add or modify operations are routed through the
bridge. Normal bag inventory, storage, deletes, and IGN rename remain
offline-only until their live memory paths are implemented.

On first startup, the JDBC connection creates `cosmic_database_console` when it does not exist,
then Liquibase creates every required table and applies all migrations. The configured MySQL
user must have `CREATE` permission for databases and tables. Existing schemas and records are
preserved; subsequent startups apply only migrations that have not already run.

## WZ catalog sources

The default import root is `<project>\wz`. The importer currently reads:

- `String.wz` for names and descriptions
- `Character.wz` for equipment requirements, average stats, and server roll ranges
- `Item.wz` for item metadata and effects
- `Mob.wz` for monster levels, HP, and properties
- `Skill.wz` for skill metadata and every available skill level
- `Map.wz` for map life entries, monster spawn counts, NPC placements, and map metadata
- `String.wz/Map.img.xml` for map names, descriptions, streets, and region grouping

Every library detail drawer shows its relative XML source and catalog SQL lookup. A completed catalog
refresh also displays the absolute import root used by the API.

The map catalog powers region filters, regional monster browsing, monster spawn-map links, NPC and
shop locations, and gachapon placement information. Unknown or custom maps remain visible under the
`Unclassified` region rather than being silently omitted.

The default local configuration expects the game schema on MySQL at `localhost:3306` and
automatically creates the Console schema when needed:

- `cosmic`: existing game data
- `cosmic_database_console`: Console-owned data

Run the API:

```powershell
cd database-console\api
..\..\mvnw.cmd spring-boot:run
```

Run the web app with Node 22+:

```powershell
cd database-console\web
npm install
npm run dev
```

Open `http://localhost:3000`. The API defaults to `http://localhost:8081`.

## First login

When `console_users` is empty, `POST /api/setup` creates the first Owner account. Setup closes
automatically after the first account is created.

## Safety model

- Content and confirmed-offline records use validated MySQL transactions.
- Live state is changed only through the Cosmic bridge.
- Drop and shop changes remain saved while Cosmic is offline; cache reloads are queued.
- Item grants, destructive character changes, and shutdown actions are never silently replayed.
