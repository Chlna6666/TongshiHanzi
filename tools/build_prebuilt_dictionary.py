#!/usr/bin/env python3
"""Build the prepopulated Room dictionary database used by the Android application.

This converts the generated gzip NDJSON corpus into SQLite during CI. On-device startup
then copies a validated Room database instead of parsing JSON and performing hundreds of
thousands of inserts. User favourites and history remain in a separate database.
"""

from __future__ import annotations

import argparse
import gzip
import json
import re
import sqlite3
import unicodedata
from pathlib import Path
from typing import Any, Iterable

BATCH_SIZE = 512
PINYIN_TONE_MARKS = {
    **dict.fromkeys("āēīōūǖ", 1),
    **dict.fromkeys("áéíóúǘ", 2),
    **dict.fromkeys("ǎěǐǒǔǚ", 3),
    **dict.fromkeys("àèìòùǜ", 4),
}


def normalize_pinyin(value: str) -> str:
    text = value.strip().lower().replace("u:", "v").replace("ü", "v")
    text = re.sub(r"[1-5]$", "", text)
    decomposed = unicodedata.normalize("NFD", text)
    return "".join(
        character
        for character in decomposed
        if unicodedata.category(character) != "Mn" and character.isalpha()
    )


def extract_tone(value: str) -> int:
    text = value.strip().lower()
    if text and text[-1:] in {"1", "2", "3", "4", "5"}:
        return int(text[-1])
    for character in text:
        if character in PINYIN_TONE_MARKS:
            return PINYIN_TONE_MARKS[character]
    return 0


def apply_room_schema(connection: sqlite3.Connection, schema_path: Path) -> int:
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    database = schema["database"]
    version = int(database["version"])
    for entity in database.get("entities", []):
        table_name = entity["tableName"]
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", table_name))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", table_name))
    for view in database.get("views", []):
        connection.execute(view["createSql"].replace("${VIEW_NAME}", view["viewName"]))
    for query in database.get("setupQueries", []):
        connection.execute(query)
    connection.execute(f"PRAGMA user_version = {version}")
    return version


