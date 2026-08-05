#!/usr/bin/env python3
"""Generate TongshiHanzi's complete bundled offline dictionary.

The generator keeps the project-reviewed 25-character seed as an editorial overlay
and fills the remaining corpus from a pinned mapull/chinese-dictionary revision.
Upstream files are JSON Lines despite their .json suffix, so records are streamed.
The generated Android asset is deterministic gzip-compressed NDJSON.
"""

from __future__ import annotations

import argparse
import copy
import gzip
import json
import re
import time
import unicodedata
import urllib.request
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable, Iterator

SOURCE_REPOSITORY = "mapull/chinese-dictionary"
SOURCE_COMMIT = "e804ada333b68afddfdccbe8dcc938a72da157a7"
SOURCE_VERSION = "2025-02-04"
RAW_BASE = f"https://raw.githubusercontent.com/{SOURCE_REPOSITORY}/{SOURCE_COMMIT}/"
SOURCE_FILES = {
    "base": "character/char_base.json",
    "detail": "character/char_detail.json",
    "words": "word/word.json",
}
USER_AGENT = "TongshiHanzi-dictionary-builder/2.1"
MAX_DEFINITIONS_PER_READING = 6
MAX_WORDS_PER_READING = 8
MAX_WORD_CANDIDATES_PER_CHARACTER = 36
MAX_DEFINITION_LENGTH = 360
MAX_WORD_DEFINITION_LENGTH = 180
SPACE_RE = re.compile(r"\s+")
PINYIN_SEPARATOR_RE = re.compile(r"[,，、/;；]+")
HAN_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")

STRUCTURES = {
    "D0": "独体结构",
    "D1": "镶嵌结构",
    "A0": "品字形结构",
    "B0": "上下结构",
    "B1": "上下结构",
    "B2": "上下结构",
    "B3": "上下结构",
    "B4": "田字结构",
    "E0": "上中下结构",
    "E1": "上中下结构",
    "E2": "上中下结构",
    "H0": "左右结构",
    "H1": "左右结构",
    "H2": "左右结构",
    "H3": "左右结构",
    "M0": "左中右结构",
    "M1": "左中右结构",
    "M2": "左中右结构",
    "Q0": "全包围结构",
    "R0": "半包围结构",
    "R1": "半包围结构",
    "R2": "半包围结构",
    "R3": "半包围结构",
    "R4": "半包围结构",
    "R5": "半包围结构",
    "R6": "半包围结构",
}


def download(relative_path: str, cache_dir: Path, retries: int = 3) -> Path:
    destination = cache_dir / relative_path
    if destination.is_file() and destination.stat().st_size > 0:
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        RAW_BASE + relative_path,
        headers={"User-Agent": USER_AGENT},
    )
    temporary = destination.with_suffix(destination.suffix + ".part")
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            print(f"Downloading {relative_path} (attempt {attempt + 1}/{retries})")
            with urllib.request.urlopen(request, timeout=240) as response, temporary.open("wb") as output:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    output.write(chunk)
            temporary.replace(destination)
            return destination
        except Exception as error:
            last_error = error
            temporary.unlink(missing_ok=True)
            if attempt + 1 < retries:
                time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Unable to download {relative_path}: {last_error}")


def iter_json_records(path: Path) -> Iterator[dict[str, Any]]:
    """Read a normal JSON array/object or newline-delimited JSON records."""
    with path.open("r", encoding="utf-8-sig") as source:
        first_line = ""
        for line in source:
            if line.strip():
                first_line = line
                break
        if not first_line:
            return
        first_non_space = first_line.lstrip()[:1]
        if first_non_space in ("[", "{"):
            # A leading object can still be JSONL. Try that line first, then continue
            # line-by-line; if it is a multi-line document, fall back to full parsing.
            if first_non_space == "{":
                try:
                    value = json.loads(first_line)
                except json.JSONDecodeError:
                    remainder = first_line + source.read()
                    parsed = json.loads(remainder)
                    if isinstance(parsed, list):
                        for item in parsed:
                            if isinstance(item, dict):
                                yield item
                    elif isinstance(parsed, dict):
                        yield parsed
                    return
                if isinstance(value, dict):
                    yield value
                for line_number, line in enumerate(source, 2):
                    stripped = line.strip()
                    if not stripped:
                        continue
                    try:
                        value = json.loads(stripped)
                    except json.JSONDecodeError as error:
                        raise ValueError(
                            f"{path}:{line_number}: invalid JSONL record: {error}"
                        ) from error
                    if isinstance(value, dict):
                        yield value
                return

            document = first_line + source.read()
            parsed = json.loads(document)
            if isinstance(parsed, list):
                for item in parsed:
                    if isinstance(item, dict):
                        yield item
            elif isinstance(parsed, dict):
                yield parsed
            return
        raise ValueError(f"Unsupported dictionary format: {path}")


