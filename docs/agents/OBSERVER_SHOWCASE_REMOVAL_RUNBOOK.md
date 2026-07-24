# Observer Showcase Removal Runbook

## Purpose

The independent Kiwi observer is a demonstration controller, not a required
part of the Agent runtime. It has its own scheduler and private movement state
and does not register Kiwi as a normal Agent or attach it to plans, combat,
TownLife, lifecycle management, or the Agent population.

This runbook records how to stop the showcase safely and how to remove it
permanently without changing normal Agent behavior.

## Temporary shutdown

Run:

```text
!observer stop
```

This cancels the observer's scheduled task, stops its current movement, and
discards its in-memory session. When no observer session has been started, the
F1 notification hook returns without changing gameplay.

Before a permanent removal, stop any active showcase and confirm:

```text
!observer status
```

Expected result:

```text
No observer showcase is active.
```

## Permanent removal boundary

Remove the following observer-owned source files:

```text
src/main/java/server/agents/observer/AgentObserverCommandService.java
src/main/java/server/agents/observer/AgentObserverMovementController.java
src/main/java/server/agents/observer/AgentObserverPolicy.java
src/main/java/server/agents/observer/AgentObserverRuntime.java
src/main/java/server/agents/observer/AgentObserverSession.java
src/main/java/client/command/commands/gm6/ObserverCommand.java
src/test/java/server/agents/observer/AgentObserverPolicyTest.java
```

Then remove the three integration surfaces:

1. From `src/main/java/client/command/CommandsExecutor.java`, remove the
   `ObserverCommand` import and the `addCommand("observer", 6, ...)`
   registration.
2. From
   `src/main/java/net/server/channel/handlers/FaceExpressionHandler.java`,
   remove the `AgentObserverRuntime` import and the `emote == 1` call to
   `AgentObserverRuntime.signalF1(...)`. Keep normal face-expression handling.
3. From `agent-engine.yaml`, remove the contiguous
   `Independent Kiwi observer showcase` configuration section and all keys
   beginning with `server.agents.observer.`.

No database migration, character migration, plan conversion, Agent registry
cleanup, or TownLife cleanup is required. Observer state is in memory only.

## Verification after removal

Run:

```powershell
rg -n "AgentObserver|!observer|server\.agents\.observer" . `
  --glob "!target/**" --glob "!logs/**" --glob "!*.log"
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q "-Dtest=server.agents.architecture.AgentArchitectureBoundaryTest" test
```

The reference scan should return no observer implementation or configuration
references. Compilation and the architecture boundary test must pass.

Also smoke-test these unaffected behaviors:

- pressing F1 still broadcasts the normal face expression;
- normal Agent commands still register and execute;
- Maple Island plans still reach Southperry;
- the Shanks administration dialogue or command can still assign the
  Southperry-to-Lith-Harbor handoff directly;
- TownLife still starts through its normal plan or arrival path.

## Rollback

The original observer implementation was introduced by commit
`974e6e5549`. While that commit remains isolated and has not been modified by
later observer work, reverting it is the shortest removal path:

```powershell
git revert 974e6e5549
```

Use the explicit removal steps above instead if later commits have changed the
observer or its integration points. This avoids reverting unrelated work that
may have accumulated around those files.

