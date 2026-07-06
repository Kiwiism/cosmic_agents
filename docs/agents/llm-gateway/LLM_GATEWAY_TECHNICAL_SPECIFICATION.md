# LLM Gateway Technical Specification

Purpose:

```text
Define interfaces, DTOs, tool schemas, permission checks, rate limits, command
queues, result polling, batch control, audit events, and tests for the future
LLM Gateway package.
```

Package name:

```text
agent-llm-gateway
```

This specification is documentation only until Agent reconstruction is stable.

## Package Boundary

LLM Gateway owns:

- tool schema registry.
- tool request validation.
- permission/rate-limit enforcement.
- read-only query dispatch.
- proposal submission.
- command envelope creation.
- batch command handling.
- command status/result summaries.
- audit event emission.

It does not own:

- Agent runtime execution.
- capability validators.
- plan scheduler internals.
- profile storage.
- catalog building.
- economy valuation.
- direct server mutation.
- LLM provider/client implementation.

## Suggested Layout

```text
agent-llm-gateway/
  api/
    LlmGatewayService.java
    LlmToolRegistry.java
    LlmPermissionPolicy.java
    LlmRateLimiter.java
    LlmCommandQueue.java
    LlmResultSummarizer.java
  model/
    LlmToolRequest.java
    LlmToolResponse.java
    LlmToolDescriptor.java
    LlmCommandEnvelope.java
    LlmCommandResult.java
    LlmBatchCommandRequest.java
    LlmBatchCommandResult.java
    LlmPermissionDecision.java
    LlmRateLimitDecision.java
    LlmGatewayContext.java
    LlmAuditRecord.java
  runtime/
    DefaultLlmGatewayService.java
    DefaultLlmToolRegistry.java
    BoundedLlmCommandQueue.java
    LlmIdempotencyStore.java
    LlmCommandStatusStore.java
  tools/
    CatalogLlmTools.java
    AgentStateLlmTools.java
    ProfileLlmTools.java
    PlanLlmTools.java
    EconomyLlmTools.java
    CommandLlmTools.java
  audit/
    LlmGatewayAuditEvent.java
    LlmCommandAuditEvent.java
```

## Core Interface

```java
public interface LlmGatewayService {
    LlmToolResponse invoke(LlmToolRequest request);
    LlmBatchCommandResult submitBatch(LlmBatchCommandRequest request);
    LlmCommandResult getCommandStatus(String commandId);
    List<LlmToolDescriptor> listTools(LlmGatewayContext context);
}
```

## Tool Request

```java
record LlmToolRequest(
    String requestId,
    String issuer,
    String toolName,
    Map<String, Object> payload,
    LlmGatewayContext context,
    long requestedAtMs
) {}
```

## Gateway Context

```java
record LlmGatewayContext(
    String environment,
    String world,
    Integer channel,
    String sessionId,
    String agentId,
    List<String> agentGroupIds,
    Set<String> permissions,
    Map<String, Long> budgets
) {}
```

## Tool Descriptor

```java
record LlmToolDescriptor(
    String name,
    String toolClass,
    String riskClass,
    String inputSchemaId,
    String outputSchemaId,
    boolean supportsDryRun,
    boolean supportsBatch,
    List<String> requiredPermissions,
    List<String> rateLimitKeys
) {}
```

Tool classes:

- `READ_ONLY`
- `PROPOSAL`
- `COMMAND`
- `EMERGENCY`

Risk classes:

- `SAFE_READ`
- `LOW_RISK_GAMEPLAY`
- `MUTATING_GAMEPLAY`
- `ECONOMY`
- `SOCIAL`
- `SCRIPT_SENSITIVE`
- `EMERGENCY`

## Command Envelope

```java
record LlmCommandEnvelope(
    String commandId,
    String issuedBy,
    String agentId,
    String priority,
    Long deadlineMs,
    String idempotencyKey,
    String type,
    Map<String, Object> payload,
    LlmCommandSafety safety,
    long createdAtMs
) {}
```

Safety block:

```java
record LlmCommandSafety(
    boolean allowScriptSensitive,
    int allowMarketSpendMesos,
    boolean allowTradeWithPlayers,
    boolean allowProfileHardPolicyPatch,
    boolean requireDryRun,
    boolean requireManualReview
) {}
```

## Command Result

```java
record LlmCommandResult(
    String commandId,
    String agentId,
    String status,
    String taskId,
    Long estimatedDurationMs,
    List<String> warnings,
    List<String> rejectionReasons,
    Map<String, Object> summary
) {}
```

Statuses:

