package io.eroshenkoam.xcresults.export;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ExportMeta {

    private Map<String, String> labels;

    public ExportMeta label(final String name, final String value) {
        getLabels().put(name, value);
        return this;
    }

    public Map<String, String> getLabels() {
        if (Objects.isNull(labels)) {
            this.labels = new HashMap<>();
        }
        return labels;
    }

}
