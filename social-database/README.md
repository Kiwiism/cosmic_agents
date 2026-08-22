# Social memory database

This PostgreSQL database is deliberately separate from Cosmic's character
database and the economy evidence database. Social dialogue remains optional:
if this database is disabled or unavailable, Agents retain bounded in-memory
conversation state and all operational behaviour continues normally.

For a new local volume, set credentials and start the service:

```powershell
$env:SOCIAL_DB_ENABLED = "true"
$env:SOCIAL_DB_USER = "cosmic_social"
$env:SOCIAL_DB_PASSWORD = "choose-a-local-password"
docker compose -f social-database/compose.yaml up -d
```

Defaults are database `cosmic_social`, host `127.0.0.1`, and host port `5434`.
Override them with `SOCIAL_DB_NAME`, `SOCIAL_DB_HOST`, and `SOCIAL_DB_PORT`.
Credentials are never stored in repository configuration.

PostgreSQL runs migrations only when initializing an empty volume. Existing
volumes must be upgraded by applying new `migrations/V*.sql` files in lexical
order; deleting a database volume is not an upgrade procedure.

Durable relationship summaries do not contain raw chat. Recent conversation
turns are retained for seven days and are deleted by background maintenance.
