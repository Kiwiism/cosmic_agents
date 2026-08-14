# Cosmic Economy Observatory

This is a read-only static dashboard. It consumes an exported JSON evidence bundle from the
separate PostgreSQL economy database and never connects to Cosmic MySQL.

```powershell
powershell -ExecutionPolicy Bypass -File tools/economy/Export-EconomyDashboard.ps1 -RunId <uuid>
python -m http.server 8899 --directory economy-dashboard
```

Open `http://127.0.0.1:8899/`. The report supports logical-day filtering, item drill-downs,
full-table search, provenance, meso source/sink plots, room traffic, stalls, listings, decisions,
agent state, public negotiation chat, and invariant/ingestion failure evidence. Re-run the export
while a simulation is active to refresh its snapshot, then use **Load** in the page.
