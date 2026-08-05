/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.data.stroke;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;

import com.chlna6666.tongshihanzi.data.dictionary.StrokeEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Random-access reader for the bundled TSHS stroke-order pack.
 *
 * <p>The fixed-size index is memory-mapped. Each character payload is independently compressed,
 * so opening a detail page inflates only a few kilobytes instead of parsing the complete stroke
 * corpus. The pack contains source-authored stroke order; it never guesses order from a font
 * outline.</p>
 */
public final class StrokePackRepository {
    private static final String TAG = "StrokePackRepository";
    private static final String ASSET = "dictionary/stroke_pack.tshs";
    private static final int MAGIC = 0x54534853; // TSHS
    private static final int FORMAT_VERSION = 1;
    private static final int HEADER_SIZE = 12;
    private static final int INDEX_ENTRY_SIZE = 20;
    private static final int MAX_RAW_PAYLOAD_BYTES = 2 * 1024 * 1024;
    private static final LruCache<String, List<StrokeEntity>> CACHE = new LruCache<>(64);

    private static volatile StrokePackRepository instance;

    private final Map<Integer, Entry> index = new HashMap<>();
    private ByteBuffer bytes;

    private StrokePackRepository(Context context) {
        try {
            bytes = mapAsset(context.getApplicationContext());
            readIndex(bytes.duplicate().order(ByteOrder.BIG_ENDIAN));
            Log.i(TAG, "Loaded stroke pack index with " + index.size() + " characters");
        } catch (Exception error) {
            bytes = null;
            index.clear();
            Log.e(TAG, "Unable to open bundled stroke pack", error);
        }
    }