def read_manifest(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def add_alias(
    aliases: list[tuple[int, str, str, int]],
    character_id: int,
    alias_type: str,
    value: str,
    weight: int,
) -> None:
    text = value.strip()
    if text:
        aliases.append((character_id, alias_type, text, weight))


def iter_codepoints(value: str) -> Iterable[str]:
    return iter(value)


def insert_sources(connection: sqlite3.Connection, manifest: dict[str, Any]) -> None:
    rows = []
    for source in manifest.get("sources", []):
        rows.append((
            source["sourceId"],
            source.get("name", ""),
            source.get("version", ""),
            source.get("licenseId", ""),
            source.get("attribution", ""),
            source.get("modificationNote", ""),
        ))
    connection.executemany(
        "INSERT INTO data_sources(source_id,name,version,license_id,attribution,modification_note) "
        "VALUES(?,?,?,?,?,?)",
        rows,
    )


def insert_character(
    connection: sqlite3.Connection,
    value: dict[str, Any],
    counters: dict[str, int],
) -> None:
    character_id = int(value["id"])
    character = str(value["character"])
    traditional = str(value.get("traditional") or character)
    connection.execute(
        "INSERT INTO characters(id,character_text,simplified,traditional,unicode_codepoint,radical,"
        "total_strokes,structure,stroke_number,frequency_rank,is_common,source_id) "
        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        (
            character_id,
            character,
            str(value.get("simplified") or character),
            traditional,
            str(value.get("unicode") or f"U+{ord(character):04X}"),
            str(value.get("radical") or "—"),
            int(value.get("strokes") or 0),
            str(value.get("structure") or "未分类结构"),
            str(value.get("strokeNumber") or ""),
            int(value.get("frequencyRank") or 99999),
            1 if value.get("common", False) else 0,
            str(value.get("sourceId") or "project"),
        ),
    )

    aliases: list[tuple[int, str, str, int]] = []
    add_alias(aliases, character_id, "CHARACTER", character, 100)
    if traditional != character:
        for variant in iter_codepoints(traditional):
            add_alias(aliases, character_id, "TRADITIONAL", variant, 100)

    readings = value.get("pronunciations")
    if not isinstance(readings, list) or not readings:
        readings = [{
            "pinyin": "未收录",
            "tone": 0,
            "speakWord": character,
            "definitions": ["读音和释义仍待审校。"],
            "words": [],
        }]

    for reading_order, reading in enumerate(readings):
        pronunciation_id = counters["pronunciation"]
        counters["pronunciation"] += 1
        pinyin_tone = str(reading.get("pinyin") or "未收录")
        pinyin_plain = normalize_pinyin(pinyin_tone)
        tone = int(reading.get("tone") or extract_tone(pinyin_tone))
        pinyin_number = pinyin_plain + (str(tone) if tone else "")
        connection.execute(
            "INSERT INTO pronunciations(id,character_id,pinyin_tone,pinyin_plain,pinyin_number,tone,"
            "is_primary,speak_word,display_order) VALUES(?,?,?,?,?,?,?,?,?)",
            (
                pronunciation_id,
                character_id,
                pinyin_tone,
                pinyin_plain,
                pinyin_number,
                tone,
                1 if reading_order == 0 else 0,
                str(reading.get("speakWord") or character),
                reading_order,
            ),
        )
        if pinyin_plain:
            add_alias(aliases, character_id, "PINYIN", pinyin_plain, 90)
        if pinyin_number:
            add_alias(aliases, character_id, "PINYIN_NUMBER", pinyin_number, 95)

        definitions = reading.get("definitions")
        if isinstance(definitions, list):
            for definition_order, raw_definition in enumerate(definitions):
                if isinstance(raw_definition, dict):
                    text = str(raw_definition.get("text") or "").strip()
                    source_id = str(raw_definition.get("sourceId") or value.get("sourceId") or "project")
                else:
                    text = str(raw_definition).strip()
                    source_id = str(value.get("sourceId") or "project")
                if not text:
                    continue
                definition_id = counters["definition"]
                counters["definition"] += 1
                connection.execute(
                    "INSERT INTO definitions(id,character_id,pronunciation_id,kind,text,display_order,source_id) "
                    "VALUES(?,?,?,?,?,?,?)",
                    (
                        definition_id,
                        character_id,
                        pronunciation_id,
                        "CHILD_SHORT" if definition_order == 0 else "BASIC",
                        text,
                        definition_order,
                        source_id,
                    ),
                )

        words = reading.get("words")
        if isinstance(words, list):
            for word_order, word in enumerate(words):
                if not isinstance(word, dict):
                    continue
                word_text = str(word.get("word") or "").strip()
                if not word_text:
                    continue
                word_id = counters["word"]
                counters["word"] += 1
                connection.execute(
                    "INSERT INTO words(id,character_id,pronunciation_id,word,pinyin,definition,display_order) "
                    "VALUES(?,?,?,?,?,?,?)",
                    (
                        word_id,
                        character_id,
                        pronunciation_id,
                        word_text,
                        str(word.get("pinyin") or ""),
                        str(word.get("definition") or ""),
                        word_order,
                    ),
                )

    codes = value.get("wubi86")
    if isinstance(codes, list):
        for code_order, code_value in enumerate(codes):
            code = str(code_value).strip().upper()
            if not code:
                continue
            wubi_id = counters["wubi"]
            counters["wubi"] += 1
            connection.execute(
                "INSERT INTO wubi_codes(id,character_id,scheme,code,is_primary) VALUES(?,?,?,?,?)",
                (wubi_id, character_id, "WUBI86", code, 1 if code_order == 0 else 0),
            )
            add_alias(aliases, character_id, "WUBI86", code.lower(), 80)

    stroke_names = value.get("strokeNames")
    if isinstance(stroke_names, list):
        for stroke_index, name_value in enumerate(stroke_names):
            name = str(name_value).strip() or f"第 {stroke_index + 1} 笔"
            stroke_id = counters["stroke"]
            counters["stroke"] += 1
            connection.execute(
                "INSERT INTO strokes(id,character_id,stroke_index,name,path_data,median_data) "
                "VALUES(?,?,?,?,?,?)",
                (stroke_id, character_id, stroke_index, name, "", ""),
            )

    connection.executemany(
        "INSERT INTO search_alias(character_id,alias_type,normalized_text,weight) VALUES(?,?,?,?)",
        aliases,
    )


def build_database(
    schema_path: Path,
    dictionary_path: Path,
    manifest_path: Path,
    output_path: Path,
) -> None:
    manifest = read_manifest(manifest_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.unlink(missing_ok=True)
    connection = sqlite3.connect(output_path)
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("PRAGMA temp_store=MEMORY")
        connection.execute("PRAGMA locking_mode=EXCLUSIVE")
        connection.execute("PRAGMA foreign_keys=OFF")
        connection.execute("PRAGMA page_size=4096")
        version = apply_room_schema(connection, schema_path)
        insert_sources(connection, manifest)

        counters = {
            "pronunciation": 1,
            "definition": 1,
            "word": 1,
            "wubi": 1,
            "stroke": 1,
        }
        count = 0
        connection.execute("BEGIN")
        with gzip.open(dictionary_path, "rt", encoding="utf-8") as source:
            for line in source:
                if not line.strip():
                    continue
                insert_character(connection, json.loads(line), counters)
                count += 1
                if count % BATCH_SIZE == 0:
                    connection.commit()
                    connection.execute("BEGIN")
                    print(f"Inserted {count} characters")
        connection.commit()

        expected = int(manifest["characterCount"])
        if count != expected:
            raise RuntimeError(f"Dictionary count mismatch: inserted={count}, expected={expected}")
        actual = connection.execute("SELECT COUNT(*) FROM characters").fetchone()[0]
        if actual != expected:
            raise RuntimeError(f"SQLite character count mismatch: {actual} != {expected}")

        connection.execute("ANALYZE")
        connection.commit()
        connection.execute("PRAGMA journal_mode=DELETE")
        connection.execute("VACUUM")
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity check failed: {integrity}")
        foreign_key_errors = connection.execute("PRAGMA foreign_key_check").fetchall()
        if foreign_key_errors:
            raise RuntimeError(f"SQLite foreign-key check failed: {foreign_key_errors[:5]}")
        print(
            f"Built Room database v{version}: {count} characters, "
            f"{output_path.stat().st_size / 1024 / 1024:.2f} MiB"
        )
    finally:
        connection.close()


def find_schema(schema_root: Path, version: int) -> Path:
    matches = list(schema_root.glob(f"**/{version}.json"))
    if len(matches) != 1:
        raise SystemExit(
            f"Expected exactly one Room schema v{version} under {schema_root}, found {matches}"
        )
    return matches[0]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--schema", type=Path)
    parser.add_argument("--schema-root", type=Path, default=Path("app/schemas"))
    parser.add_argument("--version", type=int, default=4)
    parser.add_argument(
        "--dictionary",
        type=Path,
        default=Path("app/src/main/assets/dictionary/full_dictionary.ndjson.bin"),
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("app/src/main/assets/dictionary/full_dictionary_manifest.json"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/database/dictionary.db"),
    )
    args = parser.parse_args()
    schema_path = args.schema or find_schema(args.schema_root, args.version)
    build_database(schema_path, args.dictionary, args.manifest, args.output)


if __name__ == "__main__":
    main()
