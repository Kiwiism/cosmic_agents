# Economy evidence database

This PostgreSQL database is deliberately separate from Cosmic's character database. Cosmic only
owns `economy_transaction_outbox`; a relay copies committed rows into this database idempotently.

Apply migrations in lexical order. Credentials come from the environment and never from checked-in
YAML. All dashboard views must be rebuildable from `economic_event` and `ledger_posting`; projection
tables are disposable read models, not authority.

For local development, run `docker compose up -d`, then apply `migrations/V001__initial.sql` with the
PostgreSQL migration runner chosen for deployment. The Compose credentials are local-only defaults.
