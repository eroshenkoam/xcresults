package io.eroshenkoam.xcresults.export;

import io.qameta.allure.model.TestResult;

import java.nio.file.Path;
import java.util.Map;

public interface ExportPostProcessor {

    void processTestResults(Path outputPath, Map<Path, TestResult> testResults);

}
