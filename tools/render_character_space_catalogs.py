from __future__ import annotations

import math
import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "docs" / "agents" / "images" / "space-catalogs"
FM_SOURCE = ROOT / "src" / "main" / "java" / "server" / "maps" / "reservation" / "FreeMarketCharacterSpaceCatalog.java"
MAPLE_SOURCE = ROOT / "src" / "main" / "java" / "server" / "maps" / "reservation" / "MapleIslandCharacterSpaceCatalog.java"

WIDTH = 3200
HEIGHT = 1900
PLOT_LEFT = 150
PLOT_TOP = 280
PLOT_RIGHT = 2470
PLOT_BOTTOM = 1700
PANEL_LEFT = 2540
PANEL_RIGHT = 3110

BACKGROUND = "#F4F6F8"
PLOT_BACKGROUND = "#FFFFFF"
PANEL_BACKGROUND = "#E9EEF3"
INK = "#18232F"
MUTED = "#667383"
GRID = "#DDE3E9"
FOOTHOLD = "#24384A"
VERTICAL_FOOTHOLD = "#9AA7B4"
SPOT = "#F05A3C"
SPOT_DARK = "#8E2E20"
LEFT_SPOT = "#168C91"
LEFT_SPOT_DARK = "#0B5559"
MIDPOINT = "#7656C9"


@dataclass(frozen=True)
class Spot:
    number: int
    row: int
    slot: int
    x: int
    y: int


@dataclass(frozen=True)
class Diagram:
    filename: str
    title: str
    subtitle: str
    map_id: int
    spots: list[Spot]
    notes: list[str]
    split_x: int | None = None


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    filename = "seguisb.ttf" if bold else "segoeui.ttf"
    path = Path("C:/Windows/Fonts") / filename
    return ImageFont.truetype(str(path), size=size)


TITLE_FONT = font(58, True)
SUBTITLE_FONT = font(28)
SECTION_FONT = font(29, True)
BODY_FONT = font(24)
SMALL_FONT = font(20)
TINY_FONT = font(17)
SPOT_FONT = font(19, True)


def map_xml_path(map_id: int) -> Path:
    padded = f"{map_id:09d}"
    return ROOT / "wz" / "Map.wz" / "Map" / f"Map{padded[0]}" / f"{padded}.img.xml"


def parse_footholds(map_id: int) -> list[tuple[int, int, int, int]]:
    root = ET.parse(map_xml_path(map_id)).getroot()
    foothold_root = next(
        node for node in root.iter("imgdir") if node.attrib.get("name") == "foothold"
    )
    segments: list[tuple[int, int, int, int]] = []
    for node in foothold_root.iter("imgdir"):
        values = {
            child.attrib.get("name"): int(child.attrib["value"])
            for child in node
            if child.tag == "int" and child.attrib.get("name") in {"x1", "y1", "x2", "y2"}
        }
        if values.keys() >= {"x1", "y1", "x2", "y2"}:
            segments.append((values["x1"], values["y1"], values["x2"], values["y2"]))
    return segments


def extract_block(source: str, marker: str) -> str:
    start = source.index(marker)
    end = source.index(";", start)
    return source[start:end]


def build_spots(points: list[tuple[int, int]]) -> list[Spot]:
    rows: dict[int, int] = {}
    row_slots: dict[int, int] = {}
    result: list[Spot] = []
    for number, (x, y) in enumerate(points, start=1):
        row = rows.setdefault(y, len(rows))
        slot = row_slots.get(row, 0)
        row_slots[row] = slot + 1
        result.append(Spot(number, row, slot, x, y))
    return result


def parse_fm_spots(name: str) -> list[Spot]:
    source = FM_SOURCE.read_text(encoding="utf-8")
    block = extract_block(source, f"private static final List<Point> {name}")
    points = [(int(x), int(y)) for x, y in re.findall(r"new Point\((-?\d+),\s*(-?\d+)\)", block)]
    return build_spots(points)


def parse_maple_spots(name: str) -> list[Spot]:
    source = MAPLE_SOURCE.read_text(encoding="utf-8")
    block = extract_block(source, f"private static final List<CharacterSpace> {name}")
    ranges = [
        (int(start), int(end), int(y))
        for start, end, y in re.findall(
            r"new HorizontalRange\((-?\d+),\s*(-?\d+),\s*(-?\d+)\)", block
        )
    ]
    result: list[Spot] = []
    number = 1
    for row, (start, end, y) in enumerate(ranges):
        for slot, x in enumerate(range(start, end + 1, 25)):
            result.append(Spot(number, row, slot, x, y))
            number += 1
    return result


