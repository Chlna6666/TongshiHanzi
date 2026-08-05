#!/usr/bin/env python3
"""Build TongshiHanzi's bundled full offline dictionary.

The reviewed 25-character project seed remains the high-quality child-facing overlay.
Additional characters, definitions and words are generated from a pinned revision of
mapull/chinese-dictionary. The generated stream is gzip-compressed NDJSON so Android
can import it incrementally without retaining the complete dictionary in memory.
"""

from __future__ import annotations

import argparse
import copy
import gzip
import json
import re
import unicodedata
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable

SOURCE_REPOSITORY = "mapull/chinese-dictionary"
SOURCE_COMMIT = "e804ada333b68afddfdccbe8dcc938a72da157a7"
SOURCE_VERSION = "2025-02-04"
RAW_BASE = f"https://raw.githubusercontent.com/{SOURCE_REPOSITORY}/{SOURCE_COMMIT}/"
SOURCE_FILES = {
    "base": "character/char_base.json",
    "detail": "character/char_detail.json",
    "words": "word/word.json",
}
USER_AGENT = "TongshiHanzi-dictionary-builder/2.0"
MAX_DEFINITIONS_PER_READING = 6
MAX_WORDS_PER_READING = 8
MAX_WORD_CANDIDATES_PER_CHARACTER = 28
MAX_DEFINITION_LENGTH = 360
MAX_WORD_DEFINITION_LENGTH = 160
HAN_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
SPACE_RE = re.compile(r"\s+")

STRUCTURES = {
    "D0": "独体结构", "D1": "镶嵌结构", "A0": "品字形结构",
    "B0": "上下结构", "B1": "上下结构", "B2": "上下结构",
    "B3": "上下结构", "B4": "田字结构", "E0": "上中下结构",
    "E1": "上中下结构", "E2": "上中下结构", "H0": "左右结构",
    "H1": "左右结构", "H2": "左右结构", "H3": "左右结构",
    "M0": "左中右结构", "M1": "左中右结构", "M2": "左中右结构",
    "Q0": "全包围结构", "R0": "半包围结构", "R1": "半包围结构",
    "R2": "半包围结构", "R3": "半包围结构", "R4": "半包围结构",
    "R5": "半包围结构", "R6": "半包围结构",
}


