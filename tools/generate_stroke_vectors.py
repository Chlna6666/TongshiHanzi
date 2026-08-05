#!/usr/bin/env python3
"""Generate the bundled stroke vector subset used by TongshiHanzi.

The source revision is deliberately pinned so APK builds remain reproducible.
Stroke data is fetched only while building; the application remains fully offline.
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

SOURCE_REPOSITORY = "chanind/hanzi-writer-data"
SOURCE_COMMIT = "68d10a4b21150cae5e1ebbd223eed289cf32d90c"
BASE_URL = (
    "https://raw.githubusercontent.com/"
    f"{SOURCE_REPOSITORY}/{SOURCE_COMMIT}/data/"
)


def fetch_character(character: str, retries: int = 3) -> dict:
    url = BASE_URL + urllib.parse.quote(character, safe="") + ".json"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "TongshiHanzi-dictionary-builder/1.0"},
    )
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as error:  # Network failures need retry context.
            last_error = error
            if attempt + 1 < retries:
                time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Unable to fetch stroke data for {character}: {last_error}")


def generate(seed_path: Path, output_path: Path, strict: bool) -> None:
    seed = json.loads(seed_path.read_text(encoding="utf-8"))
    output: dict[str, object] = {
        "source": {
            "repository": SOURCE_REPOSITORY,
            "commit": SOURCE_COMMIT,
            "licenseFiles": ["ARPHICPL.TXT", "README.md"],
        },
        "characters": {},
    }
    failures: list[str] = []
    characters: dict[str, dict] = output["characters"]  # type: ignore[assignment]

    for item in seed["characters"]:
        character = item["character"]
        try:
            data = fetch_character(character)
            strokes = data.get("strokes", [])
            medians = data.get("medians", [])
            if len(strokes) != len(medians):
                raise ValueError(
                    f"stroke/median count mismatch: {len(strokes)} != {len(medians)}"
                )
            expected = len(item.get("strokeNames", []))
            if expected and expected != len(strokes):
                raise ValueError(
                    f"seed/vector stroke count mismatch: {expected} != {len(strokes)}"
                )
            characters[character] = {"strokes": strokes, "medians": medians}
            print(f"Loaded {character}: {len(strokes)} strokes")
        except Exception as error:
            failures.append(f"{character}: {error}")

    if failures and strict:
        raise SystemExit("Stroke generation failed:\n" + "\n".join(failures))
    for failure in failures:
        print("WARNING:", failure)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(output, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"Wrote {len(characters)} characters to {output_path}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--seed",
        type=Path,
        default=Path("app/src/main/assets/dictionary/dictionary_seed.json"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/dictionary/stroke_vectors.json"),
    )
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    generate(args.seed, args.output, args.strict)


if __name__ == "__main__":
    main()