    public static StrokePackRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (StrokePackRepository.class) {
                if (instance == null) {
                    instance = new StrokePackRepository(context);
                }
            }
        }
        return instance;
    }

    /** Returns vector strokes while preserving reviewed stroke names from Room when available. */
    @NonNull
    public List<StrokeEntity> load(String character, List<StrokeEntity> metadata) {
        if (bytes == null || character == null || character.isEmpty()) {
            return metadata == null ? Collections.emptyList() : metadata;
        }
        int codePoint = character.codePointAt(0);
        Entry entry = index.get(codePoint);
        if (entry == null) {
            return metadata == null ? Collections.emptyList() : metadata;
        }

        List<StrokeEntity> names = metadata == null
                ? Collections.emptyList() : new ArrayList<>(metadata);
        names.sort(Comparator.comparingInt(value -> value.strokeIndex));
        String cacheKey = codePoint + ":" + namesHash(names);
        List<StrokeEntity> cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            JSONObject payload = inflate(entry);
            JSONArray paths = payload.getJSONArray("strokes");
            JSONArray medians = payload.getJSONArray("medians");
            if (paths.length() == 0 || paths.length() != medians.length()) {
                throw new IllegalStateException("Invalid stroke/median count for " + character);
            }

            List<StrokeEntity> result = new ArrayList<>(paths.length());
            for (int strokeIndex = 0; strokeIndex < paths.length(); strokeIndex++) {
                StrokeEntity reviewed = strokeIndex < names.size() ? names.get(strokeIndex) : null;
                StrokeEntity stroke = new StrokeEntity();
                stroke.id = reviewed == null ? strokeIndex + 1 : reviewed.id;
                stroke.characterId = reviewed == null ? 0 : reviewed.characterId;
                stroke.strokeIndex = strokeIndex;
                stroke.name = reviewed == null || reviewed.name == null || reviewed.name.trim().isEmpty()
                        ? "第 " + (strokeIndex + 1) + " 笔"
                        : reviewed.name;
                stroke.pathData = paths.getString(strokeIndex);
                stroke.medianData = medians.getJSONArray(strokeIndex).toString();
                result.add(stroke);
            }

            if (!names.isEmpty() && names.size() != result.size()) {
                Log.w(TAG, "Reviewed/vector stroke count differs for " + character
                        + ": reviewed=" + names.size() + ", vector=" + result.size());
            }
            List<StrokeEntity> immutable = Collections.unmodifiableList(result);
            CACHE.put(cacheKey, immutable);
            return immutable;
        } catch (Exception error) {
            Log.e(TAG, "Unable to decode stroke data for " + character, error);
            return names;
        }
    }

    public boolean contains(String character) {
        return character != null && !character.isEmpty()
                && index.containsKey(character.codePointAt(0));
    }

    public int size() {
        return index.size();
    }

    private JSONObject inflate(Entry entry) throws Exception {
        if (entry.rawLength <= 0 || entry.rawLength > MAX_RAW_PAYLOAD_BYTES) {
            throw new IllegalStateException("Invalid raw stroke payload length: " + entry.rawLength);
        }
        if (entry.offset < 0 || entry.compressedLength <= 0
                || entry.offset + entry.compressedLength > bytes.capacity()) {
            throw new IllegalStateException("Stroke payload is outside the pack bounds");
        }

        ByteBuffer source = bytes.duplicate();
        source.position((int) entry.offset);
        byte[] compressed = new byte[entry.compressedLength];
        source.get(compressed);
        byte[] raw = new byte[entry.rawLength];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            int written = 0;
            while (!inflater.finished() && written < raw.length) {
                int count = inflater.inflate(raw, written, raw.length - written);
                if (count == 0) {
                    if (inflater.needsDictionary() || inflater.needsInput()) {
                        break;
                    }
                }
                written += count;
            }
            if (!inflater.finished() || written != raw.length) {
                throw new DataFormatException(
                        "Expected " + raw.length + " bytes, inflated " + written);
            }
        } finally {
            inflater.end();
        }
        return new JSONObject(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
    }

    private void readIndex(ByteBuffer source) {
        if (source.remaining() < HEADER_SIZE || source.getInt() != MAGIC) {
            throw new IllegalStateException("Invalid TSHS magic");
        }
        int version = source.getInt();
        int count = source.getInt();
        if (version != FORMAT_VERSION || count <= 0) {
            throw new IllegalStateException(
                    "Unsupported TSHS header: version=" + version + ", count=" + count);
        }
        long required = (long) HEADER_SIZE + (long) count * INDEX_ENTRY_SIZE;
        if (required > source.capacity()) {
            throw new IllegalStateException("TSHS index exceeds asset length");
        }
        for (int item = 0; item < count; item++) {
            int codePoint = source.getInt();
            long offset = source.getLong();
            int compressedLength = source.getInt();
            int rawLength = source.getInt();
            if (!Character.isValidCodePoint(codePoint)) {
                throw new IllegalStateException("Invalid code point in TSHS index: " + codePoint);
            }
            index.put(codePoint, new Entry(offset, compressedLength, rawLength));
        }
    }

    private static ByteBuffer mapAsset(Context context) throws Exception {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(ASSET);
             FileInputStream input = descriptor.createInputStream();
             FileChannel channel = input.getChannel()) {
            long length = descriptor.getDeclaredLength();
            if (length <= 0) {
                throw new IllegalStateException("Unknown uncompressed stroke pack length");
            }
            MappedByteBuffer mapped = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(),
                    length);
            return mapped.order(ByteOrder.BIG_ENDIAN);
        } catch (Exception compressedAsset) {
            Log.w(TAG, "Stroke pack was compressed by packaging; copying fallback asset", compressedAsset);
            File target = new File(context.getCacheDir(), "stroke_pack_v1.tshs");
            try (InputStream source = context.getAssets().open(ASSET);
                 FileOutputStream output = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[128 * 1024];
                int count;
                while ((count = source.read(buffer)) >= 0) {
                    if (count > 0) output.write(buffer, 0, count);
                }
            }
            try (FileInputStream input = new FileInputStream(target);
                 FileChannel channel = input.getChannel()) {
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                        .order(ByteOrder.BIG_ENDIAN);
            }
        }
    }

    private static int namesHash(List<StrokeEntity> names) {
        int hash = 1;
        for (StrokeEntity value : names) {
            hash = 31 * hash + value.strokeIndex;
            hash = 31 * hash + (value.name == null ? 0 : value.name.hashCode());
        }
        return hash;
    }

    private static final class Entry {
        final long offset;
        final int compressedLength;
        final int rawLength;

        Entry(long offset, int compressedLength, int rawLength) {
            this.offset = offset;
            this.compressedLength = compressedLength;
            this.rawLength = rawLength;
        }
    }
}
