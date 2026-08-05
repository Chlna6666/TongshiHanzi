/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.chlna.tongshihanzi.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight release guard for the generated seed JSON. */
public final class DictionaryValidator {
    private static final Pattern CHARACTER = Pattern.compile("\\\"character\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern SOURCE_ID = Pattern.compile("\\\"sourceId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private DictionaryValidator() {}

    public static void main(String[] args) throws IOException {
        Path input = args.length == 0
                ? Path.of("app/src/main/assets/dictionary/dictionary_seed.json")
                : Path.of(args[0]);
        ValidationReport report = validate(input);
        System.out.printf("Validated %d characters from %d declared sources: %s%n",
                report.characterCount(), report.sourceCount(), input);
    }

    public static ValidationReport validate(Path input) throws IOException {
        String json = Files.readString(input, StandardCharsets.UTF_8);
        if (!json.stripLeading().startsWith("{")) {
            throw new IllegalArgumentException("Dictionary seed must be a JSON object");
        }
        Set<String> characters = new HashSet<>();
        Matcher characterMatcher = CHARACTER.matcher(json);
        while (characterMatcher.find()) {
            String value = characterMatcher.group(1);
            if (value.codePointCount(0, value.length()) != 1) {
                throw new IllegalArgumentException("Invalid character entry: " + value);
            }
            if (!characters.add(value)) {
                throw new IllegalArgumentException("Duplicate character entry: " + value);
            }
        }
        if (characters.isEmpty()) {
            throw new IllegalArgumentException("Dictionary seed contains no character entries");
        }

        Set<String> sources = new HashSet<>();
        Matcher sourceMatcher = SOURCE_ID.matcher(json);
        while (sourceMatcher.find()) {
            sources.add(sourceMatcher.group(1));
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("Dictionary seed contains no source attribution");
        }
        if (!json.contains("licenseId")) {
            throw new IllegalArgumentException("Dictionary seed must declare source licenses");
        }
        return new ValidationReport(characters.size(), sources.size());
    }

    public record ValidationReport(int characterCount, int sourceCount) {}
}
