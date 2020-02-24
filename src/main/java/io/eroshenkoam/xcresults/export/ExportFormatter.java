package io.eroshenkoam.xcresults.export;

import com.fasterxml.jackson.databind.JsonNode;

public interface ExportFormatter {

    Object format(final JsonNode node);

}
