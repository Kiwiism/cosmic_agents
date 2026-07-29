from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "progress-frames"
CHROME = Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")

CHECKPOINTS = {
    1: {
        "size": (460, 615),
        "centers": [(45, 150), (45, 207), (45, 286), (45, 365), (45, 444), (45, 561)],
        "names": [
            "advance-to-thief",
            "first-training-session",
            "second-training-session",
            "third-training-session",
            "last-training-session",
            "buy-potions-and-supplies",
        ],
    },
    2: {
        "size": (460, 560),
        "centers": [(45, 150), (45, 229), (45, 308), (45, 410), (45, 489)],
        "names": [
            "intimidating-octopuses",
            "im-bored-1",
            "im-bored-2",
            "pigs-at-the-corner",
            "that-red-isnt-for-everyone",
        ],
    },
    3: {
        "size": (460, 515),
        "centers": [(45, 150), (45, 252), (45, 331), (45, 470)],
        "names": [
            "sweep-the-snails",
            "the-stump-horror-story",
            "preparations-for-the-traditional-ceremony",
            "reach-level-15",
        ],
    },
}


def checked_marker(x: int, y: int) -> str:
    return f"""
  <g aria-label="completed" transform="translate({x} {y})">
    <circle r="14" fill="#43c77b" stroke="#e8fff1" stroke-width="2"/>
    <path d="M-7 .2l4.3 4.4L7.5-5.6" fill="none" stroke="#ffffff"
          stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/>
  </g>"""


def current_marker(x: int, y: int) -> str:
    return f"""
  <g aria-label="current objective" transform="translate({x} {y})">
    <circle r="14" fill="#f6c85f" stroke="#fff8e7" stroke-width="2"/>
    <path d="M-3.5-6.5L7 0-3.5 6.5z" fill="#142335"/>
  </g>"""


def render(svg_path: Path, png_path: Path, width: int, height: int) -> None:
    subprocess.run(
        [
            str(CHROME),
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--default-background-color=00000000",
            "--force-device-scale-factor=1",
            f"--window-size={width},{height}",
            f"--screenshot={png_path}",
            svg_path.as_uri(),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def main() -> None:
    if not CHROME.exists():
        raise SystemExit(f"Chrome was not found at {CHROME}")

    OUTPUT.mkdir(exist_ok=True)

    for checkpoint, config in CHECKPOINTS.items():
        base = (ROOT / f"checkpoint-{checkpoint}.svg").read_text(encoding="utf-8")
        width, height = config["size"]
        centers = config["centers"]
        names = config["names"]

        for current_index, current_name in enumerate(names):
            markers = [
                checked_marker(*center)
                for center in centers[:current_index]
            ]
            markers.append(current_marker(*centers[current_index]))
            composed = base.replace("</svg>", "".join(markers) + "\n</svg>")
            stem = f"checkpoint-{checkpoint}-{current_index + 1:02d}-current-{current_name}"
            svg_path = OUTPUT / f"{stem}.svg"
            png_path = OUTPUT / f"{stem}.png"
            svg_path.write_text(composed, encoding="utf-8")
            render(svg_path, png_path, width, height)

        markers = [checked_marker(*center) for center in centers]
        composed = base.replace("</svg>", "".join(markers) + "\n</svg>")
        stem = f"checkpoint-{checkpoint}-{len(names) + 1:02d}-all-complete"
        svg_path = OUTPUT / f"{stem}.svg"
        png_path = OUTPUT / f"{stem}.png"
        svg_path.write_text(composed, encoding="utf-8")
        render(svg_path, png_path, width, height)


if __name__ == "__main__":
    main()
