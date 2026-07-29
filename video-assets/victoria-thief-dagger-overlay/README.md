# Victoria Thief–Dagger Video Overlay

Transparent checkpoint cards for a 1280×720 gameplay video.

## Files

- `checkpoint-1.png` — job advancement, instructor training, and stock-up
- `checkpoint-2.png` — Kerning City quests
- `checkpoint-3.png` — Perion quests and the level 15 goal
- `checkmark.png` — place over a checkbox when its objective is complete
- `current-objective.png` — optional yellow marker for the active objective
- Matching `.svg` files are included as editable masters.
- `progress-frames/` — every progress state with markers already baked into the
  card. Use these if you do not want to position markers manually.

## Ready-made progress frames

The numbered files in `progress-frames/` are already ordered for editing:

- Amber play symbol — current objective
- Green check — completed objective
- The last image for each checkpoint — every objective completed

Place the frames in filename order. Cut from one image to the next whenever the
agent completes an objective. All frames belonging to the same checkpoint have
identical dimensions and alignment, so the card will not jump between updates.

## OpenShot setup

1. Import the three checkpoint PNGs and the two marker PNGs.
2. Put gameplay on Track 1 and a checkpoint card on Track 4.
3. Place the card near the upper-right corner. At native size, use about `X = 800`,
   `Y = 18`; scale it down slightly if it covers important gameplay.
4. Put each checkmark/current-objective marker on Track 5 or above.
5. Set a marker clip's start time to the moment the objective changes or completes.
6. Use a short 0.20–0.30 second fade for card and marker changes.

## Checkbox centers

Coordinates below are relative to the top-left of each native-size checkpoint card.
Center a `checkmark.png` on the matching point.

- Checkpoint 1: `(45,150)`, `(45,207)`, `(45,286)`, `(45,365)`,
  `(45,444)`, `(45,561)`
- Checkpoint 2: `(45,150)`, `(45,229)`, `(45,308)`, `(45,410)`,
  `(45,489)`
- Checkpoint 3: `(45,150)`, `(45,252)`, `(45,331)`, `(45,470)`

The yellow current-objective marker is wider than the checkbox. Position its arrow
point at the checkbox or place it just outside the left edge of the card.
