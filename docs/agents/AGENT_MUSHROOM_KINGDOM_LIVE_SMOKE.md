# Mushroom Kingdom Agent Live Smoke Test

`AgentMushroomKingdomLiveSmokeMain` runs the real Mushroom Kingdom universal plan without a MapleStory client. It starts the configured server and MySQL-backed runtime, creates disposable Agent-only accounts, applies the level-30 second-job AP/SP/equipment fixtures, and deletes all temporary characters and accounts before exit.

The runner follows the ordinary quest, NPC, portal, navigation, combat, loot, solo-instance, and boss paths through quest `2336`. For large collections, the cohort must obtain 30 real drops in total and every branch must demonstrate combat for that quest before the runner supplies repetitive remainder counts. A branch that personally sees no RNG drop is not forced to repeat a proven route after the cohort threshold is met.

Quest `2326` may receive its one-off rare item only after the cohort demonstrates real Helmet Pepe combat. Each randomized Yeti variant must take real damage and receive real kill credit, but its remaining HP may be shortened. The Prime Minister is fought at full real HP. A missing Royal Seal may be supplied only after that same branch demonstrates real Prime Minister combat. Repeated long castle travel may be staged only after the required natural route has already been demonstrated. Optional quest `2337` is outside the mainline pass condition.

Stop any server using this worktree's compiled classes, then build the test runtime and dependency classpath:

```powershell
.\mvnw.cmd -q -DskipTests test-compile
.\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=tmp/mushroom-live-classpath.txt" "-Dmdep.includeScope=test"
$deps = (Get-Content -Raw tmp/mushroom-live-classpath.txt).Trim()
$cp = "target/test-classes;target/classes;$deps"
& "$env:JAVA_HOME\bin\java.exe" -Xms512m -Xmx2048m -cp $cp server.agents.progression.AgentMushroomKingdomLiveSmokeMain
```

With no arguments, the runner covers all 12 Explorer second jobs. Pass one or more branch IDs for a diagnostic subset, for example:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xms512m -Xmx2048m -cp $cp server.agents.progression.AgentMushroomKingdomLiveSmokeMain fighter cleric gunslinger
```

Named diagnostic snapshots provide reproducible development checkpoints without replacing the final full run:

```powershell
# q2323 active with 100 Pig Tails on the authored field
& "$env:JAVA_HOME\bin\java.exe" -Dmushroom.live.snapshot=q2323-return -cp $cp server.agents.progression.AgentMushroomKingdomLiveSmokeMain fighter

# exact observed below-map failure position
& "$env:JAVA_HOME\bin\java.exe" -Dmushroom.live.snapshot=q2323-out-of-bounds -cp $cp server.agents.progression.AgentMushroomKingdomLiveSmokeMain fighter

# stable continuation frontier: q2324 complete, q2325 not started, at the entrance
& "$env:JAVA_HOME\bin\java.exe" -Dmushroom.live.snapshot=q2325-entry -cp $cp server.agents.progression.AgentMushroomKingdomLiveSmokeMain fighter
```

The q2323 runtime uses authored portal `4` from map `106020401` to `106020400` and recovers a stale grounded character that is physically below a Mushroom Kingdom map. Boss-route staging accepts q2333 completion or downstream q2335/q2336 activity; q2331 counts as durable post-boss evidence only after completion because it is intentionally started before the fight.

A successful run ends with:

```text
[MUSHROOM-LIVE] RESULT=PASS
```
