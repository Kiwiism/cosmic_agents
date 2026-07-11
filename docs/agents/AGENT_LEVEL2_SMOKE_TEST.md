# Agent Level 2 Smoke Test

`AgentLevel2SmokeMain` validates the real server and headless Agent runtime
without a MapleStory client. It is intentionally outside the normal JUnit
suite because it starts network listeners and uses the configured MySQL
database.

The runner creates a uniquely named temporary Agent-only account and backing
character, then verifies:

- server startup and database migrations;
- offline Agent loading into the requested live map;
- runtime registration and scheduled ticks;
- same-map navigation and visible position mutation;
- combat through real monster EXP gain;
- pickup of a real eligible map drop;
- runtime removal and headless disconnect;
- map, EXP, and inventory persistence after reload;
- deletion of the temporary character and account.

The run does not use or modify `.runtime/` and does not use an existing player
or Agent character. A successful run ends with:

```text
[AGENT-LEVEL2] RESULT=PASS
```

## Run

Stop any server using the repository's compiled classes first. Build the test
runtime and dependency classpath before starting the smoke process:

```powershell
.\mvnw.cmd -q -DskipTests test-compile
.\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=tmp/agent-level2-classpath.txt" "-Dmdep.includeScope=test"
$deps = (Get-Content -Raw tmp/agent-level2-classpath.txt).Trim()
$cp = "target/test-classes;target/classes;$deps"
& "$env:JAVA_HOME\bin\java.exe" -Xms512m -Xmx2048m -cp $cp server.agents.integration.live.AgentLevel2SmokeMain
```

Do not run Maven while the smoke server is alive. Maven rebuilds
`target/classes`, which can make a running JVM observe an inconsistent class
tree.

## Current Evidence

On 2026-07-11, all 613 fully qualified test classes under
`src/test/java/server/agents` passed in Level 1 batches. They use 610 distinct
simple class names; all six classes belonging to the three duplicated names
were also run explicitly. The Level 2 runner then passed three times
independently. The first Level 2 attempt exposed and led to a fix for stale map
binding during offline Agent placement.

This is server-side Level 2 evidence. Client rendering, animation, packet
presentation, and visual movement still require a real MapleStory client.
