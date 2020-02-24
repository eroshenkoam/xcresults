package io.eroshenkoam.xcresults.export;

public enum  ExportFormat {

    json(new JsonExportFormatter()),

    allure2(new Allure2ExportFormatter());

    private ExportFormatter formatter;

    ExportFormat(final ExportFormatter formatter) {
        this.formatter = formatter;
    }

    public ExportFormatter getFormatter() {
        return formatter;
    }

}
