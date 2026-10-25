package com.flower.net.ruler.forms.traffic;

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
            filterResult.set(request.getFilterResult());
            from.set(request.getFrom());
        }
    }

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
