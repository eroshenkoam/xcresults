package io.eroshenkoam.xcresults.broken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.eroshenkoam.xcresults.export.ExportPostProcessor;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.TestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BrokenPostProcessor implements ExportPostProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final Path configPath;

    public BrokenPostProcessor(final String configPath) {
        this.configPath = Path.of(configPath).toAbsolutePath();
    }

    @Override
    public void processTestResults(final Path outputPath, final Map<Path, TestResult> testResults) {
        System.out.println("Broken test processor enabled");
        if (Files.notExists(configPath)) {
            System.out.println("Broken test processor config file not found: " + configPath);
            return;
        }
        final Optional<BrokenConfig> config = readConfig(configPath);
        if (config.isEmpty()) {
            System.out.println("Broken test processor config file is invalid: " + configPath);
            return;
        }
        testResults.forEach((path, testResult) -> updateStatus(path, testResult, config.get()));
    }

    private void updateStatus(final Path path, final TestResult testResult, final BrokenConfig config) {
        if (Objects.nonNull(testResult.getStatus()) && testResult.getStatus().equals(Status.FAILED)) {
            final StatusDetails statusDetails = testResult.getStatusDetails();

            final String message = Optional.ofNullable(statusDetails.getMessage()).orElse("");
            final String trace = Optional.ofNullable(statusDetails.getTrace()).orElse("");

            if (isBroken(message, trace, config)) {
                testResult.setStatus(Status.BROKEN);
                try {
                    MAPPER.writeValue(path.toFile(), testResult);
                } catch (IOException e) {
                    System.out.println("Can not update broken test status: " + e.getMessage());
                }
            }
        }
    }

    private static boolean isBroken(final String message, final String trace, final BrokenConfig config) {
        for (final BrokenStatusMatcher matcher: config.getMatchers()) {
            final boolean messageMatched = Optional.ofNullable(matcher.getMessagePattern())
                    .map(pattern -> pattern.matcher(message).matches())
                    .orElse(false);
            final boolean traceMatched = Optional.ofNullable(matcher.getTracePattern())
                    .map(pattern -> pattern.matcher(message).matches())
                    .orElse(false);
            if (messageMatched || traceMatched) {
                return true;
            }
        }
        return false;
    }

    private static Optional<BrokenConfig> readConfig(final Path config) {
        try {
            return Optional.of(MAPPER.readValue(config.toFile(), BrokenConfig.class));
        } catch (IOException e) {
            System.out.println("Can not read broken test processor config file:" + e.getMessage());
            return Optional.empty();
        }
    }
}
