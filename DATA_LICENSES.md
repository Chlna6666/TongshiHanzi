# Dictionary data licenses

Application code and dictionary content are separate works. Third-party data keeps its original copyright and license even when it is packaged in the GPL-3.0-or-later application.

## Reviewed child-facing seed

`app/src/main/assets/dictionary/dictionary_seed.json` contains 25 project-reviewed entries. Their concise child-facing definitions, selected words, Wubi codes, stroke counts, stroke-number strings and stroke names are maintained by TongshiHanzi and identified with `sourceId: project`.

The reviewed seed is an editorial overlay: when the full dictionary is generated, these entries replace the corresponding upstream lexical records rather than being discarded. The build separately validates each reviewed stroke count against the selected vector record, preventing a lexical override from silently removing or conflicting with animation data.

## Full offline dictionary

`tools/generate_full_dictionary_asset.py` invokes the pinned source generator and produces:

```text
app/src/main/assets/dictionary/full_dictionary.ndjson.bin
app/src/main/assets/dictionary/full_dictionary_manifest.json
```

The `.bin` file contains deterministic gzip-compressed NDJSON. The non-`.gz` extension is intentional: Android AAPT expands assets ending in `.gz` and removes that suffix, which would make the runtime asset name differ from the source path.

The source is pinned to:

```text
Repository: mapull/chinese-dictionary
Commit: e804ada333b68afddfdccbe8dcc938a72da157a7
License: MIT
Copyright (c) 2021 码谱
```

The generator imports the upstream character base, character details and word data. It normalizes structures and pinyin, removes duplicates, limits unusually large per-reading payloads and currently generates 21,056 character records. Imported definitions remain extended reference data and require continued editorial review.

The upstream project states that its data was assembled from multiple public and open resources and warns that rare-character accuracy has not been strictly verified. TongshiHanzi therefore keeps the 25 project-reviewed records visually and semantically preferred.

The complete upstream MIT text is preserved in `licenses/MIT-mapull-chinese-dictionary.txt`.

`pwxcoo/chinese-xinhua` is not bundled. Although that repository contains an MIT file, its README states that the data was scraped from websites and provides no complete per-record provenance. It may be used only after a separate provenance review.

## Prepopulated Room database

`tools/build_prebuilt_dictionary.py` converts the generated NDJSON into:

```text
app/src/main/assets/database/dictionary.db
```

This SQLite file is a generated representation of the same licensed records, not a separately relicensed dataset. It includes the Room identity hash, schema version 4, indices and statistics. CI runs `integrity_check`, `foreign_key_check` and exact character-count verification before packaging. Android copies and validates it through `Room.createFromAsset()` instead of rebuilding the corpus on the user's device.

User favourites, history and preferences remain in a separate user database and are not part of this generated artifact.

## Stroke-order vectors

### Complete indexed pack

`tools/generate_stroke_pack.py` creates:

```text
app/src/main/assets/dictionary/stroke_pack.tshs
app/src/main/assets/dictionary/stroke_pack_manifest.json
```

The pack contains 10,210 independently compressed character records. It uses Make Me a Hanzi as the primary source and accepts AnimCJK `ZhHans` records only for code points absent from the primary corpus.

Primary source:

```text
Repository: skishore/makemeahanzi
Commit: bddc96d41bef78427ed0e034e9f7e31d71fd1b92
File: graphics.txt
Records used: 9,574
Data license: Arphic Public License (1999)
```

Secondary source:

```text
Repository: parsimonhi/animCJK
Commit: ec5e17cca76c87587790bcbce5ea0b4d4fb753d6
File: graphicsZhHans.txt
Records used: 636
Data license: Arphic Public License (1999)
```

The pack stores ordered SVG paths and medians supplied by those data sources. Android memory-maps the fixed index and inflates only the requested character. This storage transformation does not create or infer new stroke order.

The 25 reviewed characters are strict build invariants: a vector source record is accepted only when its path count equals the project-reviewed stroke count. All 25 must have a compatible record or CI fails.

### Reviewed fallback asset

`tools/generate_stroke_vectors.py` continues to generate `stroke_vectors.json` for the 25 reviewed characters from `chanind/hanzi-writer-data`, pinned to commit:

```text
68d10a4b21150cae5e1ebbd223eed289cf32d90c
```

This is a small compatibility fallback. The normal detail page uses the indexed TSHS pack. Hanzi Writer Data is derived from Make Me a Hanzi and retains the applicable upstream data terms.

Redistributions must preserve the applicable Arphic license text, source attribution, pinned revisions and modification notes. Do not describe the generated pack as wholly original GPL data.

## No automatic stroke-order fabrication

A font outline describes the final filled contour, not writing sequence, pen direction, stroke boundaries or regional normative order. CJKVI IDS or Unicode component data can describe character composition but likewise cannot establish a unique standard stroke sequence.

TongshiHanzi may use component or radical data to improve search and explanations. It must not present a sequence inferred from a font, IDS decomposition or heuristic component order as verified standard笔顺. A character without source-authored vector data degrades to a static glyph reference and explicit missing-data message.

## Unicode fallback

A single CJK Unified Ideograph absent from the bundled dictionaries can still be opened through a Unicode fallback record. This record provides only code-point display, system TTS access and an explicit “待补充” state. It must not be presented as a reviewed pronunciation, radical, definition, Wubi code or stroke order.

## Candidate future sources

Potential future imports require a separate provenance and license review:

- Unicode Unihan for radicals, total strokes, readings, variants and regional metadata under Unicode-3.0;
- OpenCC for simplified/traditional and regional variant mappings under Apache-2.0;
- CJKVI IDS for component and ideographic-description data under its stated repository terms;
- Rime Wubi tables for Wubi codes under the license declared by each schema repository;
- CC-CEDICT or Chinese Wiktionary/Kaikki for word coverage, with their attribution and share-alike obligations kept separate from project-authored child-facing definitions.

## Update procedure

1. Review newer upstream revisions and their license/provenance notes.
2. Change pinned revisions in the corresponding generator.
3. Regenerate the lexical NDJSON and TSHS stroke pack.
4. Force Room schema export and build the prepopulated database.
5. Validate common, polyphonic, rare and regional-variant samples.
6. Confirm that 25/25 reviewed characters pass vector-count validation.
7. Confirm that both APKs contain the Room database and uncompressed TSHS asset and that their internal counts match the manifests.
8. Commit generated assets, manifests, schemas, license changes and source revisions together.

Do not import commercial dictionary text, non-commercial-only data, no-derivatives data or content with unknown provenance. Every imported dataset must retain source ID, pinned version, license, modification note and review status.
