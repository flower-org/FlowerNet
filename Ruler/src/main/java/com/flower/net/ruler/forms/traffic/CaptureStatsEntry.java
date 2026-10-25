package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/** Captured requests aggregated by destination host:port. UI thread only. */
public class CaptureStatsEntry {
    private final String host;
    private final int port;
    private final ReadOnlyLongWrapper count = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper lastAccess = new ReadOnlyLongWrapper(Long.MIN_VALUE);
    private final ReadOnlyStringWrapper filterResult = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper from = new ReadOnlyStringWrapper("");
    /** Latest result, kept for re-applying the capture filter */
    private Access access = Access.DENY;
    private boolean isRuleMatched;

    public CaptureStatsEntry(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Counts the request; last access, filter result and source follow the most recent request */
    void record(CapturedRequest request) {
        count.set(count.get() + 1);
        long timestamp = request.getCreationTimestamp();
        if (timestamp >= lastAccess.get()) {
            lastAccess.set(timestamp);
            setResult(request.getAccess(), request.isRuleMatched(), request.isDirectIpBlock());
            from.set(request.getFrom());
        }
    }

    /** Replaces the result with what the current rules give; count, last access and source stay */
    void reevaluate(CheckResult check) {
        setResult(check.checkResult, check.isRuleMatched, check.isDirectIpBlock);
    }

    private void setResult(Access access, boolean isRuleMatched, boolean isDirectIpBlock) {
        this.access = access;
        this.isRuleMatched = isRuleMatched;
        filterResult.set(CapturedRequest.formatFilterResult(access, isRuleMatched, isDirectIpBlock));
    }

    public Access getAccess() { return access; }

    public boolean isRuleMatched() { return isRuleMatched; }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public long getCount() { return count.get(); }

    public ReadOnlyLongProperty countProperty() { return count.getReadOnlyProperty(); }

    public long getLastAccess() { return lastAccess.get(); }

    public ReadOnlyLongProperty lastAccessProperty() { return lastAccess.getReadOnlyProperty(); }

    public String getFilterResult() { return filterResult.get(); }

    public ReadOnlyStringProperty filterResultProperty() { return filterResult.getReadOnlyProperty(); }

    public String getFrom() { return from.get(); }

    public ReadOnlyStringProperty fromProperty() { return from.getReadOnlyProperty(); }
}
