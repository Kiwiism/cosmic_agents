# Economy live-test roster

This local-only fixture prepares a deterministic, reversible 200-character roster from accounts
already locked as Agent-only. It never creates characters or accounts. Run it only while Cosmic is
stopped and provide the local MySQL password through `COSMIC_TEST_DB_PASSWORD`.

```powershell
$env:COSMIC_TEST_DB_PASSWORD = '<local password>'
powershell -ExecutionPolicy Bypass -File tools/economy/Prepare-EconomyLiveTestRoster.ps1 -Mode calibration
```

Calibration mode assigns 25 characters equally across the five first-job families and places those
characters on the configured real farm map. It gives only that calibration cohort tagged NPC-stock
potions and job ammunition so ordinary supply maintenance does not abort the measurement. The
population file targets only those 25. After real
calibration sessions are captured, stop Cosmic and switch all 200 to the Free Market:

```powershell
powershell -ExecutionPolicy Bypass -File tools/economy/Prepare-EconomyLiveTestRoster.ps1 -Mode market
```

Set `COSMIC_AGENT_POPULATION_FILE` to the reported population path when launching Cosmic. The tool
backs up every changed character row before the first mutation and tags every inserted permit with
`giftFrom=ECONOMY_TEST_FIXTURE`. Those permits and the prepared stats are test prerequisites, not
organic economic supply, and the generated audit manifest says so explicitly. Market mode removes
all tagged calibration consumables/ammunition before the economy imports holdings.

To restore the changed character fields and remove only tagged fixture permits:

```powershell
powershell -ExecutionPolicy Bypass -File tools/economy/Prepare-EconomyLiveTestRoster.ps1 -Mode restore
```
