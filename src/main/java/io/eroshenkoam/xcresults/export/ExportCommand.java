package io.eroshenkoam.xcresults.export;

import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@CommandLine.Command(
        name = "export", mixinStandardHelpOptions = true,
        description = "Export XC test results to json with attachments"
)
public class ExportCommand implements Runnable {

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
            description = "The directories with *.xcresults"
    )
    protected List<Path> inputPath;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Export output directory"
    )
    protected Path outputPath;

    @Override
    public void run() {
        try {
            final List<Path> input = getInputPaths();
            final Path output = getOutputPath();
            if (Objects.isNull(output)) {
                System.out.println("Output path [-o, --output] is required");
                return;
            }
            if (Files.exists(output)) {
                System.out.println("Delete existing output directory...");
                FileUtils.deleteDirectory(output.toFile());
            }
            Files.createDirectories(output);
            for (Path path: input) {
                runUnsafe(path, output);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private void runUnsafe(final Path input, final Path output) throws Exception  {
        System.out.printf("Export xcresults from [%s] to [%s]\n", input, output);
        final ExportProcessor processor = new ExportProcessor(
                input, output, addCarouselAttachment, carouselTemplatePath
        );
        processor.export();
    }

    private List<Path> getInputPaths() {
        if (inputPath.size() == 2 && Objects.isNull(outputPath)) {
            return Arrays.asList(inputPath.get(0));
        } else {
            return inputPath;
        }
    }

    private Path getOutputPath() {
        if (inputPath.size() == 2 && Objects.isNull(outputPath)) {
            return inputPath.get(1);
        } else {
            return outputPath;
        }
    }

}
