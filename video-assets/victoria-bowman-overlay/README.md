# Victoria Bowman Video Overlay

Green transparent checkpoint cards for a 1280x720 gameplay video.

## Files

- `checkpoint-1.png` - Bowman advancement, instructor training, and supplies
- `checkpoint-2.png` - Henesys quests and the level 15 goal
- `checkmark.png` - completed-objective marker
- `current-objective.png` - active-objective marker
- Matching `.svg` files are editable masters.
- `progress-frames/` contains every ordered progress state with markers baked in.

## Progress-frame usage

Place the numbered PNG files in filename order and cut to the next frame whenever
the Agent completes an objective. Green checks are completed objectives; the
light-green play symbol is the current objective. The final file for each
checkpoint shows every objective completed.

## OpenShot placement

Put gameplay on Track 1 and the progress-frame PNG on Track 4 or above. For a
1280x720 project, start near `X = 800`, `Y = 18`. All frames have identical
dimensions and alignment, so switching images will not move the card.

## Checkbox centers

- Checkpoint 1: `(45,150)`, `(45,207)`, `(45,286)`, `(45,365)`, `(45,444)`, `(45,561)`
- Checkpoint 2: `(45,150)`, `(45,252)`, `(45,331)`, `(45,410)`, `(45,530)`
