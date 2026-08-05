#!/usr/bin/env python3
"""Build an indexed, on-demand stroke-order pack for TongshiHanzi.

Make Me a Hanzi is the primary PRC stroke-order source. AnimCJK's ZhHans corpus is a
secondary source for simplified and uncommon characters missing from the primary set.
Each character payload is compressed independently so Android can memory-map the index
and inflate only the character currently displayed. Font outlines are never used to
invent stroke order.
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
from dataclasses import dataclass
from pathlib import Path
from typing import Any

USER_AGENT = "TongshiHanzi-stroke-pack-builder/2.0"
MAGIC = b"TSHS"
FORMAT_VERSION = 1
HEADER = struct.Struct(">4sII")
INDEX_ENTRY = struct.Struct(">IQII")


@dataclass(frozen=True)
class Source:
    source_id: str
    repository: str
    commit: str
    file: str
    license_id: str
    note: str

    @property
    def url(self) -> str:
        return (
            "https://raw.githubusercontent.com/"
            f"{self.repository}/{self.commit}/{self.file}"
        )

    @property
    def cache_path(self) -> Path:
        return Path(".cache/stroke-sources") / self.source_id / self.commit / self.file


SOURCES = (
    Source(
        source_id="makemeahanzi",
        repository="skishore/makemeahanzi",
        commit="bddc96d41bef78427ed0e034e9f7e31d71fd1b92",
        file="graphics.txt",
        license_id="Arphic-1999",
        note="Primary PRC stroke-order vectors.",
    ),
    Source(
        source_id="animcjk-zh-hans",
        repository="parsimonhi/animCJK",
        commit="ec5e17cca76c87587790bcbce5ea0b4d4fb753d6",
        file="graphicsZhHans.txt",
        license_id="Arphic-1999",
        note="Secondary ZhHans fallback for additional uncommon characters.",
    ),
)


def download(source: Source, destination: Path, retries: int = 3, force: bool = False) -> Path:
    if destination.is_file() and destination.stat().st_size > 1024 and not force:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    request = urllib.request.Request(source.url, headers={"User-Agent": USER_AGENT})
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            print(f"Downloading {source.source_id}/{source.file} (attempt {attempt + 1}/{retries})")
            with urllib.request.urlopen(request, timeout=360) as response, temporary.open("wb") as target:
                shutil.copyfileobj(response, target, length=1024 * 1024)
            temporary.replace(destination)
            return destination
        except Exception as error:
            last_error = error
            temporary.unlink(missing_ok=True)
            if attempt + 1 < retries:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Unable to download {source.url}: {last_error}")


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
    if not isinstance(strokes, list) or not strokes or not all(
        isinstance(value, str) and value for value in strokes
    ):
        raise ValueError(f"{character}: invalid stroke paths")
    if not isinstance(medians, list) or len(medians) != len(strokes):
        raise ValueError(f"{character}: stroke/median count mismatch")
    for median in medians:
        if not isinstance(median, list) or len(median) < 2:
            raise ValueError(f"{character}: invalid median")
    return character, strokes, medians


def build_pack(
    source_paths: list[tuple[Source, Path]],
    seed_path: Path,
    output_path: Path,
    manifest_path: Path,
    strict_curated: bool,
) -> None:
    curated = read_curated_counts(seed_path)
    curated_seen: dict[str, int] = {}
    curated_rejections: dict[str, list[str]] = {}
    index: list[tuple[int, int, int, int]] = []
    codepoints: set[int] = set()
    source_counts: dict[str, int] = {source.source_id: 0 for source, _ in source_paths}
    payload_path = output_path.with_suffix(output_path.suffix + ".payload")
    payload_path.parent.mkdir(parents=True, exist_ok=True)
    relative_offset = 0

    with payload_path.open("wb") as payload_file:
        for source_info, graphics_path in source_paths:
            with graphics_path.open("r", encoding="utf-8-sig") as source:
                for line_number, line in enumerate(source, 1):
                    if not line.strip():
                        continue
                    try:
                        record = json.loads(line)
                        character, strokes, medians = validate_record(record)
                    except Exception as error:
                        raise ValueError(
                            f"Invalid {source_info.source_id} record at line {line_number}: {error}"
                        ) from error
                    codepoint = ord(character)
                    if codepoint in codepoints:
                        continue

                    if character in curated and len(strokes) != curated[character]:
                        curated_rejections.setdefault(character, []).append(
                            f"{source_info.source_id}:{len(strokes)}"
                        )
                        continue

                    raw = json.dumps(
                        {"strokes": strokes, "medians": medians},
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ).encode("utf-8")
                    compressed = zlib.compress(raw, level=6)
                    payload_file.write(compressed)
                    index.append((codepoint, relative_offset, len(compressed), len(raw)))
                    relative_offset += len(compressed)
                    codepoints.add(codepoint)
                    source_counts[source_info.source_id] += 1

                    if character in curated:
                        curated_seen[character] = len(strokes)

    missing_curated = sorted(set(curated) - set(curated_seen))
    if strict_curated and missing_curated:
        details = [
            f"{character} (expected {curated[character]}, rejected {curated_rejections.get(character, [])})"
            for character in missing_curated
        ]
        raise SystemExit("Missing compatible curated stroke vectors:\n" + "\n".join(details))
    if not index:
        raise SystemExit("Stroke sources produced no records")

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
        "characterCount": len(index),
        "curatedCharacterCount": len(curated),
        "validatedCuratedCount": len(curated_seen),
        "missingCurated": missing_curated,
        "sourceCounts": source_counts,
        "sources": [
            {
                "sourceId": source.source_id,
                "repository": source.repository,
                "commit": source.commit,
                "file": source.file,
                "licenseId": source.license_id,
                "note": source.note,
            }
            for source, _ in source_paths
        ],
        "sha256": digest,
        "notes": (
            "PRC/ZhHans stroke order. Primary records come from Make Me a Hanzi; "
            "AnimCJK contributes only code points absent from the primary corpus. "
            "Payloads are independently zlib-compressed and loaded on demand."
        ),
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Built {len(index)} stroke records {source_counts}, "
        f"validated {len(curated_seen)}/{len(curated)} curated characters, "
        f"pack size {output_path.stat().st_size / 1024 / 1024:.2f} MiB"
    )


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

    source_paths = [
        (source, download(source, source.cache_path, force=args.force_download))
        for source in SOURCES
    ]
    build_pack(
        source_paths,
        args.seed,
        args.output,
        args.manifest,
        args.strict_curated,
    )


if __name__ == "__main__":
    main()
