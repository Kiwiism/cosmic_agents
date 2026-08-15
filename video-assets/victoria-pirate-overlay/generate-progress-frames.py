from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "progress-frames"
CHROME = Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe")
CHECKPOINTS = {
    1: {"size": (460, 615), "centers": [(45,150),(45,207),(45,286),(45,365),(45,444),(45,561)], "names": ["advance-to-pirate","first-training-session","second-training-session","third-training-session","fourth-training-session","buy-potions-and-supplies"]},
    2: {"size": (460, 615), "centers": [(45,150),(45,229),(45,308),(45,387),(45,530)], "names": ["destructively-strong-pigs","red-ribbons-around-the-pigs-neck","drowsiness-from-the-orange-mushrooms","camouflaging-slimes","reach-level-15"]},
}

def checked(x, y):
    return f'<g aria-label="completed" transform="translate({x} {y})"><circle r="14" fill="#7d8992" stroke="#f0f4f6" stroke-width="2"/><path d="M-7 .2l4.3 4.4L7.5-5.6" fill="none" stroke="#fff" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/></g>'

def current(x, y):
    return f'<g aria-label="current objective" transform="translate({x} {y})"><circle r="14" fill="#dce3e7" stroke="#fff" stroke-width="2"/><path d="M-3.5-6.5L7 0-3.5 6.5z" fill="#263038"/></g>'

def render(svg, png, width, height):
    subprocess.run([str(CHROME),"--headless=new","--disable-gpu","--hide-scrollbars","--default-background-color=00000000","--force-device-scale-factor=1",f"--window-size={width},{height}",f"--screenshot={png}",svg.as_uri()], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def main():
    if not CHROME.exists(): raise SystemExit(f"Chrome was not found at {CHROME}")
    OUTPUT.mkdir(exist_ok=True)
    for number, config in CHECKPOINTS.items():
        base_path = ROOT / f"checkpoint-{number}.svg"; base = base_path.read_text(encoding="utf-8")
        width, height = config["size"]; centers = config["centers"]; names = config["names"]
        render(base_path, ROOT / f"checkpoint-{number}.png", width, height)
        for index, name in enumerate(names):
            markers = [checked(*point) for point in centers[:index]] + [current(*centers[index])]
            stem = f"checkpoint-{number}-{index+1:02d}-current-{name}"; svg = OUTPUT / f"{stem}.svg"
            svg.write_text(base.replace("</svg>", "".join(markers)+"\n</svg>"), encoding="utf-8"); render(svg, OUTPUT / f"{stem}.png", width, height)
        stem = f"checkpoint-{number}-{len(names)+1:02d}-all-complete"; svg = OUTPUT / f"{stem}.svg"
        svg.write_text(base.replace("</svg>", "".join(checked(*point) for point in centers)+"\n</svg>"), encoding="utf-8"); render(svg, OUTPUT / f"{stem}.png", width, height)
    render(ROOT/"checkmark.svg", ROOT/"checkmark.png", 30, 30); render(ROOT/"current-objective.svg", ROOT/"current-objective.png", 38, 30)

if __name__ == "__main__": main()
