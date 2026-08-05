/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DictionaryValidatorTest {
    @TempDir Path tempDir;

    @Test
    void acceptsAttributedSeed() throws Exception {
        Path input = tempDir.resolve("seed.json");
        Files.writeString(input, "{\"sources\":[{\"sourceId\":\"project\",\"licenseId\":\"GPL-3.0-or-later\"}],\"characters\":[{\"character\":\"人\"}]}");
        assertEquals(1, DictionaryValidator.validate(input).characterCount());
    }

    @Test
    void rejectsDuplicateCharacter() throws Exception {
        Path input = tempDir.resolve("seed.json");
        Files.writeString(input, "{\"sourceId\":\"project\",\"licenseId\":\"GPL-3.0-or-later\",\"characters\":[{\"character\":\"人\"},{\"character\":\"人\"}]}");
        assertThrows(IllegalArgumentException.class, () -> DictionaryValidator.validate(input));
    }
}
