package io.eroshenkoam.xcresults.export;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ExportMeta {

    private Map<String, String> parameters;

    public ExportMeta parameter(final String name, final String value) {
        getParameters().put(name, value);
        return this;
    }

    public Map<String, String> getParameters() {
        if (Objects.isNull(parameters)) {
            this.parameters = new HashMap<>();
        }
        return parameters;
    }

}
