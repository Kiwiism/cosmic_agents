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
            "advance-to-magician",
            "first-training-session",
            "second-training-session",
            "third-training-session",
            "last-training-session",
            "buy-potions-and-supplies",
        ],
    },
    2: {
        "size": (460, 470),
        "centers": [(45, 150), (45, 229), (45, 354)],
        "names": [
            "eww-its-slimy",
            "i-need-help-on-my-homework",
            "why-are-dark-stumps-so-dark",
        ],
    },
    3: {
        "size": (460, 615),
        "centers": [(45, 150), (45, 229), (45, 308), (45, 409), (45, 530)],
        "names": [
            "destructively-strong-pigs",
            "red-ribbons-around-the-pigs-neck",
            "drowsiness-from-the-orange-mushrooms",
            "camouflaging-slimes",
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
    <circle r="14" fill="#7dd3fc" stroke="#eaf8ff" stroke-width="2"/>
    <path d="M-3.5-6.5L7 0-3.5 6.5z" fill="#10243a"/>
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
        base_path = ROOT / f"checkpoint-{checkpoint}.svg"
        base = base_path.read_text(encoding="utf-8")
        width, height = config["size"]
        centers = config["centers"]
        names = config["names"]

        render(base_path, ROOT / f"checkpoint-{checkpoint}.png", width, height)

        for current_index, current_name in enumerate(names):
            markers = [checked_marker(*center) for center in centers[:current_index]]
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

    render(ROOT / "checkmark.svg", ROOT / "checkmark.png", 30, 30)
    render(ROOT / "current-objective.svg", ROOT / "current-objective.png", 38, 30)


if __name__ == "__main__":
    main()
