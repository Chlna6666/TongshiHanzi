# Dictionary data licenses

Application code and dictionary content are separate works. Third-party data keeps its original copyright and license even when it is packaged in the GPL-3.0-or-later application.

## Reviewed child-facing seed

`app/src/main/assets/dictionary/dictionary_seed.json` contains 25 project-reviewed entries. Their concise child-facing definitions, selected words, Wubi codes and editorial metadata are maintained by TongshiHanzi and identified with `sourceId: project`.

The reviewed seed is an overlay: when the full dictionary is generated, these entries replace the corresponding upstream records rather than being discarded.

## Full offline dictionary

`tools/sync_mapull_dictionary.py` generates:

```text
app/src/main/assets/dictionary/full_dictionary.ndjson.gz
app/src/main/assets/dictionary/full_dictionary_manifest.json
```

The source is pinned to:

```text
Repository: mapull/chinese-dictionary
Commit: e804ada333b68afddfdccbe8dcc938a72da157a7
License: MIT
Copyright (c) 2021 码谱
```

The generator imports the upstream character base, character details and word data. It then normalizes structures and pinyin, removes duplicates, limits unusually large per-reading payloads and writes deterministic gzip-compressed NDJSON. The Android application imports that stream in bounded transactions, so it does not need to retain the complete dictionary in memory.

The upstream project states that its data was assembled from multiple public and open resources and also warns that rare-character accuracy has not been strictly verified. TongshiHanzi therefore marks imported records as extended reference data rather than child-reviewed content. The 25 project-reviewed records remain visually and semantically preferred.

The complete upstream MIT text is preserved in `licenses/MIT-mapull-chinese-dictionary.txt`.

`pwxcoo/chinese-xinhua` is not bundled. Although that repository contains an MIT file, its README states that the data was scraped from websites and provides no complete per-record provenance. It may be used only after a separate provenance review.

## Stroke vectors

`tools/generate_stroke_vectors.py` generates the offline `stroke_vectors.json` subset from `chanind/hanzi-writer-data`, pinned to commit:

```text
68d10a4b21150cae5e1ebbd223eed289cf32d90c
```

That dataset is derived from Make Me a Hanzi and carries its own data terms. Preserve the upstream `ARPHICPL.TXT`, README attribution and this notice when redistributing generated APKs or derived datasets. Stroke vectors are currently generated for the reviewed seed; other characters degrade cleanly to metadata-only display when no vector is bundled.

## Unicode fallback

A single CJK Unified Ideograph absent from the bundled dictionaries can still be opened through a Unicode fallback record. This record provides only code-point display, system TTS access and an explicit “待补充” state. It must not be presented as a reviewed pronunciation, radical, definition, Wubi code or stroke order.

## Update procedure

1. Review the newer upstream commit and its license/provenance notes.
2. Change `SOURCE_COMMIT` and `SOURCE_VERSION` in `tools/sync_mapull_dictionary.py`.
3. Run `python3 tools/sync_mapull_dictionary.py --force`.
4. Validate the generated manifest count and inspect a representative set of common, polyphonic and rare characters.
5. Commit the compressed asset, manifest, license changes and source revision together.

Do not import commercial dictionary text, non-commercial-only data, no-derivatives data or content with unknown provenance. Every imported dataset must retain source ID, pinned version, license, modification note and review status.
