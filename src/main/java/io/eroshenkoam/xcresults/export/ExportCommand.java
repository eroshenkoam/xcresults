package io.eroshenkoam.xcresults.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.eroshenkoam.xcresults.carousel.CarouselPostProcessor;
import io.qameta.allure.model.ExecutableItem;
import io.qameta.allure.model.TestResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Iterator;

import static io.eroshenkoam.xcresults.util.FormatUtil.getResultFilePath;
import static io.eroshenkoam.xcresults.util.FormatUtil.getValues;
import static io.eroshenkoam.xcresults.util.FormatUtil.parseDate;

@CommandLine.Command(
        name = "export", mixinStandardHelpOptions = true,
        description = "Export XC test results to json with attachments"
)
public class ExportCommand implements Runnable {

    public static final String FILE_EXTENSION_HEIC = "heic";

    private static final String ACTIONS = "actions";
    private static final String ACTION_RESULT = "actionResult";

    private static final String RUN_DESTINATION = "runDestination";
    private static final String START_TIME = "startedTime";

    private static final String SUMMARIES = "summaries";
    private static final String TESTABLE_SUMMARIES = "testableSummaries";

    private static final String TESTS = "tests";
    private static final String SUBTESTS = "subtests";

    private static final String FAILURE_SUMMARIES = "failureSummaries";
    private static final String ACTIVITY_SUMMARIES = "activitySummaries";
    private static final String SUBACTIVITIES = "subactivities";

    private static final String ATTACHMENTS = "attachments";

    private static final String FILENAME = "filename";
    private static final String PAYLOAD_REF = "payloadRef";

    private static final String SUMMARY_REF = "summaryRef";

    private static final String SUITE = "suite";

    private static final String ID = "id";
    private static final String TYPE = "_type";
    private static final String NAME = "_name";
    private static final String VALUE = "_value";
    private static final String VALUES = "_values";
    private static final String DISPLAY_NAME = "displayName";
    private static final String TARGET_NAME = "targetName";

    private static final String TEST_REF = "testsRef";

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @CommandLine.Option(
            names = {"--format"},
            description = "Export format (json, allure2), *deprecated"
    )
    protected ExportFormat format = ExportFormat.allure2;

    @CommandLine.Option(
            names = {"--add-carousel-attachment"},
            description = "Add carousel attachment to test results"
    )
    private Boolean addCarouselAttachment;

    @CommandLine.Option(
            names = {"--carousel-template-path"},
            description = "Carousel attachment template path"
    )
    private String carouselTemplatePath;

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
        if (node == null) {
            System.out.println("ERROR: xcresulttool returned no output");
            return;
        }
        if (!node.has(ACTIONS)) {
            System.out.printf("ERROR: no '%s' field in summary. Available keys: %s%n",
                    ACTIONS, iteratorToList(node.fieldNames()));
            return;
        }
        if (Files.exists(outputPath)) {
            System.out.println("Delete existing output directory...");
            FileUtils.deleteDirectory(outputPath.toFile());
        }
        Files.createDirectories(outputPath);

        final Map<String, ExportMeta> testRefIds = new HashMap<>();
        for (JsonNode action : getValues(node.get(ACTIONS))) {
            if (action.get(ACTION_RESULT).has(TEST_REF)) {
                final ExportMeta meta = new ExportMeta();
                if (action.has(RUN_DESTINATION)) {
                    meta.label(RUN_DESTINATION, action.get(RUN_DESTINATION).get(DISPLAY_NAME).get(VALUE).asText());
                }
                if (action.has(START_TIME)) {
                    meta.setStart(parseDate(action.get(START_TIME).get(VALUE).textValue()));
                }
                testRefIds.put(action.get(ACTION_RESULT).get(TEST_REF).get(ID).get(VALUE).asText(), meta);
            }
        }

