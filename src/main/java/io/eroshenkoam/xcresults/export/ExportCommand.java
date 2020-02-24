package io.eroshenkoam.xcresults.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.qameta.allure.model.TestResult;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(
        name = "export", mixinStandardHelpOptions = true,
        description = "Export XC test results to json with attachments"
)
public class ExportCommand implements Runnable {

    private static final String ACTIONS = "actions";
    private static final String ACTION_RESULT = "actionResult";

    private static final String SUMMARIES = "summaries";
    private static final String TESTABLE_SUMMARIES = "testableSummaries";

    private static final String TESTS = "tests";
    private static final String SUBTESTS = "subtests";

    private static final String ACTIVITY_SUMMARIES = "activitySummaries";
    private static final String SUBACTIVITIES = "subactivities";

    private static final String ATTACHMENTS = "attachments";

    private static final String FILENAME = "filename";
    private static final String PAYLOAD_REF = "payloadRef";

    private static final String SUMMARY_REF = "summaryRef";

    private static final String ID = "id";
    private static final String VALUE = "_value";
    private static final String VALUES = "_values";

    private static final String TEST_REF = "testsRef";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @CommandLine.Option(
            names = {"--format"},
            description = "Export format (json, allure2)"
    )
    protected ExportFormat format = ExportFormat.allure2;

    @CommandLine.Parameters(
            index = "0",
            description = "The directories with *.xcresults"
    )
    protected Path inputPath;

    @CommandLine.Parameters(
            index = "1",
            description = "Export output directory"
    )
    protected Path outputPath;

    @Override
    public void run() {
        try {
            runUnsafe();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private void runUnsafe() throws Exception {
        final JsonNode node = readSummary();
        if (Files.exists(outputPath)) {
            System.out.println("Delete existing output directory...");
            FileUtils.deleteDirectory(outputPath.toFile());
        }
        Files.createDirectories(outputPath);

        final List<String> testRefIds = new ArrayList<>();
        for (JsonNode action : node.get(ACTIONS).get(VALUES)) {
            testRefIds.add(action.get(ACTION_RESULT).get(TEST_REF).get(ID).get(VALUE).asText());
        }
        final List<String> testSummaryRefs = new ArrayList<>();
        for (final String testRefId : testRefIds) {
            final JsonNode testRef = getReference(testRefId);
            for (JsonNode summary : testRef.get(SUMMARIES).get(VALUES)) {
                for (JsonNode testableSummary : summary.get(TESTABLE_SUMMARIES).get(VALUES)) {
                    for (JsonNode test : testableSummary.get(TESTS).get(VALUES)) {
                        testSummaryRefs.addAll(getTestSummaryRefs(test));
                    }
                }
            }
        }
        System.out.println(String.format("Export information about %s test summaries...", testSummaryRefs.size()));
        final Map<String, String> attachmentsRefs = new HashMap<>();
        for (final String testSummaryRef : testSummaryRefs) {
            final JsonNode testSummary = getReference(testSummaryRef);
            exportTestSummary(testSummaryRef, testSummary);
            for (final JsonNode activity : testSummary.get(ACTIVITY_SUMMARIES).get(VALUES)) {
                attachmentsRefs.putAll(getAttachmentRefs(activity));
            }
        }
        System.out.println(String.format("Export information about %s attachments...", attachmentsRefs.size()));
        for (Map.Entry<String, String> attachment : attachmentsRefs.entrySet()) {
            final Path attachmentPath = outputPath.resolve(attachment.getKey());
            final String attachmentRef = attachment.getValue();
            exportReference(attachmentRef, attachmentPath);
        }
    }

    private void exportTestSummary(final String uuid, final JsonNode testSummary) throws IOException {
        switch (format) {
            case json: {
                final JsonNode formattedResult = new JsonExportFormatter().format(testSummary);
                final String testSummaryFilename = String.format("%s.json", uuid);
                final Path testSummaryPath = outputPath.resolve(testSummaryFilename);
                mapper.writeValue(testSummaryPath.toFile(), formattedResult);
                return;
            }
            case allure2: {
                final TestResult formattedResult = new Allure2ExportFormatter().format(testSummary);
                final String testSummaryFilename = String.format("%s-result.json", uuid);
                final Path testSummaryPath = outputPath.resolve(testSummaryFilename);
                mapper.writeValue(testSummaryPath.toFile(), formattedResult);
            }
        }
    }

    private Map<String, String> getAttachmentRefs(final JsonNode test) {
        final Map<String, String> refs = new HashMap<>();
        if (test.has(ATTACHMENTS)) {
            for (final JsonNode attachment : test.get(ATTACHMENTS).get(VALUES)) {
                final String fileName = attachment.get(FILENAME).get(VALUE).asText();
                final String attachmentRef = attachment.get(PAYLOAD_REF).get(ID).get(VALUE).asText();
                refs.put(fileName, attachmentRef);
            }
        }
        if (test.has(SUBACTIVITIES)) {
            for (final JsonNode subActivity : test.get(SUBACTIVITIES).get(VALUES)) {
                refs.putAll(getAttachmentRefs(subActivity));
            }
        }
        return refs;
    }

    private List<String> getTestSummaryRefs(final JsonNode test) {
        final List<String> refs = new ArrayList<>();
        if (test.has(SUMMARY_REF)) {
            refs.add(test.get(SUMMARY_REF).get(ID).get(VALUE).asText());
        }
        if (test.has(SUBTESTS)) {
            for (final JsonNode subTest : test.get(SUBTESTS).get(VALUES)) {
                refs.addAll(getTestSummaryRefs(subTest));
            }
        }
        return refs;
    }

    private JsonNode readSummary() throws IOException {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(
                "xcrun",
                "xcresulttool",
                "get",
                "--format", "json",
                "--path", inputPath.toAbsolutePath().toString()
        );
        return readProcessOutput(builder);
    }

    private JsonNode getReference(final String id) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(
                "xcrun",
                "xcresulttool",
                "get",
                "--format", "json",
                "--path", inputPath.toAbsolutePath().toString(),
                "--id", id
        );
        return readProcessOutput(builder);
    }

    private void exportReference(final String id, final Path output) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(
                "xcrun",
                "xcresulttool",
                "export",
                "--type", "file",
                "--path", inputPath.toAbsolutePath().toString(),
                "--id", id,
                "--output-path", output.toAbsolutePath().toString()
        );
        builder.start();
    }

    private JsonNode readProcessOutput(final ProcessBuilder builder) throws IOException {
        final Process process = builder.start();
        try (InputStream input = process.getInputStream()) {
            return mapper.readTree(input);
        }
    }
}
