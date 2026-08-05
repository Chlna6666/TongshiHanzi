#!/usr/bin/env python3
"""One-time package namespace migration for TongshiHanzi.

Moves Java source/test trees from com/chlna/tongshihanzi to
com/chlna6666/tongshihanzi, updates dotted references everywhere in text files,
and bumps the application version. Binary dictionary/stroke assets are left
untouched.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD_PACKAGE = "com.chlna.tongshihanzi"
NEW_PACKAGE = "com.chlna6666.tongshihanzi"
OLD_PACKAGE_PATH = Path(*OLD_PACKAGE.split("."))
NEW_PACKAGE_PATH = Path(*NEW_PACKAGE.split("."))

SOURCE_ROOTS = (
    Path("app/src/main/java"),
    Path("app/src/test/java"),
    Path("app/src/androidTest/java"),
    Path("dict-builder/src/main/java"),
    Path("dict-builder/src/test/java"),
)

SKIP_PARTS = {".git", ".gradle", "build", ".idea", ".cache"}
SKIP_FILES = {
    Path("tools/migrate_package_namespace.py"),
}


def move_package_tree(base: Path) -> bool:
    old = ROOT / base / OLD_PACKAGE_PATH
    new = ROOT / base / NEW_PACKAGE_PATH
    if not old.exists():
        return False
    if new.exists():
        raise RuntimeError(f"destination already exists: {new.relative_to(ROOT)}")
    new.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(old), str(new))
    remove_empty_parents(old.parent, ROOT / base)
    print(f"moved {old.relative_to(ROOT)} -> {new.relative_to(ROOT)}")
    return True


def move_room_schema() -> bool:
    schema_root = ROOT / "app/schemas"
    old = schema_root / f"{OLD_PACKAGE}.data.dictionary.DictionaryDatabase"
    new = schema_root / f"{NEW_PACKAGE}.data.dictionary.DictionaryDatabase"
    if not old.exists():
        return False
    if new.exists():
        raise RuntimeError(f"schema destination already exists: {new.relative_to(ROOT)}")
    new.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(old), str(new))
    print(f"moved {old.relative_to(ROOT)} -> {new.relative_to(ROOT)}")
    return True


def remove_empty_parents(path: Path, stop: Path) -> None:
    while path != stop and path.is_dir():
        try:
            path.rmdir()
        except OSError:
            break
        path = path.parent


def is_candidate(path: Path) -> bool:
    relative = path.relative_to(ROOT)
    if relative in SKIP_FILES:
        return False
    if any(part in SKIP_PARTS for part in relative.parts):
        return False
    if not path.is_file() or path.is_symlink():
        return False
    return path.stat().st_size <= 4 * 1024 * 1024


def replace_text_references() -> int:
    changed = 0
    old_slash = OLD_PACKAGE.replace(".", "/")
    new_slash = NEW_PACKAGE.replace(".", "/")
    for path in ROOT.rglob("*"):
        if not is_candidate(path):
            continue
        raw = path.read_bytes()
        if b"\x00" in raw:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        updated = text.replace(OLD_PACKAGE, NEW_PACKAGE).replace(old_slash, new_slash)
        if updated == text:
            continue
        path.write_text(updated, encoding="utf-8", newline="")
        changed += 1
        print(f"updated {path.relative_to(ROOT)}")
    return changed


def bump_version() -> bool:
    build_file = ROOT / "app/build.gradle"
    text = build_file.read_text(encoding="utf-8")
    updated = text.replace("versionCode = 4", "versionCode = 5")
    updated = updated.replace("versionName = '0.3.1'", "versionName = '0.3.2'")
    if updated == text:
        return False
    build_file.write_text(updated, encoding="utf-8", newline="")
    print("updated app version to 0.3.2 (5)")
    return True


def find_old_references() -> list[str]:
    failures: list[str] = []
    for root in SOURCE_ROOTS:
        old = ROOT / root / OLD_PACKAGE_PATH
        if old.exists():
            failures.append(str(old.relative_to(ROOT)))
    old_schema = ROOT / "app/schemas" / f"{OLD_PACKAGE}.data.dictionary.DictionaryDatabase"
    if old_schema.exists():
        failures.append(str(old_schema.relative_to(ROOT)))

    old_slash = OLD_PACKAGE.replace(".", "/")
    for path in ROOT.rglob("*"):
        if not is_candidate(path):
            continue
        raw = path.read_bytes()
        if b"\x00" in raw:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        if OLD_PACKAGE in text or old_slash in text:
            failures.append(str(path.relative_to(ROOT)))
    return sorted(set(failures))


def apply_migration() -> None:
    moved = any(move_package_tree(root) for root in SOURCE_ROOTS)
    schema_moved = move_room_schema()
    changed = replace_text_references()
    version_changed = bump_version()
    failures = find_old_references()
    if failures:
        raise SystemExit("old namespace remains in:\n" + "\n".join(failures))
    if not (moved or schema_moved or changed or version_changed):
        print("package namespace is already migrated")
    else:
        print("package namespace migration completed")


def check_migration() -> None:
    failures = find_old_references()
    required = ROOT / "app/src/main/java" / NEW_PACKAGE_PATH / "TongshiApplication.java"
    if not required.is_file():
        failures.append(str(required.relative_to(ROOT)))
    build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    for expected in (
        f"namespace = '{NEW_PACKAGE}'",
        f"applicationId = '{NEW_PACKAGE}'",
    ):
        if expected not in build:
            failures.append(f"app/build.gradle missing {expected}")
    if failures:
        raise SystemExit("package namespace verification failed:\n" + "\n".join(failures))
    print(f"verified package namespace {NEW_PACKAGE}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        check_migration()
    else:
        apply_migration()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"migration failed: {error}", file=sys.stderr)
        raise