def nice_grid_step(span: int) -> int:
    target = max(1, span // 8)
    magnitude = 10 ** int(math.floor(math.log10(target)))
    for multiplier in (1, 2, 5, 10):
        step = magnitude * multiplier
        if step >= target:
            return step
    return magnitude * 10


def wrap_text(draw: ImageDraw.ImageDraw, text: str, max_width: int, text_font: ImageFont.FreeTypeFont) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if draw.textlength(candidate, font=text_font) <= max_width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def row_summary(spots: list[Spot]) -> list[tuple[int, int, int, int, int]]:
    rows: dict[int, list[Spot]] = {}
    for spot in spots:
        rows.setdefault(spot.row, []).append(spot)
    return [
        (row + 1, values[0].y, len(values), values[0].number, values[-1].number)
        for row, values in rows.items()
    ]


def draw_diagram(diagram: Diagram) -> Path:
    footholds = parse_footholds(diagram.map_id)
    all_x = [value for segment in footholds for value in (segment[0], segment[2])]
    all_y = [value for segment in footholds for value in (segment[1], segment[3])]
    all_x.extend(spot.x for spot in diagram.spots)
    all_y.extend(spot.y for spot in diagram.spots)

    x_min, x_max = min(all_x), max(all_x)
    y_min, y_max = min(all_y), max(all_y)
    x_padding = max(80, int((x_max - x_min) * 0.04))
    y_padding = max(80, int((y_max - y_min) * 0.08))
    x_min -= x_padding
    x_max += x_padding
    y_min -= y_padding
    y_max += y_padding

    plot_width = PLOT_RIGHT - PLOT_LEFT
    plot_height = PLOT_BOTTOM - PLOT_TOP
    scale = min(plot_width / (x_max - x_min), plot_height / (y_max - y_min))
    used_width = (x_max - x_min) * scale
    used_height = (y_max - y_min) * scale
    x_offset = (plot_width - used_width) / 2
    y_offset = (plot_height - used_height) / 2

    def project(x: int, y: int) -> tuple[float, float]:
        return (
            PLOT_LEFT + x_offset + (x - x_min) * scale,
            PLOT_TOP + y_offset + (y - y_min) * scale,
        )

    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        (PLOT_LEFT, PLOT_TOP, PLOT_RIGHT, PLOT_BOTTOM),
        radius=16,
        fill=PLOT_BACKGROUND,
        outline="#CBD3DB",
        width=3,
    )
    draw.rounded_rectangle(
        (PANEL_LEFT, PLOT_TOP, PANEL_RIGHT, PLOT_BOTTOM),
        radius=16,
        fill=PANEL_BACKGROUND,
        outline="#CBD3DB",
        width=3,
    )

    draw.text((PLOT_LEFT, 74), diagram.title, font=TITLE_FONT, fill=INK)
    draw.text((PLOT_LEFT, 154), diagram.subtitle, font=SUBTITLE_FONT, fill=MUTED)

    x_step = nice_grid_step(x_max - x_min)
    y_step = nice_grid_step(y_max - y_min)
    first_x = math.ceil(x_min / x_step) * x_step
    first_y = math.ceil(y_min / y_step) * y_step
    for x in range(first_x, x_max + 1, x_step):
        px, _ = project(x, y_min)
        draw.line((px, PLOT_TOP + 2, px, PLOT_BOTTOM - 2), fill=GRID, width=2)
        draw.text((px + 8, PLOT_BOTTOM - 34), str(x), font=TINY_FONT, fill=MUTED)
    for y in range(first_y, y_max + 1, y_step):
        _, py = project(x_min, y)
        draw.line((PLOT_LEFT + 2, py, PLOT_RIGHT - 2, py), fill=GRID, width=2)
        draw.text((PLOT_LEFT + 10, py - 26), str(y), font=TINY_FONT, fill=MUTED)

    for x1, y1, x2, y2 in footholds:
        p1 = project(x1, y1)
        p2 = project(x2, y2)
        color = VERTICAL_FOOTHOLD if x1 == x2 else FOOTHOLD
        width = 4 if x1 == x2 else 7
        draw.line((*p1, *p2), fill=color, width=width)

    if diagram.split_x is not None:
        split_top = project(diagram.split_x, y_min)[0]
        dash = 18
        y = PLOT_TOP + 16
        while y < PLOT_BOTTOM - 16:
            draw.line((split_top, y, split_top, min(y + dash, PLOT_BOTTOM - 16)), fill=MIDPOINT, width=4)
            y += dash * 2
        draw.text((split_top + 12, PLOT_TOP + 16), f"right-half boundary x={diagram.split_x}", font=SMALL_FONT, fill=MIDPOINT)

    fm_layout = diagram.map_id >= 910000000
    radius = 22 if fm_layout else 8
    for spot in diagram.spots:
        px, py = project(spot.x, spot.y)
        is_left = diagram.split_x is not None and spot.x < diagram.split_x
        fill = LEFT_SPOT if is_left else SPOT
        outline = LEFT_SPOT_DARK if is_left else SPOT_DARK
        draw.ellipse((px - radius, py - radius, px + radius, py + radius), fill=fill, outline=outline, width=3)
        if fm_layout:
            label = str(spot.number)
            box = draw.textbbox((0, 0), label, font=SPOT_FONT)
            draw.text((px - (box[2] - box[0]) / 2, py - (box[3] - box[1]) / 2 - 2), label, font=SPOT_FONT, fill="#FFFFFF")

    rows: dict[int, list[Spot]] = {}
    for spot in diagram.spots:
        rows.setdefault(spot.row, []).append(spot)
    if not fm_layout and len(rows) <= 14:
        placed_labels: list[tuple[float, float, float, float]] = []
        for row, values in rows.items():
            first = values[0]
            last = values[-1]
            px, py = project(first.x, first.y)
            label = f"R{row + 1}: #{first.number}-#{last.number}"
            label_width = max(140, draw.textbbox((0, 0), label, font=TINY_FONT)[2] + 18)
            candidates = [
                (px - 4, py - 43, px - 4 + label_width, py - 13),
                (px - 4, py + 13, px - 4 + label_width, py + 43),
                (px - 4, py - 78, px - 4 + label_width, py - 48),
                (px - 4, py + 48, px - 4 + label_width, py + 78),
            ]
            label_box = next(
                (
                    candidate
                    for candidate in candidates
                    if candidate[0] >= PLOT_LEFT + 4
                    and candidate[2] <= PLOT_RIGHT - 4
                    and candidate[1] >= PLOT_TOP + 4
                    and candidate[3] <= PLOT_BOTTOM - 4
                    and not any(
                        candidate[0] < placed[2] + 5
                        and candidate[2] + 5 > placed[0]
                        and candidate[1] < placed[3] + 5
                        and candidate[3] + 5 > placed[1]
                        for placed in placed_labels
                    )
                ),
                None,
            )
            if label_box is None:
                continue
            placed_labels.append(label_box)
            draw.rounded_rectangle(label_box, radius=7, fill="#FFFFFF", outline="#B9C4CE", width=2)
            draw.text((label_box[0] + 9, label_box[1] + 1), label, font=TINY_FONT, fill=INK)

    panel_x = PANEL_LEFT + 34
    y_cursor = PLOT_TOP + 34
    draw.text((panel_x, y_cursor), "Catalog", font=SECTION_FONT, fill=INK)
    y_cursor += 52
    draw.text((panel_x, y_cursor), f"Map ID  {diagram.map_id}", font=BODY_FONT, fill=INK)
    y_cursor += 38
    draw.text((panel_x, y_cursor), f"Spots   {len(diagram.spots)}", font=BODY_FONT, fill=INK)
    y_cursor += 56
    draw.text((panel_x, y_cursor), "Allocation", font=SECTION_FONT, fill=INK)
    y_cursor += 48
    for note in diagram.notes:
        for line_index, line in enumerate(wrap_text(draw, note, PANEL_RIGHT - panel_x - 32, SMALL_FONT)):
            prefix = "- " if line_index == 0 else "  "
            draw.text((panel_x, y_cursor), prefix + line, font=SMALL_FONT, fill=INK)
            y_cursor += 29
        y_cursor += 7

    y_cursor += 12
    draw.text((panel_x, y_cursor), "Rows", font=SECTION_FONT, fill=INK)
    y_cursor += 47
    table_font = TINY_FONT if len(rows) > 14 else SMALL_FONT
    row_height = 36 if len(rows) > 14 else 42
    draw.text((panel_x, y_cursor), "Row    y       count    spot numbers", font=table_font, fill=MUTED)
    y_cursor += row_height
    for row, y_value, count, first_number, last_number in row_summary(diagram.spots):
        text = f"R{row:<2}  {y_value:>5}      {count:>3}      #{first_number}-#{last_number}"
        draw.text((panel_x, y_cursor), text, font=table_font, fill=INK)
        y_cursor += row_height

    draw.text((PLOT_LEFT, 1760), "Dark lines: WZ footholds", font=SMALL_FONT, fill=FOOTHOLD)
    draw.ellipse((PLOT_LEFT + 310, 1758, PLOT_LEFT + 330, 1778), fill=SPOT, outline=SPOT_DARK, width=2)
    draw.text((PLOT_LEFT + 342, 1760), "cataloged character/stall center", font=SMALL_FONT, fill=INK)
    if diagram.split_x is not None:
        draw.ellipse((PLOT_LEFT + 780, 1758, PLOT_LEFT + 800, 1778), fill=LEFT_SPOT, outline=LEFT_SPOT_DARK, width=2)
        draw.text((PLOT_LEFT + 812, 1760), "Southperry left half", font=SMALL_FONT, fill=INK)

    draw.text(
        (PLOT_LEFT, 1825),
        "Coordinates are read directly from the reservation catalog and local Map.wz XML. MapleStory y increases downward.",
        font=SMALL_FONT,
        fill=MUTED,
    )

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    output = OUTPUT_DIR / diagram.filename
    image.save(output, format="PNG", optimize=True)
    return output


