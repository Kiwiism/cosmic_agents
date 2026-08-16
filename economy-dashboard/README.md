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
while a simulation is active to refresh its snapshot, then use **Load** in the page. The Ownership
page exposes entry/appraisal reviews, one-use mutation authorizations, guard outcomes, and legacy
versus shadow disposition disagreements. The Market page exposes every structured stall offer,
numeric ask/offer, exact fingerprint, status, public flavor text, and seller response.
It also exposes accepted private arrangements and valuation queries with private-observation,
catalog-anchor, or audited YAML-override provenance.
