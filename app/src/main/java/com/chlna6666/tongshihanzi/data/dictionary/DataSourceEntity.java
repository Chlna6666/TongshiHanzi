/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.dictionary;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "data_sources")
public class DataSourceEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "source_id") public String sourceId = "";
    @NonNull public String name = "";
    @NonNull public String version = "";
    @NonNull @ColumnInfo(name = "license_id") public String licenseId = "";
    @NonNull public String attribution = "";
    @NonNull @ColumnInfo(name = "modification_note") public String modificationNote = "";
}
