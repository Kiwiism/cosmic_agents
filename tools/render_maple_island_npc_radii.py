from __future__ import annotations

import argparse
import math
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
RADIUS_SOURCE = ROOT / "src/main/java/server/agents/plans/mapleisland/MapleIslandNpcInteractionRadiusCatalog.java"
ANCHOR_SOURCE = ROOT / "src/main/java/server/agents/plans/mapleisland/MapleIslandNpcInteractionAnchorCatalog.java"
OUTPUT_DIR = ROOT / "docs/agents/images/maple-island-npc-radii"
PANEL_SIZE = (1800, 1050)


@dataclass(frozen=True)
class Npc:
    map_id: int
    map_name: str
    npc_id: int
    name: str
    offset_x: int
    offset_y: int
    radius: int
    x: int
    y: int
    anchors: tuple[tuple[int, int], ...]


@dataclass(frozen=True)
class Portal:
    name: str
    x: int
    y: int
    target_map_id: int | None
    arrival: bool


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    filename = "seguisb.ttf" if bold else "segoeui.ttf"
    return ImageFont.truetype(str(Path("C:/Windows/Fonts") / filename), size=size)


TITLE = font(38, True)
LABEL = font(24, True)
BODY = font(21)
SMALL = font(18)


def map_xml_path(map_id: int) -> Path:
    padded = f"{map_id:09d}"
    return ROOT / "wz/Map.wz/Map" / f"Map{padded[0]}" / f"{padded}.img.xml"


def child_value(node: ET.Element, name: str, tag: str | None = None) -> str | None:
    for child in node:
        if child.attrib.get("name") == name and (tag is None or child.tag == tag):
            return child.attrib.get("value")
    return None


def parse_radius_rows() -> list[tuple[int, str, int, str, int, int, int]]:
    source = RADIUS_SOURCE.read_text(encoding="utf-8")
    pattern = re.compile(
        r'(?:curatedEntry|entry)\(([\d_]+),\s*"([^"]+)",\s*([\d_]+),\s*"([^"]+)",'
        r'\s*(-?\d+),\s*(-?\d+),\s*(\d+)\)'
    )
    rows = []
    for map_id, map_name, npc_id, npc_name, offset_x, offset_y, radius in pattern.findall(source):
        rows.append((
            int(map_id.replace("_", "")), map_name,
            int(npc_id.replace("_", "")), npc_name,
            int(offset_x), int(offset_y), int(radius),
        ))
    return rows


def parse_anchor_rows() -> dict[tuple[int, int], tuple[tuple[int, int], ...]]:
    source = ANCHOR_SOURCE.read_text(encoding="utf-8")
    rows: dict[tuple[int, int], tuple[tuple[int, int], ...]] = {}
    pattern = re.compile(r"anchors\((\d+),\s*(\d+),\s*points\((.*?)\)\)", re.S)
    for map_id, npc_id, values in pattern.findall(source):
        numbers = [int(value) for value in re.findall(r"-?\d+", values)]
        rows[(int(map_id), int(npc_id))] = tuple(zip(numbers[::2], numbers[1::2]))
    # The Yoona constants make that one entry intentionally non-literal.
    rows[(1_010_000, 20_100)] = ((-179, 95), (-199, 95), (-159, 95), (-219, 95), (-141, 95))
    return rows


