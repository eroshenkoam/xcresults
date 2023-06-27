package io.eroshenkoam.xcresults.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public class FreemarkerUtil {

    private FreemarkerUtil() {
    }

    public static String render(final String templateName, final Map<String, Object> data)
            throws IOException, TemplateException {
        final Configuration configuration = getDefaultConfiguration();
        Template temp = configuration.getTemplate(templateName);
        try (StringWriter result = new StringWriter()) {
            temp.process(data, result);
            return result.toString();
        }
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

}
