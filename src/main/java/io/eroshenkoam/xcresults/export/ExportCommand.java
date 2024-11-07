package io.eroshenkoam.xcresults.export;

import org.apache.commons.io.FileUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    protected List<String> inputPath;

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
            for (Path path : input) {
                runUnsafe(path, output);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private void runUnsafe(final Path input, final Path output) throws Exception {
        System.out.printf("Export xcresults from [%s] to [%s]\n", input, output);
        final ExportProcessor processor = new ExportProcessor(
                input, output, addCarouselAttachment, carouselTemplatePath
        );
        processor.export();
    }

    private List<Path> getInputPaths() {
        if (inputPath.size() == 2 && Objects.isNull(outputPath)) {
            return findGlob(inputPath.get(0));
        } else {
            return inputPath.stream()
                    .flatMap(i -> findGlob(i).stream())
                    .collect(Collectors.toList());
        }
    }

    private Path getOutputPath() {
        if (inputPath.size() == 2 && Objects.isNull(outputPath)) {
            return Path.of(inputPath.get(1));
        } else {
            return outputPath;
        }
    }

    private List<Path> findGlob(final String input) {
        final Path path = Paths.get(input);
        if (path.isAbsolute()) {
            return List.of(path);
        }
        final Path workDir = Path.of(Optional.ofNullable(System.getProperty("user.dir")).orElse(""));
        final String pattern = String.format("glob:%s", input);
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(pattern);
        try (final Stream<Path> walk = Files.walk(workDir)) {
            return walk.map(workDir::relativize)
                    .filter(pathMatcher::matches)
                    .map(workDir::resolve)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

}
