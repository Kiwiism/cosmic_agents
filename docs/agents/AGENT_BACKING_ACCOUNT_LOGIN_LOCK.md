# Agent Backing Account Login Lock

New Agent backing accounts are assigned an unreported random password and then
locked against interactive login before their backing character is created.
Cosmic uses its existing `accounts.banned` field with the reason
`Agent-only backing account`; headless Agent loading does not authenticate
through the player login handler and remains unchanged. No schema change is
required.

Provisioning fails closed if the lock cannot be persisted. The account can remain
empty after such a database failure and must be reviewed by an administrator
before retrying the same name.

For historical shared-password Agent accounts, back up the database, identify
the exact backing accounts, rotate each password to a unique random BCrypt hash,
and apply the same login lock:

```sql
UPDATE accounts
SET banned = 1, banreason = 'Agent-only backing account'
WHERE id = ?;
```

Use an explicit reviewed account ID. Do not bulk-update accounts by character
name pattern. To roll back the login lock for an account that is permanently
retired from Agent use, first ensure no Agent session can load its characters,
then clear the ban through the normal administrative unban process.
