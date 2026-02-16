package io.eroshenkoam.xcresults.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import freemarker.template.Version;
import io.eroshenkoam.xcresults.broken.BrokenPostProcessor;
import io.eroshenkoam.xcresults.carousel.CarouselPostProcessor;
import io.qameta.allure.model.ExecutableItem;
import io.qameta.allure.model.TestResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.eroshenkoam.xcresults.util.FormatUtil.getResultFilePath;
import static io.eroshenkoam.xcresults.util.FormatUtil.parseDate;
import static io.eroshenkoam.xcresults.util.ProcessUtil.*;

public class ExportProcessor {

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

    private final Path inputPath;
    private final Path outputPath;
    private final String brokenConfigPath;
    private final Boolean addCarouselAttachment;
    private final String carouselTemplatePath;
    private final int threadCount;
    private final ExecutorService customThreadPool;
    private final ThreadLocal<Allure2ExportFormatter> formatterThreadLocal =
            ThreadLocal.withInitial(Allure2ExportFormatter::new);

    public ExportProcessor(final Path inputPath,
                           final Path outputPath,
                           final String brokenConfigPath,
                           final Boolean addCarouselAttachment,
                           final String carouselTemplatePath,
                           String threadCount) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.brokenConfigPath = brokenConfigPath;
        this.addCarouselAttachment = addCarouselAttachment;
        this.carouselTemplatePath = carouselTemplatePath;
        this.threadCount = Integer.parseInt(threadCount);
        this.customThreadPool = Executors.newFixedThreadPool(this.threadCount);
    }

    public void export() throws Exception {
        if (threadCount == 1) {
            exportSequential();
        } else {
            exportParallel();
        }
    }

    private void exportSequential() throws Exception {
        final JsonNode node = readSummary();

        final Map<String, ExportMeta> testRefIds = new HashMap<>();
        for (JsonNode action : node.get(ACTIONS).get(VALUES)) {
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
        for (Map.Entry<String, ExportMeta> entry : testRefIds.entrySet()) {
            final JsonNode testRef = getReference(entry.getKey());
            for (JsonNode summary : testRef.get(SUMMARIES).get(VALUES)) {
                for (JsonNode testableSummary : summary.get(TESTABLE_SUMMARIES).get(VALUES)) {
                    final ExportMeta testMeta = getTestMeta(entry.getValue(), testableSummary);
                    if (testableSummary.has(TESTS) && testableSummary.get(TESTS).has(VALUES)) {
                        for (JsonNode test : testableSummary.get(TESTS).get(VALUES)) {
                            getTestSummaries(test).forEach(testSummary -> {
                                testSummaries.put(testSummary, testMeta);
                            });
                        }
                    } else {
                        System.out.printf("No tests found for '%s'%n", testableSummary.get("name").get(VALUE));
                    }
                }
            }
        }

        System.out.printf("Export information about %s test summaries...%n", testSummaries.size());

        final Map<String, String> attachmentsRefs = new HashMap<>();
        final Map<Path, TestResult> testResults = new HashMap<>();
        final Allure2ExportFormatter formatter = new Allure2ExportFormatter();

        for (Map.Entry<JsonNode, ExportMeta> entry : testSummaries.entrySet()) {
            final TestResult testResult = formatter.format(entry.getValue(), entry.getKey());
            final Path testSummaryPath = getResultFilePath(outputPath);
            mapper.writeValue(testSummaryPath.toFile(), testResult);

            final Map<String, List<String>> attachmentSources = getAttachmentSources(testResult);
            final List<JsonNode> summaries = new ArrayList<>();
            summaries.addAll(getAttributeValues(entry.getKey(), ACTIVITY_SUMMARIES));
            summaries.addAll(getAttributeValues(entry.getKey(), FAILURE_SUMMARIES));
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

        for (Map.Entry<String, String> entry : attachmentsRefs.entrySet()) {
            final Path attachmentPath = outputPath.resolve(entry.getKey());
            exportReference(entry.getValue(), attachmentPath);
        }

        final List<ExportPostProcessor> postProcessors = new ArrayList<>();
        if (Boolean.TRUE.equals(addCarouselAttachment)) {
            postProcessors.add(new CarouselPostProcessor(carouselTemplatePath));
        }
        if (Objects.nonNull(brokenConfigPath)) {
            postProcessors.add(new BrokenPostProcessor(brokenConfigPath));
        }
        for (ExportPostProcessor postProcessor : postProcessors) {
            postProcessor.processTestResults(outputPath, testResults);
        }
    }

    private void exportParallel() {
        try {
            final JsonNode node = readSummary();

            final ConcurrentHashMap<String, ExportMeta> testRefIds = new ConcurrentHashMap<>();
            for (JsonNode action : node.get(ACTIONS).get(VALUES)) {
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

            final ConcurrentHashMap<JsonNode, ExportMeta> testSummaries = new ConcurrentHashMap<>();

            List<Map.Entry<String, ExportMeta>> refEntries = new ArrayList<>(testRefIds.entrySet());
            int refBatchSize = Math.max(1, refEntries.size() / threadCount);
            List<List<Map.Entry<String, ExportMeta>>> refBatches = partition(refEntries, refBatchSize);

            List<CompletableFuture<Void>> refFutures = new ArrayList<>();
            for (List<Map.Entry<String, ExportMeta>> batch : refBatches) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (Map.Entry<String, ExportMeta> entry : batch) {
                        final JsonNode testRef = getReference(entry.getKey());
                        for (JsonNode summary : testRef.get(SUMMARIES).get(VALUES)) {
                            for (JsonNode testableSummary : summary.get(TESTABLE_SUMMARIES).get(VALUES)) {
                                final ExportMeta testMeta = getTestMeta(entry.getValue(), testableSummary);
                                if (testableSummary.has(TESTS) && testableSummary.get(TESTS).has(VALUES)) {
                                    for (JsonNode test : testableSummary.get(TESTS).get(VALUES)) {
                                        getTestSummaries(test).forEach(testSummary -> {
                                            testSummaries.put(testSummary, testMeta);
                                        });
                                    }
                                } else {
                                    System.out.printf("No tests found for '%s'%n", testableSummary.get("name").get(VALUE));
                                }
                            }
                        }
                    }
                }, customThreadPool);
                refFutures.add(future);
            }
            CompletableFuture.allOf(refFutures.toArray(new CompletableFuture[0])).join();

            System.out.printf("Export information about %s test summaries...%n", testSummaries.size());

            final ConcurrentHashMap<String, String> attachmentsRefs = new ConcurrentHashMap<>();
            final ConcurrentHashMap<Path, TestResult> testResults = new ConcurrentHashMap<>();

            List<Map.Entry<JsonNode, ExportMeta>> testEntries = new ArrayList<>(testSummaries.entrySet());
            int testBatchSize = Math.max(1, testEntries.size() / threadCount);
            List<List<Map.Entry<JsonNode, ExportMeta>>> testBatches = partition(testEntries, testBatchSize);

            List<CompletableFuture<Void>> testFutures = new ArrayList<>();
            for (List<Map.Entry<JsonNode, ExportMeta>> batch : testBatches) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    for (Map.Entry<JsonNode, ExportMeta> entry : batch) {
                        try {
                            final TestResult testResult = formatterThreadLocal.get().format(entry.getValue(), entry.getKey());
                            final Path testSummaryPath = getResultFilePath(outputPath);
                            mapper.writeValue(testSummaryPath.toFile(), testResult);

                            final Map<String, List<String>> attachmentSources = getAttachmentSources(testResult);
                            final List<JsonNode> summaries = new ArrayList<>();
                            summaries.addAll(getAttributeValues(entry.getKey(), ACTIVITY_SUMMARIES));
                            summaries.addAll(getAttributeValues(entry.getKey(), FAILURE_SUMMARIES));
                            summaries.forEach(summary -> {
                                getAttachmentRefs(summary).forEach((name, ref) -> {
                                    if (attachmentSources.containsKey(name)) {
                                        final List<String> sources = attachmentSources.get(name);
                                        sources.forEach(source -> attachmentsRefs.putIfAbsent(source, ref));
                                    }
                                });
                            });
                            testResults.put(testSummaryPath, testResult);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, customThreadPool);
                testFutures.add(future);
            }
            CompletableFuture.allOf(testFutures.toArray(new CompletableFuture[0])).join();

            System.out.printf("Export information about %s attachments...%n", attachmentsRefs.size());

            exportAttachmentsInBatches(attachmentsRefs);

            final List<ExportPostProcessor> postProcessors = new ArrayList<>();
            if (Boolean.TRUE.equals(addCarouselAttachment)) {
                postProcessors.add(new CarouselPostProcessor(carouselTemplatePath));
            }
            if (Objects.nonNull(brokenConfigPath)) {
                postProcessors.add(new BrokenPostProcessor(brokenConfigPath));
            }
            for (ExportPostProcessor postProcessor : postProcessors) {
                postProcessor.processTestResults(outputPath, testResults);
            }
        } finally {
            customThreadPool.shutdown();
            try {
                if (!customThreadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    customThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                customThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void exportAttachmentsInBatches(Map<String, String> attachmentsRefs) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(attachmentsRefs.entrySet());
        if (entries.isEmpty()) return;

        int batchSize = Math.max(1, Math.min(entries.size() / (threadCount * 2), 100));
        List<List<Map.Entry<String, String>>> batches = partition(entries, batchSize);

        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
        for (List<Map.Entry<String, String>> batch : batches) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (Map.Entry<String, String> entry : batch) {
                    try {
                        Path attachmentPath = outputPath.resolve(entry.getKey());
                        exportReference(entry.getValue(), attachmentPath);
                    } catch (Exception e) {
                        System.err.printf("Failed to export attachment %s: %s%n", entry.getKey(), e.getMessage());
                    }
                }
            }, customThreadPool);
            batchFutures.add(future);
        }
        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
    }

    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
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
            for (final JsonNode attachment : test.get(ATTACHMENTS).get(VALUES)) {
                if (attachment.has(PAYLOAD_REF)) {
                    final String fileName = attachment.get(FILENAME).get(VALUE).asText();
                    final String attachmentRef = attachment.get(PAYLOAD_REF).get(ID).get(VALUE).asText();
                    refs.put(fileName, attachmentRef);
                }
            }
        }
        if (test.has(SUBACTIVITIES)) {
            for (final JsonNode subActivity : test.get(SUBACTIVITIES).get(VALUES)) {
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
            for (final JsonNode subTest : test.get(SUBTESTS).get(VALUES)) {
                summaries.addAll(getTestSummaries(subTest));
            }
        }
        return summaries;
    }

    private List<JsonNode> getAttributeValues(final JsonNode node, final String attributeName) {
        final List<JsonNode> result = new ArrayList<>();
        if (node.has(attributeName) && node.get(attributeName).has(VALUES)) {
            node.get(attributeName).get(VALUES).forEach(result::add);
        }
        return result;
    }

    private static boolean isLegacyMode() {
        try {
            final String output = readProcessOutputAsString(new ProcessBuilder("xcodebuild", "-version"));
            final String versionLine = output.split("\n")[0];
            final Version version = new Version(versionLine.replaceFirst("Xcode ", "").trim());
            return version.getMajor() >= 16;
        } catch (final Exception e) {
            return false;
        }
    }

    private ProcessBuilder processBuilderForXCResultToolCommand(String... command) {
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(command);
        builder.command().add(0, "xcrun");
        builder.command().add(1, "xcresulttool");
        if (isLegacyMode()) {
            builder.command().add("--legacy");
        }
        return builder;
    }

    private JsonNode readSummary() {
        final ProcessBuilder builder = processBuilderForXCResultToolCommand(
                "get",
                "--format", "json",
                "--path", inputPath.toAbsolutePath().toString()
        );
        return readProcessOutputAsJson(builder, mapper);
    }

    private JsonNode getReference(final String id) {
        return readProcessOutputAsJson(
                processBuilderForXCResultToolCommand(
                        "get",
                        "--format", "json",
                        "--path", inputPath.toAbsolutePath().toString(),
                        "--id", id
                ),
                mapper
        );
    }

    private void exportReference(final String id, final Path output) {
        final ProcessBuilder exportBuilder = processBuilderForXCResultToolCommand(
                "export",
                "--type", "file",
                "--path", inputPath.toAbsolutePath().toString(),
                "--id", id,
                "--output-path", output.toAbsolutePath().toString()
        );

        readProcessOutput(exportBuilder, (i) -> null);

        if (FILE_EXTENSION_HEIC.equals(FilenameUtils.getExtension(output.toString()))) {
            convertHeicToJpeg(output);
        }
    }

    private void convertHeicToJpeg(Path heicPath) {
        try {
            final Path parent = heicPath.getParent();
            final String jpegFilename = String.format("%s.%s",
                    FilenameUtils.getBaseName(heicPath.toString()), "jpeg");
            final Path jpegFilePath = parent.resolve(jpegFilename);

            final ProcessBuilder convertBuilder = new ProcessBuilder();
            convertBuilder.command(
                    "sips", "-s",
                    "format", "jpeg",
                    heicPath.toAbsolutePath().toString(),
                    "--out", jpegFilePath.toAbsolutePath().toString()
            );

            Process process = convertBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                FileUtils.deleteQuietly(heicPath.toFile());
            } else {
                System.err.printf("Failed to convert HEIC to JPEG, sips exit code %d: %s%n",
                        exitCode, heicPath);
            }
        } catch (IOException e) {
            System.err.printf("IO error converting HEIC to JPEG %s: %s%n", heicPath, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.printf("Interrupted while converting HEIC to JPEG %s%n", heicPath);
        }
    }
}