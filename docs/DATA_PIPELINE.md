# Dictionary data pipeline

The repository intentionally does not commit an unreviewed scraped dictionary. A release dataset should be generated reproducibly from licensed sources.

## Stages

1. Download immutable source releases manually or in a controlled data workflow.
2. Record URL, version, checksum, license and retrieval date.
3. Parse Unicode code points, simplified/traditional mappings, readings and variants.
4. Normalize pinyin into tone-mark, plain and tone-number forms.
5. Import Wubi 86 codes separately and preserve Rime attribution.
6. Import stroke data separately because graphics and dictionary files may have distinct licenses.
7. Import definitions only from sources whose reuse terms are compatible.
8. Deduplicate records without losing source attribution.
9. Validate foreign keys, code points, stroke counts, pinyin and duplicate aliases.
10. Generate the app seed JSON or a prepackaged Room SQLite database.
11. Emit a source report and SHA-256 checksum.
12. Review child-facing definitions and examples before release.

## Source directory convention

```text
data/raw/unihan/
data/raw/rime-wubi/
data/raw/makemeahanzi/
data/raw/cedict/
data/raw/wiktionary/
data/generated/
```

Raw data and generated full databases are ignored by default unless a release process explicitly approves committing them.