def parse_map(map_id: int):
    root = ET.parse(map_xml_path(map_id)).getroot()
    life_root = next(node for node in root if node.tag == "imgdir" and node.attrib.get("name") == "life")
    npc_positions: dict[int, tuple[int, int]] = {}
    for node in life_root:
        if child_value(node, "type", "string") != "n":
            continue
        npc_id = int(child_value(node, "id", "string"))
        x = int(child_value(node, "x", "int"))
        y_value = child_value(node, "cy", "int") or child_value(node, "y", "int")
        npc_positions[npc_id] = (x, int(y_value))

    foothold_root = next(node for node in root if node.tag == "imgdir" and node.attrib.get("name") == "foothold")
    footholds = []
    for node in foothold_root.iter("imgdir"):
        values = {child.attrib.get("name"): int(child.attrib["value"])
                  for child in node if child.tag == "int" and child.attrib.get("name") in {"x1", "y1", "x2", "y2"}}
        if values.keys() >= {"x1", "y1", "x2", "y2"}:
            footholds.append((values["x1"], values["y1"], values["x2"], values["y2"]))

    ropes = []
    ladder_root = next((node for node in root if node.tag == "imgdir" and node.attrib.get("name") == "ladderRope"), None)
    if ladder_root is not None:
        for node in ladder_root:
            values = {child.attrib.get("name"): int(child.attrib["value"])
                      for child in node if child.tag == "int" and child.attrib.get("name") in {"x", "y1", "y2"}}
            if values.keys() >= {"x", "y1", "y2"}:
                ropes.append((values["x"], values["y1"], values["x"], values["y2"]))
    portals = []
    portal_root = next((node for node in root
                        if node.tag == "imgdir" and node.attrib.get("name") == "portal"), None)
    if portal_root is not None:
        for node in portal_root:
            portal_type = int(child_value(node, "pt", "int") or -1)
            target_map_id = int(child_value(node, "tm", "int") or 999_999_999)
            # Keep route-relevant exits and arrival points. Tutorial prompts,
            # random spawn points, and same-map teleport helpers obscure them.
            if target_map_id == 999_999_999 and portal_type not in {1, 7}:
                continue
            portals.append(Portal(
                child_value(node, "pn", "string") or "portal",
                int(child_value(node, "x", "int") or 0),
                int(child_value(node, "y", "int") or 0),
                None if target_map_id == 999_999_999 else target_map_id,
                target_map_id == 999_999_999,
            ))
    return npc_positions, footholds, ropes, portals


def load_maps() -> dict[int, tuple[str, list[Npc], list[tuple[int, int, int, int]],
                                 list[tuple[int, int, int, int]], list[Portal]]]:
    anchors = parse_anchor_rows()
    result = {}
    for map_id, map_name, npc_id, npc_name, offset_x, offset_y, radius in parse_radius_rows():
        if map_id not in result:
            positions, footholds, ropes, portals = parse_map(map_id)
            result[map_id] = (map_name, [], footholds, ropes, portals)
        position = parse_map(map_id)[0].get(npc_id)
        if position is None:
            raise RuntimeError(f"NPC {npc_id} is missing from map {map_id}")
        result[map_id][1].append(Npc(
            map_id, map_name, npc_id, npc_name, offset_x, offset_y, radius,
            position[0], position[1], anchors.get((map_id, npc_id), ()),
        ))
    return result


def panel_projection(npcs: list[Npc], footholds, ropes, portals):
    width, height = PANEL_SIZE
    margin = 70
    header = 105
    footer = 55
    all_x = [value for line in footholds + ropes for value in (line[0], line[2])]
    all_y = [value for line in footholds + ropes for value in (line[1], line[3])]
    for npc in npcs:
        all_x.extend((npc.x + npc.offset_x - npc.radius, npc.x + npc.offset_x + npc.radius))
        all_y.extend((npc.y + npc.offset_y - npc.radius, npc.y + npc.offset_y + npc.radius))
        all_x.extend(x for x, _ in selectable_anchors(npc))
        all_y.extend(y for _, y in selectable_anchors(npc))
    all_x.extend(portal.x for portal in portals)
    all_y.extend(portal.y for portal in portals)
    x_min, x_max = min(all_x), max(all_x)
    y_min, y_max = min(all_y), max(all_y)
    x_pad = max(40, int((x_max - x_min) * .03))
    y_pad = max(40, int((y_max - y_min) * .06))
    x_min, x_max = x_min - x_pad, x_max + x_pad
    y_min, y_max = y_min - y_pad, y_max + y_pad
    plot_w = width - 2 * margin
    plot_h = height - header - footer
    scale = min(plot_w / (x_max - x_min), plot_h / (y_max - y_min))
    used_w = (x_max - x_min) * scale
    used_h = (y_max - y_min) * scale
    x_off = margin + (plot_w - used_w) / 2
    y_off = header + (plot_h - used_h) / 2

    def point(x: int, y: int):
        return x_off + (x - x_min) * scale, y_off + (y - y_min) * scale

    return scale, point