def main() -> None:
    diagrams = [
        Diagram(
            "fm-henesys-spots.png",
            "Free Market - Henesys layout",
            "Representative room 1; the same 23-spot template is used by rooms 1-6.",
            910000001,
            parse_fm_spots("HENESYS"),
            ["One catalog slot per stall.", "Player placement checks only the nearest left and right spot.", "Maximum player-to-spot snap distance is 125 px."],
        ),
        Diagram(
            "fm-ludibrium-spots.png",
            "Free Market - Ludibrium layout",
            "Representative room 7; the same 28-spot template is used by rooms 7-12.",
            910000007,
            parse_fm_spots("LUDIBRIUM"),
            ["One catalog slot per stall.", "Rows follow the exact y groups in the catalog.", "The isolated final spot remains independently reservable."],
        ),
        Diagram(
            "fm-perion-spots.png",
            "Free Market - Perion layout",
            "Representative room 13; the same 26-spot template is used by rooms 13-17.",
            910000013,
            parse_fm_spots("PERION"),
            ["One catalog slot per stall.", "Two center-platform spots form their own row.", "Reservations are independent per world, channel, and room."],
        ),
        Diagram(
            "fm-el-nath-spots.png",
            "Free Market - El Nath layout",
            "Representative room 18; the same 27-spot template is used by rooms 18-22.",
            910000018,
            parse_fm_spots("EL_NATH"),
            ["One catalog slot per stall.", "The lower row includes the detached left-side point.", "Portal clearance is validated before reservation."],
        ),
        Diagram(
            "amherst-character-spots.png",
            "Amherst character-space catalog",
            "All approved resting positions on map 1000000, sampled every 25 pixels.",
            1000000,
            parse_maple_spots("AMHERST"),
            ["Agents choose a random unreserved center.", "Adjacent slots can form a wider footprint for large chairs.", "Each row is a hand-approved horizontal foothold range."],
        ),
        Diagram(
            "southperry-character-spots.png",
            "Southperry character-space catalog",
            "Full-map catalog with left and right allocation views on map 2000000.",
            2000000,
            parse_maple_spots("SOUTHPERRY"),
            ["Teal points belong to the left-half view.", "Orange points belong to the right-half view used by the full Maple Island run.", "Adjacent slots support multi-slot chair footprints."],
            split_x=1890,
        ),
    ]
    for diagram in diagrams:
        output = draw_diagram(diagram)
        print(output.relative_to(ROOT))


if __name__ == "__main__":
    main()
