# Privacy, Retention, and AGPL Deployment Baseline

This is the operational baseline for a publicly reachable Cosmic Agents deployment. It is an engineering checklist, not legal advice. The deployment owner remains responsible for applicable privacy, consumer-protection, employment, and game-service rules in every jurisdiction served.

## Data inventory and purpose

| Data | Purpose | Access | Default retention target |
|---|---|---|---|
| Account identity, password hash, PIN/PIC state | Authentication and account recovery | Database administrators only | Account lifetime, then deletion/anonymization according to the published account policy |
| Source IP, session and login-attempt evidence | Abuse prevention and incident response | Security operators only | 30 days normally; up to 90 days when attached to an active incident |
| Chat and command logs | Moderation, debugging and player support | Authorized moderators and operators | 30 days normally |
| Structured security events and packet evidence | Detecting packet edits, automation abuse and fraud | Security operators only | 90 days normally; longer only for a documented incident |
| Economy journal, trade, merchant, storage, MTS and Duey records | Reconciliation, fraud investigation and rollback evidence | Economy/security operators | 180 days or the period required for dispute handling |
| Character, inventory, quest and progression state | Providing the game service | Runtime and authorized support staff | Character/account lifetime |
| Agent decisions, plan checkpoints and journey diagnostics | Reliability, tuning and reproducibility | Agent-engine operators | 30 days for verbose traces; durable checkpoints while the Agent exists |
| Backups | Disaster recovery | Database administrators only | A documented rolling schedule, encrypted and tested for restoration |

Do not collect data merely because it may become useful. New telemetry must identify its purpose, owner, access group, retention period, deletion path and whether values can be aggregated or pseudonymized.

## Required deployment controls

1. Publish a privacy notice describing collected data, purposes, retention, operator contact and deletion/access request procedures.
2. Use the production deployment profile, dedicated non-root database credentials and authenticated local administration bridge.
3. Bind MySQL and internal administration ports to private or loopback interfaces. Never expose them directly to the public internet.
4. Encrypt public traffic at the network edge and restrict operator access through a VPN, bastion or equivalent private control plane.
5. Restrict database, log, backup and runtime-checkpoint access by role. Do not share operator accounts.
6. Keep secrets outside Git and logs. Rotate any credential that appears in a repository, screenshot, crash dump or support transcript.
7. Apply retention deletion jobs and record their success. An unimplemented retention target is not a retention policy.
8. Document incident response: containment, evidence preservation, credential rotation, user notification and post-incident review.
9. Test backup restoration and Agent checkpoint reconciliation before depending on them for recovery.
10. Review third-party processors and hosted services before sending them player, chat, security or Agent data.

## AGPL network deployment checklist

Cosmic Agents is distributed under the GNU Affero General Public License version 3. For a modified version offered over a network:

1. Preserve copyright and license notices.
2. Provide every network user a prominent way to obtain the complete corresponding source for the exact deployed version, including local modifications and the scripts needed to build and install it.
3. Keep that source offer reachable for as long as the modified service is offered.
4. Include the AGPL license and clearly identify modifications and their dates.
5. Ensure deployment automation does not depend on undisclosed proprietary glue required to build or run the corresponding source.
6. Audit bundled assets and third-party dependencies separately; the AGPL license for server code does not grant rights to proprietary game assets, trademarks or services.
7. Re-run this review whenever distribution, hosting, asset sources or external integrations change.

## Release gate

A production release is not approved until an owner records:

- deployed commit and source-offer URL;
- database/admin network exposure check;
- secret scan and dependency/code-scanning results;
- backup restore date;
- retention job status;
- operator access review;
- open high/critical security findings and explicit risk acceptance, if any.

