# Map VR Boundary Generation TODO

Status: deferred. Documentation only; no map assets have been changed.

## Objective

Generate suitable `VRLeft`, `VRRight`, `VRTop`, and `VRBottom` values for maps
where one or more values are missing. The generated bounds should keep the
camera within intentional map content at supported client resolutions without
hiding playable platforms, portals, NPCs, reactors, or scripted field objects.

## Source Data

Use the project root as the base for all stored source paths. Resolve geometry
from the matching Cosmic client IMG and server WZ XML data:

- foothold endpoints and foothold groups;
- tile positions and resolved tile image bounds;
- object positions, origins, dimensions, and animation-frame extents;
- portal, NPC, mob spawn, reactor, and field-object positions;
- existing VR values on comparable maps;
- map background tiling/repeat configuration;
- the largest explicitly supported client viewport.

Footholds alone are not sufficient because they may omit decorative ground,
portals outside the main platform, tall objects, and scripted content.

## Proposed Derivation

1. Build playable bounds from footholds, portals, spawns, NPCs, and reactors.
2. Build visual bounds from tiles and resolved object extents.
3. Combine the bounds and add conservative horizontal, top, and bottom padding.
4. Enforce a minimum rectangle at least as large as the largest supported
   viewport, centering small maps where appropriate.
5. Compare the result with nearby maps using the same tileset and layout style.
6. Flag unusual or low-confidence maps instead of writing them automatically.

Conceptual calculation:

```text
VRLeft   = min(playableX, visualX) - leftPadding
VRRight  = max(playableX, visualX) + rightPadding
VRTop    = min(playableY, visualY) - topPadding
VRBottom = max(playableY, visualY) + bottomPadding
```

## Required Output

Generate a review report before producing any patch:

```text
Map ID | Existing VR | Proposed VR | Geometry sources | Confidence | Warnings
```

The report must separately identify:

- maps with all four VR values missing;
- maps with only some VR values missing;
- bounds smaller than a supported viewport;
- important objects outside the proposed bounds;
- backgrounds that do not cover the proposed VR rectangle;
- maps requiring manual review.

## Manual-Review Classes

Do not automatically apply generated values to:

- jump quests and scrolling maps;
- boss, expedition, event, and party-quest maps;
- ships and moving transport maps;
- scripted tutorial or cutscene maps;
- maps with hidden, dynamic, or instance-only platforms;
- maps whose backgrounds are intentionally finite or non-repeating;
- maps where client and server geometry disagree.

## Application Rules

- The client Map IMG is authoritative for visible camera behavior.
- Apply matching values to server XML only when needed for server catalogs,
  navigation, validation, or client/server data consistency.
- Preserve existing complete VR definitions unless a separate review approves
  changing them.
- Do not treat VR generation as a fix for an undersized background. Background
  tiling, repeat flags, or image assets require a separate client-data fix.
- Store generated paths and reports relative to the project root.

## Verification

For every applied map:

1. Test the lowest, highest, leftmost, and rightmost reachable positions.
2. Test every supported resolution, especially the widest HD viewport.
3. Confirm no blue/blank area or unintended background edge is exposed.
4. Confirm portals, NPCs, reactors, and playable footholds remain reachable and
   visible.
5. Confirm minimap and camera movement remain coherent.
6. Keep before/after screenshots and the generated report as review evidence.

## Completion Criteria

- Every map missing VR data is classified as generated, manually defined,
  intentionally unbounded, or accepted exception.
- High-confidence generated values pass automated geometry validation.
- Applied values pass the client resolution matrix.
- Client and server copies have no unexplained VR mismatch.
