# Third-party notices

TongshiHanzi application code is licensed under GPL-3.0-or-later. Third-party software and data retain their original copyright and license terms.

## Android libraries

Android Gradle Plugin, AndroidX, Room, Preference, Navigation, Lifecycle and Material Components retain Apache-2.0. Binary distributions must preserve applicable notices from dependency artifacts.

## Interface icons

The application packages only the Lucide vector icons it actually uses: search, bookmark, settings, volume-2 and arrow-left. No complete Lucide runtime or icon catalogue is bundled. Lucide portions retain the ISC License; Feather-derived icons retain the MIT License. The complete upstream notice is preserved in `licenses/ISC-MIT-lucide-icons.txt`.

## Lexical data

The expanded offline dictionary contains normalized records from `mapull/chinese-dictionary`, pinned to commit `e804ada333b68afddfdccbe8dcc938a72da157a7`, under the MIT License. Copyright (c) 2021 码谱. The full MIT text is preserved in `licenses/MIT-mapull-chinese-dictionary.txt`.

Unicode factual metadata retains the Unicode-3.0 license and Unicode, Inc. attribution.

## Stroke-order data

The indexed stroke pack contains transformed copies of ordered paths and medians from:

- `skishore/makemeahanzi`, commit `bddc96d41bef78427ed0e034e9f7e31d71fd1b92`, `graphics.txt`;
- `parsimonhi/animCJK`, commit `ec5e17cca76c87587790bcbce5ea0b4d4fb753d6`, `graphicsZhHans.txt`;
- the reviewed fallback uses `chanind/hanzi-writer-data`, commit `68d10a4b21150cae5e1ebbd223eed289cf32d90c`.

These graphics datasets retain the applicable Arphic Public License terms and upstream attribution. The application changes storage format, builds an index, deduplicates overlapping code points and compresses each character independently; it does not claim original authorship of the stroke geometry or sequence.

Detailed provenance, counts, modification notes and update procedures are recorded in `DATA_LICENSES.md` and the generated data manifests.
