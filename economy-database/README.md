# Economy evidence database

This PostgreSQL database is deliberately separate from Cosmic's character database. Cosmic only
owns `economy_transaction_outbox`; a relay copies committed rows into this database idempotently.

Apply migrations in lexical order. Credentials come from the environment and never from checked-in
YAML. All dashboard views must be rebuildable from `economic_event` and `ledger_posting`; projection
tables are disposable read models, not authority.

For a new local volume, set `ECONOMY_DB_PASSWORD` and run
`docker compose up -d economy-postgres`. The database, user, and host port default to
`cosmic_economy`, `cosmic_economy`, and `5433`; override them with the corresponding
`ECONOMY_DB_*` variables. The compose file contains no credential default.
PostgreSQL executes `migrations/V001` through `V014` in lexical order only while initializing an
empty volume. Existing volumes must be upgraded by the deployment migration runner; deleting a
volume is never an upgrade procedure.

Runtime credentials are read only from `ECONOMY_DB_*` environment variables. Startup verifies the
V014 schema contract before creating a run. The PostgreSQL JDBC pool is independent of Cosmic's
MySQL pool.

The migrations are verified against a clean PostgreSQL data directory during implementation;
all V001-V014 scripts and the application `EconomyDatabaseVerifier` must accept the result.
