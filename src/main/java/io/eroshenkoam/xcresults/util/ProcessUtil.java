package io.eroshenkoam.xcresults.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ProcessUtil {

    private ProcessUtil() {
    }

    public static String readProcessOutputAsString(final ProcessBuilder builder) {
        return readProcessOutput(builder, input -> IOUtils.toString(input, StandardCharsets.UTF_8));
    }

    public static JsonNode readProcessOutputAsJson(final ProcessBuilder builder,
                                                   final ObjectMapper mapper) {
        return readProcessOutput(builder, mapper::readTree);
    }

    public static <T> T readProcessOutput(final ProcessBuilder builder,
                                          final ThrowableFunction<InputStream, T> reader) {
        try {
            final Process process = builder.start();
            try (InputStream input = process.getInputStream()) {
                if (Objects.nonNull(input)) {
                    return reader.apply(input);
                } else {
                    return null;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface ThrowableFunction<T, R> {
        R apply(T t) throws IOException;
    }

}
