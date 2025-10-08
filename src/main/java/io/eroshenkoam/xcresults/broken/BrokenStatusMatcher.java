package io.eroshenkoam.xcresults.broken;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

public class BrokenStatusMatcher implements Serializable {

    private String messageRegexp;
    private Pattern messagePattern;

    private String traceRegexp;
    private Pattern tracePattern;

    public Pattern getMessagePattern() {
        return messagePattern;
    }

    public String getMessageRegexp() {
        return messageRegexp;
    }

    public BrokenStatusMatcher setMessageRegexp(String messageRegexp) {
        this.messageRegexp = messageRegexp;
        if (Objects.nonNull(messageRegexp)) {
            this.messagePattern = Pattern.compile(messageRegexp);
        }
        return this;
    }

    public Pattern getTracePattern() {
        return tracePattern;
    }

    public String getTraceRegexp() {
        return traceRegexp;
    }

    public BrokenStatusMatcher setTraceRegexp(String traceRegexp) {
        this.traceRegexp = traceRegexp;
        if (Objects.nonNull(traceRegexp)) {
            this.tracePattern = Pattern.compile(traceRegexp);
        }
        return this;
    }
}
