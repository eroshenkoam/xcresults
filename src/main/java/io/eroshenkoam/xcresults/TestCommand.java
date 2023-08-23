package io.eroshenkoam.xcresults;

import freemarker.template.TemplateException;
import io.eroshenkoam.xcresults.util.FreemarkerUtil;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Map;

@CommandLine.Command(
        name = "test", mixinStandardHelpOptions = true,
        description = "Test template"
)
public class TestCommand implements Runnable {

    @Override
    public void run() {
        try {
            final String carouselContent = FreemarkerUtil
                    .render("templates/carousel.ftl", Map.of("carousel", new Object()));
        } catch (IOException | TemplateException e) {
            throw new RuntimeException(e);
        }
    }

}
