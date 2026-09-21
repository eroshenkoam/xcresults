package io.eroshenkoam.xcresults.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;

import static java.util.Objects.isNull;

public final class JsonUtil {

    private static final String VALUES = "_values";

    private JsonUtil() {
    }

    /**
     * Xcode 27 serialises an empty array as {@code {"_type":{"_name":"Array"}}} with no
     * {@code _values} key, where earlier Xcode versions emitted the key with an empty array.
     * Reading it unguarded yields a null Iterable and blows up the export.
     */
    public static Iterable<JsonNode> values(final JsonNode container) {
        if (isNull(container)) {
            return Collections.emptyList();
        }
        final JsonNode values = container.get(VALUES);
        return isNull(values) ? Collections.emptyList() : values;
    }

    public static Iterable<JsonNode> values(final JsonNode parent, final String field) {
        return isNull(parent) ? Collections.emptyList() : values(parent.get(field));
    }

}