def download(relative_path: str, cache_dir: Path) -> Path:
    destination = cache_dir / relative_path
    if destination.is_file() and destination.stat().st_size > 0:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        RAW_BASE + relative_path,
        headers={"User-Agent": USER_AGENT},
    )
    temporary = destination.with_suffix(destination.suffix + ".part")
    print(f"Downloading {relative_path}")
    with urllib.request.urlopen(request, timeout=180) as response, temporary.open("wb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            out.write(chunk)
    temporary.replace(destination)
    return destination


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def clean_text(value: Any, limit: int) -> str:
    if not isinstance(value, str):
        return ""
    text = SPACE_RE.sub(" ", value).strip()
    if len(text) > limit:
        text = text[: limit - 1].rstrip() + "…"
    return text


def normalize_pinyin(value: str) -> str:
    value = value.strip().lower().replace("u:", "v").replace("ü", "v")
    value = re.sub(r"[1-5]$", "", value)
    decomposed = unicodedata.normalize("NFD", value)
    return "".join(
        character for character in decomposed
        if unicodedata.category(character) != "Mn" and character.isalpha()
    )


def tone_number(value: str) -> int:
    stripped = value.strip().lower()
    if stripped and stripped[-1] in "12345":
        return int(stripped[-1])
    tone_marks = {
        **dict.fromkeys("āēīōūǖ", 1), **dict.fromkeys("áéíóúǘ", 2),
        **dict.fromkeys("ǎěǐǒǔǚ", 3), **dict.fromkeys("àèìòùǜ", 4),
    }
    for character in stripped:
        if character in tone_marks:
            return tone_marks[character]
    return 0


def unique_strings(values: Iterable[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        if not isinstance(value, str):
            continue
        text = value.strip()
        if text and text not in seen:
            seen.add(text)
            result.append(text)
    return result


def detail_index(entries: list[dict[str, Any]]) -> dict[str, dict[str, list[dict[str, Any]]]]:
    result: dict[str, dict[str, list[dict[str, Any]]]] = {}
    for entry in entries:
        character = entry.get("char")
        if not isinstance(character, str) or not character:
            continue
        readings: dict[str, list[dict[str, Any]]] = {}
        for reading in entry.get("pronunciations") or []:
            if not isinstance(reading, dict):
                continue
            pinyin = clean_text(reading.get("pinyin"), 32)
            if not pinyin:
                continue
            explanations = reading.get("explanations") or []
            readings[normalize_pinyin(pinyin)] = [
                item for item in explanations if isinstance(item, dict)
            ]
        result[character] = readings
    return result


def word_index(
    entries: list[dict[str, Any]], known_characters: set[str]
) -> dict[str, list[dict[str, str]]]:
    result: dict[str, list[dict[str, str]]] = defaultdict(list)
    for item in entries:
        if not isinstance(item, dict):
            continue
        word = clean_text(item.get("word"), 32)
        pinyin = clean_text(item.get("pinyin"), 96)
        explanation = clean_text(item.get("explanation"), MAX_WORD_DEFINITION_LENGTH)
        if len(word) < 2 or len(word) > 8 or not pinyin:
            continue
        characters = {character for character in word if character in known_characters}
        if not characters:
            continue
        compact = {"word": word, "pinyin": pinyin, "definition": explanation}
        for character in characters:
            bucket = result[character]
            if len(bucket) < MAX_WORD_CANDIDATES_PER_CHARACTER:
                bucket.append(compact)
    return result


def extract_definitions_and_words(
    explanations: list[dict[str, Any]], source_id: str
) -> tuple[list[str], list[dict[str, str]]]:
    definitions: list[str] = []
    words: list[dict[str, str]] = []
    seen_words: set[str] = set()
    for explanation in explanations:
        candidates = [
            explanation.get("content"),
            explanation.get("refer"),
            explanation.get("same"),
            explanation.get("modern"),
            explanation.get("simplified"),
            explanation.get("variant"),
        ]
        for candidate in candidates:
            text = clean_text(candidate, MAX_DEFINITION_LENGTH)
            if text and text not in definitions:
                definitions.append(text)
                break
        for value in explanation.get("words") or []:
            if not isinstance(value, dict):
                continue
            word = clean_text(value.get("word"), 32)
            if not word or word in seen_words:
                continue
            seen_words.add(word)
            words.append({
                "word": word,
                "pinyin": "",
                "definition": clean_text(
                    value.get("text") or value.get("example"),
                    MAX_WORD_DEFINITION_LENGTH,
                ),
                "sourceId": source_id,
            })
    return definitions[:MAX_DEFINITIONS_PER_READING], words


def matching_words(
    character: str,
    reading: str,
    candidates: list[dict[str, str]],
    primary: bool,
) -> list[dict[str, str]]:
    normalized = normalize_pinyin(reading)
    matched: list[dict[str, str]] = []
    fallback: list[dict[str, str]] = []
    seen: set[str] = set()
    for item in candidates:
        word = item["word"]
        if word in seen:
            continue
        seen.add(word)
        syllables = [normalize_pinyin(value) for value in item["pinyin"].split()]
        han = [value for value in word if HAN_RE.fullmatch(value)]
        positions = [index for index, value in enumerate(han) if value == character]
        is_match = False
        if len(syllables) == len(han):
            is_match = any(
                position < len(syllables) and syllables[position] == normalized
                for position in positions
            )
        compact = {
            "word": word,
            "pinyin": item["pinyin"],
            "definition": item["definition"],
            "sourceId": "mapull",
        }
        if is_match:
            matched.append(compact)
        elif primary:
            fallback.append(compact)
    return (matched + fallback)[:MAX_WORDS_PER_READING]


def build_character(
    base: dict[str, Any],
    details: dict[str, dict[str, list[dict[str, Any]]]],
    words: dict[str, list[dict[str, str]]],
) -> dict[str, Any] | None:
    character = base.get("char")
    if not isinstance(character, str) or len(character) != 1:
        return None
    pinyin_values = unique_strings(base.get("pinyin") or [])
    detail_readings = details.get(character, {})
    if not pinyin_values:
        pinyin_values = unique_strings(
            reading.get("pinyin")
            for reading in base.get("pronunciations") or []
            if isinstance(reading, dict)
        )
    if not pinyin_values and detail_readings:
        # Detail keys are normalized, so retaining them is better than dropping a rare character.
        pinyin_values = list(detail_readings.keys())
    if not pinyin_values:
        return None

    base_index = int(base.get("index") or 0)
    frequency = int(base.get("frequency") or 5)
    readings: list[dict[str, Any]] = []
    for order, pinyin in enumerate(pinyin_values):
        explanation_values = detail_readings.get(normalize_pinyin(pinyin), [])
        definitions, embedded_words = extract_definitions_and_words(
            explanation_values, "mapull"
        )
        if not definitions:
            definitions = ["该字的基础读音、部首和笔画信息已收录；释义仍待进一步审校。"]
        global_words = matching_words(
            character,
            pinyin,
            words.get(character, []),
            primary=order == 0,
        )
        merged_words: list[dict[str, str]] = []
        seen_words: set[str] = set()
        for item in embedded_words + global_words:
            word = item.get("word", "")
            if word and word not in seen_words:
                seen_words.add(word)
                merged_words.append(item)
            if len(merged_words) >= MAX_WORDS_PER_READING:
                break
        readings.append({
            "pinyin": pinyin,
            "tone": tone_number(pinyin),
            "speakWord": merged_words[0]["word"] if merged_words else character,
            "definitions": definitions,
            "words": merged_words,
        })

    traditional = base.get("traditional")
    if isinstance(traditional, list):
        traditional = "".join(unique_strings(traditional))
    if not isinstance(traditional, str) or not traditional:
        traditional = character
    structure_code = str(base.get("structure") or "")
    return {
        "id": 100000 + base_index,
        "character": character,
        "simplified": character,
        "traditional": traditional,
        "unicode": f"U+{ord(character):04X}",
        "radical": clean_text(base.get("radicals"), 16) or "—",
        "strokes": int(base.get("strokes") or 0),
        "structure": STRUCTURES.get(structure_code, "未分类结构"),
        "strokeNumber": "",
        "frequencyRank": base_index if base_index > 0 else 99999,
        "common": frequency <= 2,
        "sourceId": "mapull",
        "wubi86": [],
        "strokeNames": [],
        "pronunciations": readings,
    }


def manifest(character_count: int, skipped_count: int) -> dict[str, Any]:
    return {
        "formatVersion": 2,
        "dataVersion": f"mapull-{SOURCE_VERSION}-{SOURCE_COMMIT[:12]}",
        "sourceRepository": SOURCE_REPOSITORY,
        "sourceCommit": SOURCE_COMMIT,
        "characterCount": character_count,
        "skippedCount": skipped_count,
        "sources": [
            {
                "sourceId": "project",
                "name": "TongshiHanzi reviewed child-facing seed",
                "version": "2026.08",
                "licenseId": "GPL-3.0-or-later",
                "attribution": "Copyright (C) 2026 Chlna6666",
                "modificationNote": "Original reviewed child-facing definitions and metadata overrides.",
            },
            {
                "sourceId": "unicode",
                "name": "Unicode character metadata",
                "version": "Unicode 17.0 compatible fields",
                "licenseId": "Unicode-3.0",
                "attribution": "Unicode, Inc.",
                "modificationNote": "Code-point formatting normalized for the application.",
            },
            {
                "sourceId": "mapull",
                "name": "mapull/chinese-dictionary",
                "version": SOURCE_COMMIT,
                "licenseId": "MIT",
                "attribution": "Copyright (c) 2021 码谱",
                "modificationNote": (
                    "Normalized, deduplicated and size-bounded for offline use. "
                    "Definitions require continued editorial review."
                ),
            },
        ],
    }


def generate(
    curated_path: Path,
    output_path: Path,
    manifest_path: Path,
    cache_dir: Path,
    force: bool,
) -> None:
    if not force and output_path.is_file() and manifest_path.is_file():
        existing = read_json(manifest_path)
        if (
            existing.get("sourceCommit") == SOURCE_COMMIT
            and output_path.stat().st_size > 1024
        ):
            print(
                f"Full dictionary already matches {SOURCE_COMMIT[:12]} "
                f"({existing.get('characterCount', '?')} characters)"
            )
            return

    curated_root = read_json(curated_path)
    curated_entries = {
        item["character"]: copy.deepcopy(item)
        for item in curated_root.get("characters", [])
        if isinstance(item, dict) and isinstance(item.get("character"), str)
    }
    base_entries = read_json(download(SOURCE_FILES["base"], cache_dir))
    detail_entries = read_json(download(SOURCE_FILES["detail"], cache_dir))
    word_entries = read_json(download(SOURCE_FILES["words"], cache_dir))
    known_characters = {
        item.get("char") for item in base_entries
        if isinstance(item, dict) and isinstance(item.get("char"), str)
    }
    known_characters.discard(None)
    details = detail_index(detail_entries)
    words = word_index(word_entries, known_characters)

    generated: list[dict[str, Any]] = []
    skipped = 0
    emitted: set[str] = set()
    for base in sorted(
        (item for item in base_entries if isinstance(item, dict)),
        key=lambda item: int(item.get("index") or 999999),
    ):
        character = base.get("char")
        if character in curated_entries:
            entry = curated_entries[character]
            entry["id"] = int(entry.get("id") or len(generated) + 1)
            generated.append(entry)
            emitted.add(character)
            continue
        entry = build_character(base, details, words)
        if entry is None:
            skipped += 1
            continue
        generated.append(entry)
        emitted.add(entry["character"])

    for character, entry in curated_entries.items():
        if character not in emitted:
            generated.append(entry)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as compressed:
            for entry in generated:
                line = json.dumps(entry, ensure_ascii=False, separators=(",", ":"))
                compressed.write(line.encode("utf-8"))
                compressed.write(b"\n")
    result_manifest = manifest(len(generated), skipped)
    manifest_path.write_text(
        json.dumps(result_manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Generated {len(generated)} characters; skipped {skipped}; "
        f"compressed size {output_path.stat().st_size / 1024 / 1024:.2f} MiB"
    )
    if len(generated) < 8000:
        raise SystemExit("Generated dictionary is unexpectedly small")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--curated",
        type=Path,
        default=Path("app/src/main/assets/dictionary/dictionary_seed.json"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/dictionary/full_dictionary.ndjson.gz"),
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("app/src/main/assets/dictionary/full_dictionary_manifest.json"),
    )
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=Path(".cache/mapull-chinese-dictionary") / SOURCE_COMMIT,
    )
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    generate(args.curated, args.output, args.manifest, args.cache_dir, args.force)


if __name__ == "__main__":
    main()
