# Economy live-test roster

This local-only fixture prepares a deterministic, reversible 200-character roster from accounts
already locked as Agent-only. It never creates characters or accounts. Run it only while Cosmic is
stopped and provide the local MySQL password through `COSMIC_TEST_DB_PASSWORD`.

```powershell
$env:COSMIC_TEST_DB_PASSWORD = '<local password>'
powershell -ExecutionPolicy Bypass -File tools/economy/Prepare-EconomyLiveTestRoster.ps1 -Mode calibration
```

The default job counts (`36/37/33/46/48`) match the deterministic roster produced by the checked-in
scenario seed. If the seed or class weights change, pass the five `*Count` parameters using the exact
requirements reported by `!economy preflight`; the counts must sum to `RosterSize`.

Calibration mode assigns 25 characters equally across the five first-job families and places those
characters on the configured real farm map. It gives only that calibration cohort tagged NPC-stock
potions, ammunition, and first-job starter weapons with WZ-authored stats so ordinary supply or
incompatible inherited equipment does not invalidate the measurement. The population file targets
only those 25. After real
calibration sessions are captured, stop Cosmic and switch all 200 to the Free Market:

```powershell
powershell -ExecutionPolicy Bypass -File tools/economy/Prepare-EconomyLiveTestRoster.ps1 -Mode market
```

Set `COSMIC_AGENT_POPULATION_FILE` to the reported population path when launching Cosmic. The tool
backs up every changed character row, skill, inventory row, and equipment extension row before the
first mutation. Market mode defaults to a clean inventory baseline and then adds only tagged starter
weapons, pots, ammunition, and PlayerShop permits. These are explicit initial endowments, not organic
production, and the generated audit manifest says so. Pass `-PreserveInventory` only when a scenario
deliberately imports existing character possessions as its declared initial endowment.
The test-only level/job reset also installs deterministic level-appropriate first-job skill builds;
`economy_test_skill_backup` preserves and restores every original roster skill row.

To restore the changed character fields, skills, and exact pre-fixture inventory (removing all
test-period holdings first):

```powershell
powershell -ExecutionPolicy Bypass -File tools/economy/Prepare-EconomyLiveTestRoster.ps1 -Mode restore
```
