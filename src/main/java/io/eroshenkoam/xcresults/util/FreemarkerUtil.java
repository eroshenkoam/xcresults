package io.eroshenkoam.xcresults.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class FreemarkerUtil {

    private FreemarkerUtil() {
    }

    public static String render(final Path templatePath, final Map<String, Object> data)
            throws IOException, TemplateException {
        final Template template = new Template(
                templatePath.getFileName().toString(), Files.readString(templatePath), getDefaultConfiguration()
        );
        return render(template, data);
    }

    public static String render(final String templateName, final Map<String, Object> data)
            throws IOException, TemplateException {
        return render(getDefaultConfiguration().getTemplate(templateName), data);
    }

    public static Configuration getDefaultConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_30);
        configuration.setClassForTemplateLoading(FreemarkerUtil.class, "/");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }

    private static String render(final Template template, final Map<String, Object> data)
            throws IOException, TemplateException {
        try (StringWriter result = new StringWriter()) {
            template.process(data, result);
            return result.toString();
        }
    }

}