def read_json_document(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as source:
        return json.load(source)


def as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    if isinstance(value, tuple):
        return list(value)
    return [value]


def clean_text(value: Any, limit: int) -> str:
    if isinstance(value, (list, tuple)):
        value = "、".join(
            item.strip() for item in value if isinstance(item, str) and item.strip()
        )
    if not isinstance(value, str):
        return ""
    text = SPACE_RE.sub(" ", value).strip()
    if len(text) > limit:
        return text[: limit - 1].rstrip() + "…"
    return text


def safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def unique_strings(values: Iterable[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        if not isinstance(value, str):
            continue
        for part in PINYIN_SEPARATOR_RE.split(value):
            text = part.strip()
            if text and text not in seen:
                seen.add(text)
                result.append(text)
    return result


def normalize_pinyin(value: str) -> str:
    value = value.strip().lower().replace("u:", "v").replace("ü", "v")
    value = re.sub(r"[1-5]$", "", value)
    decomposed = unicodedata.normalize("NFD", value)
    return "".join(
        character
        for character in decomposed
        if unicodedata.category(character) != "Mn" and character.isalpha()
    )


def tone_number(value: str) -> int:
    stripped = value.strip().lower()
    if stripped and stripped[-1] in "12345":
        return int(stripped[-1])
    tone_marks = {
        **dict.fromkeys("āēīōūǖ", 1),
        **dict.fromkeys("áéíóúǘ", 2),
        **dict.fromkeys("ǎěǐǒǔǚ", 3),
        **dict.fromkeys("àèìòùǜ", 4),
    }
    for character in stripped:
        if character in tone_marks:
            return tone_marks[character]
    return 0


def build_detail_index(path: Path) -> dict[str, dict[str, list[dict[str, Any]]]]:
    result: dict[str, dict[str, list[dict[str, Any]]]] = {}
    for entry in iter_json_records(path):
        character = entry.get("char")
        if not isinstance(character, str) or not character:
            continue
        readings: dict[str, list[dict[str, Any]]] = {}
        for reading in as_list(entry.get("pronunciations")):
            if not isinstance(reading, dict):
                continue
            pinyin = clean_text(reading.get("pinyin"), 32)
            if not pinyin:
                continue
            explanations = [
                item
                for item in as_list(reading.get("explanations"))
                if isinstance(item, dict)
            ]
            readings[normalize_pinyin(pinyin)] = explanations
        result[character] = readings
    return result


def build_word_index(
    path: Path,
    known_characters: set[str],
) -> dict[str, list[dict[str, str]]]:
    result: dict[str, list[dict[str, str]]] = defaultdict(list)
    accepted = 0
    for item in iter_json_records(path):
        word = clean_text(item.get("word"), 32)
        pinyin = clean_text(item.get("pinyin"), 96)
        explanation = clean_text(
            item.get("explanation"), MAX_WORD_DEFINITION_LENGTH
        )
        if len(word) < 2 or len(word) > 8 or not pinyin:
            continue
        characters = {character for character in word if character in known_characters}
        if not characters:
            continue
        compact = {
            "word": word,
            "pinyin": pinyin,
            "definition": explanation,
        }
        inserted = False
        for character in characters:
            bucket = result[character]
            if len(bucket) < MAX_WORD_CANDIDATES_PER_CHARACTER:
                bucket.append(compact)
                inserted = True
        if inserted:
            accepted += 1
    print(f"Indexed {accepted} bounded word candidates for {len(result)} characters")
    return result


def extract_definitions_and_words(
    explanations: list[dict[str, Any]],
) -> tuple[list[str], list[dict[str, str]]]:
    definitions: list[str] = []
    words: list[dict[str, str]] = []
    seen_words: set[str] = set()
    for explanation in explanations:
        candidates = (
            explanation.get("content"),
            explanation.get("refer"),
            explanation.get("same"),
            explanation.get("modern"),
            explanation.get("simplified"),
            explanation.get("variant"),
        )
        for candidate in candidates:
            text = clean_text(candidate, MAX_DEFINITION_LENGTH)
            if text and text not in definitions:
                definitions.append(text)
                break
        for value in as_list(explanation.get("words")):
            if not isinstance(value, dict):
                continue
            word = clean_text(value.get("word"), 32)
            if not word or word in seen_words:
                continue
            seen_words.add(word)
            words.append(
                {
                    "word": word,
                    "pinyin": clean_text(value.get("pinyin"), 96),
                    "definition": clean_text(
                        value.get("text") or value.get("example"),
                        MAX_WORD_DEFINITION_LENGTH,
                    ),
                }
            )
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
        is_match = len(syllables) == len(han) and any(
            position < len(syllables) and syllables[position] == normalized
            for position in positions
        )
        compact = {
            "word": word,
            "pinyin": item["pinyin"],
            "definition": item["definition"],
        }
        if is_match:
            matched.append(compact)
        elif primary:
            fallback.append(compact)
    return (matched + fallback)[:MAX_WORDS_PER_READING]


def build_character(
    base: dict[str, Any],
    ordinal: int,
    details: dict[str, dict[str, list[dict[str, Any]]]],
    words: dict[str, list[dict[str, str]]],
) -> dict[str, Any] | None:
    character = base.get("char")
    if not isinstance(character, str) or len(character) != 1:
        return None

    pinyin_values = unique_strings(as_list(base.get("pinyin")))
    detail_readings = details.get(character, {})
    if not pinyin_values:
        pinyin_values = unique_strings(
            reading.get("pinyin")
            for reading in as_list(base.get("pronunciations"))
            if isinstance(reading, dict)
        )
    if not pinyin_values and detail_readings:
        pinyin_values = list(detail_readings.keys())
    if not pinyin_values:
        return None

    base_index = safe_int(base.get("index"), ordinal)
    frequency = safe_int(base.get("frequency"), 5)
    readings: list[dict[str, Any]] = []
    for order, pinyin in enumerate(pinyin_values):
        explanation_values = detail_readings.get(normalize_pinyin(pinyin), [])
        definitions, embedded_words = extract_definitions_and_words(explanation_values)
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
        readings.append(
            {
                "pinyin": pinyin,
                "tone": tone_number(pinyin),
                "speakWord": merged_words[0]["word"] if merged_words else character,
                "definitions": definitions,
                "words": merged_words,
            }
        )

    traditional_values = unique_strings(as_list(base.get("traditional")))
    traditional = "".join(traditional_values) if traditional_values else character
    structure_value = as_list(base.get("structure"))
    structure_code = str(structure_value[0]) if structure_value else ""
    radical = clean_text(base.get("radicals") or base.get("radical"), 24) or "—"
    return {
        "id": 100000 + base_index,
        "character": character,
        "simplified": character,
        "traditional": traditional,
        "unicode": f"U+{ord(character):04X}",
        "radical": radical,
        "strokes": safe_int(base.get("strokes"), 0),
        "structure": STRUCTURES.get(structure_code, "未分类结构"),
        "strokeNumber": "",
        "frequencyRank": base_index if base_index > 0 else 99999,
        "common": frequency <= 2,
        "sourceId": "mapull",
        "wubi86": [],
        "strokeNames": [],
        "pronunciations": readings,
    }


def build_manifest(character_count: int, skipped_count: int) -> dict[str, Any]:
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
                "modificationNote": "Normalized, deduplicated and size-bounded for offline use; imported definitions require continued editorial review.",
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
        existing = read_json_document(manifest_path)
        if (
            isinstance(existing, dict)
            and existing.get("sourceCommit") == SOURCE_COMMIT
            and output_path.stat().st_size > 1024
        ):
            print(
                f"Full dictionary already matches {SOURCE_COMMIT[:12]} "
                f"({existing.get('characterCount', '?')} characters)"
            )
            return

    curated_root = read_json_document(curated_path)
    curated_entries = {
        item["character"]: copy.deepcopy(item)
        for item in as_list(curated_root.get("characters"))
        if isinstance(item, dict) and isinstance(item.get("character"), str)
    }

    base_path = download(SOURCE_FILES["base"], cache_dir)
    detail_path = download(SOURCE_FILES["detail"], cache_dir)
    word_path = download(SOURCE_FILES["words"], cache_dir)

    base_entries = list(iter_json_records(base_path))
    known_characters = {
        item.get("char")
        for item in base_entries
        if isinstance(item.get("char"), str) and len(item["char"]) == 1
    }
    print(f"Loaded {len(base_entries)} base character records")
    details = build_detail_index(detail_path)
    print(f"Indexed details for {len(details)} characters")
    words = build_word_index(word_path, known_characters)

    generated: list[dict[str, Any]] = []
    emitted: set[str] = set()
    skipped = 0
    for ordinal, base in enumerate(base_entries, 1):
        character = base.get("char")
        if character in curated_entries:
            entry = curated_entries[character]
            entry["id"] = safe_int(entry.get("id"), len(generated) + 1)
            generated.append(entry)
            emitted.add(character)
            continue
        entry = build_character(base, ordinal, details, words)
        if entry is None:
            skipped += 1
            continue
        generated.append(entry)
        emitted.add(entry["character"])

    for character, entry in curated_entries.items():
        if character not in emitted:
            generated.append(entry)

    generated.sort(key=lambda item: safe_int(item.get("frequencyRank"), 99999))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as compressed:
            for entry in generated:
                compressed.write(
                    json.dumps(entry, ensure_ascii=False, separators=(",", ":"))
                    .encode("utf-8")
                )
                compressed.write(b"\n")

    manifest = build_manifest(len(generated), skipped)
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"Generated {len(generated)} characters; skipped {skipped}; "
        f"compressed size {output_path.stat().st_size / 1024 / 1024:.2f} MiB"
    )
    if len(generated) < 8000:
        raise SystemExit(
            f"Generated dictionary is unexpectedly small: {len(generated)} characters"
        )


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
    arguments = parser.parse_args()
    generate(
        arguments.curated,
        arguments.output,
        arguments.manifest,
        arguments.cache_dir,
        arguments.force,
    )


if __name__ == "__main__":
    main()