def selectable_anchors(npc: Npc) -> tuple[tuple[int, int], ...]:
    center_x = npc.x + npc.offset_x
    center_y = npc.y + npc.offset_y
    radius_squared = npc.radius * npc.radius
    return tuple(
        (x, y) for x, y in npc.anchors
        if (x - center_x) ** 2 + (y - center_y) ** 2 <= radius_squared
    )


def draw_panel(map_id: int, map_name: str, npcs: list[Npc], footholds, ropes, portals) -> Image.Image:
    width, height = PANEL_SIZE
    margin = 70
    header = 105
    footer = 55
    scale, point = panel_projection(npcs, footholds, ropes, portals)

    image = Image.new("RGB", PANEL_SIZE, "#f4f6f8")
    draw = ImageDraw.Draw(image, "RGBA")
    draw.text((margin, 24), f"{map_name}  ·  {map_id}", font=TITLE, fill="#18232f")
    draw.text((width - 500, 36), f"scale: {scale:.2f} image px / game px", font=SMALL, fill="#667383")

    for x1, y1, x2, y2 in footholds:
        draw.line((*point(x1, y1), *point(x2, y2)), fill="#293e52", width=5)
    for x1, y1, x2, y2 in ropes:
        draw.line((*point(x1, y1), *point(x2, y2)), fill="#9b6b35", width=4)

    for portal in portals:
        px, py = point(portal.x, portal.y)
        marker = 12
        draw.polygon(((px, py - marker), (px + marker, py),
                      (px, py + marker), (px - marker, py)),
                     fill="#f59e0b" if not portal.arrival else "#f4f6f8",
                     outline="#9a5b00")
        destination = "arrival" if portal.arrival else f"to {portal.target_map_id}"
        draw.text((px + 16, py - 15), f"{portal.name} {destination}",
                  font=BODY, fill="#6b4100")

    colors = ((30, 136, 229), (236, 88, 64), (117, 86, 201), (13, 148, 136))
    for index, npc in enumerate(npcs):
        color = colors[index % len(colors)]
        cx, cy = point(npc.x, npc.y)
        circle_x, circle_y = point(npc.x + npc.offset_x, npc.y + npc.offset_y)
        radius = npc.radius * scale
        draw.ellipse((circle_x - radius, circle_y - radius, circle_x + radius, circle_y + radius),
                     fill=(*color, 32), outline=(*color, 220), width=5)
        draw.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=(*color, 255), outline="#ffffff", width=3)
        for anchor_x, anchor_y in selectable_anchors(npc):
            ax, ay = point(anchor_x, anchor_y)
            draw.rectangle((ax - 6, ay - 6, ax + 6, ay + 6), fill=(*color, 255), outline="#ffffff", width=2)
        label = f"{npc.name}  offset=({npc.offset_x:+d},{npc.offset_y:+d})  r={npc.radius}px"
        box = draw.textbbox((0, 0), label, font=LABEL)
        lx = min(width - margin - (box[2] - box[0]) - 18, max(margin, cx + 14))
        ly = min(height - footer - 38, max(header, cy - 42))
        draw.rounded_rectangle((lx - 8, ly - 4, lx + box[2] - box[0] + 8, ly + 32),
                               radius=7, fill=(244, 246, 248, 230), outline=(*color, 210), width=2)
        draw.text((lx, ly), label, font=LABEL, fill="#18232f")

    draw.text((margin, height - 42), "circle = cohort spread radius   • = NPC   ■ = curated anchor   ◆ = exit portal   ◇ = arrival   brown = ladder/rope",
              font=BODY, fill="#4f5d6b")
    return image


