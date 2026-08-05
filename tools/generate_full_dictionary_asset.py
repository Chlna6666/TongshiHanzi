#!/usr/bin/env python3
"""Generate the gzip dictionary in an Android AAPT-safe `.bin` container.

AAPT treats assets ending in `.gz` specially: it expands them and removes the suffix
inside the APK. Keeping the gzip payload in `.bin` preserves its exact name and bytes,
so `AssetManager` can open it and `GZIPInputStream` can decode it at runtime.
"""

from pathlib import Path

from sync_mapull_dictionary import SOURCE_COMMIT, generate


def main() -> None:
    generate(
        Path("app/src/main/assets/dictionary/dictionary_seed.json"),
        Path("app/src/main/assets/dictionary/full_dictionary.ndjson.bin"),
        Path("app/src/main/assets/dictionary/full_dictionary_manifest.json"),
        Path(".cache/mapull-chinese-dictionary") / SOURCE_COMMIT,
        False,
    )


if __name__ == "__main__":
    main()
