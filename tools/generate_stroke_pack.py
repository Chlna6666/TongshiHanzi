#!/usr/bin/env python3
"""Build an indexed, on-demand stroke-order pack for TongshiHanzi.

The source is Make Me a Hanzi's PRC-oriented graphics.txt. Each character payload is
compressed independently so Android can memory-map the index and inflate only the
character currently displayed. Font outlines are never used to invent stroke order.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
import time
import urllib.request
import zlib
from pathlib import Path
from typing import Any

SOURCE_REPOSITORY = "skishore/makemeahanzi"
SOURCE_COMMIT = "bddc96d41bef78427ed0e034e9f7e31d71fd1b92"
SOURCE_FILE = "graphics.txt"
SOURCE_URL = (
    "https://raw.githubusercontent.com/"
    f"{SOURCE_REPOSITORY}/{SOURCE_COMMIT}/{SOURCE_FILE}"
)
USER_AGENT = "TongshiHanzi-stroke-pack-builder/1.0"
MAGIC = b"TSHS"
FORMAT_VERSION = 1
HEADER = struct.Struct(">4sII")
INDEX_ENTRY = struct.Struct(">IQII")


def download(destination: Path, retries: int = 3, force: bool = False) -> Path:
    if destination.is_file() and destination.stat().st_size > 1024 and not force:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(SOURCE_URL, headers={"User-Agent": USER_AGENT})
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            print(f"Downloading {SOURCE_FILE} (attempt {attempt + 1}/{retries})")
            with urllib.request.urlopen(request, timeout=300) as response, temporary.open("wb") as target:
                shutil.copyfileobj(response, target, length=1024 * 1024)
            temporary.replace(destination)
            return destination
        except Exception as error:
            last_error = error
            temporary.unlink(missing_ok=True)
            if attempt + 1 < retries:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Unable to download {SOURCE_URL}: {last_error}")


def read_curated_counts(seed_path: Path) -> dict[str, int]:
    seed = json.loads(seed_path.read_text(encoding="utf-8"))
    result: dict[str, int] = {}
    for item in seed.get("characters", []):
        character = item.get("character")
        if not isinstance(character, str) or len(character) != 1:
            continue
        names = item.get("strokeNames")
        expected = len(names) if isinstance(names, list) and names else int(item.get("strokes", 0))
        if expected > 0:
            result[character] = expected
    return result


def validate_record(record: dict[str, Any]) -> tuple[str, list[str], list[list[list[int]]]]:
    character = record.get("character")
    strokes = record.get("strokes")
    medians = record.get("medians")
    if not isinstance(character, str) or len(character) != 1:
        raise ValueError("character must be exactly one Unicode scalar")
    if not isinstance(strokes, list) or not strokes or not all(isinstance(value, str) and value for value in strokes):
        raise ValueError(f"{character}: invalid stroke paths")
    if not isinstance(medians, list) or len(medians) != len(strokes):
        raise ValueError(f"{character}: stroke/median count mismatch")
    for median in medians:
        if not isinstance(median, list) or len(median) < 2:
            raise ValueError(f"{character}: invalid median")
    return character, strokes, medians


def build_pack(
    graphics_path: Path,
    seed_path: Path,
    output_path: Path,
    manifest_path: Path,
    strict_curated: bool,
) -> None:
    curated = read_curated_counts(seed_path)
    curated_seen: dict[str, int] = {}
    index: list[tuple[int, int, int, int]] = []
    codepoints: set[int] = set()
    payload_path = output_path.with_suffix(output_path.suffix + ".payload")
    payload_path.parent.mkdir(parents=True, exist_ok=True)
    relative_offset = 0

    with graphics_path.open("r", encoding="utf-8-sig") as source, payload_path.open("wb") as payload_file:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
                character, strokes, medians = validate_record(record)
            except Exception as error:
                raise ValueError(f"Invalid graphics record at line {line_number}: {error}") from error
            codepoint = ord(character)
            if codepoint in codepoints:
                raise ValueError(f"Duplicate stroke record for {character} U+{codepoint:04X}")
            codepoints.add(codepoint)

            raw = json.dumps(
                {"strokes": strokes, "medians": medians},
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
            compressed = zlib.compress(raw, level=6)
            payload_file.write(compressed)
            index.append((codepoint, relative_offset, len(compressed), len(raw)))
            relative_offset += len(compressed)

            if character in curated:
                actual = len(strokes)
                curated_seen[character] = actual
                expected = curated[character]
                if actual != expected:
                    raise ValueError(
                        f"Curated stroke count mismatch for {character}: seed={expected}, vector={actual}"
                    )

    missing_curated = sorted(set(curated) - set(curated_seen))
    if strict_curated and missing_curated:
        raise SystemExit("Missing curated stroke vectors: " + " ".join(missing_curated))
    if not index:
        raise SystemExit("Stroke source produced no records")

    payload_base = HEADER.size + INDEX_ENTRY.size * len(index)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as output:
        output.write(HEADER.pack(MAGIC, FORMAT_VERSION, len(index)))
        for codepoint, relative, compressed_length, raw_length in index:
            output.write(INDEX_ENTRY.pack(
                codepoint,
                payload_base + relative,
                compressed_length,
                raw_length,
            ))
        with payload_path.open("rb") as payload_file:
            shutil.copyfileobj(payload_file, output, length=1024 * 1024)
    payload_path.unlink(missing_ok=True)

    digest = hashlib.sha256(output_path.read_bytes()).hexdigest()
    manifest = {
        "format": "TSHS",
        "formatVersion": FORMAT_VERSION,
        "sourceRepository": SOURCE_REPOSITORY,
        "sourceCommit": SOURCE_COMMIT,
        "sourceFile": SOURCE_FILE,
        "licenseId": "Arphic-1999",
        "characterCount": len(index),
        "curatedCharacterCount": len(curated),
        "validatedCuratedCount": len(curated_seen),
        "missingCurated": missing_curated,
        "sha256": digest,
        "notes": "PRC stroke order. Payloads are independently zlib-compressed and loaded on demand.",
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Built {len(index)} stroke records, validated {len(curated_seen)}/{len(curated)} curated characters, "
        f"pack size {output_path.stat().st_size / 1024 / 1024:.2f} MiB"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source",
        type=Path,
        default=Path(".cache/makemeahanzi") / SOURCE_COMMIT / SOURCE_FILE,
    )
    parser.add_argument(
        "--seed",
        type=Path,
        default=Path("app/src/main/assets/dictionary/dictionary_seed.json"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/dictionary/stroke_pack.tshs"),
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("app/src/main/assets/dictionary/stroke_pack_manifest.json"),
    )
    parser.add_argument("--force-download", action="store_true")
    parser.add_argument("--strict-curated", action="store_true")
    args = parser.parse_args()

    graphics_path = download(args.source, force=args.force_download)
    build_pack(
        graphics_path,
        args.seed,
        args.output,
        args.manifest,
        args.strict_curated,
    )


if __name__ == "__main__":
    main()
