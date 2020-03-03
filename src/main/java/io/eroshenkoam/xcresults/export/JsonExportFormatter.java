package io.eroshenkoam.xcresults.export;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonExportFormatter implements ExportFormatter {
    @Override
    public JsonNode format(final ExportMeta meta, final JsonNode node) {
        return node;
    }
}
