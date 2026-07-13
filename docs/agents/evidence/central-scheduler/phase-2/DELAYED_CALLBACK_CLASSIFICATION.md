# Phase 2 Delayed Callback Classification

| Callback family | Phase 2 ownership |
| --- | --- |
| dialogue, replies, movement, combat, supplies, inventory, equipment, trade, shop, build | entry-scoped generation check followed by mailbox delivery |
| airshow frames and trail cleanup | entry-scoped generation check followed by mailbox delivery |
| Amherst showcase continuation | entry-scoped generation check followed by mailbox delivery |
| gained-item offer notification | entry-scoped generation check followed by mailbox delivery |
| scroll-event initial delay | global read-only event fanout; selected reactions then use entry-scoped mailbox delivery |
| post-disconnect relog delay | generation-independent critical lifecycle re-admission |
| navigation overlay auto-clear | presentation cleanup owned by the viewer, not an Agent session |
| population cadence/fast-start | global maintenance scheduler; live session changes enter lifecycle facade |
| LLM follow-up without `AgentRuntimeEntry` | bounded LLM executor continuation; live runtime entries use scoped delivery |
| central dispatcher loop and immediate wake | scheduler infrastructure, not Agent-session callback mutation |

`TimerManager` is reachable only through `CosmicSchedulerGateway`; Agent code
does not import it directly. Unused unscoped capability timer overloads were
removed so new session mutation must carry `AgentRuntimeEntry` and generation.
