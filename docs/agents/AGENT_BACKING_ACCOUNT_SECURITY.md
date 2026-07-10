# Agent Backing Account Security

Agent backing accounts are implementation details for headless characters. They
must not be used as player accounts.

## Provisioning policy

- Spawning an existing authorized Agent is unchanged.
- Creating a missing Agent requires GM level 6 by default.
- A controller may provision three Agents per ten-minute window by default.
- A controller may own 25 registered Agents by default.
- An existing account with the requested Agent name is never reused.
- New account passwords are random, hashed, and never displayed.

The defaults can be changed with JVM system properties:

```text
agents.provisioning.minimumGmLevel
agents.provisioning.maxPerWindow
agents.provisioning.windowMs
agents.provisioning.maxPerController
```

## Existing shared-password accounts

Interactive login is denied whenever an account contains a character registered
in `bot_owners`, so old accounts using the historical shared password are blocked
without a schema migration. Administrators should still rotate those hashes as
defense in depth after taking a database backup. Generate a unique BCrypt hash
per affected account and update only accounts selected through this relationship:

```sql
SELECT DISTINCT accounts.id, accounts.name
FROM accounts
JOIN characters ON characters.accountid = accounts.id
JOIN bot_owners ON bot_owners.bot_char_id = characters.id;
```

Do not assign a known reusable password. Test Agent spawn after rotation; Agent
headless loading does not authenticate with the backing-account password.
