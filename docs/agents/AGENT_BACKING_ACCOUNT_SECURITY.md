# Agent Backing Account Security

Agent backing-account credentials must remain unique and protected like any
other account credentials.

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

Administrators should rotate hashes for old Agent accounts that used the
historical shared password after taking a database backup. Generate a unique
BCrypt hash per affected account and update only accounts selected through this
relationship:

```sql
SELECT DISTINCT accounts.id, accounts.name
FROM accounts
JOIN characters ON characters.accountid = accounts.id
JOIN bot_owners ON bot_owners.bot_char_id = characters.id;
```

Do not assign a known reusable password. Test Agent spawn after rotation; Agent
headless loading does not authenticate with the backing-account password.
