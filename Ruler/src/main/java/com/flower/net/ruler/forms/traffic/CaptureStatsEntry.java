package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import com.flower.net.conntrack.TrafficDirection;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.stream.Collectors;

/** Captured requests aggregated by destination host:port. UI thread only. */
public class CaptureStatsEntry {
    private final String host;
    private final int port;
    private final ReadOnlyLongWrapper count = new ReadOnlyLongWrapper(0);
    private final ReadOnlyLongWrapper lastAccess = new ReadOnlyLongWrapper(Long.MIN_VALUE);
    private final ReadOnlyStringWrapper filterResult = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper from = new ReadOnlyStringWrapper("");
    /** All directions seen: a server's requests and replies show up as OUT and IN, so the last one would flip-flop */
    private final EnumSet<TrafficDirection> directionsSeen = EnumSet.noneOf(TrafficDirection.class);
    private final ReadOnlyStringWrapper directions = new ReadOnlyStringWrapper("");
    /** Direction of the most recent request, for re-evaluating with the direction default */
    private @Nullable TrafficDirection lastDirection;
    /** Latest result, kept for re-applying the capture filter */
    private final ReadOnlyObjectWrapper<Access> access = new ReadOnlyObjectWrapper<>(Access.DENY);
    private boolean isRuleMatched;

    public CaptureStatsEntry(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Counts the request; last access, filter result and source follow the most recent request */
    void record(CapturedRequest request) {
        count.set(count.get() + 1);
        TrafficDirection direction = request.getTrafficDirection();
        if (direction != null && directionsSeen.add(direction)) {
            directions.set(directionsSeen.stream().map(Enum::name).collect(Collectors.joining(", ")));
        }
        long timestamp = request.getCreationTimestamp();
        if (timestamp >= lastAccess.get()) {
            lastAccess.set(timestamp);
            setResult(request.getAccess(), request.isRuleMatched(), request.isDirectIpBlock(), request.isDirectionDefault());
            from.set(request.getFrom());
            lastDirection = direction;
        }
    }

    /** Replaces the result with what the current rules give; count, last access and source stay */
    void reevaluate(CheckResult check) {
        setResult(check.checkResult, check.isRuleMatched, check.isDirectIpBlock, check.isDirectionDefault);
    }

    private void setResult(Access access, boolean isRuleMatched, boolean isDirectIpBlock, boolean isDirectionDefault) {
        this.access.set(access);
        this.isRuleMatched = isRuleMatched;
        filterResult.set(CapturedRequest.formatFilterResult(access, isRuleMatched, isDirectIpBlock, isDirectionDefault));
    }

    public Access getAccess() { return access.get(); }

    public ReadOnlyObjectProperty<Access> accessProperty() { return access.getReadOnlyProperty(); }

    public boolean isRuleMatched() { return isRuleMatched; }

    public String getHost() { return host; }

    public int getPort() { return port; }

    public long getCount() { return count.get(); }

    public ReadOnlyLongProperty countProperty() { return count.getReadOnlyProperty(); }

    public long getLastAccess() { return lastAccess.get(); }

    public ReadOnlyLongProperty lastAccessProperty() { return lastAccess.getReadOnlyProperty(); }

    public String getFilterResult() { return filterResult.get(); }

    public ReadOnlyStringProperty filterResultProperty() { return filterResult.getReadOnlyProperty(); }

    public String getDirections() { return directions.get(); }

    public @Nullable TrafficDirection getLastDirection() { return lastDirection; }

    public ReadOnlyStringProperty directionsProperty() { return directions.getReadOnlyProperty(); }

    public String getFrom() { return from.get(); }

    public ReadOnlyStringProperty fromProperty() { return from.getReadOnlyProperty(); }
}