- `ACCEPTED`
- `REJECTED`
- `QUEUED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `NEEDS_PLANNING`
- `BLOCKED`
- `MANUAL_REVIEW_REQUIRED`

## Permission Pipeline

```text
parse request
validate schema
resolve tool descriptor
check environment policy
check issuer permissions
check agent/group scope
check budget
check manual-review gates
check rate limits
dispatch read/proposal/command path
emit audit
```

Permission decision:

```java
record LlmPermissionDecision(
    boolean allowed,
    boolean manualReviewRequired,
    List<String> grants,
    List<String> denialReasons,
    List<String> warnings
) {}
```

## Rate Limiter

```java
public interface LlmRateLimiter {
    LlmRateLimitDecision check(LlmToolRequest request, LlmToolDescriptor descriptor);
    void record(LlmToolRequest request, LlmToolResponse response);
}
```

Rate-limit keys:

- issuer.
- agent id.
- agent group id.
- tool name.
- risk class.
- market room.
- item id.
- map id.
- world/channel.

## Tool Schemas

Initial read-only tools:

```text
get_agent_state(agentId, detailLevel)
get_agent_batch_status(filter, limit)
get_agent_profile_summary(agentId)
get_agent_plan_state(agentId)
get_agent_perception(agentId, level)
search_catalog(kind, query, filters, limit)
find_drop_source(itemId)
find_shop_selling(itemId)
plan_route(fromMapId, toMapId, profileKey)
find_market_price(itemId, confidenceMin)
```

Initial proposal tools:

```text
propose_plan(agentId, planIntent, constraints)
propose_profile_patch(agentId, patch, reason)
propose_economy_action(agentId, actionIntent, budget)
```

Initial command tools:

```text
submit_command(commandEnvelope)
submit_batch_commands(commands, dryRun)
cancel_command(commandId)
pause_agent(agentId, reason)
resume_agent(agentId, reason)
assign_plan(agentId, planId, options)
navigate_to_point(agentId, mapId, x, y, reason)
```

`navigate_to_point` must create a temporary controlled objective/sidetrack; it
must not directly move the Agent.

## Batch Command Result

```java
record LlmBatchCommandResult(
    String batchId,
    int requestedCount,
    int acceptedCount,
    int rejectedCount,
    int manualReviewCount,
    List<LlmCommandResult> results,
    List<String> batchWarnings
) {}
```

Batch processing must support:

- dry run.
- partial success.
- per-command idempotency.
- aggregate budget checks.
- per-Agent rejection reasons.
- max batch size.
- fail-fast option for high-risk batches.

## Idempotency

Idempotency key shape:

```text
issuer + agentId + commandType + semanticTarget + timeBucket
```

Rules:

- duplicate accepted command returns original command id/status.
- duplicate completed command returns final summary.
- conflicting command with same idempotency key is rejected.

## Audit Events

Emit:

- tool request received.
- schema validation failed.
- permission denied.
- rate limit denied.
- manual review required.
- read-only tool completed.
- proposal submitted.
- command queued.
- command status changed.
- batch accepted/rejected.

Minimum fields:

- request id.
- command id or batch id.
- issuer.
- tool name.
- tool class.
- risk class.
- agent/group scope.
- decision.
- warnings/reasons.
- budgets consumed.
- latency.

## Observability Metrics

Track:

- tool calls by name/class/risk.
- accepted/rejected/manual-review counts.
- command queue depth.
- command latency p50/p95/p99.
- result polling count.
- batch size distribution.
- rate-limit denials.
- schema validation failures.
- budget denials.
- LLM-triggered plan/profile/economy proposals.

## Transport Mapping

The internal contract should be transport-neutral.

Possible transports:

- MCP server tools.
- REST API.
- local in-process command bus.
- Agent Console backend.
- test harness fake gateway.

Transport adapters must map to the same `LlmToolRequest` and
`LlmToolResponse` model.

## Tests

Unit tests:

- unknown tool is rejected.
- malformed payload is rejected by schema.
- read-only tool cannot mutate state.
- command tool requires command permission.
- script-sensitive command triggers manual review.
- duplicate idempotency key returns existing command status.
- batch dry run performs no queue mutation.
- batch partial acceptance returns per-Agent reasons.
- `navigate_to_point` creates objective command, not direct movement.
- rate limiter rejects excessive market scan.
- audit event emitted for accepted and rejected requests.

Integration tests later:

- read-only catalog/perception/profile tools return bounded summaries.
- command submission reaches Plan Runtime queue.
- cancelled command stops pending work.
- batch assignment handles hundreds of Agents without unbounded queue growth.
- LLM Gateway observability appears in Agent Console.

## Implementation Gates

Do not implement live integration until:

- Plan Runtime has command/objective queue boundaries.
- Capability Runtime exposes validators and command results.
- Perception Runtime exposes bounded snapshots.
- Catalog Platform exposes read-only query APIs.
- Profile Platform exposes validated patch preview/apply flow.
- Economy Engine exposes read-only market summaries and proposal validators.
- Event Bus and Observability can record gateway events.
