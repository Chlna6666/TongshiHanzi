# Dictionary data licenses

Application code and dictionary content are separate works. Do not relabel third-party data as GPL merely because it is packaged in the APK.

## Reviewed dictionary seed

The committed development seed contains project-authored child-facing definitions and factual Unicode fields. Each record retains a `sourceId`. The seed is intentionally small while the review and provenance pipeline is established.

## Stroke vectors

`tools/generate_stroke_vectors.py` generates the offline `stroke_vectors.json` asset from `chanind/hanzi-writer-data`, pinned to commit:

```text
68d10a4b21150cae5e1ebbd223eed289cf32d90c
```

That dataset is derived from Make Me a Hanzi and carries its own data terms. Preserve the upstream `ARPHICPL.TXT`, README attribution and this notice when redistributing generated APKs or derived datasets. The generated asset contains only the characters present in the reviewed seed; the application does not make a runtime network request.

## Rare-character fallback

A single CJK Unified Ideograph that is absent from the reviewed seed can still be opened through a Unicode fallback record. This record provides only code-point display, system TTS access and an explicit “待补充” state. It must not be presented as a reviewed pronunciation, radical, definition, Wubi code or stroke order.

## Supported future imports

Supported full-data sources include Unicode Unihan (Unicode-3.0), Rime Wubi (LGPL-3.0), Make Me a Hanzi / Hanzi Writer Data (upstream data terms), CC-CEDICT (CC BY-SA 4.0) and Chinese Wiktionary (CC BY-SA 4.0 and applicable source terms).

Do not import commercial dictionary text, scraped website content, non-commercial-only data, no-derivatives data or content with unknown provenance. Every imported record must retain source ID, version, license, modification and review status.
