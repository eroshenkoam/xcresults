package io.eroshenkoam.xcresults.export;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StatusDetails;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.WithSteps;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class Allure2ExportFormatter implements ExportFormatter {

    private static final String IDENTIFIER = "identifier";
    private static final String DURATION = "duration";
    private static final String STATUS = "testStatus";

    private static final String ACTIVITY_SUMMARIES = "activitySummaries";
    private static final String ACTIVITY_TITLE = "title";
    private static final String ACTIVITY_START = "start";
    private static final String ACTIVITY_FINISH = "finish";

    private static final String SUBACTIVITIES = "subactivities";

    private static final String ATTACHMENTS = "attachments";

    private static final String NAME = "name";
    private static final String FILENAME = "filename";
    private static final String VALUE = "_value";
    private static final String VALUES = "_values";

    @Override
    public TestResult format(final JsonNode node) {
        final TestResult result = new TestResult()
                .setLabels(new ArrayList<>())
                .setSteps(new ArrayList<>())
                .setAttachments(new ArrayList<>());
        if (node.has(NAME)) {
            result.setName(node.get(NAME).get(VALUE).asText());
        }
        if (node.has(IDENTIFIER)) {
            result.setFullName(node.get(IDENTIFIER).get(VALUE).asText());
        }
        if (node.has(STATUS)) {
            result.setStatus(getTestStatus(node));
        }

        if (node.has(ACTIVITY_SUMMARIES)) {
            final Iterable<JsonNode> activities = node.get(ACTIVITY_SUMMARIES).get(VALUES);
            for (JsonNode activity : activities) {
                parseStep(result, result, activity);
            }
        }

        if (nonNull(result.getStart())) {
            final Double durationText = node.get(DURATION).get(VALUE).asDouble();
            result.setStop(result.getStart() + TimeUnit.SECONDS.toMillis(durationText.longValue()));
        }

        final Optional<StepResult> lastFailedStep = getLastFailed(result.getSteps());
        lastFailedStep.map(StepResult::getStatusDetails).ifPresent(result::setStatusDetails);

        return result;
    }

    @SuppressWarnings("PMD.NcssCount")
    private void parseStep(final TestResult result, final WithSteps parent, final JsonNode activity) {
        final String activityTitle = activity.get(ACTIVITY_TITLE).get(VALUE).asText();

        final Pattern pattern = Pattern.compile("allure\\.label\\.(?<name>.*):(?<value>.*)");
        final Matcher matcher = pattern.matcher(activityTitle);
        if (matcher.matches()) {
            final Label label = new Label()
                    .setName(matcher.group("name"))
                    .setValue(matcher.group("value").trim());
            result.getLabels().add(label);
            return;
        }

        if (activityTitle.startsWith("Start Test at") && activity.has(ACTIVITY_START)) {
            result.setStart(parseDate(activity.get(ACTIVITY_START).get(VALUE).asText()));
            return;
        }

        final StepResult step = new StepResult()
                .setName(activityTitle)
                .setStatus(Status.PASSED)
                .setSteps(new ArrayList<>())
                .setAttachments(new ArrayList<>());

        if (activityTitle.startsWith("Assertion Failure:")) {
            step.setStatusDetails(new StatusDetails()
                    .setMessage(activityTitle));
            step.setStatus(Status.FAILED);
        }

        if (activity.has(ACTIVITY_START) && activity.has(ACTIVITY_FINISH)) {
            step.setStart(parseDate(activity.get(ACTIVITY_START).get(VALUE).asText()));
            step.setStop(parseDate(activity.get(ACTIVITY_FINISH).get(VALUE).asText()));
        }
        if (activity.has(SUBACTIVITIES)) {
            for (JsonNode subActivity : activity.get(SUBACTIVITIES).get(VALUES)) {
                parseStep(result, step, subActivity);
            }
        }
        if (activity.has(ATTACHMENTS)) {
            step.getAttachments().addAll(getAttachments(activity.get(ATTACHMENTS).get(VALUES)));
        }
        result.getSteps().add(step);
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private List<Attachment> getAttachments(final Iterable<JsonNode> activities) {
        final List<Attachment> attachments = new ArrayList<>();
        for (JsonNode activity : activities) {
            final Attachment attachment = new Attachment()
                    .setSource(activity.get(FILENAME).get(VALUE).asText())
                    .setName(activity.get(FILENAME).get(VALUE).asText());
            attachments.add(attachment);
        }
        return attachments;
    }

    protected Optional<StepResult> getLastFailed(final List<StepResult> steps) {
        if (isNull(steps)) {
            return Optional.empty();
        }
        return steps.stream()
                .filter(s -> !s.getStatus().equals(Status.PASSED))
                .reduce((first, second) -> second);
    }

    private Status getTestStatus(final JsonNode node) {
        final String status = node.get(STATUS).get(VALUE).asText();
        if (isNull(status)) {
            return null;
        }
        if ("Success".equals(status)) {
            return Status.PASSED;
        }
        if ("Failure".equals(status)) {
            return Status.FAILED;
        }
        return null;
    }

    @SuppressWarnings("PMD.SimpleDateFormatNeedsLocale")
    protected Long parseDate(final String date) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        try {
            return format.parse(date).getTime();
        } catch (ParseException e) {
            return null;
        }
    }

}