def measure_annotation(path: Path) -> None:
    """Translate hand-drawn solid-color circles back to game-coordinate suggestions."""
    annotated = Image.open(path).convert("RGB")
    source_width = 2 * 1500 + 3 * 28
    source_height = math.ceil(9 / 2) * 875 + (math.ceil(9 / 2) + 1) * 28
    scale_x = annotated.width / source_width
    scale_y = annotated.height / source_height
    overlay_colors = {
        "blue": (63, 72, 204),
        "red": (136, 0, 21),
        "bright-red": (237, 28, 36),
        "orange": (255, 127, 39),
        "cyan": (0, 162, 232),
        "purple": (163, 73, 164),
        "green": (34, 177, 76),
    }
    maps = load_maps()
    pixels = annotated.load()
    for index, (map_id, (map_name, npcs, footholds, ropes, portals)) in enumerate(maps.items()):
        column = index % 2
        row = index // 2
        left = round((28 + column * (1500 + 28)) * scale_x)
        top = round((28 + row * (875 + 28)) * scale_y)
        right = round((28 + column * (1500 + 28) + 1500) * scale_x)
        bottom = round((28 + row * (875 + 28) + 875) * scale_y)
        panel_scale, project = panel_projection(npcs, footholds, ropes, portals)
        game_scale_x = panel_scale * (1500 / PANEL_SIZE[0]) * scale_x
        game_scale_y = panel_scale * (875 / PANEL_SIZE[1]) * scale_y
        for color_name, color in overlay_colors.items():
            xs = []
            ys = []
            for y in range(max(0, top), min(annotated.height, bottom)):
                for x in range(max(0, left), min(annotated.width, right)):
                    if pixels[x, y] == color:
                        xs.append(x)
                        ys.append(y)
            if len(xs) < 100:
                continue
            center_x = (min(xs) + max(xs)) / 2
            center_y = (min(ys) + max(ys)) / 2
            radius_x = (max(xs) - min(xs)) / 2 / game_scale_x
            radius_y = (max(ys) - min(ys)) / 2 / game_scale_y
            nearest = min(npcs, key=lambda npc: (
                center_x - (left + project(npc.x, npc.y)[0] * (1500 / PANEL_SIZE[0]) * scale_x)
            ) ** 2 + (
                center_y - (top + project(npc.x, npc.y)[1] * (875 / PANEL_SIZE[1]) * scale_y)
            ) ** 2)
            npc_panel_x, npc_panel_y = project(nearest.x, nearest.y)
            npc_x = left + npc_panel_x * (1500 / PANEL_SIZE[0]) * scale_x
            npc_y = top + npc_panel_y * (875 / PANEL_SIZE[1]) * scale_y
            offset_x = (center_x - npc_x) / game_scale_x
            offset_y = (center_y - npc_y) / game_scale_y
            rounded = lambda value: int(round(value / 5) * 5)
            print(f"{nearest.name:6} map={map_id:7} color={color_name:6} "
                  f"offset=({rounded(offset_x):+d},{rounded(offset_y):+d}) "
                  f"radius={rounded((radius_x + radius_y) / 2)}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--measure-annotation", type=Path)
    arguments = parser.parse_args()
    if arguments.measure_annotation:
        measure_annotation(arguments.measure_annotation)
        return
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    panels = []
    for map_id, (map_name, npcs, footholds, ropes, portals) in load_maps().items():
        panel = draw_panel(map_id, map_name, npcs, footholds, ropes, portals)
        filename = f"{map_id:09d}-{re.sub(r'[^a-z0-9]+', '-', map_name.lower()).strip('-')}.png"
        panel.save(OUTPUT_DIR / filename, optimize=True)
        panels.append((map_id, map_name, panel))

    columns = 2
    thumb_w, thumb_h = 1500, 875
    gap = 28
    rows = math.ceil(len(panels) / columns)
    sheet = Image.new("RGB", (columns * thumb_w + (columns + 1) * gap,
                              rows * thumb_h + (rows + 1) * gap), "#dfe4e9")
    for index, (_, _, panel) in enumerate(panels):
        thumb = panel.resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        x = gap + (index % columns) * (thumb_w + gap)
        y = gap + (index // columns) * (thumb_h + gap)
        sheet.paste(thumb, (x, y))
    sheet.save(OUTPUT_DIR / "maple-island-all-npc-radii.png", optimize=True)
    print(OUTPUT_DIR / "maple-island-all-npc-radii.png")


if __name__ == "__main__":
    main()
