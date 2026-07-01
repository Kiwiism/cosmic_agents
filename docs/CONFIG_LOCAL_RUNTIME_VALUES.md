# Local Runtime Config Values

This project currently expects the checked-out `config.yaml` runtime values below to remain in place for local server runs.

These values are intentionally recorded here so later commits/reviews can distinguish desired local runtime settings from accidental config churn.

## World 0

- `server_message`: `Welcome to Scania!`
- `event_message`: `Scania!`
- `why_am_i_recommended`: `Welcome to Scania!`
- `exp_rate`: `1`
- `meso_rate`: `1`
- `drop_rate`: `1`
- `boss_drop_rate`: `1`
- `mob_rate`: `1.0`
- `travel_rate`: `10`

## Login And Database

- `DB_HOST`: `localhost`
- `DB_USER`: `root`
- `DB_PASS`: `root`
- `ENABLE_PIC`: `false`
- `ENABLE_PIN`: `false`

## Custom Feature Toggles

- `SCROLL_SUCCESS_BONUS_ENABLED`: `false`
- `SCROLL_SUCCESS_BONUS`: `10`

Note: with `SCROLL_SUCCESS_BONUS_ENABLED: false`, the configured bonus value is present but inactive.
