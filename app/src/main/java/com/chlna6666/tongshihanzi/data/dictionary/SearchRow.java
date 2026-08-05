/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.dictionary;

import androidx.room.ColumnInfo;

public class SearchRow {
    @ColumnInfo(name = "character_id") public int characterId;
    public String character;
    public String pinyin;
    public String radical;
    @ColumnInfo(name = "total_strokes") public int totalStrokes;
    public String definition;
    public String wubi;
    @ColumnInfo(name = "frequency_rank") public int frequencyRank;
}