        final Map<JsonNode, ExportMeta> testSummaries = new HashMap<>();
        testRefIds.forEach((testRefId, meta) -> {
            final JsonNode testRef = getReference(testRefId);
            for (JsonNode summary : getValues(testRef.get(SUMMARIES))) {
                for (JsonNode testableSummary : getValues(summary.get(TESTABLE_SUMMARIES))) {
                    final ExportMeta testMeta = getTestMeta(meta, testableSummary);
                    if (testableSummary.has(TESTS)) {
                        for (JsonNode test : getValues(testableSummary.get(TESTS))) {
                            getTestSummaries(test).forEach(testSummary -> {
                                testSummaries.put(testSummary, testMeta);
                            });
                        }
                    } else {
                        System.out.printf("No tests found for '%s'%n", testableSummary.get("name").get(VALUE));
                    }
                }
            }
        });

        System.out.printf("Export information about %s test summaries...%n", testSummaries.size());
        final Map<String, String> attachmentsRefs = new HashMap<>();
        final Map<Path, TestResult> testResults = new HashMap<>();
        for (final Map.Entry<JsonNode, ExportMeta> entry : testSummaries.entrySet()) {
            final JsonNode testSummary = entry.getKey();
            final ExportMeta meta = entry.getValue();

            final TestResult testResult = new Allure2ExportFormatter().format(meta, testSummary);
            final Path testSummaryPath = getResultFilePath(outputPath);
            mapper.writeValue(testSummaryPath.toFile(), testResult);

            final Map<String, List<String>> attachmentSources = getAttachmentSources(testResult);
            final List<JsonNode> summaries = new ArrayList<>();
            summaries.addAll(getAttributeValues(testSummary, ACTIVITY_SUMMARIES));
            summaries.addAll(getAttributeValues(testSummary, FAILURE_SUMMARIES));
            summaries.forEach(summary -> {
                getAttachmentRefs(summary).forEach((name, ref) -> {
                    if (attachmentSources.containsKey(name)) {
                        final List<String> sources = attachmentSources.get(name);
                        sources.forEach(source -> attachmentsRefs.put(source, ref));
                    }
                });
            });
            testResults.put(testSummaryPath, testResult);
        }
        System.out.printf("Export information about %s attachments...%n", attachmentsRefs.size());
        for (Map.Entry<String, String> attachment : attachmentsRefs.entrySet()) {
            final String attachmentRef = attachment.getValue();
            final Path attachmentPath = outputPath.resolve(attachment.getKey());
            exportReference(attachmentRef, attachmentPath);
        }
        final List<ExportPostProcessor> postProcessors = new ArrayList<>();
        if (Objects.nonNull(addCarouselAttachment)) {
            postProcessors.add(new CarouselPostProcessor(carouselTemplatePath));
        }
        postProcessors.forEach(postProcessor -> postProcessor.processTestResults(outputPath, testResults));
    }

    private ExportMeta getTestMeta(final ExportMeta meta, final JsonNode testableSummary) {
        final ExportMeta exportMeta = new ExportMeta();
        exportMeta.setStart(meta.getStart());
        meta.getLabels().forEach(exportMeta::label);
        exportMeta.label(SUITE, testableSummary.get(TARGET_NAME).get(VALUE).asText());
        return exportMeta;
    }

    private Map<String, List<String>> getAttachmentSources(final ExecutableItem executableItem) {
        final Map<String, List<String>> attachments = new HashMap<>();
        if (Objects.nonNull(executableItem.getAttachments())) {
            executableItem.getAttachments().forEach(a -> {
                final List<String> sources = attachments.getOrDefault(a.getName(), new ArrayList<>());
                sources.add(a.getSource());
                attachments.put(a.getName(), sources);
            });
        }
        if (Objects.nonNull(executableItem.getSteps())) {
            executableItem.getSteps().forEach(s -> attachments.putAll(getAttachmentSources(s)));
        }
        return attachments;
    }

    private Map<String, String> getAttachmentRefs(final JsonNode test) {
        final Map<String, String> refs = new HashMap<>();
        if (test.has(ATTACHMENTS)) {
            for (final JsonNode attachment : getValues(test.get(ATTACHMENTS))) {
                if (attachment.has(PAYLOAD_REF)) {
                    final String fileName = attachment.get(FILENAME).get(VALUE).asText();
                    final String attachmentRef = attachment.get(PAYLOAD_REF).get(ID).get(VALUE).asText();
                    refs.put(fileName, attachmentRef);
                }
            }
        }
        if (test.has(SUBACTIVITIES)) {
            for (final JsonNode subActivity : getValues(test.get(SUBACTIVITIES))) {
                refs.putAll(getAttachmentRefs(subActivity));
            }
        }
        return refs;
    }

    private List<JsonNode> getTestSummaries(final JsonNode test) {
        final List<JsonNode> summaries = new ArrayList<>();
        if (test.has(SUMMARY_REF)) {
            final String ref = test.get(SUMMARY_REF).get(ID).get(VALUE).asText();
            summaries.add(getReference(ref));
        } else {
            if (test.has(TYPE) && test.get(TYPE).get(NAME).textValue().equals("ActionTestMetadata")) {
                summaries.add(test);
            }
        }

        if (test.has(SUBTESTS)) {
            for (final JsonNode subTest : getValues(test.get(SUBTESTS))) {
                summaries.addAll(getTestSummaries(subTest));
            }
        }
        return summaries;
    }

    private List<JsonNode> getAttributeValues(final JsonNode node, final String attributeName) {
        final List<JsonNode> result = new ArrayList<>();
        if (node.has(attributeName)) {
            getValues(node.get(attributeName)).forEach(result::add);
        }
        return result;
    }

    private JsonNode readSummary() {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(
                "xcrun",
                "xcresulttool",
                "get",
                "--legacy",
                "--format", "json",
                "--path", inputPath.toAbsolutePath().toString()
        );
        return readProcessOutput(builder);
    }

    private JsonNode getReference(final String id) {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(
                "xcrun",
                "xcresulttool",
                "get",
                "--legacy",
                "--format", "json",
                "--path", inputPath.toAbsolutePath().toString(),
                "--id", id
        );
        return readProcessOutput(builder);
    }

    private void exportReference(final String id, final Path output) {
        final ProcessBuilder exportBuilder = new ProcessBuilder();
        exportBuilder.command(
                "xcrun",
                "xcresulttool",
                "export",
                "--legacy",
                "--type", "file",
                "--path", inputPath.toAbsolutePath().toString(),
                "--id", id,
                "--output-path", output.toAbsolutePath().toString()
        );
        runProcess(exportBuilder);
        if (FILE_EXTENSION_HEIC.equals(FilenameUtils.getExtension(output.toString()))) {
            convertHeicToJpeg(output);
        }
    }

    private void convertHeicToJpeg(Path heicPath) {
        try {
            final Path parent = heicPath.getParent();
            final String jpegFilename = String.format("%s.%s", FilenameUtils.getBaseName(heicPath.toString()), "jpeg");
            final Path jpegFilePath = parent.resolve(jpegFilename);
            final ProcessBuilder convertBuilder = new ProcessBuilder();
            convertBuilder.command(
                    "sips", "-s",
                    "format", "jpeg",
                    heicPath.toAbsolutePath().toString(),
                    "--out", jpegFilePath.toAbsolutePath().toString()
            );
            Process process = convertBuilder.start();
            process.waitFor();
            FileUtils.deleteQuietly(heicPath.toFile());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void runProcess(final ProcessBuilder builder) {
        try {
            final Process process = builder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode readProcessOutput(final ProcessBuilder builder) {
        try {
            builder.redirectErrorStream(false);
            final Process process = builder.start();
            final JsonNode result;
            try (InputStream input = process.getInputStream()) {
                result = mapper.readTree(input);
            }
            try (InputStream err = process.getErrorStream()) {
                final String stderr = new String(err.readAllBytes()).trim();
                if (!stderr.isEmpty()) {
                    System.out.printf("xcresulttool stderr: %s%n", stderr);
                }
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> List<T> iteratorToList(final Iterator<T> iterator) {
        final List<T> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }
}
