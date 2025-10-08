package io.eroshenkoam.xcresults.broken;

import java.io.Serializable;
import java.util.List;

public class BrokenConfig implements Serializable {

    private List<BrokenStatusMatcher> matchers;

    public List<BrokenStatusMatcher> getMatchers() {
        return matchers;
    }

    public void setMatchers(final List<BrokenStatusMatcher> matchers) {
        this.matchers = matchers;
    }
}
