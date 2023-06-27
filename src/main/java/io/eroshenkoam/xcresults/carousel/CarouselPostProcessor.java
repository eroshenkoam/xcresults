package io.eroshenkoam.xcresults.carousel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import freemarker.template.TemplateException;
import io.eroshenkoam.xcresults.export.ExportPostProcessor;
import io.eroshenkoam.xcresults.util.FreemarkerUtil;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.ExecutableItem;
import io.qameta.allure.model.TestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.eroshenkoam.xcresults.util.FormatUtil.getAttachmentFileName;

public class CarouselPostProcessor implements ExportPostProcessor {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @Override
    public void processTestResults(final Path outputPath, final Map<Path, TestResult> testResults) {
        System.out.println("Carousel attachment feature enabled");
        testResults.forEach((path, result) -> processTestResult(outputPath, path, result));
    }

    private void processTestResult(final Path outputPath, final Path path, final TestResult testResult) {
        final List<Attachment> attachments = getAttachment(testResult, (a) -> a.getName().endsWith(".jpeg"));
        final List<CarouselImage> carouselImages = attachments.stream()
                .map(a -> convert(outputPath, a))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        if (carouselImages.size() == 0) {
            return;
        }
        final Carousel carousel = new Carousel(carouselImages);
        try {
            final String carouselContent = FreemarkerUtil.render("templates/carousel.ftl", Map.of("carousel", carousel));
            final Path carouselPath = outputPath.resolve(getAttachmentFileName("html"));
            Files.write(carouselPath, carouselContent.getBytes(StandardCharsets.UTF_8));
            testResult.getAttachments().add(new Attachment()
                    .setName("Carousel")
                    .setSource(carouselPath.getFileName().toString()));
            mapper.writeValue(path.toFile(), testResult);
        } catch (IOException | TemplateException e) {
            System.out.println("Can not create carousel attachment: " + e.getMessage());
        }
    }

    private List<Attachment> getAttachment(final ExecutableItem item, final Predicate<Attachment> filter) {
        final List<Attachment> attachments = new ArrayList<>();
        item.getAttachments().stream()
                .filter(filter)
                .forEach(attachments::add);
        if (Objects.nonNull(item.getSteps())) {
            item.getSteps().forEach(step -> attachments.addAll(getAttachment(step, filter)));
        }
        return attachments;
    }

    private Optional<CarouselImage> convert(final Path outputPath, final Attachment attachment) {
        try {
            final byte[] bytes = Files.readAllBytes(outputPath.resolve(attachment.getSource()));
            final String content = "data:image/jpeg;base64, " + Base64.getEncoder().encodeToString(bytes);
            return Optional.of(new CarouselImage(attachment.getName(), content));
        } catch (IOException e) {
            return Optional.empty();
        }

    }

}
